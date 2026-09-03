package io.arhome.localizer.localization

import com.google.ar.core.Pose
import io.arhome.localizer.map.PersistentKeyframe
import io.arhome.localizer.map.PersistentMap
import kotlin.math.sqrt
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs

data class PersistentVisualLandmark(
    val worldPointMeters: DoubleArray,
    val descriptor: ByteArray,
    val sourceKeyframeId: String,
    val pairedKeyframeId: String,
)

data class PersistentLandmarkMap(
    val landmarks: List<PersistentVisualLandmark>,
    val evaluatedPairs: Int,
    val triangulatedPairs: Int,
    val rejectedGeometry: Int,
)

class PersistentLandmarkBuilder {

    private data class Features(
        val keyframe: PersistentKeyframe,
        val keypoints: List<KeyPoint>,
        val descriptors: Mat,
    )

    private data class Extrinsic(
        val r: Array<DoubleArray>,
        val t: DoubleArray,
    )

    private val orb: ORB = createOrb()
    private val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

    fun build(map: PersistentMap, checkpoint: () -> Unit = {}): PersistentLandmarkMap {
        val features = ArrayList<Features>()
        val landmarks = ArrayList<PersistentVisualLandmark>()
        var evaluatedPairs = 0
        var triangulatedPairs = 0
        var rejectedGeometry = 0

        try {
            map.keyframes.forEach { checkpoint(); extractFeatures(it)?.let(features::add) }
            for (i in features.indices) {
                for (j in i + 1..minOf(i + PAIR_WINDOW, features.lastIndex)) {
                    checkpoint()
                    val first = features[i]
                    val second = features[j]
                    evaluatedPairs++
                    val baseline = poseDistanceMeters(first.keyframe.pose, second.keyframe.pose)
                    if (baseline !in MIN_BASELINE_METERS..MAX_BASELINE_METERS) {
                        rejectedGeometry++
                        continue
                    }

                    val matches = goodMatches(first.descriptors, second.descriptors)
                    if (matches.size < MIN_PAIR_MATCHES) continue
                    val accepted = triangulate(first, second, matches)
                    if (accepted.isNotEmpty()) triangulatedPairs++
                    landmarks += accepted
                    if (landmarks.size >= MAX_LANDMARKS) break
                }
                if (landmarks.size >= MAX_LANDMARKS) break
            }
        } finally {
            features.forEach { it.descriptors.release() }
            orb.clear()
            matcher.clear()
        }

        return PersistentLandmarkMap(
            landmarks = landmarks.take(MAX_LANDMARKS),
            evaluatedPairs = evaluatedPairs,
            triangulatedPairs = triangulatedPairs,
            rejectedGeometry = rejectedGeometry,
        )
    }

    private fun extractFeatures(keyframe: PersistentKeyframe): Features? {
        val gray = Imgcodecs.imread(keyframe.image.absolutePath, Imgcodecs.IMREAD_GRAYSCALE)
        if (gray.empty()) {
            gray.release()
            return null
        }
        val keypointMat = MatOfKeyPoint()
        val descriptors = Mat()
        return try {
            orb.detectAndCompute(gray, Mat(), keypointMat, descriptors)
            if (descriptors.empty() || descriptors.rows() < MIN_FEATURES) {
                descriptors.release()
                null
            } else {
                Features(keyframe, keypointMat.toList(), descriptors)
            }
        } finally {
            gray.release()
            keypointMat.release()
        }
    }

    private fun goodMatches(first: Mat, second: Mat): List<DMatch> {
        val pairs = mutableListOf<MatOfDMatch>()
        matcher.knnMatch(first, second, pairs, 2)
        return buildList {
            for (pair in pairs) {
                val values = pair.toArray()
                if (values.size >= 2 && values[0].distance < LOWE_RATIO * values[1].distance) add(values[0])
                pair.release()
            }
        }
    }

