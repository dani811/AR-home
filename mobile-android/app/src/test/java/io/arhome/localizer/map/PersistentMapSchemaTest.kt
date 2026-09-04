package io.arhome.localizer.map

import org.junit.Test

class PersistentMapSchemaTest {
    @Test
    fun `accepts complete pairwise pose chain`() {
        PersistentMapSchema.validatePoseMetadata(
            schemaVersion = 3,
            coordinateFrame = "ARCORE_PAIRWISE_ANCHOR_CHAIN",
            frameCount = 24,
            poseCount = 24,
            poseTimestampNs = 123L,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects truncated pairwise pose chain`() {
        PersistentMapSchema.validatePoseMetadata(
            schemaVersion = 3,
            coordinateFrame = "ARCORE_PAIRWISE_ANCHOR_CHAIN",
            frameCount = 24,
            poseCount = 23,
            poseTimestampNs = 123L,
        )
    }

    @Test
    fun `keeps legacy snapshot schema readable`() {
        PersistentMapSchema.validatePoseMetadata(
            schemaVersion = 2,
            coordinateFrame = "ARCORE_ANCHOR_SNAPSHOT",
            frameCount = 38,
            poseCount = 38,
            poseTimestampNs = 456L,
        )
    }
}
