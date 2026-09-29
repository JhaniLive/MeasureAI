package com.jhani.measurear.measurement

import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import kotlin.math.sqrt

/**
 * A placed measurement endpoint. Dragging moves it in place (new anchor), so every line and
 * shape that uses it follows. Compared by identity.
 *
 * @param onSurface true when placed on a tracked plane (most accurate); false when it came
 * from a depth / feature-point estimate, which is less reliable without a depth sensor
 * @param draggable false for points fixed by geometry (tops of heights, where you stood)
 */
class PlacedPoint(
    var anchor: Anchor,
    var onSurface: Boolean,
    val draggable: Boolean = true
)

/**
 * A completed measurement: a line segment between two world-anchored endpoints.
 */
data class MeasuredLine(
    val start: PlacedPoint,
    val end: PlacedPoint
) {
    val isEstimate: Boolean
        get() = !start.onSurface || !end.onSurface
}

/**
 * A closed shape formed by a chain of lines that returns to its first point.
 * Lines are referenced by identity.
 */
class MeasuredArea(val lines: List<MeasuredLine>) {
    val isEstimate: Boolean
        get() = lines.any { it.isEstimate }
}

/**
 * Area in square meters of a (roughly planar) 3D polygon, via Newell's method:
 * half the length of the summed cross products of consecutive vertices.
 */
fun polygonArea(vertices: List<Pose>): Float {
    var nx = 0f
    var ny = 0f
    var nz = 0f
    for (i in vertices.indices) {
        val a = vertices[i]
        val b = vertices[(i + 1) % vertices.size]
        nx += a.ty() * b.tz() - a.tz() * b.ty()
        ny += a.tz() * b.tx() - a.tx() * b.tz()
        nz += a.tx() * b.ty() - a.ty() * b.tx()
    }
    return 0.5f * sqrt(nx * nx + ny * ny + nz * nz)
}

/**
 * One saved measurement value, for sharing and history.
 */
data class MeasurementSummary(
    val value: Float,
    val isArea: Boolean,
    val isEstimate: Boolean,
    /** Lines only: flat distance (ignoring height) and height difference, in meters. */
    val horizontal: Float = 0f,
    val vertical: Float = 0f,
    /** What [value] measures, and its name ("Area", "Volume"...) for shape modes. */
    val kind: ValueKind = if (isArea) ValueKind.AREA else ValueKind.LENGTH,
    val label: String? = null
) {
    /** Lines only: angle from level, in degrees. */
    val angleDegrees: Float
        get() = Math.toDegrees(kotlin.math.atan2(vertical, horizontal).toDouble()).toFloat()
}

/** How to straighten an existing line. */
enum class StraightenMode { LEVEL, VERTICAL }

/**
 * A closed area projected to view pixels.
 */
data class ScreenArea(
    val xs: List<Float>,
    val ys: List<Float>,
    val centerX: Float,
    val centerY: Float,
    val squareMeters: Float,
    val isEstimate: Boolean
)

/**
 * What the center reticle is currently aimed at.
 */
enum class ReticleState {
    /** Nothing usable under the screen center yet. */
    SEARCHING,

    /** On a tracked plane: accurate placement. */
    SURFACE,

    /** On a depth / feature-point estimate: usable but less accurate. */
    ESTIMATE,

    /** Snapped onto an existing endpoint. */
    SNAPPED
}

/**
 * Alignment the live segment is snapped to, which makes straight lines easy to draw.
 */
enum class SnapAxis(val label: String) {
    VERTICAL("Vertical"),
    LEVEL("Level"),
    PARALLEL("Parallel"),
    PERPENDICULAR("Perpendicular")
}

/**
 * A measurement segment projected to view pixels, ready for the 2D overlay.
 *
 * @param isLive true for the segment from the pending start point to the reticle
 */
data class ScreenSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val meters: Float,
    val isEstimate: Boolean,
    val isLive: Boolean,
    val startVisible: Boolean,
    val endVisible: Boolean,
    /** Index into the completed lines, or -1 for the live segment / guides. */
    val lineIndex: Int = -1,
    /** Draw the length pill on this segment (off for shape outlines; they use scene labels). */
    val labelled: Boolean = true,
    /** Tape-measure ticks along the segment: x, y, major (1/0) triples in view pixels. */
    val ticks: FloatArray? = null
)

/**
 * Snapshot of the measuring session published from the GL thread to the UI every frame.
 */
