package io.arhome.localizer.depth

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.floor

/** Pure geometry and binary layout helpers, shared by capture and reconstruction. */
object DepthGeometry {
    const val MIN_CONFIDENCE = 192 // Engineering policy, not a probability of correctness.
    const val MIN_MM = 200
    const val MAX_MM = 8000

    fun packedPlane(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int, bytesPerPixel: Int): ByteArray {
        require(width > 0 && height > 0 && pixelStride >= bytesPerPixel && bytesPerPixel in 1..2)
        require(rowStride >= (width - 1) * pixelStride + bytesPerPixel)
        val start = buffer.position()
        require(start.toLong() + (height - 1L) * rowStride + (width - 1L) * pixelStride + bytesPerPixel <= buffer.limit())
        return ByteArray(Math.multiplyExact(Math.multiplyExact(width, height), bytesPerPixel)).also { out ->
            for (y in 0 until height) for (x in 0 until width) for (b in 0 until bytesPerPixel) {
                out[(y * width + x) * bytesPerPixel + b] = buffer.get(start + y * rowStride + x * pixelStride + b)
            }
        }
    }

    fun millimeters(bytes: ByteArray, index: Int): Int =
        (bytes[index * 2].toInt() and 255) or ((bytes[index * 2 + 1].toInt() and 255) shl 8)

    /** Affine map from unrotated CPU image pixels to unrotated depth UVs. */
    fun depthPixel(x: Double, y: Double, affine: FloatArray, width: Int, height: Int): Int? {
        require(affine.size == 6 && affine.all { it.isFinite() } && width > 0 && height > 0)
        val u = affine[0] * x + affine[1] * y + affine[2]
        val v = affine[3] * x + affine[4] * y + affine[5]
        if (!u.isFinite() || !v.isFinite() || u < 0 || v < 0 || u >= 1 || v >= 1) return null
        return floor(v * height).toInt() * width + floor(u * width).toInt()
    }

    /** Reject missing/low-confidence measurements and mixed foreground/background neighborhoods. */
    fun stableDepthMeters(depth: ByteArray, confidence: ByteArray, width: Int, height: Int, index: Int): Double? {
        require(depth.size == width * height * 2 && confidence.size == width * height)
        val x = index % width; val y = index / width
        if (x !in 1 until width - 1 || y !in 1 until height - 1) return null
        val center = millimeters(depth, index)
        if (center !in MIN_MM..MAX_MM || (confidence[index].toInt() and 255) < MIN_CONFIDENCE) return null
        var count = 0
        for (dy in -1..1) for (dx in -1..1) {
            val n = index + dy * width + dx
            if ((confidence[n].toInt() and 255) < MIN_CONFIDENCE) continue
            val value = millimeters(depth, n)
            if (value !in MIN_MM..MAX_MM) continue
            if (abs(value - center) > maxOf(50.0, center * 0.05)) return null
            count++
        }
        return if (count >= 5) center / 1000.0 else null
    }

    /** Raw depth is optical-axis Z; ARCore uses +X right, +Y up and -Z forward. */
    fun cameraPoint(x: Double, y: Double, z: Double, fx: Double, fy: Double, cx: Double, cy: Double): FloatArray {
        require(listOf(x, y, z, fx, fy, cx, cy).all { it.isFinite() } && fx > 0 && fy > 0 && z > 0)
        return floatArrayOf(((x - cx) * z / fx).toFloat(), (-(y - cy) * z / fy).toFloat(), -z.toFloat())
    }
}
