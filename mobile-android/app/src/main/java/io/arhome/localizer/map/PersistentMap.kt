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
)

data class PersistentMap(
    val schemaVersion: Int,
    val sessionId: String,
    val coordinateFrame: String,
    val root: File,
    val keyframes: List<PersistentKeyframe>,
) {
    init {
        require(coordinateFrame == "ARCORE_SESSION_LOCAL") {
            "Unsupported coordinate frame: $coordinateFrame"
        }
        require(keyframes.isNotEmpty()) { "Persistent map contains no keyframes" }
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
