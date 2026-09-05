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
    /** Timestamp of the underlying raw estimate, including reprojected attempts. */
    val observedTimestampNs: Long? = null,
)

class RawDepthCapture private constructor(
    val width: Int,
    val height: Int,
    /** Timestamp of the underlying raw-depth estimate. */
    val timestampNs: Long,
    /** ARCore frame to which the returned depth image is spatially aligned. */
    val alignedFrameTimestampNs: Long,
    val confidenceTimestampNs: Long,
    val millimeters: ByteArray,
    val confidence: ByteArray,
    val imageToDepthUv: FloatArray,
) {
    private val quality = RawDepthQuality.measure(millimeters, confidence, width, height)
    val totalPixels: Int get() = quality.totalPixels
    val validPixels: Int get() = quality.validPixels
    val confidentPixels: Int get() = quality.confidentPixels
    val validCoverageFraction: Double get() = quality.validCoverageFraction
    val confidentCoverageFraction: Double get() = quality.confidentCoverageFraction
    val isUsableForMapping: Boolean get() = quality.isUsableForMapping

    fun save(root: File, id: String): JSONObject {
        val directory = File(root, "depth").also { check(it.mkdirs() || it.isDirectory) }
        File(directory, "$id.u16le").writeBytes(millimeters)
        File(directory, "$id.confidence.u8").writeBytes(confidence)
        return JSONObject().put("format", "ARCORE_RAW_DEPTH_MM_U16_LE")
            .put("image", "depth/$id.u16le").put("confidence", "depth/$id.confidence.u8")
            .put("width", width).put("height", height).put("timestampNs", timestampNs)
            .put("alignedFrameTimestampNs", alignedFrameTimestampNs)
            .put("confidenceTimestampNs", confidenceTimestampNs)
            .put("imageToDepthUv", JSONArray(imageToDepthUv.toList()))
            .put("validPixels", validPixels).put("validCoverageFraction", validCoverageFraction)
            .put("confidentPixels", confidentPixels).put("confidentCoverageFraction", confidentCoverageFraction)
            .put("minimumConfidence", DepthGeometry.MIN_CONFIDENCE)
            .put("usableForMapping", isUsableForMapping)
    }

    companion object {
        fun capture(
            frame: Frame,
            imageWidth: Int,
            imageHeight: Int,
            previousDepthTimestampNs: Long?,
        ): RawDepthAttempt {
            try {
                frame.acquireRawDepthImage16Bits().use { depth ->
                    frame.acquireRawDepthConfidenceImage().use { confidence ->
                        // ARCore identifies a new underlying estimate by a change from the
                        // previous raw-depth timestamp. Between updates it returns a 3D
                        // reprojection aligned to the current frame.
                        if (!RawDepthFreshness.isNew(previousDepthTimestampNs, depth.timestamp)) {
                            return RawDepthAttempt(
                                null,
                                RawDepthStatus.REPROJECTED,
                                "previous=$previousDepthTimestampNs, depth=${depth.timestamp}, frame=${frame.timestamp}",
                                depth.timestamp,
                            )
                        }
                        if (depth.width != confidence.width || depth.height != confidence.height) return RawDepthAttempt(
                            null, RawDepthStatus.DIMENSION_MISMATCH,
                            "depth=${depth.width}x${depth.height}, confidence=${confidence.width}x${confidence.height}",
                            depth.timestamp,
                        )
                        val xy = floatArrayOf(0f, 0f, imageWidth.toFloat(), 0f, 0f, imageHeight.toFloat())
                        val uv = FloatArray(6)
                        frame.transformCoordinates2d(Coordinates2d.IMAGE_PIXELS, xy, Coordinates2d.TEXTURE_NORMALIZED, uv)
                        val affine = floatArrayOf((uv[2]-uv[0])/imageWidth, (uv[4]-uv[0])/imageHeight, uv[0],
                            (uv[3]-uv[1])/imageWidth, (uv[5]-uv[1])/imageHeight, uv[1])
                        if (affine.any { !it.isFinite() }) return RawDepthAttempt(
                            null,
                            RawDepthStatus.INVALID_ALIGNMENT,
                            "RGB-to-depth transform is not finite",
                            depth.timestamp,
                        )
                        val dp = depth.planes[0]; val cp = confidence.planes[0]
                        val sample = RawDepthCapture(
                            depth.width,
                            depth.height,
                            depth.timestamp,
                            frame.timestamp,
                            confidence.timestamp,
                            DepthGeometry.packedPlane(dp.buffer.duplicate(), depth.width, depth.height, dp.rowStride, dp.pixelStride, 2),
                            DepthGeometry.packedPlane(cp.buffer.duplicate(), confidence.width, confidence.height, cp.rowStride, cp.pixelStride, 1),
                            affine,
                        )
                        return RawDepthAttempt(
                            sample,
                            RawDepthStatus.FRESH,
                            "previous=$previousDepthTimestampNs, depth=${depth.timestamp}, frame=${frame.timestamp}, " +
                                "confident=${sample.confidentPixels}/${sample.totalPixels}",
                            depth.timestamp,
                        )
                    }
                }
            } catch (_: NotYetAvailableException) {
                return RawDepthAttempt(null, RawDepthStatus.NOT_YET_AVAILABLE, "ARCore todavía no ofrece Raw Depth")
            }
        }
    }
}