    private fun triangulate(first: Features, second: Features, matches: List<DMatch>): List<PersistentVisualLandmark> {
        val p1 = projection(first.keyframe)
        val p2 = projection(second.keyframe)
        val points1 = Mat(2, matches.size, CvType.CV_64F)
        val points2 = Mat(2, matches.size, CvType.CV_64F)
        val points4d = Mat()
        try {
            matches.forEachIndexed { index, match ->
                val a = first.keypoints[match.queryIdx].pt
                val b = second.keypoints[match.trainIdx].pt
                points1.put(0, index, a.x)
                points1.put(1, index, a.y)
                points2.put(0, index, b.x)
                points2.put(1, index, b.y)
            }
            Calib3d.triangulatePoints(p1, p2, points1, points2, points4d)
            val firstExtrinsic = cvExtrinsic(first.keyframe.pose)
            val secondExtrinsic = cvExtrinsic(second.keyframe.pose)
            return buildList {
                for (index in matches.indices) {
                    val w = points4d.get(3, index)?.firstOrNull() ?: continue
                    if (!w.isFinite() || kotlin.math.abs(w) < 1e-9) continue
                    val point = DoubleArray(3) { row -> (points4d.get(row, index)?.firstOrNull() ?: Double.NaN) / w }
                    if (point.any { !it.isFinite() }) continue
                    if (cameraDepth(firstExtrinsic, point) <= MIN_DEPTH_METERS) continue
                    if (cameraDepth(secondExtrinsic, point) <= MIN_DEPTH_METERS) continue

                    val a = first.keypoints[matches[index].queryIdx].pt
                    val b = second.keypoints[matches[index].trainIdx].pt
                    if (reprojectionError(p1, point, a.x, a.y) > MAX_REPROJECTION_ERROR_PX) continue
                    if (reprojectionError(p2, point, b.x, b.y) > MAX_REPROJECTION_ERROR_PX) continue

                    val descriptor = ByteArray(first.descriptors.cols())
                    first.descriptors.get(matches[index].queryIdx, 0, descriptor)
                    add(
                        PersistentVisualLandmark(
                            worldPointMeters = point,
                            descriptor = descriptor,
                            sourceKeyframeId = first.keyframe.id,
                            pairedKeyframeId = second.keyframe.id,
                        ),
                    )
                }
            }
        } finally {
            p1.release()
            p2.release()
            points1.release()
            points2.release()
            points4d.release()
        }
    }

    private fun projection(keyframe: PersistentKeyframe): Mat {
        val e = cvExtrinsic(keyframe.pose)
        val fx = keyframe.focalLengthPixels[0].toDouble()
        val fy = keyframe.focalLengthPixels[1].toDouble()
        val cx = keyframe.principalPointPixels[0].toDouble()
        val cy = keyframe.principalPointPixels[1].toDouble()
        val p = Mat(3, 4, CvType.CV_64F)
        for (column in 0..2) {
            p.put(0, column, fx * e.r[0][column] + cx * e.r[2][column])
            p.put(1, column, fy * e.r[1][column] + cy * e.r[2][column])
            p.put(2, column, e.r[2][column])
        }
        p.put(0, 3, fx * e.t[0] + cx * e.t[2])
        p.put(1, 3, fy * e.t[1] + cy * e.t[2])
        p.put(2, 3, e.t[2])
        return p
    }

    private fun cvExtrinsic(pose: Pose): Extrinsic {
        val q = pose.rotationQuaternion
        val x = q[0].toDouble()
        val y = q[1].toDouble()
        val z = q[2].toDouble()
        val w = q[3].toDouble()
        val rwc = arrayOf(
            doubleArrayOf(1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)),
            doubleArrayOf(2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)),
            doubleArrayOf(2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)),
        )
        val r = Array(3) { row -> DoubleArray(3) { col -> rwc[col][row] } }
        // ARCore camera convention is OpenGL-like (+Y up, camera looks toward -Z).
        // OpenCV expects +Y down and +Z forward, so flip camera Y and Z.
        for (col in 0..2) {
            r[1][col] = -r[1][col]
            r[2][col] = -r[2][col]
        }
        val c = pose.translation.map(Float::toDouble)
        val t = DoubleArray(3) { row -> -(r[row][0] * c[0] + r[row][1] * c[1] + r[row][2] * c[2]) }
        return Extrinsic(r, t)
    }

    private fun cameraDepth(e: Extrinsic, point: DoubleArray): Double =
        e.r[2][0] * point[0] + e.r[2][1] * point[1] + e.r[2][2] * point[2] + e.t[2]

    private fun reprojectionError(p: Mat, point: DoubleArray, expectedX: Double, expectedY: Double): Double {
        fun row(index: Int): Double =
            p.get(index, 0)[0] * point[0] + p.get(index, 1)[0] * point[1] + p.get(index, 2)[0] * point[2] + p.get(index, 3)[0]
        val z = row(2)
        if (kotlin.math.abs(z) < 1e-9) return Double.POSITIVE_INFINITY
        val dx = row(0) / z - expectedX
        val dy = row(1) / z - expectedY
        return sqrt(dx * dx + dy * dy)
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
        private fun createOrb(): ORB {
            check(OpenCVLoader.initDebug()) { "OpenCV native runtime failed to initialize" }
            return ORB.create(1800)
        }

        private const val PAIR_WINDOW = 4
        private const val MIN_FEATURES = 100
        private const val MIN_PAIR_MATCHES = 35
        private const val MIN_BASELINE_METERS = 0.12f
        private const val MAX_BASELINE_METERS = 1.50f
        private const val MIN_DEPTH_METERS = 0.10
        private const val MAX_REPROJECTION_ERROR_PX = 4.0
        private const val LOWE_RATIO = 0.75f
        private const val MAX_LANDMARKS = 4000
    }
}
