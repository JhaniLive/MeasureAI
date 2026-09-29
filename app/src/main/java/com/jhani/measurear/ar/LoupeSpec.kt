package com.jhani.measurear.ar

/**
 * Magnifier layout and behavior, shared by the GL renderer (camera image) and the Compose
 * overlay (ring, crosshair and magnified measurements) so both line up exactly.
 *
 * It floats just above-left of the center crosshair, and only appears when it helps: the aim
 * is held steady on something close enough to resolve fine detail.
 */
object LoupeSpec {
    const val RADIUS_DP = 56f

    // Loupe center relative to the screen center (the crosshair)
    const val OFFSET_X_DP = -100f
    const val OFFSET_Y_DP = -140f

    const val ZOOM = 2.5f

    // While dragging a point, the loupe floats this far above the finger
    const val DRAG_LIFT_DP = 120f

    // Beyond this the camera can't resolve detail worth magnifying
    const val MAX_DISTANCE_METERS = 2.0f

    // "Steady" is judged from the phone's own motion (the aim point itself can jitter on
    // estimated surfaces even with a still hand): over the last STEADY_NANOS the camera
    // rotated less than STEADY_DEGREES and moved less than STEADY_METERS.
    const val STEADY_NANOS = 250_000_000L
    const val STEADY_DEGREES = 1.0f
    const val STEADY_METERS = 0.015f

    // Once shown, hide again only after a clear move (hysteresis avoids flicker)
    const val HIDE_DEGREES = 2.5f
    const val HIDE_METERS = 0.04f

    /** Loupe center in view pixels for a viewport of the given size. */
    fun centerX(viewportWidth: Float, density: Float) = viewportWidth / 2f + OFFSET_X_DP * density
    fun centerY(viewportHeight: Float, density: Float) = viewportHeight / 2f + OFFSET_Y_DP * density
}
