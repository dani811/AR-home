package io.arhome.localizer.localization

import io.arhome.localizer.depth.DepthGeometry
import io.arhome.localizer.map.PersistentMap
import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs

/** Backprojects measured depth at RGB feature locations using the saved crop/alignment. */
class DepthLandmarkBuilder {
    fun build(map: PersistentMap, checkpoint: () -> Unit): PersistentLandmarkMap {
        val orb = ORB.create(1800)
        val all = ArrayList<List<PersistentVisualLandmark>>()
        var rejected = 0
        try {
            for (frame in map.keyframes) {
                checkpoint()
                val depth = frame.depth ?: continue
                val millimeters = depth.image.readBytes()
                val confidence = depth.confidence.readBytes()
                val gray = Imgcodecs.imread(frame.image.absolutePath, Imgcodecs.IMREAD_GRAYSCALE)
                val mask = Mat(); val points = MatOfKeyPoint(); val descriptors = Mat()
                try {
                    require(!gray.empty()) { "Unreadable RGB image for depth: ${frame.id}" }
                    orb.detectAndCompute(gray, mask, points, descriptors)
                    val keypoints = points.toList()
                    val accepted = ArrayList<PersistentVisualLandmark>()
                    // One feature per measured depth pixel per frame: no multiplication of one measurement.
                    val usedPixels = HashSet<Int>()
                    for (n in keypoints.indices.sortedByDescending { keypoints[it].response }) {
                        val point = keypoints[n].pt
                        val pixel = DepthGeometry.depthPixel(point.x, point.y, depth.imageToDepthUv, depth.width, depth.height)
                        if (pixel == null || pixel in usedPixels) { rejected++; continue }
                        val z = DepthGeometry.stableDepthMeters(millimeters, confidence, depth.width, depth.height, pixel)
                        if (z == null) { rejected++; continue }
                        val camera = DepthGeometry.cameraPoint(point.x, point.y, z,
                            frame.focalLengthPixels[0].toDouble(), frame.focalLengthPixels[1].toDouble(),
                            frame.principalPointPixels[0].toDouble(), frame.principalPointPixels[1].toDouble())
                        val world = frame.pose.transformPoint(camera)
                        if (world.any { !it.isFinite() }) { rejected++; continue }
                        usedPixels.add(pixel)
                        val descriptor = ByteArray(descriptors.cols())
                        descriptors.get(n, 0, descriptor)
                        accepted.add(PersistentVisualLandmark(world.map { it.toDouble() }.toDoubleArray(), descriptor,
                            frame.id, "RAW_DEPTH"))
                    }
                    all.add(accepted)
                } finally { gray.release(); mask.release(); points.release(); descriptors.release() }
            }
        } finally { orb.clear() }
        // Fair round-robin sampling preserves coverage when the point budget is reached.
        val landmarks = ArrayList<PersistentVisualLandmark>()
        var index = 0
        while (landmarks.size < 4000) {
            checkpoint()
            var added = false
            for (framePoints in all) {
                if (index < framePoints.size && landmarks.size < 4000) {
                    landmarks.add(framePoints[index]); added = true
                }
            }
            if (!added) break
            index++
        }
        return PersistentLandmarkMap(landmarks, 0, 0, rejected)
    }
}
