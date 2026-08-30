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
        require(schemaVersion == 1) { "Unsupported map schema version: $schemaVersion" }

        val keyframesJson = manifest.getJSONArray("keyframes")
        val keyframes = buildList(keyframesJson.length()) {
            for (index in 0 until keyframesJson.length()) {
                val json = keyframesJson.getJSONObject(index)
                val image = File(root, json.getString("image"))
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
        )
    }
}
