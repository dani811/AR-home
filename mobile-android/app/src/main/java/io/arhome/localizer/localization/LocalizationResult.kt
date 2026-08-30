package io.arhome.localizer.localization

import com.google.ar.core.Pose

data class LocalizationResult(
    val worldCameraPose: Pose,
    val matchedKeyframeId: String,
    val confidence: Double,
    val inlierCount: Int,
    val timestampNs: Long,
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be in [0,1]" }
        require(inlierCount >= 0) { "inlierCount must be non-negative" }
    }
}
