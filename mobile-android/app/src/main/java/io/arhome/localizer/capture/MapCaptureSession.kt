package io.arhome.localizer.capture

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import io.arhome.localizer.depth.RawDepthCapture
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.sqrt

class MapCaptureSession(context: Context, private val session: Session) : AutoCloseable {

    data class Result(
        val sessionId: String,
        val keyframeCount: Int,
        val directory: File,
        val archive: File,
    )

    private val sessionId = "map-${System.currentTimeMillis()}"
    private val startedAt = Instant.now().toString()
    private val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "map-sessions/$sessionId")
    private val images = File(root, "images")
    private val keyframes = JSONArray()
    // Only the latest anchor remains live. Each accepted frame is linked to it
    // before the previous anchor is detached, so both poses come from one update.
    private var lastCaptureAnchor: Anchor? = null
    private var lastMapPose = Pose.IDENTITY
    private var poseChainTimestampNs = 0L
    private var poseChainCount = 0
    private var pausedAnchorSinceNs = 0L
    private var lastCaptureTimestampNs = 0L
    private var closed = false
    private val depthEnabled = session.config.depthMode != Config.DepthMode.DISABLED
    @Volatile var depthFrameCount: Int = 0
        private set
    @Volatile var depthStatus: String = if (depthEnabled) "Esperando mediciones de profundidad" else "Profundidad no disponible en esta sesión"
        private set
    @Volatile var captureState: String = "INICIALIZANDO"
        private set
    val isClosed: Boolean get() = closed

    @Volatile
    var keyframeCount: Int = 0
        private set

    @Volatile
    var guidance: String = "Mira la cajonera y sus alrededores. Desplázate despacio hacia un lado, manteniéndolos en pantalla."
        private set

    init {
        check(images.mkdirs() || images.isDirectory) { "Could not create map session directory: ${images.absolutePath}" }
        writeManifest(null)
    }

    @Synchronized
    fun onFrame(frame: Frame) {
        if (closed) return
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            captureState = "SEGUIMIENTO_PERDIDO"
            guidance = "Seguimiento perdido: detente y vuelve a mirar una zona con detalles."
            return
        }
        if (keyframeCount >= 80) {
            captureState = "LIMITE_ALCANZADO"
            guidance = "Has llegado a 80 fotos. Pulsa detener y exportar para comprobar el mapa."
            return
        }
        val pose = frame.camera.pose
        val previousAnchor = lastCaptureAnchor
        if (previousAnchor != null && previousAnchor.trackingState != TrackingState.TRACKING) {
            if (pausedAnchorSinceNs == 0L) pausedAnchorSinceNs = frame.timestamp
            if (keyframeCount == 1 && (previousAnchor.trackingState == TrackingState.STOPPED ||
                    frame.timestamp - pausedAnchorSinceNs >= SINGLE_REFERENCE_RECOVERY_NS)) {
                discardSingleLostReference()
                captureState = "REINICIO_AUTOMATICO"
                guidance = "La sesión perdió la primera referencia y la he descartado. Mantén esta zona visible mientras reinicio la captura."
                return
            }
            captureState = "REFERENCIA_${previousAnchor.trackingState.name}"
            guidance = "La sesión se interrumpió y la última referencia está ${previousAnchor.trackingState.name}. " +
                "Mira la zona de la última foto hasta recuperarla."
            return
        }
        pausedAnchorSinceNs = 0L
        if (!shouldCapture(pose, frame.timestamp)) return

        try {
            frame.acquireCameraImage().use { image ->
                val depth = if (depthEnabled) RawDepthCapture.capture(frame, image.width, image.height) else null
                if (depthEnabled && depth == null) {
                    captureState = "ESPERANDO_PROFUNDIDAD"
                    guidance = "Posición nueva detectada. Mantén el móvil estable un instante para recibir profundidad."
                    depthStatus = "Movimiento suficiente; esperando la siguiente medición nueva de profundidad."
                    return
                }
                val id = "%05d".format(keyframeCount)
                val imageName = "$id.jpg"
                val newAnchor = session.createAnchor(pose)
                val prepared = try {
                    check(newAnchor.trackingState == TrackingState.TRACKING) { "New capture anchor is not tracking" }
                    val mapPose = if (previousAnchor == null) Pose.IDENTITY else {
                        CapturePoseChain.append(lastMapPose, previousAnchor.pose, newAnchor.pose)
                    }
                    File(images, imageName).writeBytes(image.toJpeg(90))

                    val intrinsics = frame.camera.imageIntrinsics
                    val keyframe = JSONObject()
                        .put("id", id)
                        .put("image", "images/$imageName")
                        .put("timestampNs", image.timestamp)
                        .put("frameTimestampNs", frame.timestamp)
                        .put("depth", depth?.save(root, id) ?: JSONObject.NULL)
                        .put("capturePoseTranslationMeters", JSONArray(pose.translation.toList()))
                        .put("capturePoseRotationQuaternion", JSONArray(pose.rotationQuaternion.toList()))
                        .put("poseTranslationMeters", JSONArray(mapPose.translation.toList()))
                        .put("poseRotationQuaternion", JSONArray(mapPose.rotationQuaternion.toList()))
                        .put(
                            "intrinsics",
                            JSONObject()
                                .put("focalLengthPixels", JSONArray(intrinsics.focalLength.toList()))
                                .put("principalPointPixels", JSONArray(intrinsics.principalPoint.toList()))
                                .put("imageDimensionsPixels", JSONArray(intrinsics.imageDimensions.toList())),
                        )
                    mapPose to keyframe
                } catch (error: Exception) {
                    newAnchor.detach()
                    throw error
                }
                val (mapPose, keyframe) = prepared
                keyframes.put(keyframe)
                keyframeCount++
                poseChainCount = keyframeCount
                poseChainTimestampNs = frame.timestamp
                lastMapPose = mapPose
                lastCaptureAnchor = newAnchor
                previousAnchor?.detach()
                if (depth != null) {
                    depthFrameCount++
                    depthStatus = if (depth.confidentPixels == 0)
                        "Foto guardada sin distancias de confianza alta. Acércate a una zona con más detalles."
                    else "Distancias guardadas en $depthFrameCount fotos. Comprobación del mapa pendiente."
                }
                lastCaptureTimestampNs = frame.timestamp
                captureState = "FOTO_GUARDADA"
                guidance = "${keyframeCount}/80 fotos. Mantén zonas ya vistas en pantalla y cambia de posición; no solo gires el móvil."
                writeManifest(null)
            }
        } catch (_: NotYetAvailableException) {
            captureState = "ESPERANDO_IMAGEN"
            guidance = "Posición nueva detectada. Mantén el móvil estable un instante para guardar la foto."
        } catch (_: ResourceExhaustedException) {
            captureState = "LIMITE_DE_RECURSOS"
            guidance = "Límite de referencias del dispositivo: detén y exporta esta captura."
        }
    }

    @Synchronized
    fun finish(): Result {
        check(!closed) { "Map capture session is already finished" }
        check(keyframeCount >= 12) { "Faltan vistas: captura al menos 12 fotos antes de exportar." }
        check(poseChainCount == keyframeCount && poseChainTimestampNs > 0) {
            "La cadena de posiciones de la captura está incompleta."
        }
        writeManifest(Instant.now().toString())
        closed = true
        releaseAnchors()
        val archive = File(root.parentFile, "$sessionId.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        check(archive.isFile && archive.length() > 0L) { "Map capture archive was not written" }
        return Result(sessionId, keyframeCount, root, archive)
    }

    private fun shouldCapture(pose: Pose, timestampNs: Long): Boolean {
        val previous = lastCaptureAnchor?.pose ?: return true
        val elapsedMs = (timestampNs - lastCaptureTimestampNs) / 1_000_000.0
        if (elapsedMs < MIN_INTERVAL_MS) {
            captureState = "ESPERANDO_INTERVALO"
            return false
        }
        val distance = translationDistance(previous, pose)
        val angle = rotationDegrees(previous, pose)
        val moved = distance >= MIN_TRANSLATION_METERS || angle >= MIN_ROTATION_DEGREES
        if (!moved) {
            captureState = "ESPERANDO_MOVIMIENTO"
            val remainingCm = ceil((MIN_TRANSLATION_METERS - distance) * 100).toInt().coerceAtLeast(0)
            val remainingDegrees = ceil(MIN_ROTATION_DEGREES - angle).toInt().coerceAtLeast(0)
            guidance = "Siguiente foto: desplázate $remainingCm cm más o gira $remainingDegrees° manteniendo detalles ya vistos."
        }
        return moved
    }

    private fun releaseAnchors() {
        lastCaptureAnchor?.detach()
        lastCaptureAnchor = null
    }

    private fun discardSingleLostReference() {
        check(keyframeCount == 1)
        lastCaptureAnchor?.detach()
        lastCaptureAnchor = null
        File(images, "00000.jpg").delete()
        File(root, "depth/00000.u16le").delete()
        File(root, "depth/00000.confidence.u8").delete()
        keyframes.remove(0)
        keyframeCount = 0
        depthFrameCount = 0
        lastMapPose = Pose.IDENTITY
        poseChainCount = 0
        poseChainTimestampNs = 0L
        lastCaptureTimestampNs = 0L
        pausedAnchorSinceNs = 0L
        depthStatus = if (depthEnabled) "Esperando mediciones de profundidad" else "Profundidad no disponible en esta sesión"
        writeManifest(null)
    }

    @Synchronized
    override fun close() {
        closed = true
        releaseAnchors()
    }

    private fun writeManifest(completedAt: String?) {
        val manifest = JSONObject()
            .put("schemaVersion", 3)
            .put("landmarkSource", if (depthEnabled) "RAW_DEPTH" else "TRIANGULATED_RGB")
            .put("depthFrameCount", depthFrameCount)
            .put("sessionId", sessionId)
            .put("startedAt", startedAt)
            .put("completedAt", completedAt ?: JSONObject.NULL)
            .put("coordinateFrame", "ARCORE_PAIRWISE_ANCHOR_CHAIN")
            .put("poseChainTimestampNs", poseChainTimestampNs)
            .put("poseChainCount", poseChainCount)
            .put("poseReference", "FIRST_KEYFRAME_ANCHOR_CHAIN")
            .put(
                "keyframePolicy",
                JSONObject()
                    .put("minIntervalMs", MIN_INTERVAL_MS)
                    .put("minTranslationMeters", MIN_TRANSLATION_METERS)
                    .put("minRotationDegrees", MIN_ROTATION_DEGREES),
            )
            .put("keyframes", keyframes)
        File(root, "manifest.json").writeText(manifest.toString(2))
    }

    private fun translationDistance(a: Pose, b: Pose): Double {
        val at = a.translation
        val bt = b.translation
        val dx = (at[0] - bt[0]).toDouble()
        val dy = (at[1] - bt[1]).toDouble()
        val dz = (at[2] - bt[2]).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun rotationDegrees(a: Pose, b: Pose): Double {
        val aq = a.rotationQuaternion
        val bq = b.rotationQuaternion
        val dot = abs(
            aq[0] * bq[0] + aq[1] * bq[1] + aq[2] * bq[2] + aq[3] * bq[3],
        ).coerceIn(0f, 1f)
        return Math.toDegrees(2.0 * acos(dot.toDouble()))
    }

    private fun Image.toJpeg(quality: Int): ByteArray {
        check(format == ImageFormat.YUV_420_888) { "Expected YUV_420_888 camera image, got $format" }
        val nv21 = ByteArray(width * height * 3 / 2)
        copyPlane(planes[0], width, height, nv21, 0, 1)
        copyPlane(planes[2], width / 2, height / 2, nv21, width * height, 2)
        copyPlane(planes[1], width / 2, height / 2, nv21, width * height + 1, 2)
        return ByteArrayOutputStream().use { output ->
            check(YuvImage(nv21, ImageFormat.NV21, width, height, null).compressToJpeg(Rect(0, 0, width, height), quality, output))
            output.toByteArray()
        }
    }

    private fun copyPlane(
        plane: Image.Plane,
        planeWidth: Int,
        planeHeight: Int,
        target: ByteArray,
        targetOffset: Int,
        targetPixelStride: Int,
    ) {
        val buffer = plane.buffer.duplicate().apply { rewind() }
        var out = targetOffset
        for (row in 0 until planeHeight) {
            val rowStart = row * plane.rowStride
            for (column in 0 until planeWidth) {
                target[out] = buffer.get(rowStart + column * plane.pixelStride)
                out += targetPixelStride
            }
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 500.0
        private const val MIN_TRANSLATION_METERS = 0.20
        private const val MIN_ROTATION_DEGREES = 12.0
        private const val SINGLE_REFERENCE_RECOVERY_NS = 2_000_000_000L
    }
}
