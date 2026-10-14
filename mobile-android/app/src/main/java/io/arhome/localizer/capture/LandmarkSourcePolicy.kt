package io.arhome.localizer.capture

/** Chooses the reconstruction source only when raw depth has useful view coverage. */
object LandmarkSourcePolicy {
    const val MIN_DEPTH_FRAMES = 6

    fun select(keyframeCount: Int, depthFrameCount: Int): String {
        require(keyframeCount >= 0)
        require(depthFrameCount in 0..keyframeCount)
        val coversAtLeastHalf = depthFrameCount * 2 >= keyframeCount
        return if (depthFrameCount >= MIN_DEPTH_FRAMES && coversAtLeastHalf) {
            "RAW_DEPTH"
        } else {
            "TRIANGULATED_RGB"
        }
    }
}
