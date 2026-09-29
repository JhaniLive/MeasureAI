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
}
