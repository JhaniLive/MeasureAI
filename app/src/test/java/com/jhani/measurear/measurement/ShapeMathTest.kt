package com.jhani.measurear.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ShapeMathTest {
    private fun v(x: Float, y: Float, z: Float) = Vec3(x, y, z)
    private fun ShapeResult.value(label: String) = values.first { it.label == label }.value

    @Test fun line() {
        val r = ShapeMath.compute(MeasureMode.LINE, listOf(v(0f, 0f, 0f), v(0f, 0f, 2.5f)))
        assertEquals(2.5f, r.value("Length"), 1e-4f)
    }

    @Test fun distanceFromCamera() {
        val r = ShapeMath.compute(MeasureMode.DISTANCE, listOf(v(0f, 1.5f, -4f)), camera = v(0f, 1.5f, 0f))
        assertEquals(4f, r.value("Distance from you"), 1e-4f)
    }

    @Test fun angleRightCornerWithArms() {
        val r = ShapeMath.compute(MeasureMode.ANGLE, listOf(v(2f, 0f, 0f), v(0f, 0f, 0f), v(0f, 0f, 1.5f)))
        assertEquals(90f, r.values[0].value, 1e-3f)
        assertEquals(ValueKind.ANGLE, r.values[0].kind)
        assertEquals(2f, r.value("Arm 1"), 1e-4f)
        assertEquals(1.5f, r.value("Arm 2"), 1e-4f)
    }

    @Test fun pathTotal() {
        val r = ShapeMath.compute(MeasureMode.PATH, listOf(v(0f, 0f, 0f), v(3f, 0f, 0f), v(3f, 0f, 4f)))
        assertEquals(7f, r.value("Total length"), 1e-4f)
        assertEquals(4f, r.value("Last segment"), 1e-4f)
        assertEquals(2, r.labels.size)
    }

    @Test fun rectangleRoom() {
        val r = ShapeMath.compute(MeasureMode.RECTANGLE, listOf(v(0f, 0f, 0f), v(3.6f, 0f, 0f), v(3.5f, 0f, 3f)))
        assertEquals(10.8f, r.value("Area"), 1e-3f)
        assertEquals(3.6f, r.value("Width"), 1e-4f)
        assertEquals(3f, r.value("Length"), 1e-4f)
        assertEquals(13.2f, r.value("Perimeter"), 1e-3f)
        assertEquals(4, r.fill!!.size)
    }

    @Test fun circleTable() {
        val r = ShapeMath.compute(MeasureMode.CIRCLE, listOf(v(0f, 0.75f, 0f), v(0.4f, 0.76f, 0f)))
        // Rim point 1 cm high is projected onto the table plane: radius stays 0.4
        assertEquals(0.8f, r.value("Diameter"), 1e-4f)
        assertEquals((PI * 0.16).toFloat(), r.value("Area"), 1e-4f)
    }

    @Test fun areaRoomClosesAutomatically() {
        val pts = listOf(v(0f, 0f, 0f), v(4f, 0f, 0f), v(4f, 0f, 3f), v(2f, 0f, 3f), v(2f, 0f, 2f), v(0f, 0f, 2f))
        val r = ShapeMath.compute(MeasureMode.AREA, pts)
        assertEquals(10f, r.value("Area"), 1e-3f)
        assertEquals(14f, r.value("Perimeter"), 1e-3f)
        assertEquals(6, r.labels.size) // every side labelled, including the closing one
    }

    @Test fun areaOnWallWithoutDetectedSurface() {
        // 0.4 × 0.3 m picture frame on a wall (vertical), placed from estimates: no surface normal
        val pts = listOf(v(0f, 1f, -1f), v(0.4f, 1f, -1f), v(0.4f, 1.3f, -1f), v(0f, 1.3f, -1f))
        val r = ShapeMath.compute(MeasureMode.AREA, pts, surfaceNormal = null)
        assertEquals(0.12f, r.value("Area"), 1e-4f)
    }

    @Test fun rectangleOnWallWithoutDetectedSurface() {
        val pts = listOf(v(0f, 1f, -1f), v(0.9f, 1f, -1f), v(0.9f, 3.1f, -1f))
        val r = ShapeMath.compute(MeasureMode.RECTANGLE, pts, surfaceNormal = null)
        assertEquals(0.9f * 2.1f, r.value("Area"), 1e-3f)
    }

    @Test fun farBuilding() {
        // 30 m building, base 40 m away, phone 1.5 m above the ground
        val pts = listOf(v(0f, 0f, -40f), v(0f, 1.5f, 0f), v(0f, 30f, -40f))
        val r = ShapeMath.compute(MeasureMode.FAR, pts)
        assertEquals(30f, r.value("Height"), 1e-3f)
        assertEquals(40f, r.value("Distance to base"), 1e-3f)
        val err = r.value("± Height")
        assertTrue("error $err", err > 0.3f && err < 5f) // honest, not zero, not absurd
    }

    @Test fun farBaseOnlyShowsDistance() {
        val r = ShapeMath.compute(MeasureMode.FAR, listOf(v(3f, 0f, -4f)), camera = v(0f, 1.4f, 0f))
        assertEquals(5f, r.value("Distance to base"), 1e-4f)
    }

    @Test fun fitBoxCornersAndValues() {
        val sofa = BoxSpec(2f, 0.9f, 0.85f, "Sofa")
        val r = ShapeMath.compute(MeasureMode.FIT, listOf(v(1f, 0f, -2f)), box = sofa, yawDegrees = 0f)
        assertEquals(1.8f, r.value("Footprint"), 1e-4f)
        assertEquals(2f, r.value("Width"), 1e-4f)
        assertEquals(6, r.paths.size)
        val c = ShapeMath.boxCorners(v(1f, 0f, -2f), sofa, 0f)
        assertEquals(2f, c[0].distanceTo(c[1]), 1e-4f)      // width along x
        assertEquals(0.9f, c[1].distanceTo(c[2]), 1e-4f)    // depth along z
        assertEquals(0.85f, c[4].y - c[0].y, 1e-4f)         // height up
    }

    @Test fun fitBoxRotationKeepsSize() {
        val box = BoxSpec(1.2f, 0.6f, 0.75f)
        val c = ShapeMath.boxCorners(v(0f, 0f, 0f), box, 37f)
        assertEquals(1.2f, c[0].distanceTo(c[1]), 1e-4f)
        assertEquals(0.6f, c[1].distanceTo(c[2]), 1e-4f)
        assertEquals(0f, c[0].y, 1e-6f)
    }

    @Test fun hangThreeFramesLevelAndEvenlySpaced() {
        // Wall facing +z; 3 frames 40×50 cm, 8 cm apart, hooks 5 cm below the top
        val spec = HangSpec(3, 0.4f, 0.5f, 0.08f, 0.05f)
        val center = v(0f, 1.5f, -2f)
        val layout = ShapeMath.hangLayout(center, v(0f, 0f, 1f), spec)
        assertEquals(3, layout.nails.size)
        // Level: all nails at the same height, 20 cm above center (25 − 5)
        layout.nails.forEach { assertEquals(1.7f, it.y, 1e-4f) }
        // Evenly spaced: frame width + gap
        assertEquals(0.48f, layout.nails[0].distanceTo(layout.nails[1]), 1e-4f)
        assertEquals(0.48f, layout.nails[1].distanceTo(layout.nails[2]), 1e-4f)
        // Centered, and everything on the wall plane
        assertEquals(center.x, layout.nails[1].x, 1e-4f)
        layout.frames.flatten().forEach { assertEquals(-2f, it.z, 1e-4f) }
        assertEquals(1.36f, spec.totalWidth, 1e-4f)
        val r = ShapeMath.compute(MeasureMode.HANG, listOf(center), v(0f, 0f, 1f), hang = spec)
        assertEquals(0.48f, r.value("Nail spacing"), 1e-4f)
    }

    @Test fun volumeBox() {
        val pts = listOf(v(0f, 0f, 0f), v(0.5f, 0f, 0f), v(0.5f, 0f, 0.4f), v(0.5f, 0.3f, 0f))
        val r = ShapeMath.compute(MeasureMode.VOLUME, pts)
        assertEquals(0.06f, r.value("Volume"), 1e-4f)
        assertEquals(0.3f, r.value("Height"), 1e-4f)
        assertEquals(ValueKind.VOLUME, r.values[0].kind)
        assertEquals(6, r.paths.size) // base, top, four verticals
    }

    @Test fun draftsWithTooFewPointsAreEmpty() {
        for (mode in MeasureMode.values().filter { it != MeasureMode.FIT && it != MeasureMode.HANG }) {
            val r = ShapeMath.compute(mode, emptyList(), camera = v(0f, 0f, 0f))
            assertTrue(mode.name, r.values.isEmpty())
        }
    }
}
