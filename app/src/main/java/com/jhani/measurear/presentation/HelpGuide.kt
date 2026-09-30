package com.jhani.measurear.presentation

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jhani.measurear.R
import com.jhani.measurear.measurement.MeasureMode
import kotlinx.coroutines.launch

/**
 * Step-by-step guides behind the ? button: one per measuring mode and for the Level and
 * Compass tools, each step a drawn picture with a short title and explanation. Each guide
 * also opens by itself the first time its screen is used.
 */
enum class HelpPic {
    SCAN_FLOOR, SCAN_WALL,
    LINE_START, LINE_END, LINE_EDIT,
    HEIGHT_BASE, HEIGHT_TOP,
    FAR_PHONE, FAR_BASE, FAR_TOP,
    DIST_AIM, DIST_KEEP,
    ANGLE_ARM, ANGLE_DONE,
    PATH_TAP, PATH_DONE,
    RECT_TAP, RECT_DONE,
    CIRCLE_CENTER, CIRCLE_EDGE,
    AREA_TAP, AREA_CLOSE,
    VOL_BASE, VOL_TOP,
    HANG_SET, HANG_TAP, HANG_NAILS,
    FIT_PICK, FIT_PLACE, FIT_MOVE,
    CAL_CARD, CAL_ENDS,
    LEVEL_FLAT, LEVEL_EDGE, LEVEL_REF,
    COMPASS_FLAT, COMPASS_LOCK, COMPASS_FOLLOW
}

class HelpStep(val pic: HelpPic, @StringRes val title: Int, @StringRes val body: Int)

/** A guide's topic: a measuring mode's name, or [LEVEL] / [COMPASS]. */
object HelpTopic {
    const val LEVEL = "LEVEL"
    const val COMPASS = "COMPASS"
    fun of(mode: MeasureMode) = mode.name
}

private val scanFloor = HelpStep(HelpPic.SCAN_FLOOR, R.string.help_scan_floor_t, R.string.help_scan_floor_b)
private val scanWall = HelpStep(HelpPic.SCAN_WALL, R.string.help_scan_wall_t, R.string.help_scan_wall_b)

fun helpSteps(topic: String): List<HelpStep> = when (topic) {
    HelpTopic.LEVEL -> listOf(
        HelpStep(HelpPic.LEVEL_FLAT, R.string.help_level_1_t, R.string.help_level_1_b),
        HelpStep(HelpPic.LEVEL_EDGE, R.string.help_level_2_t, R.string.help_level_2_b),
        HelpStep(HelpPic.LEVEL_REF, R.string.help_level_3_t, R.string.help_level_3_b)
    )
    HelpTopic.COMPASS -> listOf(
        HelpStep(HelpPic.COMPASS_FLAT, R.string.help_compass_1_t, R.string.help_compass_1_b),
        HelpStep(HelpPic.COMPASS_LOCK, R.string.help_compass_2_t, R.string.help_compass_2_b),
        HelpStep(HelpPic.COMPASS_FOLLOW, R.string.help_compass_3_t, R.string.help_compass_3_b)
    )
    else -> when (MeasureMode.values().firstOrNull { it.name == topic }) {
        MeasureMode.LINE -> listOf(
            scanFloor,
            HelpStep(HelpPic.LINE_START, R.string.help_line_1_t, R.string.help_line_1_b),
            HelpStep(HelpPic.LINE_END, R.string.help_line_2_t, R.string.help_line_2_b),
            HelpStep(HelpPic.LINE_EDIT, R.string.help_line_3_t, R.string.help_line_3_b)
        )
        MeasureMode.HEIGHT -> listOf(
            scanFloor,
            HelpStep(HelpPic.HEIGHT_BASE, R.string.help_height_1_t, R.string.help_height_1_b),
            HelpStep(HelpPic.HEIGHT_TOP, R.string.help_height_2_t, R.string.help_height_2_b)
        )
        MeasureMode.FAR -> listOf(
            HelpStep(HelpPic.FAR_PHONE, R.string.help_far_1_t, R.string.help_far_1_b),
            HelpStep(HelpPic.FAR_BASE, R.string.help_far_2_t, R.string.help_far_2_b),
            HelpStep(HelpPic.FAR_TOP, R.string.help_far_3_t, R.string.help_far_3_b)
        )
        MeasureMode.DISTANCE -> listOf(
            scanFloor,
            HelpStep(HelpPic.DIST_AIM, R.string.help_dist_1_t, R.string.help_dist_1_b),
            HelpStep(HelpPic.DIST_KEEP, R.string.help_dist_2_t, R.string.help_dist_2_b)
        )
        MeasureMode.ANGLE -> listOf(
            scanFloor,
            HelpStep(HelpPic.ANGLE_ARM, R.string.help_angle_1_t, R.string.help_angle_1_b),
            HelpStep(HelpPic.ANGLE_DONE, R.string.help_angle_2_t, R.string.help_angle_2_b)
        )
        MeasureMode.PATH -> listOf(
            scanFloor,
            HelpStep(HelpPic.PATH_TAP, R.string.help_path_1_t, R.string.help_path_1_b),
            HelpStep(HelpPic.PATH_DONE, R.string.help_path_2_t, R.string.help_path_2_b)
        )
        MeasureMode.RECTANGLE -> listOf(
            scanFloor,
            HelpStep(HelpPic.RECT_TAP, R.string.help_rect_1_t, R.string.help_rect_1_b),
            HelpStep(HelpPic.RECT_DONE, R.string.help_rect_2_t, R.string.help_rect_2_b)
        )
        MeasureMode.CIRCLE -> listOf(
            scanFloor,
            HelpStep(HelpPic.CIRCLE_CENTER, R.string.help_circle_1_t, R.string.help_circle_1_b),
            HelpStep(HelpPic.CIRCLE_EDGE, R.string.help_circle_2_t, R.string.help_circle_2_b)
        )
        MeasureMode.AREA -> listOf(
            scanFloor,
            HelpStep(HelpPic.AREA_TAP, R.string.help_area_1_t, R.string.help_area_1_b),
            HelpStep(HelpPic.AREA_CLOSE, R.string.help_area_2_t, R.string.help_area_2_b)
        )
        MeasureMode.VOLUME -> listOf(
            scanFloor,
            HelpStep(HelpPic.VOL_BASE, R.string.help_vol_1_t, R.string.help_vol_1_b),
            HelpStep(HelpPic.VOL_TOP, R.string.help_vol_2_t, R.string.help_vol_2_b)
        )
        MeasureMode.HANG -> listOf(
            HelpStep(HelpPic.HANG_SET, R.string.help_hang_1_t, R.string.help_hang_1_b),
            scanWall,
            HelpStep(HelpPic.HANG_TAP, R.string.help_hang_2_t, R.string.help_hang_2_b),
            HelpStep(HelpPic.HANG_NAILS, R.string.help_hang_3_t, R.string.help_hang_3_b)
        )
        MeasureMode.FIT -> listOf(
            HelpStep(HelpPic.FIT_PICK, R.string.help_fit_1_t, R.string.help_fit_1_b),
            scanFloor,
            HelpStep(HelpPic.FIT_PLACE, R.string.help_fit_2_t, R.string.help_fit_2_b),
            HelpStep(HelpPic.FIT_MOVE, R.string.help_fit_3_t, R.string.help_fit_3_b)
        )
        MeasureMode.CALIBRATE -> listOf(
            scanFloor,
            HelpStep(HelpPic.CAL_CARD, R.string.help_cal_1_t, R.string.help_cal_1_b),
            HelpStep(HelpPic.CAL_ENDS, R.string.help_cal_2_t, R.string.help_cal_2_b)
        )
        null -> emptyList()
    }
}

