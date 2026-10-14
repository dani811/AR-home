package io.arhome.localizer.depth

data class RawDepthQuality(
    val totalPixels: Int,
    val validPixels: Int,
    val confidentPixels: Int,
) {
    init {
        require(totalPixels > 0)
        require(validPixels in 0..totalPixels)
        require(confidentPixels in 0..validPixels)
    }

    val validCoverageFraction: Double = validPixels.toDouble() / totalPixels
    val confidentCoverageFraction: Double = confidentPixels.toDouble() / totalPixels
    val isUsableForMapping: Boolean = confidentPixels >= MIN_CONFIDENT_PIXELS_FOR_MAPPING

    companion object {
        const val MIN_CONFIDENT_PIXELS_FOR_MAPPING = 100

        fun measure(
            millimeters: ByteArray,
            confidence: ByteArray,
            width: Int,
            height: Int,
        ): RawDepthQuality {
            val total = Math.multiplyExact(width, height)
            require(total > 0 && millimeters.size == total * 2 && confidence.size == total)
            var valid = 0
            var confident = 0
            for (index in 0 until total) {
                val depth = DepthGeometry.millimeters(millimeters, index)
                if (depth !in DepthGeometry.MIN_MM..DepthGeometry.MAX_MM) continue
                valid++
                if ((confidence[index].toInt() and 255) >= DepthGeometry.MIN_CONFIDENCE) confident++
            }
            return RawDepthQuality(total, valid, confident)
        }
    }
}
