package io.arhome.localizer.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureMotionPolicyTest {
    @Test fun `translated viewpoint is accepted without requiring 20 cm`() {
        assertEquals(CaptureMotionKind.TRANSLATION, CaptureMotionPolicy.classify(0.08, 0.0, 300.0))
    }

    @Test fun `rotation is RGB coverage not depth parallax`() {
        assertEquals(CaptureMotionKind.ROTATION_ONLY, CaptureMotionPolicy.classify(0.0, 7.0, 300.0))
    }

    @Test fun `slow intentional translation is accepted`() {
        assertEquals(CaptureMotionKind.TRANSLATION, CaptureMotionPolicy.classify(0.03, 0.0, 1_200.0))
    }

    @Test fun `slow intentional rotation is accepted`() {
        assertEquals(CaptureMotionKind.ROTATION_ONLY, CaptureMotionPolicy.classify(0.0, 2.5, 1_200.0))
    }

    @Test fun `stationary jitter is rejected`() {
        assertEquals(CaptureMotionKind.WAIT, CaptureMotionPolicy.classify(0.005, 0.4, 5_000.0))
    }

    @Test fun `frame spacing is enforced`() {
        assertEquals(CaptureMotionKind.WAIT, CaptureMotionPolicy.classify(0.5, 30.0, 299.0))
    }

    @Test fun `slow thresholds do not apply prematurely`() {
        assertEquals(CaptureMotionKind.WAIT, CaptureMotionPolicy.classify(0.03, 2.5, 500.0))
    }
}
