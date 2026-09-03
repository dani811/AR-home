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
    // Keep tracked references, not numerical world poses from unrelated frames.
    private val cameraAnchors = ArrayList<Anchor>()
    private var snapshotTimestampNs = 0L
    private var snapshotCount = 0
    private var lastCaptureTimestampNs = 0L
    private var closed = false
    private val depthEnabled = session.config.depthMode != Config.DepthMode.DISABLED
    @Volatile var depthFrameCount: Int = 0
        private set
    @Volatile var depthStatus: String = if (depthEnabled) "Esperando mediciones de profundidad" else "Profundidad no disponible en esta sesión"
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
            guidance = "Seguimiento perdido: detente y vuelve a mirar una zona con detalles."
            return
        }
        refreshPoseSnapshot(frame.timestamp)
        if (keyframeCount >= 80) {
            guidance = "Has llegado a 80 fotos. Pulsa detener y exportar para comprobar el mapa."
            return
        }
        val pose = frame.camera.pose
        if (cameraAnchors.any { it.trackingState != TrackingState.TRACKING }) {
            guidance = "Estoy recuperando las referencias anteriores. Vuelve despacio a la zona inicial."
            return
        }
        if (!shouldCapture(pose, frame.timestamp)) return

        try {
            frame.acquireCameraImage().use { image ->
                val depth = if (depthEnabled) RawDepthCapture.capture(frame, image.width, image.height) else null
                if (depthEnabled && depth == null) {
                    depthStatus = "Esperando una medición nueva; muévete despacio manteniendo detalles visibles."
                    return
                }
                val id = "%05d".format(keyframeCount)
                val imageName = "$id.jpg"
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
                        .put("poseTranslationMeters", JSONArray(pose.translation.toList()))
                        .put("poseRotationQuaternion", JSONArray(pose.rotationQuaternion.toList()))
                        .put(
                            "intrinsics",
                            JSONObject()
                                .put("focalLengthPixels", JSONArray(intrinsics.focalLength.toList()))
                                .put("principalPointPixels", JSONArray(intrinsics.principalPoint.toList()))
                                .put("imageDimensionsPixels", JSONArray(intrinsics.imageDimensions.toList())),
                        )
                val anchor = session.createAnchor(pose)
                cameraAnchors.add(anchor)
                keyframes.put(keyframe)
                keyframeCount++
                if (depth != null) {
                    depthFrameCount++
                    depthStatus = if (depth.confidentPixels == 0)
                        "Foto guardada sin distancias de confianza alta. Acércate a una zona con más detalles."
                    else "Distancias guardadas en $depthFrameCount fotos. Comprobación del mapa pendiente."
                }
                lastCaptureTimestampNs = frame.timestamp
                refreshPoseSnapshot(frame.timestamp, true)
                guidance = "${keyframeCount}/80 fotos. Mantén zonas ya vistas en pantalla y cambia de posición; no solo gires el móvil."
                writeManifest(null)
            }
        } catch (_: NotYetAvailableException) {
            // A later tracked frame will be eligible again.
        } catch (_: ResourceExhaustedException) {
            guidance = "Límite de referencias del dispositivo: detén y exporta esta captura."
        }
    }

    @Synchronized
    fun finish(): Result {
        check(!closed) { "Map capture session is already finished" }
        check(keyframeCount >= 12) { "Faltan vistas: captura al menos 12 fotos antes de exportar." }
        check(snapshotCount == keyframeCount && snapshotTimestampNs > 0) {
            "Espera a recuperar el seguimiento de todas las referencias antes de exportar."
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
        val previous = cameraAnchors.lastOrNull()?.pose ?: return true
        val elapsedMs = (timestampNs - lastCaptureTimestampNs) / 1_000_000.0
        if (elapsedMs < MIN_INTERVAL_MS) return false
        return translationDistance(previous, pose) >= MIN_TRANSLATION_METERS ||
            rotationDegrees(previous, pose) >= MIN_ROTATION_DEGREES
    }

    /** Called only from the frame consumer: all exported poses use one ARCore update. */
    private fun refreshPoseSnapshot(timestampNs: Long, force: Boolean = false) {
        if (!force && timestampNs - snapshotTimestampNs < 250_000_000L) return
        if (cameraAnchors.isEmpty() || cameraAnchors.any { it.trackingState != TrackingState.TRACKING }) return
        val currentPoses = cameraAnchors.map { it.pose }
        val mapFromWorld = currentPoses.first().inverse()
        currentPoses.forEachIndexed { index, worldPose ->
            val pose = mapFromWorld.compose(worldPose)
            keyframes.getJSONObject(index)
                .put("poseTranslationMeters", JSONArray(pose.translation.toList()))
                .put("poseRotationQuaternion", JSONArray(pose.rotationQuaternion.toList()))
        }
        snapshotTimestampNs = timestampNs
        snapshotCount = currentPoses.size
    }

    private fun releaseAnchors() {
        cameraAnchors.forEach { it.detach() }
        cameraAnchors.clear()
    }

    @Synchronized
    override fun close() {
        closed = true
        releaseAnchors()
    }

    private fun writeManifest(completedAt: String?) {
        val manifest = JSONObject()
            .put("schemaVersion", 2)
            .put("landmarkSource", if (depthEnabled) "RAW_DEPTH" else "TRIANGULATED_RGB")
            .put("depthFrameCount", depthFrameCount)
            .put("sessionId", sessionId)
            .put("startedAt", startedAt)
            .put("completedAt", completedAt ?: JSONObject.NULL)
            .put("coordinateFrame", "ARCORE_ANCHOR_SNAPSHOT")
            .put("poseSnapshotTimestampNs", snapshotTimestampNs)
            .put("poseSnapshotCount", snapshotCount)
            .put("poseReference", "FIRST_KEYFRAME_ANCHOR")
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
    }
}
