package com.jhani.measurear.level

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassMathTest {

    @Test fun cardinalPoints() {
        assertEquals("N", CompassMath.cardinal(0f))
        assertEquals("N", CompassMath.cardinal(359f))
        assertEquals("N", CompassMath.cardinal(22f))
        assertEquals("NE", CompassMath.cardinal(23f))
        assertEquals("E", CompassMath.cardinal(90f))
        assertEquals("SW", CompassMath.cardinal(225f))
        assertEquals("NW", CompassMath.cardinal(-45f))
    }

    @Test fun shortestTurnAcrossNorth() {
        assertEquals(20f, CompassMath.delta(350f, 10f), 1e-4f)
        assertEquals(-20f, CompassMath.delta(10f, 350f), 1e-4f)
        assertEquals(180f, CompassMath.delta(0f, 180f), 1e-4f)
    }

    @Test fun smoothingWrapsAroundNorth() {
        // Halfway from 350° to 10° is 0°, not 180°
        assertEquals(0f, CompassMath.smooth(350f, 10f, 0.5f), 1e-4f)
    }
}
