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

    data class CaptureStatus(
        val guidance: String = "initializing",
        val translationMeters: Double = 0.0,
        val rotationDegrees: Double = 0.0,
        val cameraImageRetries: Int = 0,
        val depthKeyframes: Int = 0,
    )

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
    private val depthImages = File(root, "depth")
    private val confidenceImages = File(root, "confidence")
    private val keyframes = JSONArray()
    private val capturePolicy = KeyframeCapturePolicy()
    private var lastPose: Pose? = null
    private var lastCaptureTimestampNs = 0L
    private var closed = false

    @Volatile
    var keyframeCount: Int = 0
        private set

    @Volatile
    var status = CaptureStatus()
        private set

    init {
        check(images.mkdirs() || images.isDirectory) { "Could not create map session directory: ${images.absolutePath}" }
        check(depthImages.mkdirs() || depthImages.isDirectory) { "Could not create depth directory" }
        check(confidenceImages.mkdirs() || confidenceImages.isDirectory) { "Could not create confidence directory" }
        writeManifest(null)
    }

    @Synchronized
    fun onFrame(frame: Frame) {
        if (closed || frame.camera.trackingState != TrackingState.TRACKING) return
        val pose = frame.camera.pose
        val decision = captureDecision(pose, frame.timestamp)
        status = status.copy(
            guidance = decision.reason,
            translationMeters = decision.translationMeters,
            rotationDegrees = decision.rotationDegrees,
        )
        if (!decision.capture) return

        try {
            val id = "%05d".format(keyframeCount)
            val imageName = "$id.jpg"
            val imageTimestamp = frame.acquireCameraImage().use { image ->
                File(images, imageName).writeBytes(image.toJpeg(90))
                image.timestamp
            }
            val intrinsics = frame.camera.imageIntrinsics
            val keyframe = JSONObject()
                        .put("id", id)
                        .put("image", "images/$imageName")
                        .put("timestampNs", imageTimestamp)
                        .put("poseTranslationMeters", JSONArray(pose.translation.toList()))
                        .put("poseRotationQuaternion", JSONArray(pose.rotationQuaternion.toList()))
                        .put(
                            "intrinsics",
                            JSONObject()
                                .put("focalLengthPixels", JSONArray(intrinsics.focalLength.toList()))
                                .put("principalPointPixels", JSONArray(intrinsics.principalPoint.toList()))
                                .put("imageDimensionsPixels", JSONArray(intrinsics.imageDimensions.toList())),
                        )
            captureOptionalDepth(frame, id)?.let { (depth, confidence) ->
                keyframe.put("rawDepth", depth)
                keyframe.put("rawDepthConfidence", confidence)
                status = status.copy(depthKeyframes = status.depthKeyframes + 1)
            }
            keyframes.put(keyframe)
            keyframeCount++
            lastPose = pose
            lastCaptureTimestampNs = frame.timestamp
            status = status.copy(guidance = "captured $id — keep orbiting")
            writeManifest(null)
        } catch (_: NotYetAvailableException) {
            status = status.copy(
                guidance = "camera frame busy — keep moving slowly",
                cameraImageRetries = status.cameraImageRetries + 1,
            )
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

    private data class PoseDecision(
        val capture: Boolean,
        val reason: String,
        val translationMeters: Double,
        val rotationDegrees: Double,
    )

    private fun captureDecision(pose: Pose, timestampNs: Long): PoseDecision {
        val previous = lastPose ?: return PoseDecision(true, "first viewpoint", 0.0, 0.0)
        val elapsedMs = (timestampNs - lastCaptureTimestampNs) / 1_000_000.0
        val translation = translationDistance(previous, pose)
        val rotation = rotationDegrees(previous, pose)
        val decision = capturePolicy.evaluate(elapsedMs, translation, rotation)
        return PoseDecision(decision.capture, decision.reason, translation, rotation)
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
                    .put("mode", "RGB_VIO_WITH_OPTIONAL_DEPTH")
                    .put("minIntervalMs", KeyframeCapturePolicy.MIN_INTERVAL_MS)
                    .put("minTranslationMeters", KeyframeCapturePolicy.MIN_TRANSLATION_METERS)
                    .put("minRotationDegrees", KeyframeCapturePolicy.MIN_ROTATION_DEGREES)
                    .put("slowCaptureIntervalMs", KeyframeCapturePolicy.SLOW_CAPTURE_INTERVAL_MS)
                    .put("slowTranslationMeters", KeyframeCapturePolicy.SLOW_TRANSLATION_METERS)
                    .put("slowRotationDegrees", KeyframeCapturePolicy.SLOW_ROTATION_DEGREES),
            )
            .put("depthKeyframeCount", status.depthKeyframes)
            .put("cameraImageRetries", status.cameraImageRetries)
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

    private fun captureOptionalDepth(frame: Frame, id: String): Pair<JSONObject, JSONObject>? = try {
        val depth = frame.acquireRawDepthImage16Bits().use { image ->
            val name = "$id.depth16"
            File(depthImages, name).writeBytes(image.packedPlaneBytes(2))
            JSONObject()
                .put("file", "depth/$name")
                .put("width", image.width)
                .put("height", image.height)
                .put("timestampNs", image.timestamp)
                .put("format", "DEPTH16_LE")
        }
        val confidence = frame.acquireRawDepthConfidenceImage().use { image ->
            val name = "$id.confidence8"
            File(confidenceImages, name).writeBytes(image.packedPlaneBytes(1))
            JSONObject()
                .put("file", "confidence/$name")
                .put("width", image.width)
                .put("height", image.height)
                .put("timestampNs", image.timestamp)
                .put("format", "UINT8")
        }
        depth to confidence
    } catch (_: NotYetAvailableException) {
        null
    } catch (_: IllegalStateException) {
        // Depth is an enhancement; RGB + VIO remains a complete capture path.
        null
    }

    private fun Image.packedPlaneBytes(bytesPerPixel: Int): ByteArray {
        val plane = planes[0]
        val buffer = plane.buffer.duplicate().apply { rewind() }
        val output = ByteArray(width * height * bytesPerPixel)
        var target = 0
        for (row in 0 until height) {
            val rowStart = row * plane.rowStride
            for (column in 0 until width) {
                val pixelStart = rowStart + column * plane.pixelStride
                repeat(bytesPerPixel) { offset -> output[target++] = buffer.get(pixelStart + offset) }
            }
        }
        return output
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

}
