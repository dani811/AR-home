package io.arhome.localizer.depth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawDepthFreshnessTest {
    @Test
    fun `first observed depth estimate is new`() {
        assertTrue(RawDepthFreshness.isNew(null, 100L))
    }

    @Test
    fun `same raw timestamp is a reprojection even on a later frame`() {
        assertFalse(RawDepthFreshness.isNew(100L, 100L))
    }

    @Test
    fun `changed raw timestamp is new without requiring RGB timestamp equality`() {
        assertTrue(RawDepthFreshness.isNew(100L, 120L))
    }
}
