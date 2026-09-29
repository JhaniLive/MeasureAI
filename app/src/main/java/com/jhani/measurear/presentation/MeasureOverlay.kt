package com.jhani.measurear.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhani.measurear.ar.LoupeSpec
import com.jhani.measurear.measurement.MeasureUiState
import com.jhani.measurear.measurement.ScreenArea
import com.jhani.measurear.measurement.formatArea
import com.jhani.measurear.measurement.MeasureUnit
import com.jhani.measurear.measurement.ReticleState
import com.jhani.measurear.measurement.ScreenSegment
import com.jhani.measurear.measurement.formatDistance
import com.jhani.measurear.measurement.formatValue
import com.jhani.measurear.measurement.ScreenPolygon
import com.jhani.measurear.measurement.ScreenValueLabel
import com.jhani.measurear.measurement.ValueKind

val HudTeal = Color(0xFF1DE9D0)
val HudTealDark = Color(0xFF0B3B38)
val HudAmber = Color(0xFFFFD60A)

private val ShadowColor = Color.Black.copy(alpha = 0.35f)
private val EstimateLabelColor = Color(0xFFD0D0D0)

/**
 * 2D HUD overlay drawn on top of the camera: measurement lines, endpoints, distance labels,
 * alignment guide and the center crosshair. Positions are view pixels projected on the GL
 * thread, so everything stays crisp.
 */
@Composable
fun MeasureOverlay(
    ui: MeasureUiState,
    unit: MeasureUnit,
    onLineTap: (Int) -> Unit,
    /** Tap anywhere that isn't a line label: place a point there (view pixels). */
    onScreenTap: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val currentOnLineTap by rememberUpdatedState(onLineTap)
    val currentOnScreenTap by rememberUpdatedState(onScreenTap)

    // Label rectangles from the last draw, for tap hit-testing
    val labelHits = remember { ArrayList<Pair<Rect, Int>>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    val label = labelHits.lastOrNull { it.first.inflate(8.dp.toPx()).contains(position) }
                    if (label != null) {
                        currentOnLineTap(label.second)
                    } else {
                        currentOnScreenTap(position.x, position.y)
                    }
                }
            }
    ) {
        labelHits.clear()

        ui.areas.forEach { drawAreaFill(it) }
        ui.fills.forEach { drawShapeFill(it) }
        ui.guide?.let { drawGuide(it) }
        ui.segments.forEach { drawSegment(it) }

        if (ui.hasPendingPoint && ui.pendingVisible) {
            drawEndpoint(Offset(ui.pendingX, ui.pendingY))
        }

        ui.segments.forEach { segment ->
            if (!segment.labelled) return@forEach
            val rect = drawLabel(segment, unit, textMeasurer)
            if (rect != null && segment.lineIndex >= 0) labelHits.add(rect to segment.lineIndex)
        }
        ui.areas.forEach { drawAreaLabel(it, unit, textMeasurer) }
        ui.sceneLabels.forEach { drawValueLabel(it, unit, textMeasurer) }

        drawOffscreenArrow(ui, unit, textMeasurer)
        if (ui.isTracking && ui.loupeVisible) drawLoupe(ui)

        // Dashed "searching" crosshair until tracking starts
        drawReticle(if (ui.isTracking) ui.reticle else ReticleState.SEARCHING, ui.snapAxis != null)
    }
}

private fun DrawScope.drawSegment(segment: ScreenSegment) {
    val start = Offset(segment.startX, segment.startY)
    val end = Offset(segment.endX, segment.endY)
    val width = 3.dp.toPx()
    val color = if (segment.isLive) HudTeal else Color.White

    // Soft glow under the live segment
    if (segment.isLive) {
        drawLine(HudTeal.copy(alpha = 0.25f), start, end, strokeWidth = width + 8.dp.toPx(), cap = StrokeCap.Round)
    }
    drawLine(ShadowColor, start, end, strokeWidth = width + 3.dp.toPx(), cap = StrokeCap.Round)
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = width,
        cap = StrokeCap.Round,
        pathEffect = if (segment.isEstimate) PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())) else null
    )

    if (segment.startVisible) drawEndpoint(start)
    if (segment.endVisible && !segment.isLive) drawEndpoint(end)
}

/** Dotted alignment guide through the live segment. */
private fun DrawScope.drawGuide(guide: ScreenSegment) {
    drawLine(
        color = HudAmber,
        start = Offset(guide.startX, guide.startY),
        end = Offset(guide.endX, guide.endY),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()))
    )
}