data class MeasureUiState(
    /** The camera has delivered frames (false until the first one: the loader stays up). */
    val cameraReady: Boolean = false,
    val isTracking: Boolean = false,
    val trackingMessage: String? = null,
    val reticle: ReticleState = ReticleState.SEARCHING,
    val hasPendingPoint: Boolean = false,
    /** The pending point is the top of an object (height finished at its base). */
    val pendingIsTop: Boolean = false,
    val pendingX: Float = 0f,
    val pendingY: Float = 0f,
    val pendingVisible: Boolean = false,
    /**
     * Screen-space unit direction (x right, y down) from the center toward point A when it is
     * off screen or behind the camera; null when A is visible or not placed.
     */
    val pendingOffscreenDirX: Float? = null,
    val pendingOffscreenDirY: Float? = null,
    /** Camera-to-point-A distance, shown on the off-screen arrow. */
    val pendingDistance: Float? = null,
    val lineCount: Int = 0,
    val liveMeters: Float? = null,
    val liveIsEstimate: Boolean = false,
    val snapAxis: SnapAxis? = null,
    val guide: ScreenSegment? = null,
    val targetMeters: Float? = null,
    /** Detected surfaces ARCore is tracking right now (measuring needs at least one). */
    val surfaceCount: Int = 0,
    /** Debug view stats text, or null when the debug view is off. */
    val debugText: String? = null,
    /** A point placed here is reliable (detected surface, or locked vertical from one). */
    val reticleReliable: Boolean = false,
    /** The aim is on an object edge where depth is ambiguous (estimate may be unreliable). */
    val reticleAmbiguous: Boolean = false,
    /** Whether the magnifier is showing this frame (steady aim, close enough, or dragging). */
    val loupeVisible: Boolean = false,
    /** Magnifier center, and the screen point it magnifies (view pixels; 0 = default). */
    val loupeCenterX: Float = 0f,
    val loupeCenterY: Float = 0f,
    val loupeSourceX: Float = 0f,
    val loupeSourceY: Float = 0f,
    val segments: List<ScreenSegment> = emptyList(),
    val areas: List<ScreenArea> = emptyList(),
    /** Every completed line and area, including off-screen ones (for sharing / history). */
    val summaries: List<MeasurementSummary> = emptyList(),
    /** Far mode: the ground under the phone was detected, and the phone's height above it (m). */
    val groundDetected: Boolean = false,
    val phoneHeight: Float = 0f,
    /** Selected mode, and how many points of its current shape are placed. */
    val mode: MeasureMode = MeasureMode.LINE,
    val draftCount: Int = 0,
    /** Filled outlines of shapes (rectangles, areas, circles, box bases). */
    val fills: List<ScreenPolygon> = emptyList(),
    /** Value pills in the scene (edge lengths, angle at its corner, radius…). */
    val sceneLabels: List<ScreenValueLabel> = emptyList(),
    /** Result of the shape being placed (live) or the last finished one, for the result card. */
    val result: ShapeResultUi? = null
)

/** A shape outline projected to view pixels. */
data class ScreenPolygon(val xs: List<Float>, val ys: List<Float>, val isEstimate: Boolean)

/** A value pill at view pixel ([x], [y]). */
data class ScreenValueLabel(val x: Float, val y: Float, val value: ResultValue, val isEstimate: Boolean)

/** Result card content: values (primary first); [isLive] while the shape is being placed. */
data class ShapeResultUi(
    val mode: MeasureMode,
    val values: List<ResultValue>,
    val isEstimate: Boolean,
    val isLive: Boolean,
    /** Finished outlines (real size, meters) and their surface, for the floor plan. */
    val outline: List<Vec3>? = null,
    val outlineNormal: Vec3? = null
) {
    /** The area value, if this result has one (Area, Rectangle, Circle). */
    val area: Float? get() = values.firstOrNull { it.kind == ValueKind.AREA }?.value
}

/**
 * Straight-line (Euclidean) distance in meters between two ARCore poses.
 */
fun distanceBetween(a: Pose, b: Pose): Float {
    val dx = a.tx() - b.tx()
    val dy = a.ty() - b.ty()
    val dz = a.tz() - b.tz()
    return sqrt(dx * dx + dy * dy + dz * dz)
}

/**
 * Display unit system, toggled from the HUD.
 */
enum class MeasureUnit { METRIC, IMPERIAL }

/**
 * Measurement length: centimeters ("28.5 cm") or inches / feet-inches ("11.2 in", "3' 4.5\"").
 */
