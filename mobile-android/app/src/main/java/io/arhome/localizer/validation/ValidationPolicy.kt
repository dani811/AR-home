package io.arhome.localizer.validation

/** Versioned, provisional engineering gates, not a physical accuracy guarantee. */
object ValidationPolicy {
    const val VERSION = 1
    const val MIN_FRAMES = 12
    const val MAX_FRAMES = 160
    const val MAX_POSITION_ERROR_METERS = 0.20
    const val MAX_ROTATION_ERROR_DEGREES = 5.0

    fun heldOutIndices(count: Int): List<Int> = (0 until count).filter { it % 4 == 1 }

    fun poseConsistent(positionError: Double, rotationError: Double): Boolean =
        positionError.isFinite() && rotationError.isFinite() &&
            positionError >= 0 && rotationError >= 0 && positionError <= MAX_POSITION_ERROR_METERS && rotationError <= MAX_ROTATION_ERROR_DEGREES

    fun passes(tested: Int, recovered: Int, weakImages: Int): Boolean =
        tested >= 3 && recovered * 10 >= tested * 9 && weakImages == 0
}
