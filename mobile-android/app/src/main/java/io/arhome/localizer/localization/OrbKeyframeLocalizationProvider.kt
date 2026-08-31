package io.arhome.localizer.localization

import com.google.ar.core.Frame
import com.google.ar.core.Pose
import io.arhome.localizer.map.PersistentKeyframe
import io.arhome.localizer.map.PersistentMap
import kotlin.math.min
import kotlin.math.sqrt
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.DMatch
import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs

data class OrbMatchStatus(
    val bestKeyframeId: String? = null,
    val goodMatches: Int = 0,
    val inliers: Int = 0,
    val inlierRatio: Double = 0.0,
    val stableHits: Int = 0,
    val referenceCount: Int = 0,
    val evaluatedReferences: Int = 0,
    val message: String = "Waiting for camera image",
)

class OrbKeyframeLocalizationProvider : LocalizationProvider {

    private data class Reference(
        val keyframe: PersistentKeyframe,
        val keypoints: List<KeyPoint>,
        val descriptors: Mat,
        val fingerprint: VisualFingerprint,
    )

    private data class Candidate(
        val reference: Reference,
        val goodMatches: Int,
        val inliers: Int,
        val inlierRatio: Double,
    )

    // OpenCV must be loaded before the first JNI-backed object is constructed.
    // Kotlin evaluates property initializers before later init blocks, so loading in
    // an init block after ORB.create() leaves the native symbols unavailable.
    private val orb = loadOpenCvAndCreateOrb()
    private val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
    private var preparedSessionId: String? = null
    private var references = emptyList<Reference>()
    private var lastAttemptTimestampNs = 0L
    private var candidateReference: Reference? = null
    private var stableHits = 0

    @Volatile
    var latestStatus = OrbMatchStatus()
        private set

    /**
     * Builds ORB descriptors for the persistent map before ARCore enters its frame loop.
     * Keeping this work out of GLSurfaceView.onDrawFrame avoids a long native OpenCV burst
     * on the render thread during the first tracked frame.
     */
    fun prepare(map: PersistentMap) {
        if (preparedSessionId == map.sessionId) return
        references.forEach { it.descriptors.release() }
        references = map.keyframes.mapNotNull { keyframe -> prepareReference(keyframe) }
        require(references.size >= REQUIRED_STABLE_HITS) { "Persistent map has too few ORB-ready keyframes" }
        preparedSessionId = map.sessionId
        latestStatus = OrbMatchStatus(referenceCount = references.size, message = "ORB visual map prepared")
    }

    override fun localize(map: PersistentMap, frame: Frame): LocalizationResult? =
        CapturedLocalizationFrame.capture(frame)?.use { localize(map, it) }

    fun localize(map: PersistentMap, frame: CapturedLocalizationFrame): LocalizationResult? {
        if (frame.frameTimestampNs - lastAttemptTimestampNs < MIN_ATTEMPT_INTERVAL_NS) return null
        lastAttemptTimestampNs = frame.frameTimestampNs
        prepare(map)

        val queryKeypointMat = MatOfKeyPoint()
        val queryDescriptors = Mat()
        try {
            orb.detectAndCompute(frame.gray, Mat(), queryKeypointMat, queryDescriptors)
            if (queryDescriptors.empty() || queryDescriptors.rows() < MIN_QUERY_FEATURES) {
                resetCandidate("Too few local features · point at furniture edges, handles or room detail")
                return null
            }
            val queryKeypoints = queryKeypointMat.toList()
            val shortlisted = shortlist(VisualFingerprint.fromGrayMat(frame.gray))
            val best = shortlisted.mapNotNull { reference ->
                evaluate(queryKeypoints, queryDescriptors, reference)
            }.maxWithOrNull(compareBy<Candidate> { it.inliers }.thenBy { it.goodMatches })

            if (best == null || best.goodMatches < MIN_GOOD_MATCHES || best.inliers < MIN_INLIERS || best.inlierRatio < MIN_INLIER_RATIO) {
                resetCandidate("No geometrically consistent local match", shortlisted.size)
                if (best != null) {
                    latestStatus = latestStatus.copy(
                        bestKeyframeId = best.reference.keyframe.id,
                        goodMatches = best.goodMatches,
                        inliers = best.inliers,
                        inlierRatio = best.inlierRatio,
                        referenceCount = references.size,
                        evaluatedReferences = shortlisted.size,
                    )
                }
                return null
            }

            val coherent = candidateReference?.let {
                poseDistanceMeters(it.keyframe.pose, best.reference.keyframe.pose) <= MAX_CANDIDATE_JUMP_METERS
            } ?: true
            stableHits = if (coherent) stableHits + 1 else 1
            candidateReference = best.reference
            latestStatus = OrbMatchStatus(
                bestKeyframeId = best.reference.keyframe.id,
                goodMatches = best.goodMatches,
                inliers = best.inliers,
                inlierRatio = best.inlierRatio,
                stableHits = stableHits,
                referenceCount = references.size,
                evaluatedReferences = shortlisted.size,
                message = if (stableHits >= REQUIRED_STABLE_HITS) {
                    "Geometric keyframe localization accepted"
                } else {
                    "Local feature match found · confirming consistency"
                },
            )
            if (stableHits < REQUIRED_STABLE_HITS) return null

            val confidence = (
                0.55 * min(1.0, best.inliers / 100.0) +
                    0.45 * best.inlierRatio
                ).coerceIn(0.0, 1.0)
            return LocalizationResult(
                worldCameraPose = best.reference.keyframe.pose,
                matchedKeyframeId = best.reference.keyframe.id,
                confidence = confidence,
                inlierCount = best.inliers,
                timestampNs = frame.imageTimestampNs,
            )
        } finally {
            queryKeypointMat.release()
            queryDescriptors.release()
        }
    }

