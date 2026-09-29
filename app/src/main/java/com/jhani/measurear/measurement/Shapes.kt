package com.jhani.measurear.measurement

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Measuring modes, as offered in the mode picker. */
enum class MeasureMode(
    val title: String,
    val howTo: String,
    /** Points that complete the shape; null = open-ended (finish with Done). */
    val points: Int?,
    /** Fewest points before Done is allowed (open-ended modes). */
    val minPoints: Int = points ?: 2
) {
    LINE("Line", "Tap two points", 2),
    HEIGHT("Height", "Tap the base on the floor or table, then tilt up to the top", 2),
    DISTANCE("Distance", "Aim at any spot — tap to keep the distance from you", 1),
    ANGLE("Angle", "Tap one arm, the corner, then the other arm", 3),
    PATH("Path", "Tap points along the way, then Done", null, minPoints = 2),
    RECTANGLE("Rectangle", "Tap three corners — the fourth is added for you", 3),
    CIRCLE("Circle", "Tap the center, then a point on the edge", 2),
    AREA("Area", "Tap each corner, then back on the first to close", null, minPoints = 3),
    VOLUME("Volume", "Tap three corners of the base, then tilt up to the top", 4)
}

/** What a result value measures, so the UI formats it with the right unit. */
enum class ValueKind { LENGTH, AREA, VOLUME, ANGLE }

data class ResultValue(val label: String, val value: Float, val kind: ValueKind)

/** A label drawn in the scene at [at] (e.g. an edge length at its midpoint). */
data class SceneLabel(val at: Vec3, val value: ResultValue)

/**
 * Everything to draw and report for one shape: open polylines ([paths]), a closed outline to
 * fill ([fill]), labels in the scene, and the result values (primary first).
 */
data class ShapeResult(
    val paths: List<List<Vec3>>,
    val fill: List<Vec3>?,
    val labels: List<SceneLabel>,
    val values: List<ResultValue>
)

object ShapeMath {

    private const val CIRCLE_STEPS = 48

