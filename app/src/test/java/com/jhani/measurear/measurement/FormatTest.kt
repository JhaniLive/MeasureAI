package com.jhani.measurear.measurement

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private fun inches(i: Double) = (i / 39.37008).toFloat()

    @Test fun wholeInches() = assertEquals("5″", formatFeetInches(inches(5.0)))
    @Test fun eighths() = assertEquals("5 ⅜″", formatFeetInches(inches(5.375)))
    @Test fun sixteenthsNotReduced() = assertEquals("2 3/16″", formatFeetInches(inches(2.1875)))
    @Test fun halfReduced() = assertEquals("3′ 4 ½″", formatFeetInches(inches(40.5)))
    @Test fun roundsUpToNextFoot() = assertEquals("1′ 0″", formatFeetInches(inches(11.98)))
    @Test fun fractionOnly() = assertEquals("¼″", formatFeetInches(inches(0.25)))
    @Test fun feetWithFractionOnly() = assertEquals("2′ 0 ¾″", formatFeetInches(inches(24.75)))
    @Test fun metricMetersAndCentimeters() {
        assertEquals("28.5 cm", formatLength(0.285f, MeasureUnit.METRIC))
        assertEquals("3.60 m", formatLength(3.6f, MeasureUnit.METRIC))
    }
    @Test fun boxSizeInOneUnit() {
        assertEquals("200 × 90 × 85 cm", formatBoxSize(2f, 0.9f, 0.85f, MeasureUnit.METRIC))
        assertEquals("79 × 35 × 33 in", formatBoxSize(2f, 0.9f, 0.85f, MeasureUnit.IMPERIAL))
    }
    @Test fun areaInSquareMetersFromATenth() {
        assertEquals("0.93 m²", formatArea(0.93125f, false, MeasureUnit.METRIC))
        assertEquals("≈ 1.80 m²", formatArea(1.8f, true, MeasureUnit.METRIC))
        assertEquals("45.9 cm²", formatArea(0.00459f, false, MeasureUnit.METRIC))
    }
    @Test fun wholeCentimetresWithoutDecimal() {
        assertEquals("90 cm", formatLength(0.9f, MeasureUnit.METRIC))
        assertEquals("8.6 cm", formatLength(0.0856f, MeasureUnit.METRIC))
    }
}
