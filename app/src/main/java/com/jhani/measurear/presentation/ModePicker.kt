package com.jhani.measurear.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhani.measurear.measurement.MeasureMode
import com.jhani.measurear.measurement.MeasureUnit
import com.jhani.measurear.measurement.ShapeResultUi
import com.jhani.measurear.measurement.formatValue

private val SheetColor = Color(0xFF0E1614)
private val CardColor = Color(0xFF16211F)

/** Top-bar pill showing the current mode; tap to open the picker. */
@Composable
fun ModeChip(mode: MeasureMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, HudTeal, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeIllustration(mode, Modifier.size(28.dp), compact = true)
        Spacer(Modifier.width(6.dp))
        Text(mode.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("  ▾", color = HudTeal, fontSize = 12.sp)
    }
}

/** Bottom sheet with an illustrated card per measuring mode. */
@Composable
fun ModePickerSheet(
    visible: Boolean,
    current: MeasureMode,
    onSelect: (MeasureMode) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(SheetColor)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "What do you want to measure?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                MeasureMode.values().toList().chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { mode ->
                            ModeCard(mode, selected = mode == current, onClick = { onSelect(mode) }, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append("Built with ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFF5A7A)))
                        append("♥")
                        pop()
                        append(" by ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold))
                        append("Jhani")
                        pop()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp, bottom = 4.dp),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ModeCard(mode: MeasureMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) HudTealDark else CardColor)
            .border(if (selected) 2.dp else 1.dp, if (selected) HudTeal else Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ModeIllustration(mode, Modifier.fillMaxWidth().aspectRatio(1.35f))
        Spacer(Modifier.height(6.dp))
        Text(mode.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(
            mode.howTo,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            modifier = Modifier.height(36.dp)
        )
    }
}

/** Line-art illustration of a mode, in the HUD's teal. */
@Composable
fun ModeIllustration(mode: MeasureMode, modifier: Modifier = Modifier, compact: Boolean = false) {
    Canvas(modifier) {
        val stroke = (if (compact) 1.6f else 2.4f).dp.toPx()
        val dot = (if (compact) 2.2f else 3.6f).dp.toPx()
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        fun line(a: Offset, b: Offset, dashed: Boolean = false, color: Color = HudTeal) = drawLine(
            color, a, b, strokeWidth = stroke, cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(stroke * 2.5f, stroke * 2f)) else null
        )
        fun dotList(pts: List<Offset>) = pts.forEach {
            drawCircle(Color.White, dot * 1.25f, it)
            drawCircle(HudTeal, dot, it)
        }
        fun dots(vararg xy: Pair<Float, Float>) = dotList(xy.map { p(it.first, it.second) })
        fun poly(pts: List<Offset>, fill: Boolean = true) {
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            if (fill) drawPath(path, HudTeal.copy(alpha = 0.22f))
            drawPath(path, HudTeal, style = Stroke(stroke))
        }
        val floor = Color.White.copy(alpha = 0.18f)

        when (mode) {
            MeasureMode.LINE -> {
                line(p(0.18f, 0.7f), p(0.82f, 0.3f)); dots(0.18f to 0.7f, 0.82f to 0.3f)
            }
            MeasureMode.HEIGHT -> {
                line(p(0.1f, 0.85f), p(0.9f, 0.85f), color = floor)
                line(p(0.5f, 0.85f), p(0.5f, 0.15f), dashed = true)
                line(p(0.4f, 0.15f), p(0.6f, 0.15f))
                dots(0.5f to 0.85f, 0.5f to 0.15f)
            }
            MeasureMode.FAR -> {
                // Distant building; sight lines from the phone to its base and top
                line(p(0.05f, 0.88f), p(0.95f, 0.88f), color = floor)
                val (l, t, r) = Triple(0.62f, 0.12f, 0.88f)
                poly(listOf(p(l, 0.88f), p(l, t), p(r, t), p(r, 0.88f)))
                listOf(0.26f, 0.42f, 0.58f, 0.74f).forEach { y ->
                    line(p(0.69f, y), p(0.72f, y), color = HudTeal.copy(alpha = 0.7f))
                    line(p(0.78f, y), p(0.81f, y), color = HudTeal.copy(alpha = 0.7f))
                }
                val eye = p(0.14f, 0.62f)
                drawRoundRect(HudTeal, Offset(eye.x - w * 0.035f, eye.y - h * 0.07f), Size(w * 0.07f, h * 0.14f),
                    androidx.compose.ui.geometry.CornerRadius(stroke), style = Stroke(stroke))
                line(eye, p(l, 0.88f), dashed = true)
                line(eye, p(l, t), dashed = true)
                dots(l to 0.88f, l to t)
            }
            MeasureMode.DISTANCE -> {
                drawRoundRect(HudTeal, p(0.1f, 0.35f), Size(w * 0.14f, h * 0.36f), androidx.compose.ui.geometry.CornerRadius(stroke * 1.5f), style = Stroke(stroke))
                line(p(0.26f, 0.53f), p(0.82f, 0.53f), dashed = true)
                line(p(0.88f, 0.2f), p(0.88f, 0.86f), color = floor)
                dots(0.82f to 0.53f)
            }
            MeasureMode.ANGLE -> {
                val v = p(0.22f, 0.78f)
                line(v, p(0.85f, 0.78f)); line(v, p(0.72f, 0.2f))
                drawArc(HudTeal, -48f, 48f, false, Offset(v.x - w * 0.22f, v.y - w * 0.22f), Size(w * 0.44f, w * 0.44f), style = Stroke(stroke))
                dotList(listOf(v, p(0.85f, 0.78f), p(0.72f, 0.2f)))
            }
            MeasureMode.PATH -> {
                val pts = listOf(p(0.1f, 0.75f), p(0.35f, 0.35f), p(0.6f, 0.65f), p(0.88f, 0.25f))
                pts.zipWithNext { a, b -> line(a, b) }; dotList(pts)
            }
            MeasureMode.RECTANGLE -> {
                poly(listOf(p(0.12f, 0.72f), p(0.62f, 0.85f), p(0.88f, 0.4f), p(0.4f, 0.27f)))
                dots(0.12f to 0.72f, 0.62f to 0.85f, 0.88f to 0.4f)
            }
            MeasureMode.CIRCLE -> {
                drawOval(HudTeal.copy(alpha = 0.22f), p(0.12f, 0.25f), Size(w * 0.76f, h * 0.5f))
                drawOval(HudTeal, p(0.12f, 0.25f), Size(w * 0.76f, h * 0.5f), style = Stroke(stroke))
                line(p(0.5f, 0.5f), p(0.88f, 0.5f), dashed = true)
                dots(0.5f to 0.5f, 0.88f to 0.5f)
            }
            MeasureMode.AREA -> {
                poly(listOf(p(0.1f, 0.6f), p(0.35f, 0.85f), p(0.9f, 0.7f), p(0.75f, 0.35f), p(0.45f, 0.45f), p(0.3f, 0.2f)))
                dots(0.1f to 0.6f, 0.35f to 0.85f, 0.9f to 0.7f, 0.75f to 0.35f, 0.45f to 0.45f, 0.3f to 0.2f)
            }
            MeasureMode.VOLUME -> {
                val (a, b, c, d) = listOf(p(0.15f, 0.62f), p(0.5f, 0.8f), p(0.85f, 0.62f), p(0.5f, 0.46f))
                val up = Offset(0f, -h * 0.36f)
                poly(listOf(a + up, b + up, c + up, d + up))
                poly(listOf(a, b, b + up, a + up))
                poly(listOf(b, c, c + up, b + up))
                dotList(listOf(a, b, c))
            }
        }
    }
}

/**
 * Result card for shape modes: the primary value large, the others as a row beneath, with a
 * step counter while the shape is still being placed.
 */
@Composable
fun ResultCard(result: ShapeResultUi?, mode: MeasureMode, draftCount: Int, unit: MeasureUnit, modifier: Modifier = Modifier) {
    val needed = mode.points
    val step = when {
        // A finished shape: name what the big number is ("HEIGHT", "AREA")
        draftCount == 0 && result != null && !result.isLive && result.values.isNotEmpty() -> result.values.first().label
        draftCount == 0 -> mode.howTo
        needed != null -> "Point ${draftCount + 1} of $needed"
        draftCount < mode.minPoints -> {
            val more = mode.minPoints - draftCount
            "Point ${draftCount + 1} — $more more to go"
        }
        mode == MeasureMode.AREA -> "$draftCount corners · tap the first corner or Done"
        else -> "$draftCount points · tap Done to finish"
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            step.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            color = HudAmber,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        val values = result?.values.orEmpty()
        val primary = values.firstOrNull()
        Text(
            primary?.let { formatValue(it, result?.isEstimate == true, unit) } ?: " ",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        if (values.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                values.drop(1).take(3).forEach { v ->
                    Text(
                        "${v.label} ${formatValue(v, false, unit)}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

/**
 * Far mode: whether the ground under the phone was found (heights and distances are then
 * measured from it), or the phone height the user set, tap to change.
 */
@Composable
fun GroundChip(detected: Boolean, phoneHeight: Float, unit: MeasureUnit, onClick: () -> Unit) {
    val text = if (detected) {
        "✓ Ground found · phone ${com.jhani.measurear.measurement.formatLength(phoneHeight, unit)} up"
    } else {
        "Phone height ${com.jhani.measurear.measurement.formatLength(phoneHeight, unit)} · tap to set"
    }
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, if (detected) HudTeal else HudAmber, RoundedCornerShape(16.dp))
            .clickable(enabled = !detected, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        color = if (detected) HudTeal else HudAmber,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

/** Sets how high the phone is held above the ground (Far mode, when no ground is detected). */
@Composable
fun PhoneHeightDialog(height: Float, unit: MeasureUnit, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1614),
        title = { Text("Phone height", color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "How high you're holding the phone above the ground. About 10 cm below your eye " +
                        "height works well. Or point at the ground nearby so the app can measure it.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    com.jhani.measurear.measurement.formatLength(height, unit),
                    color = HudTeal,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                androidx.compose.material3.Slider(
                    value = height,
                    onValueChange = { onChange((it * 100).toInt() / 100f) },
                    valueRange = 0.5f..2.2f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = HudTeal,
                        activeTrackColor = HudTeal
                    )
                )
            }
        },
        confirmButton = {
            Text(
                "Done",
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(HudTeal)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}
