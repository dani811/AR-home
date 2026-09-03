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
        require(schemaVersion in 1..2) { "Unsupported map schema version: $schemaVersion" }

        val keyframesJson = manifest.getJSONArray("keyframes")
        if (schemaVersion == 2) {
            require(manifest.getInt("poseSnapshotCount") == keyframesJson.length() &&
                manifest.getLong("poseSnapshotTimestampNs") > 0) { "Incomplete common-frame pose snapshot" }
        }
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
                            val time = depth.getLong("timestampNs")
                            require(time == json.getLong("frameTimestampNs")) { "Stale depth image" }
                            PersistentDepth(depthFile(root, depth.getString("image"), width.toLong() * height * 2),
                                depthFile(root, depth.getString("confidence"), width.toLong() * height),
                                width, height, time, affine)
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