private fun DrawScope.drawEndpoint(center: Offset) {
    drawCircle(ShadowColor, radius = 8.dp.toPx(), center = center)
    drawCircle(HudTeal, radius = 6.5.dp.toPx(), center = center)
    drawCircle(Color.White, radius = 4.dp.toPx(), center = center)
}

/** Draws the segment's distance pill and returns its bounds (null when skipped). */
private fun DrawScope.drawLabel(segment: ScreenSegment, unit: MeasureUnit, textMeasurer: TextMeasurer): Rect? {
    // Skip labels for completed segments too short on screen to read
    val dx = segment.endX - segment.startX
    val dy = segment.endY - segment.startY
    // Skip labels too short on screen to read; the live one also needs room so it doesn't
    // sit on top of the crosshair (its value is in the bottom readout anyway)
    val minLength = (if (segment.isLive) 60.dp else 24.dp).toPx()
    if (dx * dx + dy * dy < minLength * minLength) return null

    val layout = textMeasurer.measure(
        text = formatDistance(segment.meters, segment.isEstimate, unit),
        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    )
    val padH = 10.dp.toPx()
    val padV = 5.dp.toPx()
    val boxSize = Size(layout.size.width + padH * 2, layout.size.height + padV * 2)
    val center = Offset((segment.startX + segment.endX) / 2f, (segment.startY + segment.endY) / 2f)
    val topLeft = Offset(center.x - boxSize.width / 2f, center.y - boxSize.height / 2f)

    val background = when {
        segment.isLive -> HudTeal
        segment.isEstimate -> EstimateLabelColor
        else -> Color.White
    }
    drawRoundRect(
        color = ShadowColor,
        topLeft = topLeft + Offset(0f, 1.5.dp.toPx()),
        size = boxSize,
        cornerRadius = CornerRadius(boxSize.height / 2f)
    )
    drawRoundRect(
        color = background,
        topLeft = topLeft,
        size = boxSize,
        cornerRadius = CornerRadius(boxSize.height / 2f)
    )
    drawText(layout, topLeft = Offset(topLeft.x + padH, topLeft.y + padV))
    return Rect(topLeft, boxSize)
}

/**
 * When point A is off screen (e.g. while walking a long distance), a teal arrow at the screen
 * edge points back to it, with the live camera-to-A distance.
 */
private fun DrawScope.drawOffscreenArrow(ui: MeasureUiState, unit: MeasureUnit, textMeasurer: TextMeasurer) {
    val dx = ui.pendingOffscreenDirX ?: return
    val dy = ui.pendingOffscreenDirY ?: return
    if (!ui.hasPendingPoint) return

    // Point where the direction ray meets an inset rectangle around the screen edge
    val inset = 56.dp.toPx()
    val halfW = size.width / 2f - inset
    val halfH = size.height / 2f - inset * 2.5f // keep clear of the top HUD and bottom controls
    val t = minOf(
        if (dx != 0f) halfW / kotlin.math.abs(dx) else Float.MAX_VALUE,
        if (dy != 0f) halfH / kotlin.math.abs(dy) else Float.MAX_VALUE
    )
    val tip = center + Offset(dx, dy) * t

    // Chevron pointing along (dx, dy)
    val arrowLen = 18.dp.toPx()
    val perp = Offset(-dy, dx)
    val back = tip - Offset(dx, dy) * arrowLen
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(back.x + perp.x * arrowLen * 0.7f, back.y + perp.y * arrowLen * 0.7f)
        lineTo(back.x - perp.x * arrowLen * 0.7f, back.y - perp.y * arrowLen * 0.7f)
        close()
    }
    drawCircle(HudTeal.copy(alpha = 0.25f), radius = arrowLen * 1.6f, center = tip - Offset(dx, dy) * arrowLen * 0.5f)
    drawPath(path, HudTeal)

    // "A · 3.05 m" label just inside the arrow
    val text = "A · " + (ui.pendingDistance?.let { com.jhani.measurear.measurement.formatRange(it, unit) } ?: "")
    val layout = textMeasurer.measure(
        text = text,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    )
    // Back off from the tip far enough that the pill clears the arrow in any direction
    val halfExtent = kotlin.math.abs(dx) * layout.size.width / 2f + kotlin.math.abs(dy) * layout.size.height / 2f
    val labelCenter = tip - Offset(dx, dy) * (arrowLen * 1.8f + halfExtent + 8.dp.toPx())
    val padH = 8.dp.toPx()
    val padV = 4.dp.toPx()
    val box = Size(layout.size.width + padH * 2, layout.size.height + padV * 2)
    val topLeft = Offset(labelCenter.x - box.width / 2f, labelCenter.y - box.height / 2f)
    drawRoundRect(HudTeal, topLeft, box, CornerRadius(box.height / 2f))
    drawText(layout, topLeft = Offset(topLeft.x + padH, topLeft.y + padV))
}

