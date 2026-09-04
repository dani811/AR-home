package io.arhome.localizer.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class LandmarkSourcePolicyTest {
    @Test
    fun `one depth frame never forces a whole map onto raw depth`() {
        assertEquals("TRIANGULATED_RGB", LandmarkSourcePolicy.select(12, 1))
    }

    @Test
    fun `raw depth requires six frames and at least half the views`() {
        assertEquals("TRIANGULATED_RGB", LandmarkSourcePolicy.select(12, 5))
        assertEquals("RAW_DEPTH", LandmarkSourcePolicy.select(12, 6))
        assertEquals("TRIANGULATED_RGB", LandmarkSourcePolicy.select(13, 6))
        assertEquals("RAW_DEPTH", LandmarkSourcePolicy.select(13, 7))
    }

    @Test
    fun `rgb remains valid when depth never arrives`() {
        assertEquals("TRIANGULATED_RGB", LandmarkSourcePolicy.select(20, 0))
    }
}