fun formatLength(meters: Float, unit: MeasureUnit): String = when (unit) {
    MeasureUnit.METRIC -> if (meters >= 1f) "%.2f m".format(meters) else "%.1f cm".format(meters * 100f)
    MeasureUnit.IMPERIAL -> formatFeetInches(meters)
}

/**
 * Imperial lengths the way a tape measure reads them: whole inches plus a fraction rounded
 * to the nearest 1/16 (reduced: 8/16 → ½), with feet above 12 inches — "5 ⅜″", "3′ 4 ½″".
 */
fun formatFeetInches(meters: Float): String {
    val sixteenths = Math.round(meters * 39.37008f * 16f)
    val feet = sixteenths / (12 * 16)
    val rest = sixteenths - feet * 12 * 16
    val inches = rest / 16
    var num = rest % 16
    var den = 16
    while (num != 0 && num % 2 == 0) {
        num /= 2
        den /= 2
    }
    val fraction = if (num == 0) "" else FRACTIONS["$num/$den"] ?: "$num/$den"
    val inchPart = when {
        fraction.isEmpty() -> "$inches″"
        inches == 0 && feet == 0 -> "$fraction″"
        inches == 0 -> "0 $fraction″"
        else -> "$inches $fraction″"
    }
    return if (feet > 0) "$feet′ $inchPart" else inchPart
}

/** Unicode vulgar fractions where they exist (others stay "3/16"). */
private val FRACTIONS = mapOf(
    "1/2" to "½", "1/4" to "¼", "3/4" to "¾",
    "1/8" to "⅛", "3/8" to "⅜", "5/8" to "⅝", "7/8" to "⅞"
)

/**
 * Measurement length. Estimates (off a detected surface) get "≈" and their typical error:
 * about ±25% on this class of phone without a depth sensor (measured: +11%, −23%).
 */
fun formatDistance(meters: Float, isEstimate: Boolean, unit: MeasureUnit): String =
    if (isEstimate) "≈ " + formatLength(meters, unit) + " ±25%" else formatLength(meters, unit)

/**
 * Area: cm² / m² or in² / ft², with "≈" for estimates.
 */
fun formatArea(squareMeters: Float, isEstimate: Boolean, unit: MeasureUnit): String {
    val value = when (unit) {
        MeasureUnit.METRIC ->
            if (squareMeters < 1f) "%.1f cm²".format(squareMeters * 10_000f) else "%.2f m²".format(squareMeters)
        MeasureUnit.IMPERIAL -> {
            val squareFeet = squareMeters * 10.7639f
            if (squareFeet < 1f) "%.1f in²".format(squareFeet * 144f) else "%.2f ft²".format(squareFeet)
        }
    }
    return (if (isEstimate) "≈ " else "") + value
}

/**
 * Human-readable list of measurements, e.g. for sharing.
 */
fun formatSummaries(summaries: List<MeasurementSummary>, unit: MeasureUnit): String =
    summaries.mapIndexed { i, s ->
        val text = formatValue(ResultValue(s.label ?: "", s.value, s.kind), s.isEstimate, unit)
        "${i + 1}. ${s.label ?: if (s.isArea) "Area" else "Length"}: $text"
    }.joinToString("\n")

/**
 * Camera-to-target range for the HUD: "1.6 m" or "5.3 ft".
 */
fun formatRange(meters: Float, unit: MeasureUnit): String = when (unit) {
    MeasureUnit.METRIC -> "%.2f m".format(meters)
    MeasureUnit.IMPERIAL -> "%.1f ft".format(meters * 3.28084f)
}

/** Volume: liters below 1 m³ (or ft³ imperial), with "≈" for estimates. */
fun formatVolume(cubicMeters: Float, isEstimate: Boolean, unit: MeasureUnit): String {
    val value = when (unit) {
        MeasureUnit.METRIC -> if (cubicMeters < 1f) "%.1f L".format(cubicMeters * 1000f) else "%.2f m³".format(cubicMeters)
        MeasureUnit.IMPERIAL -> "%.2f ft³".format(cubicMeters * 35.3147f)
    }
    return (if (isEstimate) "≈ " else "") + value
}

/** Any result value, formatted for its kind. */
fun formatValue(value: ResultValue, isEstimate: Boolean, unit: MeasureUnit): String = when (value.kind) {
    ValueKind.LENGTH -> formatDistance(value.value, isEstimate, unit)
    ValueKind.AREA -> formatArea(value.value, isEstimate, unit)
    ValueKind.VOLUME -> formatVolume(value.value, isEstimate, unit)
    ValueKind.ANGLE -> (if (isEstimate) "≈ " else "") + "%.1f°".format(value.value)
}
