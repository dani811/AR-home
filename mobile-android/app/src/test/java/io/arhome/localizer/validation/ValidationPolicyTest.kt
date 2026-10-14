package io.arhome.localizer.validation

import org.junit.Assert.*
import org.junit.Test

class ValidationPolicyTest {
    @Test fun reservedViewsNeverBelongToTrainingAndCoverTheCapture() {
        val held = ValidationPolicy.heldOutIndices(38)
        assertEquals(listOf(1, 5, 9, 13, 17, 21, 25, 29, 33, 37), held)
        val training = (0 until 38).filter { it !in held }
        assertTrue(training.intersect(held.toSet()).isEmpty())
        assertEquals(38, training.size + held.size)
    }

    @Test fun observedFiveOutOfTenMapDoesNotPass() {
        assertFalse(ValidationPolicy.passes(10, 5, 0))
        assertFalse(ValidationPolicy.passes(10, 8, 0))
        assertTrue(ValidationPolicy.passes(10, 9, 0))
        assertFalse(ValidationPolicy.passes(10, 10, 1))
        assertFalse(ValidationPolicy.passes(0, 0, 0))
        assertFalse(ValidationPolicy.passes(2, 2, 0))
    }

    @Test fun poseMustAgreeWithRecordedPositionAndOrientation() {
        assertTrue(ValidationPolicy.poseConsistent(0.12, 4.9))
        assertFalse(ValidationPolicy.poseConsistent(0.685, 4.2))
        assertFalse(ValidationPolicy.poseConsistent(0.02, 15.0))
        assertFalse(ValidationPolicy.poseConsistent(Double.NaN, 0.0))
        assertFalse(ValidationPolicy.poseConsistent(0.0, Double.POSITIVE_INFINITY))
        assertFalse(ValidationPolicy.poseConsistent(-1.0, 0.0))
    }
}
