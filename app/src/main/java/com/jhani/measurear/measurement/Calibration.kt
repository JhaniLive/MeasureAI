package com.jhani.measurear.measurement

/**
 * Scale calibration against an object of known size. Without a depth sensor, ARCore's sense
 * of scale can be off by a few percent for a whole session; measuring something exact (a bank
 * card is 85.60 mm wide by ISO/IEC 7810) gives a factor that corrects every later length.
 * Areas scale by factor², volumes by factor³ — handled by scaling the points themselves.
 */
object Calibration {

    /** Reference objects the user can measure. */
    enum class Reference(val label: String, val meters: Float) {
        CARD("Bank / ID card (long edge)", 0.0856f),
        A4_SHORT("A4 paper (short edge)", 0.210f),
        A4_LONG("A4 paper (long edge)", 0.297f)
    }

    /** Corrections beyond ±25% mean the measurement itself went wrong, not the scale. */
    const val MAX_CORRECTION = 0.25f

    /**
     * Factor that turns [measured] into [real], or null when the measurement is implausible
     * (too far off to be a scale error — e.g. a point missed the object).
     */
    fun factorFor(measured: Float, real: Float): Float? {
        if (measured <= 0f || real <= 0f) return null
        val factor = real / measured
        return if (factor in (1f - MAX_CORRECTION)..(1f + MAX_CORRECTION)) factor else null
    }
}
