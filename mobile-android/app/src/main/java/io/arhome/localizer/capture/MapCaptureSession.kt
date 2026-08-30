package io.arhome.localizer.capture

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
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

class MapCaptureSession(context: Context) {

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
    private var lastPose: Pose? = null
    private var lastCaptureTimestampNs = 0L
    private var closed = false

    @Volatile
    var keyframeCount: Int = 0
        private set

    init {
        check(images.mkdirs() || images.isDirectory) { "Could not create map session directory: ${images.absolutePath}" }
        writeManifest(null)
    }

    @Synchronized
    fun onFrame(frame: Frame) {
        if (closed || frame.camera.trackingState != TrackingState.TRACKING) return
        val pose = frame.camera.pose
        if (!shouldCapture(pose, frame.timestamp)) return

        try {
            frame.acquireCameraImage().use { image ->
                val id = "%05d".format(keyframeCount)
                val imageName = "$id.jpg"
                File(images, imageName).writeBytes(image.toJpeg(90))

                val intrinsics = frame.camera.imageIntrinsics
                keyframes.put(
                    JSONObject()
                        .put("id", id)
                        .put("image", "images/$imageName")
                        .put("timestampNs", image.timestamp)
                        .put("poseTranslationMeters", JSONArray(pose.translation.toList()))
                        .put("poseRotationQuaternion", JSONArray(pose.rotationQuaternion.toList()))
                        .put(
                            "intrinsics",
                            JSONObject()
                                .put("focalLengthPixels", JSONArray(intrinsics.focalLength.toList()))
                                .put("principalPointPixels", JSONArray(intrinsics.principalPoint.toList()))
                                .put("imageDimensionsPixels", JSONArray(intrinsics.imageDimensions.toList())),
                        )
                )
                keyframeCount++
                lastPose = pose
                lastCaptureTimestampNs = frame.timestamp
                writeManifest(null)
            }
        } catch (_: NotYetAvailableException) {
            // A later tracked frame will be eligible again.
        }
    }

    @Synchronized
    fun finish(): Result {
        check(!closed) { "Map capture session is already finished" }
        closed = true
        writeManifest(Instant.now().toString())
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
        val previous = lastPose ?: return true
        val elapsedMs = (timestampNs - lastCaptureTimestampNs) / 1_000_000.0
        if (elapsedMs < MIN_INTERVAL_MS) return false
        return translationDistance(previous, pose) >= MIN_TRANSLATION_METERS ||
            rotationDegrees(previous, pose) >= MIN_ROTATION_DEGREES
    }

    private fun writeManifest(completedAt: String?) {
        val manifest = JSONObject()
            .put("schemaVersion", 1)
            .put("sessionId", sessionId)
            .put("startedAt", startedAt)
            .put("completedAt", completedAt ?: JSONObject.NULL)
            .put("coordinateFrame", "ARCORE_SESSION_LOCAL")
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
