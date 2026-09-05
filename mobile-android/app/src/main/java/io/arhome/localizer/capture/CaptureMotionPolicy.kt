package io.arhome.localizer.capture

enum class CaptureMotionKind {
    INITIAL,
    TRANSLATION,
    ROTATION_ONLY,
    WAIT,
}

/** Keeps RGB coverage turns distinct from translation that creates depth parallax. */
object CaptureMotionPolicy {
    const val MIN_TRANSLATION_METERS = 0.20
    const val MIN_ROTATION_DEGREES = 12.0

    fun classify(translationMeters: Double, rotationDegrees: Double): CaptureMotionKind {
        require(translationMeters >= 0.0 && translationMeters.isFinite())
        require(rotationDegrees >= 0.0 && rotationDegrees.isFinite())
        return when {
            translationMeters >= MIN_TRANSLATION_METERS -> CaptureMotionKind.TRANSLATION
            rotationDegrees >= MIN_ROTATION_DEGREES -> CaptureMotionKind.ROTATION_ONLY
            else -> CaptureMotionKind.WAIT
        }
    }
}
