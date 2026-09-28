package com.jhani.measurear.presentation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Volume-key stamping: pressing a volume key places a point without touching the screen, so
 * the phone doesn't tip. Only active while the Measure screen is showing.
 */
object StampKeys {
    @Volatile
    var enabled: Boolean = false

    private val _presses = MutableSharedFlow<Long>(extraBufferCapacity = 4)

    /** Press times ([System.nanoTime]). */
    val presses: SharedFlow<Long> = _presses.asSharedFlow()

    /** Returns true when the key press was consumed as a stamp. */
    fun onVolumeKey(): Boolean {
        if (!enabled) return false
        _presses.tryEmit(System.nanoTime())
        return true
    }
}
