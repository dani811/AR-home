package io.arhome.localizer.localization

import org.junit.Assert.assertEquals
import org.junit.Test

class OrbReferenceShortlistTest {

    @Test
    fun `selects the highest fingerprint scores`() {
        assertEquals(listOf(1, 3), selectReferenceIndices(listOf(0.1f, 0.9f, 0.2f, 0.8f), 2, null))
    }

    @Test
    fun `retains an active geometric candidate outside the top scores`() {
        assertEquals(listOf(1, 0), selectReferenceIndices(listOf(0.1f, 0.9f, 0.8f), 2, 0))
    }

    @Test
    fun `does not duplicate a retained candidate already selected`() {
        assertEquals(listOf(1, 2), selectReferenceIndices(listOf(0.1f, 0.9f, 0.8f), 2, 1))
    }
}
