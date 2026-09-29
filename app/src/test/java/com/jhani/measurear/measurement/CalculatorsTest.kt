package com.jhani.measurear.measurement

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorsTest {

    @Test fun paintTwoCoatsMinusDoor() {
        // 3.6 × 2.7 m wall = 9.72 m², minus a door 1.9 → 7.82 m²; 2 coats at 10 m²/L = 1.564 L
        val r = Calculators.paint(9.72f, coats = 2, doors = 1, canLiters = 1f, pricePerCan = 450f)
        assertEquals(7.82f, r.paintedArea, 1e-3f)
        assertEquals(1.564f, r.liters, 1e-3f)
        assertEquals(2, r.cans)          // rounded up to whole 1 L cans
        assertEquals(900f, r.cost!!, 1e-3f)
    }

    @Test fun paintNeverNegative() {
        val r = Calculators.paint(2f, doors = 3)
        assertEquals(0f, r.paintedArea, 0f)
        assertEquals(0, r.cans)
    }

    @Test fun tilesWithWasteAndBoxes() {
        // 12 m² floor, 60×60 cm tiles (0.36 m²), 10% waste: 13.2 / 0.36 = 36.67 → 37 tiles; 4 per box → 10 boxes
        val r = Calculators.tiles(12f, 0.6f, 0.6f, wastePercent = 10f, perBox = 4, pricePerTile = 120f)
        assertEquals(37, r.tiles)
        assertEquals(10, r.boxes)
        assertEquals(4440f, r.cost!!, 1e-3f)
    }

    @Test fun tilesExactFitNotRoundedUp() {
        // 3.6 m² with 30×30 tiles, no waste: exactly 40
        assertEquals(40, Calculators.tiles(3.6f, 0.3f, 0.3f, wastePercent = 0f).tiles)
    }

    @Test fun flooringBoxes() {
        // 15 m², boxes of 2.4 m², 8% waste: 16.2 / 2.4 = 6.75 → 7 boxes covering 16.8 m²
        val r = Calculators.flooring(15f, 2.4f)
        assertEquals(7, r.boxes)
        assertEquals(16.8f, r.coveredArea, 1e-3f)
    }
}
