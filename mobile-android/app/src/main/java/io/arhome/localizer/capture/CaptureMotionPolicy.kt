package io.arhome.localizer.capture

enum class CaptureMotionKind {
    INITIAL,
    TRANSLATION,
    ROTATION_ONLY,
    WAIT,
}

/** Keyframe overlap thresholds are independent of depth-quality heuristics. */
object CaptureMotionPolicy {
    const val MIN_TRANSLATION_METERS = 0.08
    const val MIN_ROTATION_DEGREES = 7.0
    const val MIN_INTERVAL_MS = 300.0
    const val SLOW_INTERVAL_MS = 1_200.0
    const val SLOW_TRANSLATION_METERS = 0.03
    const val SLOW_ROTATION_DEGREES = 2.5

    fun classify(translationMeters: Double, rotationDegrees: Double, elapsedMs: Double = MIN_INTERVAL_MS): CaptureMotionKind {
        require(translationMeters >= 0.0 && translationMeters.isFinite())
        require(rotationDegrees >= 0.0 && rotationDegrees.isFinite())
        require(elapsedMs >= 0.0 && elapsedMs.isFinite())
        if (elapsedMs < MIN_INTERVAL_MS) return CaptureMotionKind.WAIT
        val slow = elapsedMs >= SLOW_INTERVAL_MS
        val translated = translationMeters >= MIN_TRANSLATION_METERS ||
            (slow && translationMeters >= SLOW_TRANSLATION_METERS)
        val rotated = rotationDegrees >= MIN_ROTATION_DEGREES ||
            (slow && rotationDegrees >= SLOW_ROTATION_DEGREES)
        return when {
            translated -> CaptureMotionKind.TRANSLATION
            rotated -> CaptureMotionKind.ROTATION_ONLY
            else -> CaptureMotionKind.WAIT
        }
    }
}
