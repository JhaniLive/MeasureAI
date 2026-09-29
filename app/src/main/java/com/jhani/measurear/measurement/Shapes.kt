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
    val minPoints: Int = points ?: 2,
    /** Shown in the mode picker grid (Calibrate is started from its own button). */
    val inPicker: Boolean = true
) {
    LINE("Line", "Tap two points", 2),
    HEIGHT("Height", "Tap the base on the floor or table, then tilt up to the top", 2),
    FAR("Far height", "Buildings & trees: aim at the base on the ground, then at the top", 3),
    DISTANCE("Distance", "Aim at any spot — tap to keep the distance from you", 1),
    ANGLE("Angle", "Tap one arm, the corner, then the other arm", 3),
    PATH("Path", "Tap points along the way, then Done", null, minPoints = 2),
    RECTANGLE("Rectangle", "Tap three corners — the fourth is added for you", 3),
    CIRCLE("Circle", "Tap the center, then a point on the edge", 2),
    AREA("Area", "Tap each corner, then back on the first to close", null, minPoints = 3),
    VOLUME("Volume", "Tap three corners of the base, then tilt up to the top", 4),
    HANG("Hang pictures", "Tap the wall where the middle of your frames should be", 1),
    FIT("Will it fit?", "Pick a size, then tap the floor to place a life-size box — drag to move it", 1),
    CALIBRATE("Calibrate", "Lay a bank card flat on the teal dots, then tap both ends of its long edge", 2, inPicker = false)
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
    /** Orientation error of the phone (1 sigma, degrees) for far measurements. */
    const val FAR_ANGLE_ERROR_DEG = 0.2f

    fun compute(
        mode: MeasureMode,
        pts: List<Vec3>,
        surfaceNormal: Vec3? = Vec3.UP,
        camera: Vec3? = null,
        /** Far mode: how well the phone's height above the ground is known (m). */
        phoneHeightError: Float = 0.02f,
        /** Will it fit?: box size and rotation. */
        box: BoxSpec? = null,
        yawDegrees: Float = 0f,
        /** Hang pictures: the arrangement. */
        hang: HangSpec? = null
    ): ShapeResult {
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
            MeasureMode.LINE, MeasureMode.HEIGHT, MeasureMode.CALIBRATE -> {
                if (pts.size < 2) return empty
                val (a, b) = pts[0] to pts[1]
                val d = a.distanceTo(b)
                val label = if (mode == MeasureMode.HEIGHT) "Height" else "Length"
                ShapeResult(listOf(listOf(a, b)), null, listOf(SceneLabel((a + b) * 0.5f, len(label, d))), listOf(len(label, d)))
            }

            MeasureMode.FAR -> {
                // Points: base on the ground, where the phone was, then the top
                val base = pts.getOrNull(0) ?: return empty
                val cam = pts.getOrNull(1) ?: camera ?: return empty
                val dx = base.x - cam.x
                val dz = base.z - cam.z
                val distance = kotlin.math.sqrt(dx * dx + dz * dz)
                val phoneHeight = cam.y - base.y
                val dErr = Geometry.farDistanceError(phoneHeight, distance, FAR_ANGLE_ERROR_DEG, phoneHeightError)
                val top = pts.getOrNull(2)
                if (top == null) {
                    return ShapeResult(
                        emptyList(), null,
                        listOf(SceneLabel(base, len("Distance", distance))),
                        listOf(len("Distance to base", distance), len("± Distance", dErr))
                    )
                }
                val h = top.y - base.y
                // Height error: from the distance error, plus the top angle's own error
                val beta = kotlin.math.atan2(top.y - cam.y, distance)
                val cos = kotlin.math.cos(beta)
                val dBeta = Math.toRadians(FAR_ANGLE_ERROR_DEG.toDouble()).toFloat()
                val hErr = kotlin.math.sqrt(
                    (h / distance * dErr).let { it * it } + (distance * dBeta / (cos * cos)).let { it * it }
                )
                ShapeResult(
                    listOf(listOf(base, top)), null,
                    listOf(SceneLabel((base + top) * 0.5f, len("Height", h))),
                    listOf(len("Height", h), len("± Height", hErr), len("Distance to base", distance))
                )
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

            MeasureMode.HANG -> {
                val c = pts.firstOrNull() ?: return empty
                val spec = hang ?: return empty
                val layout = hangLayout(c, surfaceNormal ?: Vec3(0f, 0f, 1f), spec)
                val paths = layout.frames.map { it + it.first() } + layout.nails.flatMap { nailCross(it, layout.across) }
                val labels = layout.nails.zipWithNext { a, b -> SceneLabel((a + b) * 0.5f, len("Nail spacing", a.distanceTo(b))) }
                ShapeResult(
                    paths, null, labels,
                    listOf(
                        len("Nail spacing", spec.width + spec.gap),
                        len("Total width", spec.totalWidth),
                        len("Nail below frame top", spec.hookDrop)
                    )
                )
            }

            MeasureMode.FIT -> {
                val c = pts.firstOrNull() ?: return empty
                val spec = box ?: return empty
                val corners = boxCorners(c, spec, yawDegrees)
                val base = corners.take(4)
                val top = corners.drop(4)
                val paths = listOf(base + base.first(), top + top.first()) + base.indices.map { listOf(base[it], top[it]) }
                ShapeResult(
                    paths, base,
                    listOf(
                        SceneLabel((base[0] + base[1]) * 0.5f, len("Width", spec.width)),
                        SceneLabel((base[1] + base[2]) * 0.5f, len("Depth", spec.depth)),
                        SceneLabel((base[2] + top[2]) * 0.5f, len("Height", spec.height))
                    ),
                    listOf(
                        ResultValue("Footprint", spec.width * spec.depth, ValueKind.AREA),
                        len("Width", spec.width), len("Depth", spec.depth), len("Height", spec.height)
                    )
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

    /**
     * The 8 corners of a box standing on the floor at [center] (bottom center), rotated
     * [yawDegrees] about the vertical: bottom 4 (counter-clockwise), then the top 4 above them.
     */
    fun boxCorners(center: Vec3, box: BoxSpec, yawDegrees: Float): List<Vec3> {
        val a = Math.toRadians(yawDegrees.toDouble())
        val u = Vec3(cos(a).toFloat(), 0f, sin(a).toFloat()) * (box.width / 2f)
        val v = Vec3(-sin(a).toFloat(), 0f, cos(a).toFloat()) * (box.depth / 2f)
        val bottom = listOf(center - u - v, center + u - v, center + u + v, center - u + v)
        return bottom + bottom.map { it + Vec3.UP * box.height }
    }

    /** Frames (4 corners each) and nail points for a picture arrangement, plus the level axis. */
    data class HangLayout(val frames: List<List<Vec3>>, val nails: List<Vec3>, val across: Vec3)

    /**
     * Lays out [spec] level on a wall with [wallNormal], centered at [center]: frames side by
     * side, [HangSpec.gap] apart; each nail sits [HangSpec.hookDrop] below its frame's top center.
     */
    fun hangLayout(center: Vec3, wallNormal: Vec3, spec: HangSpec): HangLayout {
        // Level direction along the wall (perpendicular to both its normal and up)
        val across = (Vec3.UP cross wallNormal).normalized().let { if (it.length < 0.5f) Vec3(1f, 0f, 0f) else it }
        val up = Vec3.UP
        val frames = ArrayList<List<Vec3>>()
        val nails = ArrayList<Vec3>()
        for (i in 0 until spec.count) {
            val x = -spec.totalWidth / 2f + spec.width / 2f + i * (spec.width + spec.gap)
            val mid = center + across * x
            val hw = across * (spec.width / 2f)
            val hh = up * (spec.height / 2f)
            frames.add(listOf(mid - hw - hh, mid + hw - hh, mid + hw + hh, mid - hw + hh))
            nails.add(mid + up * (spec.height / 2f - spec.hookDrop))
        }
        return HangLayout(frames, nails, across)
    }

    /** A small ✕ at a nail point, in the wall plane. */
    private fun nailCross(p: Vec3, across: Vec3): List<List<Vec3>> {
        val s = 0.025f
        val a = (across + Vec3.UP) * s
        val b = (across - Vec3.UP) * s
        return listOf(listOf(p - a, p + a), listOf(p - b, p + b))
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
    val normal: Vec3?,
    /** Will it fit?: the virtual box's real size, and its rotation about the vertical. */
    var box: BoxSpec? = null,
    var yawDegrees: Float = 0f,
    /** Hang pictures: the frame arrangement. */
    var hang: HangSpec? = null
) {
    val isEstimate: Boolean get() = points.any { !it.onSurface }
}

/** A real-world box size (meters) for Will it fit?, with a name for presets. */
data class BoxSpec(val width: Float, val depth: Float, val height: Float, val name: String = "Custom") {
    companion object {
        /** Common furniture and appliances (typical sizes, meters). */
        val PRESETS = listOf(
            BoxSpec(2.00f, 0.90f, 0.85f, "3-seat sofa"),
            BoxSpec(1.98f, 1.52f, 0.50f, "Queen bed"),
            BoxSpec(1.50f, 0.90f, 0.75f, "Dining table"),
            BoxSpec(1.20f, 0.60f, 0.75f, "Desk"),
            BoxSpec(0.70f, 0.70f, 1.80f, "Fridge"),
            BoxSpec(0.60f, 0.60f, 0.85f, "Washing machine"),
            BoxSpec(1.23f, 0.08f, 0.71f, "55\" TV"),
            BoxSpec(1.00f, 0.60f, 2.00f, "Wardrobe")
        )
    }
}

/** Picture frames to hang side by side (meters). */
data class HangSpec(
    val count: Int = 3,
    val width: Float = 0.40f,
    val height: Float = 0.50f,
    val gap: Float = 0.08f,
    /** How far below the frame's top edge its hook/wire sits when pulled taut. */
    val hookDrop: Float = 0.05f
) {
    val totalWidth: Float get() = count * width + (count - 1) * gap
}
