package io.arhome.localizer.depth

import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class RawDepthStatus { FRESH, REPROJECTED, DIMENSION_MISMATCH, INVALID_ALIGNMENT, NOT_YET_AVAILABLE }

data class RawDepthAttempt(
    val sample: RawDepthCapture?,
    val status: RawDepthStatus,
    val detail: String,
)

class RawDepthCapture private constructor(
    val width: Int,
    val height: Int,
    val timestampNs: Long,
    val confidenceTimestampNs: Long,
    val millimeters: ByteArray,
    val confidence: ByteArray,
    val imageToDepthUv: FloatArray,
) {
    val confidentPixels: Int get() = confidence.indices.count {
        (confidence[it].toInt() and 255) >= DepthGeometry.MIN_CONFIDENCE &&
            DepthGeometry.millimeters(millimeters, it) in DepthGeometry.MIN_MM..DepthGeometry.MAX_MM
    }

    fun save(root: File, id: String): JSONObject {
        val directory = File(root, "depth").also { check(it.mkdirs() || it.isDirectory) }
        File(directory, "$id.u16le").writeBytes(millimeters)
        File(directory, "$id.confidence.u8").writeBytes(confidence)
        return JSONObject().put("format", "ARCORE_RAW_DEPTH_MM_U16_LE")
            .put("image", "depth/$id.u16le").put("confidence", "depth/$id.confidence.u8")
            .put("width", width).put("height", height).put("timestampNs", timestampNs)
            .put("confidenceTimestampNs", confidenceTimestampNs)
            .put("imageToDepthUv", JSONArray(imageToDepthUv.toList()))
            .put("confidentPixels", confidentPixels).put("minimumConfidence", DepthGeometry.MIN_CONFIDENCE)
    }

    companion object {
        fun capture(frame: Frame, imageWidth: Int, imageHeight: Int): RawDepthAttempt {
            try {
                frame.acquireRawDepthImage16Bits().use { depth ->
                    frame.acquireRawDepthConfidenceImage().use { confidence ->
                        // Reprojected old depth is not counted as a new independent observation.
                        if (depth.timestamp != frame.timestamp) return RawDepthAttempt(null, RawDepthStatus.REPROJECTED,
                            "frame=${frame.timestamp}, depth=${depth.timestamp}, confidence=${confidence.timestamp}")
                        if (depth.width != confidence.width || depth.height != confidence.height) return RawDepthAttempt(
                            null, RawDepthStatus.DIMENSION_MISMATCH,
                            "depth=${depth.width}x${depth.height}, confidence=${confidence.width}x${confidence.height}")
                        val xy = floatArrayOf(0f, 0f, imageWidth.toFloat(), 0f, 0f, imageHeight.toFloat())
                        val uv = FloatArray(6)
                        frame.transformCoordinates2d(Coordinates2d.IMAGE_PIXELS, xy, Coordinates2d.TEXTURE_NORMALIZED, uv)
                        val affine = floatArrayOf((uv[2]-uv[0])/imageWidth, (uv[4]-uv[0])/imageHeight, uv[0],
                            (uv[3]-uv[1])/imageWidth, (uv[5]-uv[1])/imageHeight, uv[1])
                        if (affine.any { !it.isFinite() }) return RawDepthAttempt(
                            null, RawDepthStatus.INVALID_ALIGNMENT, "RGB-to-depth transform is not finite")
                        val dp = depth.planes[0]; val cp = confidence.planes[0]
                        val sample = RawDepthCapture(depth.width, depth.height, depth.timestamp, confidence.timestamp,
                            DepthGeometry.packedPlane(dp.buffer.duplicate(), depth.width, depth.height, dp.rowStride, dp.pixelStride, 2),
                            DepthGeometry.packedPlane(cp.buffer.duplicate(), confidence.width, confidence.height, cp.rowStride, cp.pixelStride, 1), affine)
                        return RawDepthAttempt(sample, RawDepthStatus.FRESH,
                            "depth=${depth.timestamp}, confidence=${confidence.timestamp}, pixels=${depth.width}x${depth.height}")
                    }
                }
            } catch (_: NotYetAvailableException) {
                return RawDepthAttempt(null, RawDepthStatus.NOT_YET_AVAILABLE, "ARCore todavía no ofrece Raw Depth")
            }
        }
    }
}
