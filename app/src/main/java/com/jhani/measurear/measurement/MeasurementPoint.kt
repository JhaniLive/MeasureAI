package com.jhani.measurear.measurement

import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import kotlin.math.sqrt

/**
 * A placed measurement endpoint.
 *
 * @param onSurface true when placed on a tracked plane (most accurate); false when it came
 * from a depth / feature-point estimate, which is less reliable without a depth sensor
 */
data class PlacedPoint(
    val anchor: Anchor,
    val onSurface: Boolean
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
    val vertical: Float = 0f
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
    val lineIndex: Int = -1
)

/**
 * Snapshot of the measuring session published from the GL thread to the UI every frame.
 */
data class MeasureUiState(
    val isTracking: Boolean = false,
    val trackingMessage: String? = null,
    val reticle: ReticleState = ReticleState.SEARCHING,
    val hasPendingPoint: Boolean = false,
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
    /** Debug view stats text, or null when the debug view is off. */
    val debugText: String? = null,
    /** A point placed here is reliable (detected surface, or locked vertical from one). */
    val reticleReliable: Boolean = false,
    /** The aim is on an object edge where depth is ambiguous (estimate may be unreliable). */
    val reticleAmbiguous: Boolean = false,
    /** Whether the magnifier is showing this frame (steady aim, close enough). */
    val loupeVisible: Boolean = false,
    val segments: List<ScreenSegment> = emptyList(),
    val areas: List<ScreenArea> = emptyList(),
    /** Every completed line and area, including off-screen ones (for sharing / history). */
    val summaries: List<MeasurementSummary> = emptyList()
)

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
    MeasureUnit.METRIC -> "%.1f cm".format(meters * 100f)
    MeasureUnit.IMPERIAL -> {
        val totalInches = meters * 39.3701f
        if (totalInches < 12f) {
            "%.1f in".format(totalInches)
        } else {
            val feet = (totalInches / 12f).toInt()
            "%d' %.1f\"".format(feet, totalInches - feet * 12f)
        }
    }
}

/**
 * Measurement length with "≈" prefix for estimates.
 */
fun formatDistance(meters: Float, isEstimate: Boolean, unit: MeasureUnit): String =
    (if (isEstimate) "≈ " else "") + formatLength(meters, unit)

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
        val text = if (s.isArea) formatArea(s.value, s.isEstimate, unit) else formatDistance(s.value, s.isEstimate, unit)
        "${i + 1}. ${if (s.isArea) "Area" else "Length"}: $text"
    }.joinToString("\n")

/**
 * Camera-to-target range for the HUD: "1.6 m" or "5.3 ft".
 */
fun formatRange(meters: Float, unit: MeasureUnit): String = when (unit) {
    MeasureUnit.METRIC -> "%.2f m".format(meters)
    MeasureUnit.IMPERIAL -> "%.1f ft".format(meters * 3.28084f)
}
