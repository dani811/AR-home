package io.arhome.localizer.localization

import android.media.Image
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.exceptions.NotYetAvailableException
import io.arhome.localizer.map.PersistentKeyframe
import io.arhome.localizer.map.PersistentMap
import kotlin.math.min
import kotlin.math.sqrt
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
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
    val message: String = "Waiting for camera image",
)

class OrbKeyframeLocalizationProvider : LocalizationProvider {

    private data class Reference(
        val keyframe: PersistentKeyframe,
        val keypoints: List<KeyPoint>,
        val descriptors: Mat,
    )

    private data class Candidate(
        val reference: Reference,
        val goodMatches: Int,
        val inliers: Int,
        val inlierRatio: Double,
    )

    private val orb = ORB.create(1800)
    private val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
    private var preparedSessionId: String? = null
    private var references = emptyList<Reference>()
    private var lastAttemptTimestampNs = 0L
    private var candidatePose: Pose? = null
    private var stableHits = 0

    @Volatile
    var latestStatus = OrbMatchStatus()
        private set

    init {
        check(OpenCVLoader.initDebug()) { "OpenCV native runtime failed to initialize" }
    }

    override fun localize(map: PersistentMap, frame: Frame): LocalizationResult? {
        if (frame.timestamp - lastAttemptTimestampNs < MIN_ATTEMPT_INTERVAL_NS) return null
        lastAttemptTimestampNs = frame.timestamp
        prepare(map)

        val imageTimestamp: Long
        val queryGray: Mat
        try {
            frame.acquireCameraImage().use { image ->
                imageTimestamp = image.timestamp
                queryGray = cameraGray(image)
            }
        } catch (_: NotYetAvailableException) {
            return null
        }

        val queryKeypointMat = MatOfKeyPoint()
        val queryDescriptors = Mat()
        try {
            orb.detectAndCompute(queryGray, Mat(), queryKeypointMat, queryDescriptors)
            if (queryDescriptors.empty() || queryDescriptors.rows() < MIN_QUERY_FEATURES) {
                resetCandidate("Too few local features · point at furniture edges, handles or room detail")
                return null
            }
            val queryKeypoints = queryKeypointMat.toList()
            val best = references.mapNotNull { reference ->
                evaluate(queryKeypoints, queryDescriptors, reference)
            }.maxWithOrNull(compareBy<Candidate> { it.inliers }.thenBy { it.goodMatches })

            if (best == null || best.goodMatches < MIN_GOOD_MATCHES || best.inliers < MIN_INLIERS || best.inlierRatio < MIN_INLIER_RATIO) {
                resetCandidate("No geometrically consistent local match")
                if (best != null) {
                    latestStatus = latestStatus.copy(
                        bestKeyframeId = best.reference.keyframe.id,
                        goodMatches = best.goodMatches,
                        inliers = best.inliers,
                        inlierRatio = best.inlierRatio,
                        referenceCount = references.size,
                    )
                }
                return null
            }

            val coherent = candidatePose?.let {
                poseDistanceMeters(it, best.reference.keyframe.pose) <= MAX_CANDIDATE_JUMP_METERS
            } ?: true
            stableHits = if (coherent) stableHits + 1 else 1
            candidatePose = best.reference.keyframe.pose
            latestStatus = OrbMatchStatus(
                bestKeyframeId = best.reference.keyframe.id,
                goodMatches = best.goodMatches,
                inliers = best.inliers,
                inlierRatio = best.inlierRatio,
                stableHits = stableHits,
                referenceCount = references.size,
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
                timestampNs = imageTimestamp,
            )
        } finally {
            queryGray.release()
            queryKeypointMat.release()
            queryDescriptors.release()
        }
    }

    private fun prepare(map: PersistentMap) {
        if (preparedSessionId == map.sessionId) return
        references.forEach { it.descriptors.release() }
        references = map.keyframes.mapNotNull { keyframe -> prepareReference(keyframe) }
        require(references.size >= REQUIRED_STABLE_HITS) { "Persistent map has too few ORB-ready keyframes" }
        preparedSessionId = map.sessionId
        latestStatus = OrbMatchStatus(referenceCount = references.size, message = "ORB visual map prepared")
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
                Reference(keyframe, keypointMat.toList(), descriptors)
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

    private fun cameraGray(image: Image): Mat {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val pixels = ByteArray(image.width * image.height)
        var target = 0
        for (y in 0 until image.height) {
            val row = y * plane.rowStride
            for (x in 0 until image.width) {
                pixels[target++] = buffer.get(row + x * plane.pixelStride)
            }
        }
        return Mat(image.height, image.width, CvType.CV_8UC1).also { it.put(0, 0, pixels) }
    }

    private fun resetCandidate(message: String) {
        candidatePose = null
        stableHits = 0
        latestStatus = OrbMatchStatus(referenceCount = references.size, message = message)
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
    }
}
