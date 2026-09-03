package io.arhome.localizer.map

import com.google.ar.core.Pose
import org.json.JSONObject
import java.io.File

data class PersistentKeyframe(
    val id: String,
    val image: File,
    val timestampNs: Long,
    val pose: Pose,
    val focalLengthPixels: FloatArray,
    val principalPointPixels: FloatArray,
    val imageDimensionsPixels: IntArray,
    val depth: PersistentDepth? = null,
)

data class PersistentDepth(
    val image: File,
    val confidence: File,
    val width: Int,
    val height: Int,
    val timestampNs: Long,
    val imageToDepthUv: FloatArray,
)

data class PersistentMap(
    val schemaVersion: Int,
    val sessionId: String,
    val coordinateFrame: String,
    val root: File,
    val keyframes: List<PersistentKeyframe>,
    val landmarkSource: String = "TRIANGULATED_RGB",
) {
    init {
        require(coordinateFrame == "ARCORE_SESSION_LOCAL" || coordinateFrame == "ARCORE_ANCHOR_SNAPSHOT") {
            "Unsupported coordinate frame: $coordinateFrame"
        }
        require(keyframes.isNotEmpty()) { "Persistent map contains no keyframes" }
        require(landmarkSource in setOf("TRIANGULATED_RGB", "RAW_DEPTH")) { "Unsupported landmark source: $landmarkSource" }
    }
}

internal fun JSONObject.floatArray(name: String): FloatArray {
    val values = getJSONArray(name)
    return FloatArray(values.length()) { index -> values.getDouble(index).toFloat() }
}

internal fun JSONObject.intArray(name: String): IntArray {
    val values = getJSONArray(name)
    return IntArray(values.length()) { index -> values.getInt(index) }
}