    private fun prepareReference(keyframe: PersistentKeyframe): Reference? {
        val gray = Imgcodecs.imread(keyframe.image.absolutePath, Imgcodecs.IMREAD_GRAYSCALE)
        if (gray.empty()) {
            gray.release()
            return null
        }
        val keypointMat = MatOfKeyPoint()
        val descriptors = Mat()
        return try {
            orb.detectAndCompute(gray, Mat(), keypointMat, descriptors)
            if (descriptors.empty() || descriptors.rows() < MIN_REFERENCE_FEATURES) {
                descriptors.release()
                null
            } else {
                Reference(keyframe, keypointMat.toList(), descriptors, VisualFingerprint.fromGrayMat(gray))
            }
        } finally {
            gray.release()
            keypointMat.release()
        }
    }

    private fun evaluate(
        queryKeypoints: List<KeyPoint>,
        queryDescriptors: Mat,
        reference: Reference,
    ): Candidate? {
        val pairs = mutableListOf<MatOfDMatch>()
        matcher.knnMatch(queryDescriptors, reference.descriptors, pairs, 2)
        val good = ArrayList<DMatch>()
        for (pair in pairs) {
            val values = pair.toArray()
            if (values.size >= 2 && values[0].distance < LOWE_RATIO * values[1].distance) {
                good += values[0]
            }
            pair.release()
        }
        if (good.size < MIN_GOOD_MATCHES_FOR_RANSAC) return Candidate(reference, good.size, 0, 0.0)

        val queryPoints = MatOfPoint2f()
        val referencePoints = MatOfPoint2f()
        val mask = Mat()
        val homography: Mat
        try {
            queryPoints.fromList(good.map { queryKeypoints[it.queryIdx].pt })
            referencePoints.fromList(good.map { reference.keypoints[it.trainIdx].pt })
            homography = Calib3d.findHomography(queryPoints, referencePoints, Calib3d.RANSAC, RANSAC_REPROJECTION_ERROR, mask)
            val inliers = if (!homography.empty() && !mask.empty()) Core.countNonZero(mask) else 0
            val ratio = if (good.isEmpty()) 0.0 else inliers.toDouble() / good.size
            homography.release()
            return Candidate(reference, good.size, inliers, ratio)
        } finally {
            queryPoints.release()
            referencePoints.release()
            mask.release()
        }
    }

    private fun shortlist(query: VisualFingerprint): List<Reference> {
        val scores = references.map { query.correlation(it.fingerprint) }
        val retained = candidateReference?.let { candidate -> references.indexOfFirst { it === candidate } }
        return selectReferenceIndices(scores, MAX_EVALUATED_REFERENCES, retained).map(references::get)
    }

    private fun resetCandidate(message: String, evaluatedReferences: Int = 0) {
        candidateReference = null
        stableHits = 0
        latestStatus = OrbMatchStatus(
            referenceCount = references.size,
            evaluatedReferences = evaluatedReferences,
            message = message,
        )
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
        private fun loadOpenCvAndCreateOrb(): ORB {
            check(OpenCVLoader.initDebug()) { "OpenCV native runtime failed to initialize" }
            return ORB.create(1800)
        }

        private const val MIN_ATTEMPT_INTERVAL_NS = 450_000_000L
        private const val MIN_QUERY_FEATURES = 100
        private const val MIN_REFERENCE_FEATURES = 100
        private const val MIN_GOOD_MATCHES_FOR_RANSAC = 12
        private const val MIN_GOOD_MATCHES = 35
        private const val MIN_INLIERS = 24
        private const val MIN_INLIER_RATIO = 0.40
        private const val LOWE_RATIO = 0.75f
        private const val RANSAC_REPROJECTION_ERROR = 4.0
        private const val MAX_CANDIDATE_JUMP_METERS = 1.0f
        private const val REQUIRED_STABLE_HITS = 3
        private const val MAX_EVALUATED_REFERENCES = 6
    }
}

internal fun selectReferenceIndices(scores: List<Float>, limit: Int, retainedIndex: Int?): List<Int> {
    require(limit > 0) { "limit must be positive" }
    if (scores.isEmpty()) return emptyList()
    val selected = scores.indices.sortedByDescending(scores::get).take(limit).toMutableList()
    if (retainedIndex != null && retainedIndex in scores.indices && retainedIndex !in selected) {
        selected[selected.lastIndex] = retainedIndex
    }
    return selected
}
