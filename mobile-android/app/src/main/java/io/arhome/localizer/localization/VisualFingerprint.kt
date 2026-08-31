package io.arhome.localizer.localization

import android.graphics.BitmapFactory
import android.media.Image
import java.io.File
import kotlin.math.sqrt
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class VisualFingerprint(
    val values: FloatArray,
    val contrast: Float,
) {
    fun correlation(other: VisualFingerprint): Float {
        if (values.size != other.values.size) return -1f
        var sum = 0f
        for (index in values.indices) sum += values[index] * other.values[index]
        return sum.coerceIn(-1f, 1f)
    }

    companion object {
        private const val WIDTH = 32
        private const val HEIGHT = 24
        private const val SAMPLE_COUNT = WIDTH * HEIGHT

        fun fromJpeg(file: File): VisualFingerprint {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: error("Could not decode keyframe: ${file.absolutePath}")
            try {
                val samples = FloatArray(SAMPLE_COUNT)
                var index = 0
                for (y in 0 until HEIGHT) {
                    val sourceY = ((y + 0.5f) * bitmap.height / HEIGHT).toInt().coerceIn(0, bitmap.height - 1)
                    for (x in 0 until WIDTH) {
                        val sourceX = ((x + 0.5f) * bitmap.width / WIDTH).toInt().coerceIn(0, bitmap.width - 1)
                        val pixel = bitmap.getPixel(sourceX, sourceY)
                        val red = pixel shr 16 and 0xff
                        val green = pixel shr 8 and 0xff
                        val blue = pixel and 0xff
                        samples[index++] = (77 * red + 150 * green + 29 * blue) / 256f
                    }
                }
                return normalize(samples)
            } finally {
                bitmap.recycle()
            }
        }

        fun fromCameraImage(image: Image): VisualFingerprint {
            val plane = image.planes[0]
            val buffer = plane.buffer.duplicate()
            val samples = FloatArray(SAMPLE_COUNT)
            var index = 0
            for (y in 0 until HEIGHT) {
                val sourceY = ((y + 0.5f) * image.height / HEIGHT).toInt().coerceIn(0, image.height - 1)
                for (x in 0 until WIDTH) {
                    val sourceX = ((x + 0.5f) * image.width / WIDTH).toInt().coerceIn(0, image.width - 1)
                    val offset = sourceY * plane.rowStride + sourceX * plane.pixelStride
                    samples[index++] = (buffer.get(offset).toInt() and 0xff).toFloat()
                }
            }
            return normalize(samples)
        }

        fun fromGrayMat(gray: Mat): VisualFingerprint {
            val resized = Mat()
            return try {
                Imgproc.resize(gray, resized, Size(WIDTH.toDouble(), HEIGHT.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                val pixels = ByteArray(SAMPLE_COUNT)
                resized.get(0, 0, pixels)
                normalize(FloatArray(SAMPLE_COUNT) { index -> (pixels[index].toInt() and 0xff).toFloat() })
            } finally {
                resized.release()
            }
        }

        internal fun normalize(samples: FloatArray): VisualFingerprint {
            val mean = samples.average().toFloat()
            var variance = 0f
            for (value in samples) {
                val delta = value - mean
                variance += delta * delta
            }
            variance /= samples.size
            val contrast = sqrt(variance)
            val scale = if (contrast > 0.001f) contrast * sqrt(samples.size.toFloat()) else 1f
            return VisualFingerprint(
                FloatArray(samples.size) { index -> (samples[index] - mean) / scale },
                contrast,
            )
        }
    }
}