/** Which guides have been shown once already (so they only open by themselves the first time). */
object HelpSeen {
    private const val PREFS = "measurear"
    private fun key(topic: String) = "help_seen_$topic"

    fun isSeen(context: Context, topic: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key(topic), false)

    fun markSeen(context: Context, topic: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key(topic), true).apply()
}

/** Round "?" button that opens the current screen's guide. */
@Composable
fun HelpButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_help)
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.5.dp, HudTeal, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text("?", color = HudTeal, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

/** The guide: swipe or tap Next through the steps; the last one closes it. */
@Composable
fun HelpDialog(topic: String, name: String, onClose: () -> Unit) {
    val steps = helpSteps(topic)
    if (steps.isEmpty()) {
        onClose()
        return
    }
    val isTool = topic == HelpTopic.LEVEL || topic == HelpTopic.COMPASS
    val pager = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0E1614))
                .border(1.dp, HudTeal.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.help_title, name),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "✕",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 18.sp,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { page ->
                val step = steps[page]
                Column {
                    HelpPicture(
                        step.pic,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.55f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF17221F))
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.help_step, page + 1, steps.size).uppercase(),
                        color = HudTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(step.title), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    // Room for the longest explanation, so the buttons don't jump between steps
                    Text(
                        stringResource(step.body),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        minLines = 4
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Step dots
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == pager.currentPage) 9.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (i == pager.currentPage) HudTeal else Color.White.copy(alpha = 0.25f))
                        )
                    }
                }
                if (pager.currentPage > 0) {
                    Text(
                        stringResource(R.string.help_back),
                        color = Color.White.copy(alpha = 0.75f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                val last = pager.currentPage == steps.lastIndex
                Text(
                    when {
                        !last -> stringResource(R.string.help_next) + "  ›"
                        isTool -> stringResource(R.string.help_got_it_tool)
                        else -> stringResource(R.string.help_got_it)
                    },
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(HudTeal)
                        .clickable {
                            if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pictures: simple drawings in the app's style (teal points and lines on a dark scene)
// ---------------------------------------------------------------------------------------------

private val Surface = Color.White.copy(alpha = 0.07f)
private val SurfaceLine = Color.White.copy(alpha = 0.12f)
private val Object = Color.White.copy(alpha = 0.22f)

@Composable
fun HelpPicture(pic: HelpPic, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) { Sketch(this, measurer).draw(pic) }
}

private class Sketch(val d: DrawScope, val tm: TextMeasurer) {
    val w = d.size.width
    val h = d.size.height
    val u = h / 100f // a scene unit: 1% of the height
    fun p(x: Float, y: Float) = Offset(w * x, h * y)

    // Floor seen in perspective: y from HORIZON (far) to 1 (near)
    private val horizon = 0.36f
    private fun floorAt(x: Float, y: Float): Offset {
        val t = (y - horizon) / (1f - horizon)
        val half = 0.26f + 0.32f * t
        return p(0.5f + (x - 0.5f) * half * 2f, y)
    }

    fun floor(dots: Boolean = true) {
        val path = Path().apply {
            val a = floorAt(0f, horizon); val b = floorAt(1f, horizon); val c = floorAt(1f, 1f); val e = floorAt(0f, 1f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(e.x, e.y); close()
        }
        d.drawPath(path, Surface)
        for (i in 0..6) {
            val x = i / 6f
            d.drawLine(SurfaceLine, floorAt(x, horizon), floorAt(x, 1f), 1f)
        }
        if (dots) {
            var y = horizon + 0.04f
            while (y < 0.99f) {
                for (i in 0..11) {
                    val o = floorAt((i + 0.5f) / 12f, y)
                    d.drawCircle(HudTeal.copy(alpha = 0.55f), radius = u * (0.45f + (y - horizon)), center = o)
                }
                y += 0.04f + (y - horizon) * 0.08f
            }
        }
    }

    fun wall(dots: Boolean = true) {
        d.drawRect(Surface, topLeft = p(0.08f, 0.06f), size = Size(w * 0.84f, h * 0.72f))
        d.drawLine(SurfaceLine, p(0f, 0.78f), p(1f, 0.78f), 2f)
        if (dots) {
            var y = 0.12f
            while (y < 0.76f) {
                var x = 0.12f
                while (x < 0.9f) {
                    d.drawCircle(HudTeal.copy(alpha = 0.5f), u * 0.7f, p(x, y))
                    x += 0.045f
                }
                y += 0.07f
            }
        }
    }

    fun pt(o: Offset) {
        d.drawCircle(Color.Black.copy(alpha = 0.4f), u * 2.8f, o)
        d.drawCircle(HudTeal, u * 2.2f, o)
        d.drawCircle(Color.White, u * 1.2f, o)
    }

    fun seg(a: Offset, b: Offset, dashed: Boolean = false, color: Color = Color.White) = d.drawLine(
        color, a, b, strokeWidth = u * 0.9f, cap = StrokeCap.Round,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(u * 2.2f, u * 1.6f)) else null
    )

    fun poly(points: List<Offset>, closed: Boolean = true, fill: Boolean = true) {
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            if (closed) close()
        }
        if (fill) d.drawPath(path, HudTeal.copy(alpha = 0.22f))
        d.drawPath(path, Color.White, style = Stroke(u * 0.9f))
    }

    fun crosshair(o: Offset) {
        val r = u * 6f
        d.drawCircle(HudAmber.copy(alpha = 0.25f), r * 1.4f, o)
        d.drawCircle(HudAmber, r, o, style = Stroke(u * 0.8f))
        listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)).forEach { v ->
            d.drawLine(HudAmber, o + v * (r * 0.6f), o + v * (r * 1.4f), u * 0.8f)
        }
    }

    /** A tap on [o]: rings around it and a fingertip just below-right, so the target stays visible. */
    fun tap(o: Offset) {
        d.drawCircle(Color.White.copy(alpha = 0.55f), u * 9f, o, style = Stroke(u * 0.6f))
        d.drawCircle(Color.White.copy(alpha = 0.3f), u * 12f, o, style = Stroke(u * 0.5f))
        val tip = o + Offset(u * 6.5f, u * 7.5f)
        d.drawCircle(Color.Black.copy(alpha = 0.35f), u * 3.2f, tip)
        d.drawCircle(Color.White.copy(alpha = 0.95f), u * 2.6f, tip)
    }

    /** Round rotate button: an open arc with an arrowhead, clockwise when [cw]. */
    fun rotateButton(o: Offset, cw: Boolean) {
        d.drawCircle(Color(0xFF0E1614), u * 7f, o)
        d.drawCircle(HudTeal, u * 7f, o, style = Stroke(u * 0.7f))
        val r = u * 3.6f
        d.drawArc(HudTeal, if (cw) -60f else 240f, if (cw) 280f else -280f, false, topLeft = o - Offset(r, r), size = Size(r * 2, r * 2), style = Stroke(u * 0.8f, cap = StrokeCap.Round))
        val a = Math.toRadians(if (cw) -60.0 else 240.0)
        val start = o + Offset(r * kotlin.math.cos(a).toFloat(), r * kotlin.math.sin(a).toFloat())
        val s = if (cw) 1f else -1f
        d.drawLine(HudTeal, start, start + Offset(s * u * 2.2f, -u * 0.4f), u * 0.8f, cap = StrokeCap.Round)
        d.drawLine(HudTeal, start, start + Offset(s * u * 0.2f, -u * 2.4f), u * 0.8f, cap = StrokeCap.Round)
    }

    /** The STAMP (+) button, bottom right; highlighted when the step presses it. */
    fun stamp(pressed: Boolean = true) {
        val o = p(0.9f, 0.8f)
        if (pressed) d.drawCircle(HudTeal.copy(alpha = 0.25f), u * 12f, o)
        d.drawCircle(Color(0xFF0E1614), u * 8.5f, o)
        d.drawCircle(HudTeal, u * 8.5f, o, style = Stroke(u * 1f))
        d.drawLine(HudTeal, o + Offset(-u * 3.5f, 0f), o + Offset(u * 3.5f, 0f), u * 1.1f, cap = StrokeCap.Round)
        d.drawLine(HudTeal, o + Offset(0f, -u * 3.5f), o + Offset(0f, u * 3.5f), u * 1.1f, cap = StrokeCap.Round)
        // Pressed: a fingertip at its lower edge (the glow already marks it; the + stays visible)
        if (pressed) d.drawCircle(Color.White.copy(alpha = 0.9f), u * 2.4f, o + Offset(u * 5f, u * 6.5f))
    }

    fun pill(text: String, o: Offset, strong: Boolean = false) {
        val layout = tm.measure(
            text,
            TextStyle(fontSize = (u * 5f / d.density).sp, fontWeight = FontWeight.SemiBold, color = if (strong) Color.Black else Color(0xFF1C2322))
        )
        val pad = Offset(u * 2.4f, u * 1f)
        val size = Size(layout.size.width + pad.x * 2, layout.size.height + pad.y * 2)
        val tl = Offset(o.x - size.width / 2f, o.y - size.height / 2f)
        d.drawRoundRect(if (strong) HudTeal else Color.White.copy(alpha = 0.85f), tl, size, CornerRadius(size.height / 2f))
        d.drawText(layout, topLeft = tl + pad)
    }

    fun label(text: String, o: Offset, color: Color = Color.White, sizeU: Float = 4.5f) {
        val layout = tm.measure(text, TextStyle(fontSize = (u * sizeU / d.density).sp, fontWeight = FontWeight.Bold, color = color))
        d.drawText(layout, topLeft = Offset(o.x - layout.size.width / 2f, o.y - layout.size.height / 2f))
    }

    /** A phone seen from behind/side, [hU] tall, rotated by [deg]. */
    fun phone(o: Offset, hU: Float = 34f, deg: Float = 0f) {
        d.rotate(deg, o) {
            val size = Size(u * hU * 0.5f, u * hU)
            val tl = Offset(o.x - size.width / 2f, o.y - size.height / 2f)
            drawRoundRect(Color(0xFF0E1614), tl, size, CornerRadius(u * 3f))
            drawRoundRect(Color.White, tl, size, CornerRadius(u * 3f), style = Stroke(u * 0.9f))
            drawCircle(HudTeal, u * 1.4f, Offset(o.x, tl.y + u * 4f))
        }
    }

    /** ← → arrows either side of [o], for "move slowly sideways". */
    fun sideArrows(o: Offset, span: Float) {
        listOf(-1f, 1f).forEach { s ->
            val a = o + Offset(s * span * 0.55f, 0f)
            val b = o + Offset(s * span, 0f)
            d.drawLine(HudTeal, a, b, u * 1f, cap = StrokeCap.Round)
            d.drawLine(HudTeal, b, b + Offset(-s * u * 3f, -u * 3f), u * 1f, cap = StrokeCap.Round)
            d.drawLine(HudTeal, b, b + Offset(-s * u * 3f, u * 3f), u * 1f, cap = StrokeCap.Round)
        }
    }

    /** Curved arrow (tilt up), around [o]. */
    fun tiltArrow(o: Offset) {
        val r = u * 10f
        d.drawArc(HudTeal, 200f, 110f, false, topLeft = o - Offset(r, r), size = Size(r * 2, r * 2), style = Stroke(u * 1f, cap = StrokeCap.Round))
        val end = o + Offset(r * kotlin.math.cos(Math.toRadians(310.0)).toFloat(), r * kotlin.math.sin(Math.toRadians(310.0)).toFloat())
        d.drawLine(HudTeal, end, end + Offset(-u * 3.5f, -u * 0.5f), u * 1f, cap = StrokeCap.Round)
        d.drawLine(HudTeal, end, end + Offset(-u * 0.5f, u * 3.5f), u * 1f, cap = StrokeCap.Round)
    }

    fun nail(o: Offset) {
        val s = u * 2.2f
        d.drawLine(HudAmber, o + Offset(-s, -s), o + Offset(s, s), u * 0.9f, cap = StrokeCap.Round)
        d.drawLine(HudAmber, o + Offset(-s, s), o + Offset(s, -s), u * 0.9f, cap = StrokeCap.Round)
    }

    /** Wireframe box: base corners [a b c e] (c opposite a) raised by [up] px. */
    fun box(base: List<Offset>, up: Float, color: Color = HudTeal, fill: Boolean = true) {
        val top = base.map { it - Offset(0f, up) }
        if (fill) {
            val path = Path().apply { moveTo(top[0].x, top[0].y); top.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            d.drawPath(path, color.copy(alpha = 0.2f))
        }
        for (i in 0..3) {
            val j = (i + 1) % 4
            d.drawLine(color, base[i], base[j], u * 0.8f)
            d.drawLine(color, top[i], top[j], u * 0.8f)
            d.drawLine(color, base[i], top[i], u * 0.8f)
        }
    }

    fun draw(pic: HelpPic) {
        when (pic) {
            HelpPic.SCAN_FLOOR -> {
                floor(dots = true)
                phone(p(0.5f, 0.2f), hU = 30f, deg = 0f)
                sideArrows(p(0.5f, 0.2f), w * 0.2f)
                seg(p(0.5f, 0.36f), floorAt(0.5f, 0.7f), dashed = true, color = HudTeal.copy(alpha = 0.7f))
            }
            HelpPic.SCAN_WALL -> {
                wall(dots = true)
                phone(p(0.5f, 0.62f), hU = 30f)
                sideArrows(p(0.5f, 0.62f), w * 0.18f)
            }
            HelpPic.LINE_START -> {
                floor(); val a = floorAt(0.25f, 0.75f)
                pt(a); crosshair(a); stamp()
            }
            HelpPic.LINE_END -> {
                floor(); val a = floorAt(0.25f, 0.75f); val b = floorAt(0.72f, 0.62f)
                seg(a, b); pt(a); pt(b); crosshair(b)
                pill("1.25 m", Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f - u * 6f)); stamp()
            }
            HelpPic.LINE_EDIT -> {
                floor(); val a = floorAt(0.2f, 0.82f); val b = floorAt(0.75f, 0.66f)
                seg(a, b); pt(a); pt(b)
                val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f - u * 4f)
                // Action card for the tapped length
                val card = Size(w * 0.34f, u * 13f)
                val tl = Offset(mid.x - card.width / 2f, mid.y - u * 26f)
                d.drawRoundRect(Color(0xFF0E1614), tl, card, CornerRadius(u * 3f))
                d.drawRoundRect(HudTeal.copy(alpha = 0.6f), tl, card, CornerRadius(u * 3f), style = Stroke(u * 0.5f))
                listOf("↔", "✕", "↗").forEachIndexed { i, g ->
                    label(g, Offset(tl.x + card.width * (i + 0.5f) / 3f, tl.y + card.height / 2f), HudTeal, 7f)
                }
                pill("1.25 m", mid, strong = true)
                tap(mid + Offset(u * 20f, u * 5f))
            }
            HelpPic.HEIGHT_BASE, HelpPic.HEIGHT_TOP -> {
                floor()
                // A cabinet standing on the floor
                val base = floorAt(0.55f, 0.72f)
                val top = base - Offset(0f, h * 0.5f)
                d.drawRect(Object, topLeft = Offset(base.x - w * 0.09f, top.y), size = Size(w * 0.18f, base.y - top.y))
                if (pic == HelpPic.HEIGHT_BASE) {
                    pt(base); crosshair(base); stamp()
                } else {
                    seg(base, top, dashed = true, color = HudTeal); pt(base); pt(top); crosshair(top)
                    pill("85 cm", Offset(base.x + w * 0.16f, (base.y + top.y) / 2f))
                    tiltArrow(p(0.2f, 0.45f)); stamp()
                }
            }
            HelpPic.FAR_PHONE, HelpPic.FAR_BASE, HelpPic.FAR_TOP -> {
                val ground = 0.84f
                d.drawLine(SurfaceLine, p(0f, ground), p(1f, ground), u * 0.8f)
                // Person holding the phone
                val head = p(0.16f, 0.36f)
                d.drawCircle(Object, u * 4.5f, head)
                d.drawLine(Object, head + Offset(0f, u * 5f), p(0.16f, 0.66f), u * 2.2f, cap = StrokeCap.Round)
                d.drawLine(Object, p(0.16f, 0.66f), p(0.13f, ground), u * 2f, cap = StrokeCap.Round)
                d.drawLine(Object, p(0.16f, 0.66f), p(0.19f, ground), u * 2f, cap = StrokeCap.Round)
                val eye = p(0.2f, 0.44f)
                d.drawRoundRect(Color.White, eye - Offset(u * 1.2f, u * 2.5f), Size(u * 2.4f, u * 5f), CornerRadius(u))
                // Building with windows
                val bl = p(0.62f, 0.12f)
                val bs = Size(w * 0.22f, h * (ground - 0.12f))
                d.drawRect(Object, bl, bs)
                for (r in 0..5) for (c in 0..2) {
                    d.drawRect(Color.White.copy(alpha = 0.18f), Offset(bl.x + bs.width * (0.12f + c * 0.3f), bl.y + bs.height * (0.06f + r * 0.15f)), Size(bs.width * 0.18f, bs.height * 0.08f))
                }
                val foot = p(0.62f, ground)
                val roof = p(0.62f, 0.12f)
                when (pic) {
                    HelpPic.FAR_PHONE -> {
                        seg(p(0.27f, 0.44f), p(0.27f, ground), dashed = true, color = HudTeal)
                        pill("1.5 m", p(0.36f, 0.64f))
                        pill("📱 1.5 m", p(0.3f, 0.12f), strong = true)
                        tap(p(0.34f, 0.14f))
                    }
                    HelpPic.FAR_BASE -> {
                        seg(eye, foot, dashed = true, color = HudTeal.copy(alpha = 0.8f)); pt(foot); crosshair(foot); stamp()
                    }
                    else -> {
                        seg(eye, roof, dashed = true, color = HudTeal.copy(alpha = 0.8f))
                        seg(foot, roof, color = HudTeal); pt(foot); pt(roof); crosshair(roof)
                        pill("≈ 18 m", p(0.5f, 0.48f)); stamp()
                    }
                }
            }
            HelpPic.DIST_AIM, HelpPic.DIST_KEEP -> {
                floor(); val spot = floorAt(0.62f, 0.64f)
                seg(p(0.5f, 1f), spot, dashed = true, color = HudTeal.copy(alpha = 0.7f))
                label("1.40 m", p(0.86f, 0.1f), sizeU = 6f)
                if (pic == HelpPic.DIST_AIM) crosshair(spot) else {
                    pt(spot); pill("1.40 m", spot - Offset(0f, u * 8f)); stamp()
                }
            }
            HelpPic.ANGLE_ARM, HelpPic.ANGLE_DONE -> {
                floor()
                val arm1 = floorAt(0.2f, 0.62f); val corner = floorAt(0.5f, 0.88f); val arm2 = floorAt(0.82f, 0.6f)
                if (pic == HelpPic.ANGLE_ARM) {
                    pt(arm1); crosshair(arm1); tap(arm1)
                } else {
                    seg(arm1, corner); seg(corner, arm2); pt(arm1); pt(corner); pt(arm2)
                    d.drawArc(HudTeal, 215f, 105f, false, topLeft = corner - Offset(u * 9f, u * 9f), size = Size(u * 18f, u * 18f), style = Stroke(u * 0.9f))
                    pill("90°", corner - Offset(0f, u * 16f), strong = true)
                }
            }
            HelpPic.PATH_TAP, HelpPic.PATH_DONE -> {
                floor()
                val pts = listOf(floorAt(0.12f, 0.9f), floorAt(0.35f, 0.66f), floorAt(0.6f, 0.8f), floorAt(0.86f, 0.6f))
                val shown = if (pic == HelpPic.PATH_TAP) pts.take(3) else pts
                shown.zipWithNext().forEach { (a, b) -> seg(a, b) }
                shown.forEach { pt(it) }
                if (pic == HelpPic.PATH_TAP) {
                    pill("82 cm", Offset((pts[0].x + pts[1].x) / 2f, (pts[0].y + pts[1].y) / 2f - u * 6f))
                    crosshair(pts[3]); stamp()
                } else {
                    pill("Σ 2.36 m", p(0.5f, 0.38f), strong = true)
                    pill("  ✓  ", p(0.5f, 0.18f), strong = true); tap(p(0.5f, 0.18f))
                }
            }
            HelpPic.RECT_TAP, HelpPic.RECT_DONE -> {
                floor()
                val c = listOf(floorAt(0.2f, 0.62f), floorAt(0.75f, 0.6f), floorAt(0.82f, 0.92f), floorAt(0.15f, 0.95f))
                if (pic == HelpPic.RECT_TAP) {
                    seg(c[0], c[1]); seg(c[1], c[2]); c.take(3).forEach { pt(it) }
                    label("1", c[0] - Offset(0f, u * 6f), HudTeal); label("2", c[1] - Offset(0f, u * 6f), HudTeal); label("3", c[2] + Offset(u * 6f, 0f), HudTeal)
                    tap(c[2])
                } else {
                    poly(c); c.forEach { pt(it) }
                    d.drawCircle(HudAmber, u * 4f, c[3], style = Stroke(u * 0.7f))
                    pill("2.40 m²", p(0.5f, 0.78f), strong = true)
                }
            }
            HelpPic.CIRCLE_CENTER, HelpPic.CIRCLE_EDGE -> {
                floor()
                val center = floorAt(0.5f, 0.76f)
                val rx = w * 0.2f; val ry = h * 0.12f
                val ovalTl = center - Offset(rx, ry)
                if (pic == HelpPic.CIRCLE_CENTER) {
                    d.drawOval(Object, ovalTl, Size(rx * 2, ry * 2))
                    pt(center); crosshair(center); stamp()
                } else {
                    d.drawOval(HudTeal.copy(alpha = 0.22f), ovalTl, Size(rx * 2, ry * 2))
                    d.drawOval(Color.White, ovalTl, Size(rx * 2, ry * 2), style = Stroke(u * 0.9f))
                    val edge = center + Offset(rx, 0f)
                    seg(center, edge, dashed = true, color = HudTeal); pt(center); pt(edge)
                    pill("Ø 90 cm", center - Offset(0f, ry + u * 7f), strong = true)
                    tap(edge)
                }
            }
            HelpPic.AREA_TAP, HelpPic.AREA_CLOSE -> {
                floor()
                val c = listOf(floorAt(0.1f, 0.6f), floorAt(0.48f, 0.55f), floorAt(0.76f, 0.68f), floorAt(0.6f, 0.92f), floorAt(0.14f, 0.88f))
                if (pic == HelpPic.AREA_TAP) {
                    c.take(4).zipWithNext().forEach { (a, b) -> seg(a, b) }
                    c.take(4).forEach { pt(it) }
                    crosshair(c[4]); stamp()
                } else {
                    poly(c); c.forEach { pt(it) }
                    tap(c[0]); pill("12.5 m²", p(0.52f, 0.78f), strong = true)
                }
            }
            HelpPic.VOL_BASE, HelpPic.VOL_TOP -> {
                floor()
                val base = listOf(floorAt(0.3f, 0.72f), floorAt(0.62f, 0.66f), floorAt(0.76f, 0.84f), floorAt(0.4f, 0.92f))
                val up = h * 0.34f
                if (pic == HelpPic.VOL_BASE) {
                    box(base, up, Object, fill = false)
                    seg(base[0], base[1]); seg(base[1], base[2]); base.take(3).forEach { pt(it) }
                    tap(base[2])
                } else {
                    box(base, up); pt(base[2] - Offset(0f, up)); crosshair(base[2] - Offset(0f, up))
                    pill("0.35 m³", p(0.5f, 0.14f), strong = true); tiltArrow(p(0.14f, 0.5f)); stamp()
                }
            }
            HelpPic.HANG_SET -> {
                // The frames dialog: count and three frames side by side
                val card = Size(w * 0.62f, h * 0.78f)
                val tl = Offset((w - card.width) / 2f, (h - card.height) / 2f)
                d.drawRoundRect(Color(0xFF0E1614), tl, card, CornerRadius(u * 4f))
                d.drawRoundRect(HudTeal.copy(alpha = 0.4f), tl, card, CornerRadius(u * 4f), style = Stroke(u * 0.5f))
                label("−", Offset(tl.x + card.width * 0.3f, tl.y + h * 0.14f), HudTeal, 7f)
                label("3", Offset(tl.x + card.width * 0.5f, tl.y + h * 0.14f), Color.White, 7f)
                label("+", Offset(tl.x + card.width * 0.7f, tl.y + h * 0.14f), HudTeal, 7f)
                tap(Offset(tl.x + card.width * 0.7f, tl.y + h * 0.14f))
                listOf(0.14f, 0.4f, 0.66f).forEach { x ->
                    d.drawRect(HudTeal, Offset(tl.x + card.width * x, tl.y + h * 0.3f), Size(card.width * 0.2f, h * 0.26f), style = Stroke(u * 0.9f))
                }
                pill("40 × 50 cm", Offset(tl.x + card.width * 0.3f, tl.y + h * 0.68f))
                pill("↔ 8 cm", Offset(tl.x + card.width * 0.74f, tl.y + h * 0.68f))
            }
            HelpPic.HANG_TAP, HelpPic.HANG_NAILS -> {
                wall(dots = pic == HelpPic.HANG_TAP)
                val mid = p(0.5f, 0.42f)
                if (pic == HelpPic.HANG_TAP) {
                    crosshair(mid); tap(mid)
                } else {
                    d.drawLine(HudTeal.copy(alpha = 0.6f), p(0.1f, 0.2f), p(0.9f, 0.2f), u * 0.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 2f, u * 1.6f)))
                    val xs = listOf(0.2f, 0.5f, 0.8f)
                    xs.forEach { x ->
                        d.drawRect(HudTeal, Offset(w * (x - 0.11f), h * 0.2f), Size(w * 0.22f, h * 0.42f), style = Stroke(u * 0.9f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 2f, u * 1.4f))))
                        nail(p(x, 0.28f))
                    }
                    pill("48 cm", p(0.35f, 0.34f)); pill("48 cm", p(0.65f, 0.34f))
                    pt(mid)
                }
            }
            HelpPic.FIT_PICK -> {
                val labels = listOf("200 × 90", "198 × 152", "70 × 70", "123 × 8")
                labels.forEachIndexed { i, t ->
                    val col = i % 2; val row = i / 2
                    val tl = Offset(w * (0.1f + col * 0.42f), h * (0.1f + row * 0.44f))
                    val s = Size(w * 0.38f, h * 0.38f)
                    val selected = i == 0
                    d.drawRoundRect(if (selected) HudTealDark else Color(0xFF0E1614), tl, s, CornerRadius(u * 3f))
                    d.drawRoundRect(if (selected) HudTeal else Color.White.copy(alpha = 0.15f), tl, s, CornerRadius(u * 3f), style = Stroke(u * 0.6f))
                    val bw = s.width * 0.42f
                    val b0 = Offset(tl.x + s.width * 0.25f, tl.y + s.height * 0.62f)
                    box(listOf(b0, b0 + Offset(bw, 0f), b0 + Offset(bw + u * 4f, -u * 4f), b0 + Offset(u * 4f, -u * 4f)), s.height * 0.22f, if (selected) HudTeal else Object, fill = selected)
                    label(t, Offset(tl.x + s.width / 2f, tl.y + s.height * 0.82f), Color.White.copy(alpha = 0.8f), 3.8f)
                }
                tap(p(0.36f, 0.4f))
            }
            HelpPic.FIT_PLACE, HelpPic.FIT_MOVE -> {
                floor()
                val base = listOf(floorAt(0.3f, 0.68f), floorAt(0.7f, 0.66f), floorAt(0.74f, 0.86f), floorAt(0.26f, 0.88f))
                box(base, h * 0.22f)
                val center = Offset(base.map { it.x }.average().toFloat(), base.map { it.y }.average().toFloat())
                if (pic == HelpPic.FIT_PLACE) {
                    tap(center)
                } else {
                    pt(center)
                    d.drawLine(HudTeal, center, center + Offset(w * 0.14f, 0f), u * 1f, cap = StrokeCap.Round)
                    val tip = center + Offset(w * 0.14f, 0f)
                    d.drawLine(HudTeal, tip, tip + Offset(-u * 3f, -u * 3f), u * 1f, cap = StrokeCap.Round)
                    d.drawLine(HudTeal, tip, tip + Offset(-u * 3f, u * 3f), u * 1f, cap = StrokeCap.Round)
                    rotateButton(p(0.1f, 0.2f), cw = false)
                    rotateButton(p(0.88f, 0.2f), cw = true)
                    tap(p(0.88f, 0.2f))
                }
            }
            HelpPic.CAL_CARD, HelpPic.CAL_ENDS -> {
                floor()
                val c = listOf(floorAt(0.3f, 0.66f), floorAt(0.7f, 0.64f), floorAt(0.74f, 0.86f), floorAt(0.27f, 0.88f))
                val path = Path().apply { moveTo(c[0].x, c[0].y); c.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                d.drawPath(path, Color(0xFF2E5BBA))
                d.drawPath(path, Color.White.copy(alpha = 0.6f), style = Stroke(u * 0.6f))
                val chip = floorAt(0.36f, 0.74f)
                d.drawRoundRect(Color(0xFFE2B84A), chip - Offset(u * 3f, u * 2f), Size(u * 6f, u * 4f), CornerRadius(u))
                if (pic == HelpPic.CAL_ENDS) {
                    seg(c[3], c[2], color = HudTeal); pt(c[3]); pt(c[2])
                    pill("8.56 cm", Offset((c[2].x + c[3].x) / 2f, c[2].y + u * 7f), strong = true)
                    tap(c[2])
                }
            }
            HelpPic.LEVEL_FLAT, HelpPic.LEVEL_REF -> {
                // Bubble level dial
                val o = p(0.5f, 0.46f)
                val r = h * 0.34f
                d.drawCircle(Color(0xFF0E1614), r, o)
                d.drawCircle(Color.White.copy(alpha = 0.3f), r, o, style = Stroke(u * 0.7f))
                d.drawCircle(Color.White.copy(alpha = 0.3f), r * 0.35f, o, style = Stroke(u * 0.6f))
                d.drawLine(Color.White.copy(alpha = 0.2f), o - Offset(r, 0f), o + Offset(r, 0f), u * 0.5f)
                d.drawLine(Color.White.copy(alpha = 0.2f), o - Offset(0f, r), o + Offset(0f, r), u * 0.5f)
                if (pic == HelpPic.LEVEL_FLAT) {
                    d.drawCircle(HudTeal, r * 0.22f, o)
                    label("✓", o, Color.Black, 7f)
                    d.drawLine(SurfaceLine, p(0.1f, 0.92f), p(0.9f, 0.92f), u * 1.2f)
                } else {
                    d.drawCircle(HudAmber, r * 0.2f, o + Offset(r * 0.45f, -r * 0.2f))
                    pill("2.5°", p(0.5f, 0.9f), strong = true)
                    tap(o)
                }
            }
            HelpPic.LEVEL_EDGE -> {
                // Phone's edge along the top of a picture frame
                d.rotate(-3f, p(0.5f, 0.55f)) {
                    drawRect(Object, topLeft = p(0.22f, 0.36f), size = Size(w * 0.56f, h * 0.5f))
                    drawRect(Color.White.copy(alpha = 0.35f), topLeft = p(0.26f, 0.42f), size = Size(w * 0.48f, h * 0.38f), style = Stroke(u * 0.6f))
                }
                phone(p(0.5f, 0.28f), hU = 44f, deg = 87f)
                pill("3.0°", p(0.5f, 0.1f), strong = true)
            }
            HelpPic.COMPASS_FLAT, HelpPic.COMPASS_LOCK, HelpPic.COMPASS_FOLLOW -> {
                val o = p(0.42f, 0.5f)
                val r = h * 0.36f
                d.drawCircle(Color(0xFF0E1614), r, o)
                d.drawCircle(Color.White.copy(alpha = 0.3f), r, o, style = Stroke(u * 0.7f))
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0)
                    val v = Offset(kotlin.math.sin(a).toFloat(), -kotlin.math.cos(a).toFloat())
                    d.drawLine(Color.White.copy(alpha = 0.4f), o + v * (r * 0.85f), o + v * r, u * 0.6f)
                }
                label("N", o + Offset(0f, -r * 0.65f), Color(0xFFFF5A5A), 5f)
                val needle = if (pic == HelpPic.COMPASS_FOLLOW) 20f else 0f
                d.rotate(needle, o) {
                    drawLine(HudTeal, o, o + Offset(0f, -r * 0.8f), u * 1.2f, cap = StrokeCap.Round)
                }
                when (pic) {
                    HelpPic.COMPASS_FLAT -> {
                        // Keep away from magnets
                        val m = p(0.84f, 0.5f)
                        d.drawArc(Color(0xFFFF5A5A), 0f, 180f, false, topLeft = m - Offset(u * 7f, u * 7f), size = Size(u * 14f, u * 14f), style = Stroke(u * 2.4f))
                        d.drawLine(Color.White, m + Offset(-u * 10f, -u * 8f), m + Offset(u * 10f, u * 14f), u * 1f)
                    }
                    HelpPic.COMPASS_LOCK -> {
                        d.drawLine(HudAmber, o, o + Offset(0f, -r), u * 0.9f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 2f, u * 1.4f)))
                        pill("🔒 45°", p(0.84f, 0.5f), strong = true); tap(p(0.86f, 0.52f))
                    }
                    else -> {
                        d.drawLine(HudAmber, o, o + Offset(0f, -r), u * 0.9f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 2f, u * 1.4f)))
                        tiltArrow(o + Offset(r * 0.2f, -r * 0.3f))
                        pill("✓ 45°", p(0.84f, 0.5f), strong = true)
                    }
                }
            }
        }
    }
}
