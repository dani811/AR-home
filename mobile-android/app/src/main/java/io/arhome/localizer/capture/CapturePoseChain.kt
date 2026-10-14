package io.arhome.localizer.capture

import com.google.ar.core.Pose

/** Joins consecutive camera anchors while both poses belong to the same ARCore update. */
object CapturePoseChain {
    fun append(previousMapPose: Pose, previousAnchorPose: Pose, currentAnchorPose: Pose): Pose =
        previousMapPose.compose(previousAnchorPose.inverse().compose(currentAnchorPose))
}
