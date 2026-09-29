package com.jhani.measurear.presentation

import com.jhani.measurear.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        Text(stringResource(mode.titleRes()), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("  ▾", color = HudTeal, fontSize = 12.sp)
    }
}

/** Bottom sheet with an illustrated card per measuring mode. */
@Composable
fun ModePickerSheet(
    visible: Boolean,
    current: MeasureMode,
    onSelect: (MeasureMode) -> Unit,
    onDismiss: () -> Unit,
    /** Current calibration factor (1 = not calibrated). */
    scale: Float = 1f,
    onCalibrate: () -> Unit = {},
    language: AppLanguage = AppLanguage.SYSTEM,
    onLanguage: () -> Unit = {}
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
                    .heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.88f)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
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
                    stringResource(R.string.picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                MeasureMode.values().filter { it.inPicker }.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { mode ->
                            ModeCard(mode, selected = mode == current, onClick = { onSelect(mode) }, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // Accuracy: calibrate against a card
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardColor)
                        .border(1.dp, HudTeal.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onCalibrate)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎯", fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.calib_button), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (scale == 1f) stringResource(R.string.calib_button_sub)
                            else stringResource(R.string.calib_button_done, "%+.1f%%".format((scale - 1f) * 100)),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                    Text("›", color = HudTeal, fontSize = 22.sp)
                }
                Spacer(Modifier.height(10.dp))
                // Language
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardColor)
                        .clickable(onClick = onLanguage)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌐", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.language), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.weight(1f))
                    Text(
                        if (language == AppLanguage.SYSTEM) stringResource(R.string.language_system) else language.nativeName,
                        color = HudTeal, fontSize = 14.sp
                    )
                    Text("  ›", color = HudTeal, fontSize = 20.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(stringResource(R.string.built_with))
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFF5A7A)))
                        append("♥")
                        pop()
                        append(stringResource(R.string.built_by))
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
        ModeIllustration(mode, Modifier.fillMaxWidth().aspectRatio(1.6f))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(mode.titleRes()), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(
            stringResource(mode.howToRes()),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            modifier = Modifier.height(34.dp)
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
            MeasureMode.HANG -> {
                // Three frames hung level, with nail marks and a level guide
                line(p(0.06f, 0.2f), p(0.94f, 0.2f), dashed = true, color = HudTeal.copy(alpha = 0.6f))
                listOf(0.1f, 0.4f, 0.7f).forEach { x ->
                    drawRect(HudTeal.copy(alpha = 0.22f), p(x, 0.32f), Size(w * 0.2f, h * 0.44f))
                    drawRect(HudTeal, p(x, 0.32f), Size(w * 0.2f, h * 0.44f), style = Stroke(stroke))
                    val n = p(x + 0.1f, 0.2f)
                    val s = w * 0.03f
                    drawLine(Color.White, Offset(n.x - s, n.y - s), Offset(n.x + s, n.y + s), stroke)
                    drawLine(Color.White, Offset(n.x - s, n.y + s), Offset(n.x + s, n.y - s), stroke)
                }
            }
            MeasureMode.FIT -> {
                // A sofa-sized box standing on the floor, with a check mark
                line(p(0.05f, 0.86f), p(0.95f, 0.86f), color = floor)
                val (a, b, c, d) = listOf(p(0.14f, 0.74f), p(0.62f, 0.8f), p(0.86f, 0.68f), p(0.4f, 0.62f))
                val up = Offset(0f, -h * 0.26f)
                poly(listOf(a + up, b + up, c + up, d + up))
                poly(listOf(a, b, b + up, a + up))
                poly(listOf(b, c, c + up, b + up))
                line(p(0.62f, 0.2f), p(0.7f, 0.28f), color = Color.White)
                line(p(0.7f, 0.28f), p(0.86f, 0.1f), color = Color.White)
            }
            MeasureMode.CALIBRATE -> {
                // A card with its long edge measured
                drawRoundRect(HudTeal.copy(alpha = 0.22f), p(0.18f, 0.3f), Size(w * 0.64f, h * 0.4f),
                    androidx.compose.ui.geometry.CornerRadius(stroke * 3))
                drawRoundRect(HudTeal, p(0.18f, 0.3f), Size(w * 0.64f, h * 0.4f),
                    androidx.compose.ui.geometry.CornerRadius(stroke * 3), style = Stroke(stroke))
                line(p(0.18f, 0.82f), p(0.82f, 0.82f))
                dots(0.18f to 0.82f, 0.82f to 0.82f)
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
        draftCount == 0 && result != null && !result.isLive && result.values.isNotEmpty() -> LocalContext.current.resultLabel(result.values.first().label)
        draftCount == 0 -> stringResource(mode.howToRes())
        needed != null -> stringResource(R.string.step_point_of, draftCount + 1, needed)
        draftCount < mode.minPoints -> {
            val more = mode.minPoints - draftCount
            stringResource(R.string.step_more, draftCount + 1, more)
        }
        mode == MeasureMode.AREA -> stringResource(R.string.step_area, draftCount)
        else -> stringResource(R.string.step_points, draftCount)
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
                        "${LocalContext.current.resultLabel(v.label)} ${formatValue(v, false, unit)}",
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
        stringResource(R.string.ground_found, com.jhani.measurear.measurement.formatLength(phoneHeight, unit))
    } else {
        stringResource(R.string.phone_height_set, com.jhani.measurear.measurement.formatLength(phoneHeight, unit))
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
        title = { Text(stringResource(R.string.phone_height), color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    stringResource(R.string.phone_height_help),
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
                stringResource(R.string.done),
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

/**
 * After measuring a reference object in Calibrate mode: pick what was measured, see the
 * correction, and apply it.
 */
@Composable
fun CalibrationDialog(
    measuredMeters: Float,
    onSurface: Boolean,
    unit: MeasureUnit,
    onApply: (Float) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    var reference by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(com.jhani.measurear.measurement.Calibration.Reference.CARD)
    }
    val factor = com.jhani.measurear.measurement.Calibration.factorFor(measuredMeters, reference.meters)
    val fmt = { m: Float -> com.jhani.measurear.measurement.formatLength(m, unit) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1614),
        title = { Text(stringResource(R.string.calibration), color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(stringResource(R.string.calib_what), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                com.jhani.measurear.measurement.Calibration.Reference.values().forEach { ref ->
                    val selected = ref == reference
                    Text(
                        "${stringResource(ref.labelRes())} · ${fmt(ref.meters)}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) HudTealDark else CardColor)
                            .border(1.dp, if (selected) HudTeal else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { reference = ref }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.calib_measured, fmt(measuredMeters), fmt(reference.meters)), color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        !onSurface -> stringResource(R.string.calib_need_surface)
                        factor == null -> stringResource(R.string.calib_too_far)
                        else -> stringResource(R.string.calib_will_correct, "%+.1f%%".format((factor - 1f) * 100))
                    },
                    color = if (!onSurface || factor == null) HudAmber else HudTeal,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            val ok = onSurface && factor != null
            Text(
                if (ok) stringResource(R.string.apply) else stringResource(R.string.try_again),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(HudTeal)
                    .clickable { if (ok) onApply(factor!!) else onRetry() }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        },
        dismissButton = {
            Text(
                stringResource(R.string.cancel),
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    )
}

/**
 * Live precision of the next point: "± 1.8 cm" with a 3-bar signal, teal on a detected
 * surface up close, fewer bars farther away, amber for estimates.
 */
@Composable
fun AccuracyMeter(error: Float, onSurface: Boolean, unit: MeasureUnit) {
    val bars = when {
        !onSurface -> 1
        error <= 0.02f -> 3
        error <= 0.04f -> 2
        else -> 1
    }
    val color = if (onSurface) HudTeal else HudAmber
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "± " + com.jhani.measurear.measurement.formatLength(error, unit),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(6.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..3).forEach { i ->
                Box(
                    Modifier
                        .size(width = 4.dp, height = (4 + 4 * i).dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (i <= bars) color else Color.White.copy(alpha = 0.25f))
                )
            }
        }
    }
}

/** Will it fit?: size of the box under the result, with rotate buttons for a placed box. */
@Composable
fun FitControls(spec: com.jhani.measurear.measurement.BoxSpec, unit: MeasureUnit, onSize: () -> Unit, onRotate: (Float) -> Unit) {
    val fmt = { m: Float -> com.jhani.measurear.measurement.formatLength(m, unit) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FitButton("⟲") { onRotate(-15f) }
        Text(
            "${LocalContext.current.boxName(spec)} · ${fmt(spec.width)} × ${fmt(spec.depth)} × ${fmt(spec.height)}  ▾",
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, HudTeal, RoundedCornerShape(16.dp))
                .clickable(onClick = onSize)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        FitButton("⟳") { onRotate(15f) }
    }
}

@Composable
private fun FitButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, HudTeal.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = HudTeal, fontSize = 18.sp) }
}

/** Pick a furniture preset or type a custom W × D × H (in the current unit). */
@Composable
fun FitSizeDialog(
    current: com.jhani.measurear.measurement.BoxSpec,
    unit: MeasureUnit,
    onPick: (com.jhani.measurear.measurement.BoxSpec) -> Unit,
    onDismiss: () -> Unit
) {
    val toUnit = if (unit == MeasureUnit.METRIC) 100f else 39.37008f
    val unitLabel = if (unit == MeasureUnit.METRIC) "cm" else "in"
    fun show(m: Float) = if (unit == MeasureUnit.METRIC) "%.0f".format(m * toUnit) else "%.1f".format(m * toUnit)
    var w by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(show(current.width)) }
    var d by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(show(current.depth)) }
    var ht by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(show(current.height)) }
    val fmt = { m: Float -> com.jhani.measurear.measurement.formatLength(m, unit) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1614),
        title = { Text(stringResource(R.string.fit_title), color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                com.jhani.measurear.measurement.BoxSpec.PRESETS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        row.forEach { preset ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (preset == current) HudTealDark else CardColor)
                                    .border(1.dp, if (preset == current) HudTeal else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { onPick(preset) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(LocalContext.current.boxName(preset), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${fmt(preset.width)} × ${fmt(preset.depth)} × ${fmt(preset.height)}",
                                    color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.fit_own_size, unitLabel), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Triple(stringResource(R.string.label_width), w) { v: String -> w = v }, Triple(stringResource(R.string.label_depth), d) { v: String -> d = v }, Triple(stringResource(R.string.label_height), ht) { v: String -> ht = v })
                        .forEach { (label, value, set) ->
                            androidx.compose.material3.OutlinedTextField(
                                value = value,
                                onValueChange = { set(it.filter { c -> c.isDigit() || c == '.' }.take(6)) },
                                label = { Text(label, fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                ),
                                modifier = Modifier.weight(1f),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = HudTeal,
                                    focusedLabelColor = HudTeal
                                )
                            )
                        }
                }
            }
        },
        confirmButton = {
            val custom = listOf(w, d, ht).map { it.toFloatOrNull()?.div(toUnit) }
            val valid = custom.all { it != null && it in 0.01f..10f }
            Text(
                stringResource(R.string.fit_use_mine),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (valid) HudTeal else HudTeal.copy(alpha = 0.3f))
                    .clickable(enabled = valid) { onPick(com.jhani.measurear.measurement.BoxSpec(custom[0]!!, custom[1]!!, custom[2]!!)) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        },
        dismissButton = {
            Text(
                stringResource(R.string.close),
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    )
}

/** Hang pictures: the arrangement summary; tap to change it. */
@Composable
fun HangControls(spec: com.jhani.measurear.measurement.HangSpec, unit: MeasureUnit, onEdit: () -> Unit) {
    val fmt = { m: Float -> com.jhani.measurear.measurement.formatLength(m, unit) }
    Text(
        pluralStringResource(R.plurals.hang_summary, spec.count, spec.count, fmt(spec.width), fmt(spec.height), fmt(spec.gap)) + "  ▾",
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, HudTeal, RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
    )
}

/** Number of frames, their size, the gap and the hook drop (in the current unit). */
@Composable
fun HangDialog(
    current: com.jhani.measurear.measurement.HangSpec,
    unit: MeasureUnit,
    onApply: (com.jhani.measurear.measurement.HangSpec) -> Unit,
    onDismiss: () -> Unit
) {
    val toUnit = if (unit == MeasureUnit.METRIC) 100f else 39.37008f
    val unitLabel = if (unit == MeasureUnit.METRIC) "cm" else "in"
    fun show(m: Float) = if (unit == MeasureUnit.METRIC) "%.0f".format(m * toUnit) else "%.1f".format(m * toUnit)
    var count by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(current.count) }
    val fields = listOf(stringResource(R.string.hang_frame_width), stringResource(R.string.hang_frame_height), stringResource(R.string.hang_gap), stringResource(R.string.hang_hook))
    val values = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateListOf(show(current.width), show(current.height), show(current.gap), show(current.hookDrop))
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1614),
        title = { Text(stringResource(R.string.mode_hang), color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.hang_frames), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("−", color = HudTeal, fontSize = 24.sp, modifier = Modifier.clickable { count = (count - 1).coerceAtLeast(1) }.padding(horizontal = 14.dp))
                    Text("$count", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("+", color = HudTeal, fontSize = 24.sp, modifier = Modifier.clickable { count = (count + 1).coerceAtMost(8) }.padding(horizontal = 14.dp))
                }
                fields.chunked(2).forEachIndexed { row, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEachIndexed { col, label ->
                            val i = row * 2 + col
                            androidx.compose.material3.OutlinedTextField(
                                value = values[i],
                                onValueChange = { values[i] = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                                label = { Text("$label ($unitLabel)", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                ),
                                modifier = Modifier.weight(1f),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = HudTeal,
                                    focusedLabelColor = HudTeal
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.hang_hook_help),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            val m = values.map { it.toFloatOrNull()?.div(toUnit) }
            val valid = m.all { it != null } && m[0]!! > 0.01f && m[1]!! > 0.01f && m[2]!! >= 0f && m[3]!! >= 0f && m[3]!! < m[1]!!
            Text(
                stringResource(R.string.apply),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (valid) HudTeal else HudTeal.copy(alpha = 0.3f))
                    .clickable(enabled = valid) {
                        onApply(com.jhani.measurear.measurement.HangSpec(count, m[0]!!, m[1]!!, m[2]!!, m[3]!!))
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        },
        dismissButton = {
            Text(
                stringResource(R.string.cancel),
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    )
}

/** Choose the app language; each is written in its own script so it's always recognisable. */
@Composable
fun LanguageDialog(current: AppLanguage, onPick: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1614),
        title = { Text(stringResource(R.string.language), color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                AppLanguage.values().forEach { lang ->
                    val on = lang == current
                    Text(
                        if (lang == AppLanguage.SYSTEM) stringResource(R.string.language_system) else lang.nativeName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) HudTealDark else CardColor)
                            .border(1.dp, if (on) HudTeal else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { onPick(lang) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Text(
                stringResource(R.string.close),
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    )
}
