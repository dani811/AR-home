package io.arhome.localizer.localization

import com.google.ar.core.Pose
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class WorldAlignmentTest {

    @Test
    fun `alignment reproduces localized world camera pose`() {
        val worldCamera = Pose.makeTranslation(3f, 1f, -2f)
        val sessionCamera = Pose.makeTranslation(0.5f, 0f, -1f)
        val localization = LocalizationResult(
            worldCameraPose = worldCamera,
            matchedKeyframeId = "00001",
            confidence = 0.9,
            inlierCount = 42,
            timestampNs = 123L,
        )

        val alignment = WorldAlignment.fromLocalization(localization, sessionCamera)
        val reconstructed = alignment.worldCameraPose(sessionCamera)

        assertArrayEquals(worldCamera.translation, reconstructed.translation, 0.0001f)
        assertArrayEquals(worldCamera.rotationQuaternion, reconstructed.rotationQuaternion, 0.0001f)
    }

    @Test
    fun `alignment keeps tracking subsequent session motion in world frame`() {
        val worldCameraAtLocalization = Pose.makeTranslation(10f, 0f, 2f)
        val sessionCameraAtLocalization = Pose.makeTranslation(1f, 0f, 0f)
        val localization = LocalizationResult(
            worldCameraPose = worldCameraAtLocalization,
            matchedKeyframeId = "00002",
            confidence = 0.8,
            inlierCount = 30,
            timestampNs = 456L,
        )

        val alignment = WorldAlignment.fromLocalization(localization, sessionCameraAtLocalization)
        val sessionCameraLater = Pose.makeTranslation(2f, 0f, 0f)
        val worldCameraLater = alignment.worldCameraPose(sessionCameraLater)

        assertArrayEquals(floatArrayOf(11f, 0f, 2f), worldCameraLater.translation, 0.0001f)
    }
}