    /**
     * Result for [mode] from its points so far (a finished shape, or a draft with the live
     * aim point appended). [normal] is the detected surface the shape lies on, or null when
     * it wasn't started on one: then the plane is fitted through the points themselves (a
     * shape on a wall is not squashed flat onto the floor).
     *
     * @param camera camera position (Distance mode only)
     */
    fun compute(mode: MeasureMode, pts: List<Vec3>, surfaceNormal: Vec3? = Vec3.UP, camera: Vec3? = null): ShapeResult {
        // Boxes stand on the floor; everything else lies on its surface or its points' plane
        val normal = when {
            mode == MeasureMode.VOLUME -> surfaceNormal ?: Vec3.UP
            surfaceNormal != null -> surfaceNormal
            else -> Geometry.fitNormal(pts.take(if (mode == MeasureMode.RECTANGLE) 3 else pts.size)) ?: Vec3.UP
        }
        fun len(label: String, v: Float) = ResultValue(label, v, ValueKind.LENGTH)
        fun edges(points: List<Vec3>, closed: Boolean): List<SceneLabel> {
            val list = if (closed && points.size > 2) points + points.first() else points
            return list.zipWithNext { a, b -> SceneLabel((a + b) * 0.5f, len("Side", a.distanceTo(b))) }
        }
        val empty = ShapeResult(emptyList(), null, emptyList(), emptyList())

        return when (mode) {
            MeasureMode.LINE, MeasureMode.HEIGHT -> {
                if (pts.size < 2) return empty
                val (a, b) = pts[0] to pts[1]
                val d = a.distanceTo(b)
                val label = if (mode == MeasureMode.HEIGHT) "Height" else "Length"
                ShapeResult(listOf(listOf(a, b)), null, listOf(SceneLabel((a + b) * 0.5f, len(label, d))), listOf(len(label, d)))
            }

            MeasureMode.DISTANCE -> {
                val p = pts.firstOrNull() ?: return empty
                val cam = camera ?: return empty
                val d = cam.distanceTo(p)
                ShapeResult(emptyList(), null, listOf(SceneLabel(p, len("From you", d))), listOf(len("Distance from you", d)))
            }

            MeasureMode.ANGLE -> {
                if (pts.size < 2) return empty
                if (pts.size == 2) return ShapeResult(listOf(pts), null, edges(pts, false), emptyList())
                val deg = Geometry.angleDegrees(pts[0], pts[1], pts[2])
                val angle = ResultValue("Angle", deg, ValueKind.ANGLE)
                ShapeResult(
                    listOf(pts.take(3)), null,
                    edges(pts.take(3), false) + SceneLabel(pts[1], angle),
                    listOf(angle, len("Arm 1", pts[0].distanceTo(pts[1])), len("Arm 2", pts[1].distanceTo(pts[2])))
                )
            }

            MeasureMode.PATH -> {
                if (pts.size < 2) return empty
                val total = Geometry.polylineLength(pts)
                ShapeResult(
                    listOf(pts), null, edges(pts, false),
                    listOf(len("Total length", total), len("Last segment", pts[pts.size - 2].distanceTo(pts.last())))
                )
            }

            MeasureMode.RECTANGLE -> {
                if (pts.size < 2) return empty
                if (pts.size == 2) return ShapeResult(listOf(pts), null, edges(pts, false), emptyList())
                val c = Geometry.rectangleCorners(pts[0], pts[1], pts[2], normal)
                val w = c[0].distanceTo(c[1])
                val h = c[1].distanceTo(c[2])
                ShapeResult(
                    listOf(c + c.first()), c, edges(c, true).take(2),
                    listOf(
                        ResultValue("Area", w * h, ValueKind.AREA),
                        len("Width", w), len("Length", h), len("Perimeter", 2 * (w + h))
                    )
                )
            }

            MeasureMode.CIRCLE -> {
                if (pts.size < 2) return empty
                val center = pts[0]
                val r = Geometry.projectOntoPlane(pts[1], center, normal).distanceTo(center)
                val ring = circle(center, r, normal)
                ShapeResult(
                    listOf(ring + ring.first(), listOf(center, pts[1])), ring,
                    listOf(SceneLabel((center + pts[1]) * 0.5f, len("Radius", r))),
                    listOf(
                        len("Diameter", 2 * r),
                        ResultValue("Area", Geometry.circleArea(r), ValueKind.AREA),
                        len("Circumference", Geometry.circumference(r))
                    )
                )
            }

            MeasureMode.AREA -> {
                if (pts.size < 2) return empty
                val closed = pts.size >= 3
                val area = if (closed) Geometry.polygonArea(pts, normal) else 0f
                ShapeResult(
                    listOf(if (closed) pts + pts.first() else pts),
                    if (closed) pts else null,
                    edges(pts, closed),
                    if (closed) listOf(ResultValue("Area", area, ValueKind.AREA), len("Perimeter", Geometry.perimeter(pts))) else emptyList()
                )
            }

            MeasureMode.VOLUME -> {
                if (pts.size < 2) return empty
                if (pts.size == 2) return ShapeResult(listOf(pts), null, edges(pts, false), emptyList())
                val base = Geometry.rectangleCorners(pts[0], pts[1], pts[2], normal)
                val w = base[0].distanceTo(base[1])
                val d = base[1].distanceTo(base[2])
                if (pts.size == 3) {
                    return ShapeResult(listOf(base + base.first()), base, edges(base, true).take(2), listOf(ResultValue("Base", w * d, ValueKind.AREA)))
                }
                val h = pts[3].y - base[1].y
                val up = Vec3.UP * h
                val top = base.map { it + up }
                val paths = listOf(base + base.first(), top + top.first()) + base.indices.map { listOf(base[it], top[it]) }
                ShapeResult(
                    paths, base,
                    edges(base, true).take(2) + SceneLabel((base[1] + top[1]) * 0.5f, len("Height", kotlin.math.abs(h))),
                    listOf(
                        ResultValue("Volume", w * d * kotlin.math.abs(h), ValueKind.VOLUME),
                        len("Width", w), len("Depth", d), len("Height", kotlin.math.abs(h))
                    )
                )
            }
        }
    }

    /** Points around a circle of [radius] at [center] in the plane with [normal]. */
    fun circle(center: Vec3, radius: Float, normal: Vec3): List<Vec3> {
        val (u, v) = Geometry.planeBasis(normal)
        return (0 until CIRCLE_STEPS).map {
            val a = 2 * PI * it / CIRCLE_STEPS
            center + u * (radius * cos(a).toFloat()) + v * (radius * sin(a).toFloat())
        }
    }
}

/**
 * A finished shape: its anchored points (re-read every frame, so ARCore's refinements carry
 * through) and the surface normal it was measured on.
 */
class MeasuredShape(
    val mode: MeasureMode,
    val points: List<PlacedPoint>,
    /** Detected surface's normal, or null to fit the plane through the points. */
    val normal: Vec3?
) {
    val isEstimate: Boolean get() = points.any { !it.onSurface }
}