/** Translucent teal fill for a closed shape. */
private fun DrawScope.drawAreaFill(area: ScreenArea) {
    val path = Path().apply {
        moveTo(area.xs[0], area.ys[0])
        for (i in 1 until area.xs.size) lineTo(area.xs[i], area.ys[i])
        close()
    }
    drawPath(path, HudTeal.copy(alpha = if (area.isEstimate) 0.10f else 0.18f))
}

/** Area pill at the shape's center. */
private fun DrawScope.drawAreaLabel(area: ScreenArea, unit: MeasureUnit, textMeasurer: TextMeasurer) {
    val layout = textMeasurer.measure(
        text = formatArea(area.squareMeters, area.isEstimate, unit),
        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HudTeal)
    )
    val padH = 12.dp.toPx()
    val padV = 6.dp.toPx()
    val boxSize = Size(layout.size.width + padH * 2, layout.size.height + padV * 2)
    val topLeft = Offset(area.centerX - boxSize.width / 2f, area.centerY - boxSize.height / 2f)
    drawRoundRect(HudTealDark.copy(alpha = 0.92f), topLeft, boxSize, CornerRadius(boxSize.height / 2f))
    drawRoundRect(
        HudTeal, topLeft, boxSize, CornerRadius(boxSize.height / 2f),
        style = Stroke(1.5.dp.toPx())
    )
    drawText(layout, topLeft = Offset(topLeft.x + padH, topLeft.y + padV))
}

/**
 * Magnifier chrome: the GL renderer draws the zoomed camera image inside this circle; here we
 * add the ring, a fine crosshair, and magnified copies of nearby lines and points so their
 * position against object edges is easy to judge.
 */
private fun DrawScope.drawLoupe(ui: MeasureUiState) {
    val loupeCenter = Offset(LoupeSpec.centerX(size.width, density), LoupeSpec.centerY(size.height, density))
    val radius = LoupeSpec.RADIUS_DP.dp.toPx()
    val zoom = LoupeSpec.ZOOM
    val screenCenter = center
    fun magnify(x: Float, y: Float) = loupeCenter + (Offset(x, y) - screenCenter) * zoom

    val circle = Path().apply { addOval(Rect(loupeCenter, radius)) }
    clipPath(circle) {
        ui.segments.forEach { segment ->
            val a = magnify(segment.startX, segment.startY)
            val b = magnify(segment.endX, segment.endY)
            drawLine(ShadowColor, a, b, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                if (segment.isLive) HudTeal else Color.White, a, b,
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
            )
            if (segment.startVisible) drawEndpoint(a)
            if (segment.endVisible && !segment.isLive) drawEndpoint(b)
        }
        if (ui.hasPendingPoint && ui.pendingVisible) drawEndpoint(magnify(ui.pendingX, ui.pendingY))

        // Fine crosshair with a gap at the exact aim point
        val color = when (ui.reticle) {
            ReticleState.SEARCHING -> Color.White.copy(alpha = 0.6f)
            ReticleState.ESTIMATE -> if (ui.snapAxis != null) HudTeal else HudAmber
            else -> HudTeal
        }
        val gap = 5.dp.toPx()
        val arm = radius * 0.55f
        val w = 1.5.dp.toPx()
        drawLine(color, loupeCenter + Offset(-arm, 0f), loupeCenter + Offset(-gap, 0f), w)
        drawLine(color, loupeCenter + Offset(gap, 0f), loupeCenter + Offset(arm, 0f), w)
        drawLine(color, loupeCenter + Offset(0f, -arm), loupeCenter + Offset(0f, -gap), w)
        drawLine(color, loupeCenter + Offset(0f, gap), loupeCenter + Offset(0f, arm), w)
        drawCircle(color, radius = 1.5.dp.toPx(), center = loupeCenter)
    }

    drawCircle(ShadowColor, radius = radius, center = loupeCenter, style = Stroke(5.dp.toPx()))
    drawCircle(HudTeal, radius = radius, center = loupeCenter, style = Stroke(2.dp.toPx()))
}

