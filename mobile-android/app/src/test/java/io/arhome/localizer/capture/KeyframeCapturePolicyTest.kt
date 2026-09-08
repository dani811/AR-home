package io.arhome.localizer.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeCapturePolicyTest {
    private val policy = KeyframeCapturePolicy()

    @Test fun rejectsFramesInsideMinimumInterval() {
        assertFalse(policy.evaluate(299.0, 0.50, 30.0).capture)
    }

    @Test fun capturesNormalTranslationOrRotation() {
        assertTrue(policy.evaluate(300.0, 0.08, 0.0).capture)
        assertTrue(policy.evaluate(300.0, 0.0, 7.0).capture)
    }

    @Test fun recoversSlowIntentionalMovement() {
        assertTrue(policy.evaluate(1_200.0, 0.03, 0.0).capture)
        assertTrue(policy.evaluate(1_200.0, 0.0, 2.5).capture)
    }

    @Test fun doesNotCreateStationaryDuplicates() {
        assertFalse(policy.evaluate(5_000.0, 0.005, 0.4).capture)
    }
}
