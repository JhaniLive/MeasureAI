package com.jhani.measurear.measurement

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Pure geometry for every measuring mode, in world meters (ARCore: +Y is up). No ARCore
 * types, so it is unit tested on the JVM: the math is exact, and any error in a result comes
 * only from where ARCore placed the points.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Float get() = sqrt(this dot this)
    fun normalized(): Vec3 = length.let { if (it < 1e-9f) this else this * (1f / it) }
    fun distanceTo(o: Vec3) = (this - o).length

    companion object {
        val UP = Vec3(0f, 1f, 0f)
    }
}

object Geometry {

    /** Total length along [points] in order. */
    fun polylineLength(points: List<Vec3>): Float =
        points.zipWithNext { a, b -> a.distanceTo(b) }.sum()

    /** Angle in degrees at [vertex] between the arms to [a] and [b] (0..180). */
    fun angleDegrees(a: Vec3, vertex: Vec3, b: Vec3): Float {
        val u = (a - vertex).normalized()
        val v = (b - vertex).normalized()
        return Math.toDegrees(acos((u dot v).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }

    /** The same angle measured within the plane with [normal] (ignores out-of-plane noise). */
    fun angleDegreesOnPlane(a: Vec3, vertex: Vec3, b: Vec3, normal: Vec3): Float {
        val n = normal.normalized()
        fun flat(p: Vec3) = (p - vertex).let { it - n * (it dot n) }
        return angleDegrees(vertex + flat(a), vertex, vertex + flat(b))
    }

    /**
     * Height: the point on the vertical line through [base] closest to the ray
     * ([rayOrigin], [rayDir]). The top needs no surface hit, only the base does.
     *
     * @return the top point, or null when the ray is itself vertical or points away
     */
    fun verticalFromBase(base: Vec3, rayOrigin: Vec3, rayDir: Vec3): Vec3? {
        val d = rayDir.normalized()
        val w0 = base - rayOrigin
        val b = d dot Vec3.UP
        val denom = 1f - b * b
        if (denom < 1e-6f) return null
        val e = d dot w0
        // Parameters of the closest points: base + s*UP and rayOrigin + t*d
        val s = (b * e - (w0 dot Vec3.UP)) / denom
        val t = (e - b * (w0 dot Vec3.UP)) / denom
        if (t <= 0f) return null
        return base + Vec3.UP * s
    }

    /**
     * Unit normal of the plane through [points] (Newell's method; exact for a planar polygon,
     * best fit otherwise), or null when they are (nearly) collinear.
     */
    fun fitNormal(points: List<Vec3>): Vec3? {
        if (points.size < 3) return null
        var n = Vec3(0f, 0f, 0f)
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            n += Vec3((a.y - b.y) * (a.z + b.z), (a.z - b.z) * (a.x + b.x), (a.x - b.x) * (a.y + b.y))
        }
        return if (n.length < 1e-6f) null else n.normalized()
    }

    /** Orthonormal in-plane axes (u, v) for a plane with [normal]. */
    fun planeBasis(normal: Vec3): Pair<Vec3, Vec3> {
        val n = normal.normalized()
        val helper = if (abs(n.y) < 0.9f) Vec3.UP else Vec3(1f, 0f, 0f)
        val u = (helper cross n).normalized()
        val v = n cross u
        return u to v
    }

    /** Projects [p] onto the plane through [origin] with [normal]. */
    fun projectOntoPlane(p: Vec3, origin: Vec3, normal: Vec3): Vec3 {
        val n = normal.normalized()
        return p - n * ((p - origin) dot n)
    }

    /**
     * Rectangle from three taps on one plane: [p0]→[p1] is the first edge, [p2] sets how far
     * the opposite edge is (projected onto the in-plane perpendicular, so it needn't be exact).
     *
     * @return the four corners p0, p1, p2', p3'
     */
    fun rectangleCorners(p0: Vec3, p1: Vec3, p2: Vec3, normal: Vec3): List<Vec3> {
        val n = normal.normalized()
        val edge = p1 - p0
        val perp = (n cross edge).normalized()
        val depth = (p2 - p1) dot perp
        val offset = perp * depth
        return listOf(p0, p1, p1 + offset, p0 + offset)
    }

    /** Polygon area (m²) within the plane with [normal], via the shoelace formula. */
    fun polygonArea(points: List<Vec3>, normal: Vec3): Float {
        if (points.size < 3) return 0f
        val (u, v) = planeBasis(normal)
        val o = points[0]
        val xs = points.map { (it - o) dot u }
        val ys = points.map { (it - o) dot v }
        var sum = 0f
        for (i in points.indices) {
            val j = (i + 1) % points.size
            sum += xs[i] * ys[j] - xs[j] * ys[i]
        }
        return abs(sum) / 2f
    }

    /** Perimeter of the closed polygon through [points]. */
    fun perimeter(points: List<Vec3>): Float =
        if (points.size < 2) 0f else polylineLength(points + points.first())

    /** Circle through three points (circumcircle): center and radius, or null if collinear. */
    fun circleFrom3(a: Vec3, b: Vec3, c: Vec3): Pair<Vec3, Float>? {
        val ab = b - a
        val ac = c - a
        val n = ab cross ac
        val nn = n dot n
        if (nn < 1e-12f) return null
        val toCenter = ((n cross ab) * (ac dot ac) + (ac cross n) * (ab dot ab)) * (1f / (2f * nn))
        return (a + toCenter) to toCenter.length
    }

    fun circleArea(radius: Float) = (PI * radius * radius).toFloat()
    fun circumference(radius: Float) = (2 * PI * radius).toFloat()
}

/**
 * Honest uncertainty for a placed point (meters, ~1 sigma), from published ARCore tests on
 * phones without a depth sensor: about 1 cm plus 1 cm per meter on a detected surface; depth
 * estimates are far worse.
 */
object Uncertainty {
    fun pointError(distanceFromCamera: Float, onSurface: Boolean): Float =
        if (onSurface) 0.01f + 0.01f * distanceFromCamera else 0.03f + 0.20f * distanceFromCamera

    /** Error of a length between two points with independent errors. */
    fun lengthError(e1: Float, e2: Float): Float = sqrt(e1 * e1 + e2 * e2)
}
