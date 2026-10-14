package io.arhome.localizer.capture

import com.google.ar.core.Pose
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CapturePoseChainTest {
    @Test
    fun `joins consecutive anchors despite a later world-origin correction`() {
        val firstMapPose = Pose.IDENTITY
        val secondMapPose = CapturePoseChain.append(
            firstMapPose,
            Pose.makeTranslation(10f, 0f, 0f),
            Pose.makeTranslation(11f, 0f, 0f),
        )

        // ARCore has corrected the numerical world coordinates before the next update.
        val thirdMapPose = CapturePoseChain.append(
            secondMapPose,
            Pose.makeTranslation(20f, 0f, 0f),
            Pose.makeTranslation(22f, 0f, 0f),
        )

        assertArrayEquals(floatArrayOf(1f, 0f, 0f), secondMapPose.translation, 0.0001f)
        assertArrayEquals(floatArrayOf(3f, 0f, 0f), thirdMapPose.translation, 0.0001f)
    }
}
