package io.arhome.localizer.localization

import com.google.ar.core.Frame
import com.google.ar.core.Pose
import io.arhome.localizer.map.PersistentMap
import kotlin.math.sqrt
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB

data class PnpLocalizationStatus(
    val landmarks: Int = 0,
    val goodMatches: Int = 0,
    val pnpInliers: Int = 0,
    val message: String = "Waiting for camera image",
)

class PnpLandmarkLocalizationProvider : LocalizationProvider {
    private val orb: ORB = createOrb()
    private val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
    private var preparedSessionId: String? = null
    private var landmarkMap: PersistentLandmarkMap? = null
    private var landmarkDescriptors = Mat()
    private var lastAttemptTimestampNs = 0L

    @Volatile
    var latestStatus = PnpLocalizationStatus()
        private set

    fun prepare(map: PersistentMap) {
        if (preparedSessionId == map.sessionId) return
        landmarkDescriptors.release()
        val built = PersistentLandmarkBuilder().build(map)
        latestStatus = PnpLocalizationStatus(
            landmarks = built.landmarks.size,
            message = "3D landmark map prepared",
        )
        require(built.landmarks.size >= MIN_PNP_CORRESPONDENCES) {
            "Persistent map produced too few 3D landmarks: ${built.landmarks.size}"
        }
        landmarkDescriptors = Mat(built.landmarks.size, built.landmarks.first().descriptor.size, CvType.CV_8U)
        built.landmarks.forEachIndexed { index, landmark -> landmarkDescriptors.put(index, 0, landmark.descriptor) }
        landmarkMap = built
        preparedSessionId = map.sessionId
    }

    override fun localize(map: PersistentMap, frame: Frame): LocalizationResult? =
        CapturedLocalizationFrame.capture(frame)?.use { localize(map, it) }

    fun localize(map: PersistentMap, frame: CapturedLocalizationFrame): LocalizationResult? {
        if (frame.frameTimestampNs - lastAttemptTimestampNs < MIN_ATTEMPT_INTERVAL_NS) return null
        lastAttemptTimestampNs = frame.frameTimestampNs
        prepare(map)
        val built = landmarkMap ?: return null

        val keypointMat = MatOfKeyPoint()
        val queryDescriptors = Mat()
        try {
            orb.detectAndCompute(frame.gray, Mat(), keypointMat, queryDescriptors)
            if (queryDescriptors.empty()) return null
            val queryKeypoints = keypointMat.toList()
            val pairs = mutableListOf<MatOfDMatch>()
            matcher.knnMatch(queryDescriptors, landmarkDescriptors, pairs, 2)
            val good = buildList {
                pairs.forEach { pair ->
                    val values = pair.toArray()
                    if (values.size >= 2 && values[0].distance < LOWE_RATIO * values[1].distance) add(values[0])
                    pair.release()
                }
            }
            if (good.size < MIN_PNP_CORRESPONDENCES) {
                latestStatus = PnpLocalizationStatus(built.landmarks.size, good.size, 0, "Too few 2D→3D matches")
                return null
            }

            val objectPoints = MatOfPoint3f()
            val imagePoints = MatOfPoint2f()
            objectPoints.fromList(good.map { match ->
                val p = built.landmarks[match.trainIdx].worldPointMeters
                Point3(p[0], p[1], p[2])
            })
            imagePoints.fromList(good.map { match -> queryKeypoints[match.queryIdx].pt })

            val focal = frame.focalLengthPixels
            val principal = frame.principalPointPixels
            val cameraMatrix = Mat.eye(3, 3, CvType.CV_64F)
            cameraMatrix.put(0, 0, focal[0].toDouble())
            cameraMatrix.put(1, 1, focal[1].toDouble())
            cameraMatrix.put(0, 2, principal[0].toDouble())
            cameraMatrix.put(1, 2, principal[1].toDouble())
            val distortion = MatOfDouble()
            val rvec = Mat()
            val tvec = Mat()
            val inliers = Mat()
            try {
                val solved = Calib3d.solvePnPRansac(
                    objectPoints,
                    imagePoints,
                    cameraMatrix,
                    distortion,
                    rvec,
                    tvec,
                    false,
                    PNP_ITERATIONS,
                    PNP_REPROJECTION_ERROR_PX,
                    PNP_CONFIDENCE,
                    inliers,
                    Calib3d.SOLVEPNP_EPNP,
                )
                val inlierCount = if (solved) inliers.rows() else 0
                latestStatus = PnpLocalizationStatus(
                    built.landmarks.size,
                    good.size,
                    inlierCount,
                    if (inlierCount >= MIN_PNP_INLIERS) "PnP pose accepted" else "PnP geometry rejected",
                )
                if (!solved || inlierCount < MIN_PNP_INLIERS) return null

                return LocalizationResult(
                    worldCameraPose = cvPoseToArCore(rvec, tvec),
                    matchedKeyframeId = "PNP_LANDMARK_MAP",
                    confidence = (inlierCount.toDouble() / good.size).coerceIn(0.0, 1.0),
                    inlierCount = inlierCount,
                    timestampNs = frame.imageTimestampNs,
                )
            } finally {
                objectPoints.release()
                imagePoints.release()
                cameraMatrix.release()
                distortion.release()
                rvec.release()
                tvec.release()
                inliers.release()
            }
        } finally {
            keypointMat.release()
            queryDescriptors.release()
        }
    }

