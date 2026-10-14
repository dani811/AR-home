package io.arhome.localizer.depth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawDepthQualityTest {
    @Test
    fun `counts only in-range measurements with sufficient confidence`() {
        val depth = littleEndian(1000, 0, 9000, 2000)
        val confidence = byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 191.toByte())

        val quality = RawDepthQuality.measure(depth, confidence, 2, 2)

        assertEquals(4, quality.totalPixels)
        assertEquals(2, quality.validPixels)
        assertEquals(1, quality.confidentPixels)
        assertEquals(0.25, quality.confidentCoverageFraction, 1e-12)
        assertFalse(quality.isUsableForMapping)
    }

    @Test
    fun `mapping gate requires a measurable amount of reliable depth`() {
        val pixels = RawDepthQuality.MIN_CONFIDENT_PIXELS_FOR_MAPPING
        val quality = RawDepthQuality.measure(
            littleEndian(*IntArray(pixels) { 1500 }),
            ByteArray(pixels) { 255.toByte() },
            pixels,
            1,
        )
        assertTrue(quality.isUsableForMapping)
    }

    private fun littleEndian(vararg values: Int): ByteArray = ByteArray(values.size * 2).also { bytes ->
        values.forEachIndexed { index, value ->
            bytes[index * 2] = (value and 255).toByte()
            bytes[index * 2 + 1] = (value ushr 8).toByte()
        }
    }
}
