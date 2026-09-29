package com.jhani.measurear.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class GeometryTest {
    private val eps = 1e-4f
    private fun v(x: Float, y: Float, z: Float) = Vec3(x, y, z)
    private fun assertVec(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
        assertEquals(expected.z, actual.z, eps)
    }

    @Test fun lineLength() {
        assertEquals(5f, v(0f, 0f, 0f).distanceTo(v(3f, 0f, 4f)), eps)
    }

    @Test fun polylineSumsSegments() {
        val pts = listOf(v(0f, 0f, 0f), v(1f, 0f, 0f), v(1f, 0f, 2f), v(1f, 3f, 2f))
        assertEquals(6f, Geometry.polylineLength(pts), eps)
    }

    @Test fun rightAngle() {
        assertEquals(90f, Geometry.angleDegrees(v(1f, 0f, 0f), v(0f, 0f, 0f), v(0f, 0f, 1f)), 1e-3f)
    }

    @Test fun angle45AndStraight() {
        assertEquals(45f, Geometry.angleDegrees(v(1f, 0f, 0f), v(0f, 0f, 0f), v(1f, 0f, 1f)), 1e-3f)
        assertEquals(180f, Geometry.angleDegrees(v(-1f, 0f, 0f), v(0f, 0f, 0f), v(2f, 0f, 0f)), 1e-2f)
    }

    @Test fun angleOnPlaneIgnoresVerticalNoise() {
        // Floor corner of 90° with a noisy 5 cm vertical error on one arm
        val a = Geometry.angleDegreesOnPlane(v(1f, 0.05f, 0f), v(0f, 0f, 0f), v(0f, 0f, 1f), Vec3.UP)
        assertEquals(90f, a, 1e-3f)
    }

    @Test fun heightFromBaseWithRayAimedAtTop() {
        // Bottle 0.31 m tall at (0,0,-1); camera at eye height looking at its top
        val base = v(0f, 0f, -1f)
        val cam = v(0.2f, 0.5f, 0f)
        val top = v(0f, 0.31f, -1f)
        val result = Geometry.verticalFromBase(base, cam, top - cam)
        assertNotNull(result)
        assertVec(top, result!!)
    }

    @Test fun heightRayMissingSlightlyStillGivesClosestPoint() {
        val base = v(0f, 0f, -1f)
        val cam = v(0f, 0.5f, 0f)
        // Aim 2 cm to the side of the top: height is still the closest point on the vertical
        val result = Geometry.verticalFromBase(base, cam, v(0.02f, 0.31f, -1f) - cam)!!
        assertEquals(0f, result.x, eps)
        assertEquals(-1f, result.z, eps)
        assertEquals(0.31f, result.y, 0.005f)
    }

    @Test fun baseBehindObjectUnderestimatesHeight() {
        // 26 cm flask 0.7 m away, camera 0.45 m above the table, top stamped on its rim.
        // Base picked on the table 10 cm *behind* the flask (plane won over the flask body):
        val cam = v(0f, 0.45f, 0f)
        val top = v(0f, 0.26f, -0.7f)
        val wrongBase = v(0f, 0f, -0.8f)
        val h = Geometry.verticalFromBase(wrongBase, cam, top - cam)!!.y
        // 0.26 − 0.19 × 0.10 / 0.70 ≈ 23.3 cm: several cm short (the phone's 22.6 fits ~12 cm behind)
        assertEquals(0.2329f, h, 1e-3f)
        // Base at the flask's foot: exact
        assertEquals(0.26f, Geometry.verticalFromBase(v(0f, 0f, -0.7f), cam, top - cam)!!.y, 1e-4f)
    }

    @Test fun rayHitsGroundFarAway() {
        // Phone 1.5 m up, looking down 3° at the base of a building: base is ~28.6 m away
        val a = Math.toRadians(3.0)
        val dir = v(0f, -Math.sin(a).toFloat(), -Math.cos(a).toFloat())
        val base = Geometry.rayHitsGround(v(0f, 1.5f, 0f), dir, 0f)!!
        assertEquals(0f, base.y, 1e-4f)
        assertEquals(1.5f / Math.tan(a).toFloat(), -base.z, 0.01f)
        // Looking up never hits the ground
        assertNull(Geometry.rayHitsGround(v(0f, 1.5f, 0f), v(0f, 0.1f, -1f), 0f))
    }

    @Test fun farBuildingHeightFromTwoAngles() {
        // 30 m tall building, 40 m away, phone at 1.5 m: base angle + top angle -> exact height
        val cam = v(0f, 1.5f, 0f)
        val base = Geometry.rayHitsGround(cam, v(0f, 0f, -40f) - cam, 0f)!!
        val top = Geometry.verticalFromBase(base, cam, v(0f, 30f, -40f) - cam)!!
        assertEquals(40f, -base.z, 1e-3f)
        assertEquals(30f, top.y - base.y, 1e-3f)
    }

    @Test fun farErrorGrowsWithDistance() {
        val near = Geometry.farDistanceError(1.5f, 10f, 0.2f, 0.02f)
        val far = Geometry.farDistanceError(1.5f, 40f, 0.2f, 0.02f)
        assertTrue(far > near * 5f) // roughly d²: 16x the angle term
        assertTrue(near < 0.5f)     // 10 m away: well under half a meter
    }

    @Test fun heightRejectsVerticalRay() {
        assertNull(Geometry.verticalFromBase(v(0f, 0f, 0f), v(0f, 1f, 0f), v(0f, -1f, 0f)))
    }

    @Test fun rectangleFromThreeTaps() {
        // 3 m × 4 m floor rectangle; third tap is off the corner along the edge (still exact)
        val corners = Geometry.rectangleCorners(v(0f, 0f, 0f), v(3f, 0f, 0f), v(2.2f, 0f, 4f), Vec3.UP)
        assertEquals(3f, corners[0].distanceTo(corners[1]), eps)
        assertEquals(4f, corners[1].distanceTo(corners[2]), eps)
        assertEquals(12f, Geometry.polygonArea(corners, Vec3.UP), 1e-3f)
        assertEquals(14f, Geometry.perimeter(corners), 1e-3f)
    }

    @Test fun rectangleOnWall() {
        // Door 0.9 m wide × 2.1 m tall on a wall facing +Z
        val corners = Geometry.rectangleCorners(v(0f, 0f, 0f), v(0.9f, 0f, 0f), v(0.9f, 2.1f, 0f), v(0f, 0f, 1f))
        assertEquals(0.9f * 2.1f, Geometry.polygonArea(corners, v(0f, 0f, 1f)), 1e-3f)
    }

    @Test fun lShapedRoomArea() {
        // L-shaped floor: 4×3 minus a 2×1 notch = 10 m²
        val pts = listOf(v(0f, 0f, 0f), v(4f, 0f, 0f), v(4f, 0f, 3f), v(2f, 0f, 3f), v(2f, 0f, 2f), v(0f, 0f, 2f))
        assertEquals(10f, Geometry.polygonArea(pts, Vec3.UP), 1e-3f)
        assertEquals(14f, Geometry.perimeter(pts), 1e-3f)
    }

    @Test fun areaOrderIndependentOfWinding() {
        val pts = listOf(v(0f, 0f, 0f), v(2f, 0f, 0f), v(2f, 0f, 2f), v(0f, 0f, 2f))
        assertEquals(Geometry.polygonArea(pts, Vec3.UP), Geometry.polygonArea(pts.reversed(), Vec3.UP), eps)
    }

    @Test fun circleFromThreeRimPoints() {
        // r = 0.35 m circle on a table at height 0.75, center (1, 0.75, 2)
        val c = v(1f, 0.75f, 2f)
        val r = 0.35f
        fun rim(deg: Double) = c + v((r * cos(deg * PI / 180)).toFloat(), 0f, (r * sin(deg * PI / 180)).toFloat())
        val (center, radius) = Geometry.circleFrom3(rim(10.0), rim(130.0), rim(250.0))!!
        assertVec(c, center)
        assertEquals(r, radius, eps)
        assertEquals((PI * r * r).toFloat(), Geometry.circleArea(radius), 1e-4f)
        assertEquals((2 * PI * r).toFloat(), Geometry.circumference(radius), 1e-4f)
    }

    @Test fun circleCollinearIsNull() {
        assertNull(Geometry.circleFrom3(v(0f, 0f, 0f), v(1f, 0f, 0f), v(2f, 0f, 0f)))
    }

    @Test fun boxVolume() {
        // 0.5 × 0.4 footprint, 0.3 tall → 0.06 m³
        val corners = Geometry.rectangleCorners(v(0f, 0f, 0f), v(0.5f, 0f, 0f), v(0.5f, 0f, 0.4f), Vec3.UP)
        val top = Geometry.verticalFromBase(corners[1], v(1f, 1f, 1f), v(0.5f, 0.3f, 0f) - v(1f, 1f, 1f))!!
        val height = top.y - corners[1].y
        assertEquals(0.06f, Geometry.polygonArea(corners, Vec3.UP) * height, 1e-4f)
    }

    @Test fun projectOntoPlane() {
        assertVec(v(1f, 0f, 2f), Geometry.projectOntoPlane(v(1f, 0.07f, 2f), v(0f, 0f, 0f), Vec3.UP))
    }
}
