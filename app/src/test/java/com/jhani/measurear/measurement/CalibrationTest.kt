package com.jhani.measurear.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationTest {

    @Test fun cardMeasuredLongCorrectsDown() {
        // Card read as 9.10 cm: every length is ~6% too long
        val f = Calibration.factorFor(0.091f, Calibration.Reference.CARD.meters)!!
        assertEquals(0.9407f, f, 1e-3f)
        assertEquals(0.0856f, 0.091f * f, 1e-5f)
    }

    @Test fun correctionScalesAreasAndVolumesThroughPoints() {
        // Scaling the points by f scales lengths by f, areas by f², volumes by f³
        val f = 0.9f
        val pts = listOf(Vec3(0f, 0f, 0f), Vec3(0.5f, 0f, 0f), Vec3(0.5f, 0f, 0.4f), Vec3(0.5f, 0.3f, 0f))
        val raw = ShapeMath.compute(MeasureMode.VOLUME, pts).values[0].value
        val scaled = ShapeMath.compute(MeasureMode.VOLUME, pts.map { it * f }).values[0].value
        assertEquals(raw * f * f * f, scaled, 1e-6f)
    }

    @Test fun implausibleMeasurementRejected() {
        // Card read as 15 cm: a point missed the card, not a scale error
        assertNull(Calibration.factorFor(0.15f, Calibration.Reference.CARD.meters))
        assertNull(Calibration.factorFor(0f, Calibration.Reference.CARD.meters))
    }
}
