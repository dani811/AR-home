package io.arhome.localizer.localization

import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.exceptions.NotYetAvailableException
import io.arhome.localizer.map.PersistentKeyframe
import io.arhome.localizer.map.PersistentMap
import kotlin.math.sqrt

data class CoarseMatchStatus(
    val bestKeyframeId: String? = null,
    val correlation: Float = 0f,
    val stableHits: Int = 0,
    val referenceCount: Int = 0,
    val message: String = "Waiting for camera image",
)

class CoarseKeyframeLocalizationProvider : LocalizationProvider {

    private data class Reference(
        val keyframe: PersistentKeyframe,
        val fingerprint: VisualFingerprint,
    )

    private var preparedSessionId: String? = null
    private var references = emptyList<Reference>()
    private var lastAttemptTimestampNs = 0L
    private var candidatePose: Pose? = null
    private var stableHits = 0

    @Volatile
    var latestStatus = CoarseMatchStatus()
        private set

    override fun localize(map: PersistentMap, frame: Frame): LocalizationResult? {
        if (frame.timestamp - lastAttemptTimestampNs < MIN_ATTEMPT_INTERVAL_NS) return null
        lastAttemptTimestampNs = frame.timestamp
        prepare(map)

        val fingerprint: VisualFingerprint
        val imageTimestamp: Long
        try {
            frame.acquireCameraImage().use { image ->
                fingerprint = VisualFingerprint.fromCameraImage(image)
                imageTimestamp = image.timestamp
            }
        } catch (_: NotYetAvailableException) {
            return null
        }

        if (fingerprint.contrast < MIN_QUERY_CONTRAST) {
            resetCandidate("Low-texture frame · move toward furniture, doorway or room detail")
            return null
        }

        var best: Reference? = null
        var bestCorrelation = -1f
        for (reference in references) {
            val score = fingerprint.correlation(reference.fingerprint)
            if (score > bestCorrelation) {
                best = reference
                bestCorrelation = score
            }
        }
        val match = best ?: return null
        if (bestCorrelation < MIN_CORRELATION) {
            resetCandidate("No confident visual match")
            latestStatus = latestStatus.copy(correlation = bestCorrelation, referenceCount = references.size)
            return null
        }

        val coherent = candidatePose?.let { poseDistanceMeters(it, match.keyframe.pose) <= MAX_CANDIDATE_JUMP_METERS } ?: true
        stableHits = if (coherent) stableHits + 1 else 1
        candidatePose = match.keyframe.pose
        latestStatus = CoarseMatchStatus(
            bestKeyframeId = match.keyframe.id,
            correlation = bestCorrelation,
            stableHits = stableHits,
            referenceCount = references.size,
            message = if (stableHits >= REQUIRED_STABLE_HITS) "Coarse persistent localization accepted" else "Visual match found · confirming consistency",
        )
        if (stableHits < REQUIRED_STABLE_HITS) return null

        return LocalizationResult(
            worldCameraPose = match.keyframe.pose,
            matchedKeyframeId = match.keyframe.id,
            confidence = bestCorrelation.toDouble().coerceIn(0.0, 1.0),
            inlierCount = 0,
            timestampNs = imageTimestamp,
        )
    }

    private fun prepare(map: PersistentMap) {
        if (preparedSessionId == map.sessionId) return
        references = map.keyframes.mapNotNull { keyframe ->
            val fingerprint = VisualFingerprint.fromJpeg(keyframe.image)
            if (fingerprint.contrast >= MIN_REFERENCE_CONTRAST) Reference(keyframe, fingerprint) else null
        }
        require(references.size >= REQUIRED_STABLE_HITS) { "Persistent map has too few textured keyframes" }
        preparedSessionId = map.sessionId
        latestStatus = CoarseMatchStatus(referenceCount = references.size, message = "Visual map prepared")
    }

    private fun resetCandidate(message: String) {
        candidatePose = null
        stableHits = 0
        latestStatus = CoarseMatchStatus(referenceCount = references.size, message = message)
    }

    private fun poseDistanceMeters(first: Pose, second: Pose): Float {
        val a = first.translation
        val b = second.translation
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        private const val MIN_ATTEMPT_INTERVAL_NS = 400_000_000L
        private const val MIN_QUERY_CONTRAST = 18f
        private const val MIN_REFERENCE_CONTRAST = 15f
        private const val MIN_CORRELATION = 0.76f
        private const val MAX_CANDIDATE_JUMP_METERS = 0.65f
        private const val REQUIRED_STABLE_HITS = 3
    }
}
