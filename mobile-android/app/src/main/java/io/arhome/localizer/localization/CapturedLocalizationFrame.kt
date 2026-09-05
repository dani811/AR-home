package io.arhome.localizer.localization

import android.media.Image
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.exceptions.NotYetAvailableException
import org.opencv.core.CvType
import org.opencv.core.Mat

class CapturedLocalizationFrame private constructor(
    private val grayPixels: ByteArray,
    private val width: Int,
    private val height: Int,
    val frameTimestampNs: Long,
    val imageTimestampNs: Long,
    val cameraPose: Pose,
    val focalLengthPixels: FloatArray,
    val principalPointPixels: FloatArray,
) : AutoCloseable {

    private val grayDelegate = lazy(LazyThreadSafetyMode.NONE) {
        Mat(height, width, CvType.CV_8UC1).also { it.put(0, 0, grayPixels) }
    }

    val gray: Mat
        get() = grayDelegate.value

    override fun close() {
        if (grayDelegate.isInitialized()) grayDelegate.value.release()
    }

    companion object {
        /** Recorded images retain sensor orientation and their original calibration. */
        fun fromKeyframe(keyframe: io.arhome.localizer.map.PersistentKeyframe, attempt: Int): CapturedLocalizationFrame {
            val image = org.opencv.imgcodecs.Imgcodecs.imread(keyframe.image.absolutePath, org.opencv.imgcodecs.Imgcodecs.IMREAD_GRAYSCALE)
            try {
                require(!image.empty()) { "Unreadable image: ${keyframe.id}" }
                val bytes = ByteArray(image.rows() * image.cols())
                image.get(0, 0, bytes)
                return CapturedLocalizationFrame(bytes, image.cols(), image.rows(),
                    (attempt + 1L) * 1_000_000_000L, keyframe.timestampNs, keyframe.pose,
                    keyframe.focalLengthPixels, keyframe.principalPointPixels)
            } finally {
                image.release()
            }
        }

        fun capture(frame: Frame): CapturedLocalizationFrame? {
            val image = try {
                frame.acquireCameraImage()
            } catch (_: NotYetAvailableException) {
                return null
            }
            return image.use {
                val intrinsics = frame.camera.imageIntrinsics
                CapturedLocalizationFrame(
                    grayPixels = copyLuma(it),
                    width = it.width,
                    height = it.height,
                    frameTimestampNs = frame.timestamp,
                    imageTimestampNs = it.timestamp,
                    cameraPose = frame.camera.pose,
                    focalLengthPixels = intrinsics.focalLength,
                    principalPointPixels = intrinsics.principalPoint,
                )
            }
        }

        private fun copyLuma(image: Image): ByteArray {
            val plane = image.planes[0]
            val buffer = plane.buffer.duplicate()
            val pixels = ByteArray(image.width * image.height)
            var target = 0
            if (plane.pixelStride == 1) {
                for (y in 0 until image.height) {
                    buffer.position(y * plane.rowStride)
                    buffer.get(pixels, target, image.width)
                    target += image.width
                }
                return pixels
            }
            for (y in 0 until image.height) {
                val row = y * plane.rowStride
                for (x in 0 until image.width) {
                    pixels[target++] = buffer.get(row + x * plane.pixelStride)
                }
            }
            return pixels
        }
    }
}