    private fun cvPoseToArCore(rvec: Mat, tvec: Mat): Pose {
        val rCw = Mat()
        try {
            Calib3d.Rodrigues(rvec, rCw)
            val cameraAxisFlip = doubleArrayOf(1.0, -1.0, -1.0)
            val rWc = Array(3) { row ->
                DoubleArray(3) { col -> rCw.get(col, row)[0] * cameraAxisFlip[col] }
            }
            val t = DoubleArray(3) { row -> tvec.get(row, 0)[0] }
            val center = DoubleArray(3) { row ->
                -(rCw.get(0, row)[0] * t[0] + rCw.get(1, row)[0] * t[1] + rCw.get(2, row)[0] * t[2])
            }
            val q = rotationMatrixToQuaternion(rWc)
            return Pose(
                floatArrayOf(center[0].toFloat(), center[1].toFloat(), center[2].toFloat()),
                floatArrayOf(q[0].toFloat(), q[1].toFloat(), q[2].toFloat(), q[3].toFloat()),
            )
        } finally {
            rCw.release()
        }
    }

    private fun rotationMatrixToQuaternion(r: Array<DoubleArray>): DoubleArray {
        val trace = r[0][0] + r[1][1] + r[2][2]
        val q = DoubleArray(4)
        if (trace > 0.0) {
            val s = sqrt(trace + 1.0) * 2.0
            q[3] = 0.25 * s
            q[0] = (r[2][1] - r[1][2]) / s
            q[1] = (r[0][2] - r[2][0]) / s
            q[2] = (r[1][0] - r[0][1]) / s
        } else if (r[0][0] > r[1][1] && r[0][0] > r[2][2]) {
            val s = sqrt(1.0 + r[0][0] - r[1][1] - r[2][2]) * 2.0
            q[3] = (r[2][1] - r[1][2]) / s
            q[0] = 0.25 * s
            q[1] = (r[0][1] + r[1][0]) / s
            q[2] = (r[0][2] + r[2][0]) / s
        } else if (r[1][1] > r[2][2]) {
            val s = sqrt(1.0 + r[1][1] - r[0][0] - r[2][2]) * 2.0
            q[3] = (r[0][2] - r[2][0]) / s
            q[0] = (r[0][1] + r[1][0]) / s
            q[1] = 0.25 * s
            q[2] = (r[1][2] + r[2][1]) / s
        } else {
            val s = sqrt(1.0 + r[2][2] - r[0][0] - r[1][1]) * 2.0
            q[3] = (r[1][0] - r[0][1]) / s
            q[0] = (r[0][2] + r[2][0]) / s
            q[1] = (r[1][2] + r[2][1]) / s
            q[2] = 0.25 * s
        }
        return q
    }

    companion object {
        private fun createOrb(): ORB {
            check(OpenCVLoader.initDebug()) { "OpenCV native runtime failed to initialize" }
            return ORB.create(1800)
        }

        private const val MIN_ATTEMPT_INTERVAL_NS = 450_000_000L
        private const val MIN_PNP_CORRESPONDENCES = 24
        private const val MIN_PNP_INLIERS = 18
        private const val LOWE_RATIO = 0.75f
        private const val PNP_ITERATIONS = 200
        private const val PNP_REPROJECTION_ERROR_PX = 4.0f
        private const val PNP_CONFIDENCE = 0.995
    }
}
