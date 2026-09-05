package io.arhome.localizer.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureMotionPolicyTest {
    @Test
    fun `lateral displacement is depth-producing motion`() {
        assertEquals(CaptureMotionKind.TRANSLATION, CaptureMotionPolicy.classify(0.20, 0.0))
    }

    @Test
    fun `rotation is retained for RGB coverage but not called depth motion`() {
        assertEquals(CaptureMotionKind.ROTATION_ONLY, CaptureMotionPolicy.classify(0.0, 12.0))
    }

    @Test
    fun `small movement waits for a deliberate next view`() {
        assertEquals(CaptureMotionKind.WAIT, CaptureMotionPolicy.classify(0.19, 11.9))
    }
}
