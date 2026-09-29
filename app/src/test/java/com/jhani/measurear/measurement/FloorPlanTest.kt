package com.jhani.measurear.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FloorPlanTest {
    private fun v(x: Float, y: Float, z: Float) = Vec3(x, y, z)

    @Test fun flattenKeepsSideLengthsAndAlignsLongestWall() {
        // 4 × 3 m room, rotated 30° on the floor
        val a = Math.toRadians(30.0)
        fun rot(x: Float, z: Float) = v((x * Math.cos(a) - z * Math.sin(a)).toFloat(), 0f, (x * Math.sin(a) + z * Math.cos(a)).toFloat())
        val room = listOf(rot(0f, 0f), rot(4f, 0f), rot(4f, 3f), rot(0f, 3f))
        val plan = FloorPlan.flatten(room, Vec3.UP)
        fun d(p: FloorPlan.P, q: FloorPlan.P) = Math.hypot((q.x - p.x).toDouble(), (q.y - p.y).toDouble()).toFloat()
        assertEquals(4f, d(plan[0], plan[1]), 1e-3f)
        assertEquals(3f, d(plan[1], plan[2]), 1e-3f)
        // Longest wall (4 m) is horizontal
        assertEquals(0f, plan[1].y - plan[0].y, 1e-3f)
        assertEquals(12f, abs(FloorPlan.signedArea(plan)), 1e-3f)
    }

    @Test fun fitStaysInsideMarginsAndKeepsProportions() {
        val plan = listOf(FloorPlan.P(0f, 0f), FloorPlan.P(4f, 0f), FloorPlan.P(4f, 3f), FloorPlan.P(0f, 3f))
        val f = FloorPlan.fit(plan, 1600f, 1200f, 100f)
        f.points.forEach {
            assertTrue(it.x >= 99.9f && it.x <= 1500.1f)
            assertTrue(it.y >= 99.9f && it.y <= 1100.1f)
        }
        // 4 × 3 in a 1400 × 1000 box: limited by height → 333.3 px/m
        assertEquals(1000f / 3f, f.pxPerMeter, 0.01f)
        assertEquals(4f * f.pxPerMeter, f.points[1].x - f.points[0].x, 0.01f)
    }

    @Test fun niceScaleBar() {
        assertEquals(1f, FloorPlan.niceLength(1.4f), 1e-6f)
        assertEquals(2f, FloorPlan.niceLength(3.9f), 1e-6f)
        assertEquals(0.5f, FloorPlan.niceLength(0.7f), 1e-6f)
        assertEquals(5f, FloorPlan.niceLength(7f), 1e-6f)
    }
}