/**
 * Teal crosshair: glowing ring with four ticks and a center dot. Dashed and dim while
 * searching, amber for estimates, tighter with a larger center when snapped.
 */
private fun DrawScope.drawReticle(state: ReticleState, aligned: Boolean) {
    val c = center
    if (state == ReticleState.SEARCHING) {
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = 26.dp.toPx(),
            center = c,
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            )
        )
        drawCircle(Color.White.copy(alpha = 0.55f), radius = 2.5.dp.toPx(), center = c)
        return
    }

    val color = if (state == ReticleState.ESTIMATE && !aligned) HudAmber else HudTeal
    val snapped = state == ReticleState.SNAPPED
    val radius = (if (snapped) 20.dp else 28.dp).toPx()
    val stroke = 2.5.dp.toPx()

    // Glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.28f), Color.Transparent),
            center = c,
            radius = radius * 1.8f
        ),
        radius = radius * 1.8f,
        center = c
    )
    drawCircle(ShadowColor, radius = radius, center = c, style = Stroke(width = stroke + 2.5.dp.toPx()))
    drawCircle(color, radius = radius, center = c, style = Stroke(width = stroke))

    // Ticks crossing the ring at 12, 3, 6 and 9 o'clock
    val inner = radius - 8.dp.toPx()
    val outer = radius + 8.dp.toPx()
    listOf(Offset(0f, -1f), Offset(1f, 0f), Offset(0f, 1f), Offset(-1f, 0f)).forEach { d ->
        drawLine(color, c + d * inner, c + d * outer, strokeWidth = stroke, cap = StrokeCap.Round)
    }

    val dotRadius = (if (snapped) 6.dp else 3.5.dp).toPx()
    drawCircle(ShadowColor, radius = dotRadius + 1.5.dp.toPx(), center = c)
    drawCircle(Color.White, radius = dotRadius, center = c)
}

/** Translucent fill for a shape-mode outline (rectangle, area, circle, box base). */
private fun DrawScope.drawShapeFill(poly: ScreenPolygon) {
    if (poly.xs.size < 3) return
    val path = Path().apply {
        moveTo(poly.xs[0], poly.ys[0])
        for (i in 1 until poly.xs.size) lineTo(poly.xs[i], poly.ys[i])
        close()
    }
    drawPath(path, HudTeal.copy(alpha = if (poly.isEstimate) 0.10f else 0.20f))
}

/** Value pill in the scene: edge length, angle at its corner, radius, height. */
private fun DrawScope.drawValueLabel(label: ScreenValueLabel, unit: MeasureUnit, textMeasurer: TextMeasurer) {
    val isAngle = label.value.kind == ValueKind.ANGLE
    val layout = textMeasurer.measure(
        text = formatValue(label.value, label.isEstimate, unit),
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isAngle) HudTeal else Color.Black)
    )
    val padH = 9.dp.toPx()
    val padV = 4.dp.toPx()
    val box = Size(layout.size.width + padH * 2, layout.size.height + padV * 2)
    // Angles sit just above their corner so the vertex stays visible
    val cy = if (isAngle) label.y - box.height else label.y
    // Hidden when its point is off screen (no pile-up at the edge); nudged fully on otherwise
    if (label.x < 0f || label.x > size.width || label.y < 0f || label.y > size.height) return
    val margin = 6.dp.toPx()
    val topLeft = Offset(
        (label.x - box.width / 2f).coerceIn(margin, (size.width - box.width - margin).coerceAtLeast(margin)),
        cy - box.height / 2f
    )
    val radius = CornerRadius(box.height / 2f)
    drawRoundRect(ShadowColor, topLeft + Offset(0f, 1.5.dp.toPx()), box, radius)
    if (isAngle) {
        drawRoundRect(HudTealDark.copy(alpha = 0.92f), topLeft, box, radius)
        drawRoundRect(HudTeal, topLeft, box, radius, style = Stroke(1.5.dp.toPx()))
    } else {
        drawRoundRect(if (label.isEstimate) EstimateLabelColor else Color.White, topLeft, box, radius)
    }
    drawText(layout, topLeft = Offset(topLeft.x + padH, topLeft.y + padV))
}
