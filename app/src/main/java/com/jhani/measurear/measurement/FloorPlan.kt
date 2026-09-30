package com.jhani.measurear.measurement

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Layout of a measured outline as a 2D drawing (pure; the renderer only draws it). */
object FloorPlan {

    data class P(val x: Float, val y: Float)

    /**
     * Flattens a 3D outline into its plane (the floor for a room), rotated so the longest
     * wall is horizontal, in meters.
     */
    fun flatten(points: List<Vec3>, normal: Vec3?): List<P> {
        if (points.size < 3) return emptyList()
        // The outline's own plane: estimated corners needn't lie on the surface that was hit,
        // and projecting onto that surface shortened edges rising off it (78 cm drawn as 13 cm).
        // Faced like the surface normal so the plan isn't mirrored.
        val fitted = Geometry.fitNormal(points)
        val n = when {
            fitted == null -> normal ?: Vec3.UP
            normal != null && (fitted dot normal) < 0f -> fitted * -1f
            else -> fitted
        }
        val (u, v) = Geometry.planeBasis(n)
        val o = points[0]
        val flat = points.map { P((it - o) dot u, (it - o) dot v) }
        // Rotate so the longest edge runs along +x
        val longest = flat.indices.maxByOrNull { i ->
            val a = flat[i]
            val b = flat[(i + 1) % flat.size]
            (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
        } ?: 0
        val a = flat[longest]
        val b = flat[(longest + 1) % flat.size]
        val angle = -atan2(b.y - a.y, b.x - a.x)
        val c = cos(angle)
        val s = sin(angle)
        return flat.map { P(it.x * c - it.y * s, it.x * s + it.y * c) }
    }

    /** Fitted drawing: points in pixels and the drawing scale in pixels per meter. */
    data class Fitted(val points: List<P>, val pxPerMeter: Float)

    /** Scales and centers [plan] (meters) into a [width] × [height] px box, keeping proportions. */
    fun fit(plan: List<P>, width: Float, height: Float, margin: Float): Fitted {
        if (plan.isEmpty()) return Fitted(emptyList(), 0f)
        val minX = plan.minOf { it.x }
        val maxX = plan.maxOf { it.x }
        val minY = plan.minOf { it.y }
        val maxY = plan.maxOf { it.y }
        val w = (maxX - minX).coerceAtLeast(1e-3f)
        val h = (maxY - minY).coerceAtLeast(1e-3f)
        val scale = min((width - 2 * margin) / w, (height - 2 * margin) / h)
        val ox = (width - w * scale) / 2f
        val oy = (height - h * scale) / 2f
        // Screen y grows downward: flip so the drawing isn't mirrored
        return Fitted(plan.map { P(ox + (it.x - minX) * scale, oy + (maxY - it.y) * scale) }, scale)
    }

    /** Signed area (m²): > 0 when the outline runs counter-clockwise (in plan coordinates). */
    fun signedArea(plan: List<P>): Float {
        var sum = 0f
        for (i in plan.indices) {
            val a = plan[i]
            val b = plan[(i + 1) % plan.size]
            sum += a.x * b.y - b.x * a.y
        }
        return sum / 2f
    }

    /** A "nice" scale-bar length (1, 2 or 5 × 10ⁿ meters) close to [target] meters. */
    fun niceLength(target: Float): Float {
        if (target <= 0f) return 1f
        var base = 1f
        while (base * 10f <= target) base *= 10f
        while (base > target) base /= 10f
        return listOf(1f, 2f, 5f, 10f).map { it * base }.last { it <= target }
    }
}
