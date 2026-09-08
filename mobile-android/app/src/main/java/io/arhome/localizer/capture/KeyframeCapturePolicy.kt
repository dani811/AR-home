package io.arhome.localizer.capture

internal class KeyframeCapturePolicy {
    data class Decision(val capture: Boolean, val reason: String)

    fun evaluate(elapsedMs: Double, translationMeters: Double, rotationDegrees: Double): Decision {
        if (elapsedMs < MIN_INTERVAL_MS) return Decision(false, "hold steady")
        if (translationMeters >= MIN_TRANSLATION_METERS || rotationDegrees >= MIN_ROTATION_DEGREES) {
            return Decision(true, "new viewpoint")
        }
        val slowMovement = translationMeters >= SLOW_TRANSLATION_METERS ||
            rotationDegrees >= SLOW_ROTATION_DEGREES
        if (elapsedMs >= SLOW_CAPTURE_INTERVAL_MS && slowMovement) {
            return Decision(true, "slow-motion recovery")
        }
        return Decision(false, "move sideways or orbit the furniture")
    }

    companion object {
        const val MIN_INTERVAL_MS = 300.0
        const val MIN_TRANSLATION_METERS = 0.08
        const val MIN_ROTATION_DEGREES = 7.0
        const val SLOW_CAPTURE_INTERVAL_MS = 1_200.0
        const val SLOW_TRANSLATION_METERS = 0.03
        const val SLOW_ROTATION_DEGREES = 2.5
    }
}
