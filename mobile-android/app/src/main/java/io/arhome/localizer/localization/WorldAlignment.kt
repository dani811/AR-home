package io.arhome.localizer.localization

import com.google.ar.core.Pose

/**
 * Alignment from a fresh ARCore session-local coordinate frame into the
 * persistent map/world coordinate frame.
 */
data class WorldAlignment(
    val worldSessionPose: Pose,
    val source: LocalizationResult,
) {
    fun worldCameraPose(sessionCameraPose: Pose): Pose =
        worldSessionPose.compose(sessionCameraPose)

    companion object {
        fun fromLocalization(
            localization: LocalizationResult,
            sessionCameraPose: Pose,
        ): WorldAlignment {
            val worldSession = localization.worldCameraPose.compose(sessionCameraPose.inverse())
            return WorldAlignment(worldSession, localization)
        }
    }
}
