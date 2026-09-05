package io.arhome.localizer.map

import com.google.ar.core.Pose
import org.json.JSONObject
import java.io.File

class PersistentMapLoader {

    fun load(root: File): PersistentMap {
        require(root.isDirectory) { "Map root does not exist: ${root.absolutePath}" }
        val manifestFile = File(root, "manifest.json")
        require(manifestFile.isFile) { "Map manifest does not exist: ${manifestFile.absolutePath}" }

        val manifest = JSONObject(manifestFile.readText())
        val schemaVersion = manifest.getInt("schemaVersion")
        val keyframesJson = manifest.getJSONArray("keyframes")
        PersistentMapSchema.validatePoseMetadata(
            schemaVersion = schemaVersion,
            coordinateFrame = manifest.getString("coordinateFrame"),
            frameCount = keyframesJson.length(),
            poseCount = when (schemaVersion) {
                2 -> manifest.optInt("poseSnapshotCount", -1)
                3, 4 -> manifest.optInt("poseChainCount", -1)
                else -> null
            },
            poseTimestampNs = when (schemaVersion) {
                2 -> manifest.optLong("poseSnapshotTimestampNs", -1)
                3, 4 -> manifest.optLong("poseChainTimestampNs", -1)
                else -> null
            },
        )
        val keyframes = buildList(keyframesJson.length()) {
            for (index in 0 until keyframesJson.length()) {
                val json = keyframesJson.getJSONObject(index)
                val image = File(root, json.getString("image"))
                require(image.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) { "Keyframe image outside map root" }
                require(image.isFile) { "Missing keyframe image: ${image.absolutePath}" }

                val translation = json.floatArray("poseTranslationMeters")
                val rotation = json.floatArray("poseRotationQuaternion")
                require(translation.size == 3) { "Invalid translation for keyframe ${json.getString("id")}" }
                require(rotation.size == 4) { "Invalid rotation for keyframe ${json.getString("id")}" }

                val intrinsics = json.getJSONObject("intrinsics")
                add(
                    PersistentKeyframe(
                        id = json.getString("id"),
                        image = image,
                        timestampNs = json.getLong("timestampNs"),
                        pose = Pose(translation, rotation),
                        focalLengthPixels = intrinsics.floatArray("focalLengthPixels"),
                        principalPointPixels = intrinsics.floatArray("principalPointPixels"),
                        imageDimensionsPixels = intrinsics.intArray("imageDimensionsPixels"),
                        depth = json.optJSONObject("depth")?.let { depth ->
                            require(depth.getString("format") == "ARCORE_RAW_DEPTH_MM_U16_LE") { "Unsupported depth format" }
                            val width = depth.getInt("width"); val height = depth.getInt("height")
                            require(width in 1..2048 && height in 1..2048) { "Invalid depth dimensions" }
                            val affine = depth.floatArray("imageToDepthUv")
                            require(affine.size == 6 && affine.all { it.isFinite() }) { "Invalid depth alignment" }
                            val sourceTime = depth.getLong("timestampNs")
                            val frameTime = json.getLong("frameTimestampNs")
                            val alignedFrameTime = if (schemaVersion >= 4) {
                                depth.getLong("alignedFrameTimestampNs")
                            } else {
                                sourceTime
                            }
                            require(sourceTime > 0 && alignedFrameTime == frameTime) {
                                "Depth image is not aligned to its keyframe"
                            }
                            val confidentPixels = depth.optInt("confidentPixels", 0)
                            require(confidentPixels in 0..Math.multiplyExact(width, height)) {
                                "Invalid confident depth pixel count"
                            }
                            PersistentDepth(depthFile(root, depth.getString("image"), width.toLong() * height * 2),
                                depthFile(root, depth.getString("confidence"), width.toLong() * height),
                                width, height, sourceTime, alignedFrameTime, affine, confidentPixels,
                                depth.optBoolean("usableForMapping", confidentPixels > 0))
                        },
                    ),
                )
            }
        }

        return PersistentMap(
            schemaVersion = schemaVersion,
            sessionId = manifest.getString("sessionId"),
            coordinateFrame = manifest.getString("coordinateFrame"),
            root = root,
            keyframes = keyframes,
            landmarkSource = manifest.optString("landmarkSource", "TRIANGULATED_RGB"),
        )
    }

    private fun depthFile(root: File, path: String, bytes: Long): File = File(root, path).also {
        require(it.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && it.isFile && it.length() == bytes) {
            "Missing, invalid or truncated depth data: $path"
        }
    }
}

object PersistentMapSchema {
    const val CURRENT_VERSION = 4

    fun validatePoseMetadata(
        schemaVersion: Int,
        coordinateFrame: String,
        frameCount: Int,
        poseCount: Int?,
        poseTimestampNs: Long?,
    ) {
        require(schemaVersion in 1..CURRENT_VERSION) { "Unsupported map schema version: $schemaVersion" }
        if (schemaVersion == 2) {
            require(coordinateFrame == "ARCORE_ANCHOR_SNAPSHOT" && poseCount == frameCount && poseTimestampNs != null && poseTimestampNs > 0) {
                "Incomplete common-frame pose snapshot"
            }
        }
        if (schemaVersion in 3..4) {
            require(coordinateFrame == "ARCORE_PAIRWISE_ANCHOR_CHAIN" && poseCount == frameCount && poseTimestampNs != null && poseTimestampNs > 0) {
                "Incomplete pairwise anchor pose chain"
            }
        }
    }
}
