package com.jhani.measurear.level

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhani.measurear.presentation.BrandDeep
import com.jhani.measurear.presentation.HudAmber
import com.jhani.measurear.presentation.HudTeal
import com.jhani.measurear.presentation.HudTealDark
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val NorthColor = Color(0xFFFF5A7A)

/**
 * Compass: a dial that turns with the phone under a fixed pointer, the heading and its
 * direction, and an optional locked bearing to walk or aim along.
 */
@Composable
fun CompassScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sensor = remember { CompassSensor(context) }
    var reading by remember { mutableStateOf<CompassReading?>(null) }
    var locked by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(sensor) {
        sensor.start { reading = it }
        onDispose { sensor.stop() }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(BrandDeep),
        contentAlignment = Alignment.Center
    ) {
        if (!sensor.isAvailable) {
            Text("This phone has no compass sensor", color = Color.White.copy(alpha = 0.7f))
            return@Box
        }
        val heading = reading?.heading ?: 0f

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 28.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${heading.roundToInt() % 360}°",
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    CompassMath.cardinal(heading),
                    color = HudTeal,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text("Magnetic north", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }

            CompassDial(heading = heading, locked = locked, modifier = Modifier.size(300.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                locked?.let { target ->
                    val turn = CompassMath.delta(heading, target)
                    Text(
                        when {
                            abs(turn) < 2f -> "On bearing ${target.roundToInt()}°"
                            turn > 0 -> "Turn ${turn.roundToInt()}° right"
                            else -> "Turn ${(-turn).roundToInt()}° left"
                        },
                        color = if (abs(turn) < 2f) HudTeal else Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    if (locked == null) "Lock bearing" else "Unlock",
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (locked == null) HudTeal else HudTealDark)
                        .border(1.dp, HudTeal, RoundedCornerShape(24.dp))
                        .clickable { locked = if (locked == null) heading else null }
                        .padding(horizontal = 26.dp, vertical = 11.dp),
                    color = if (locked == null) Color.Black else HudTeal,
                    fontWeight = FontWeight.SemiBold
                )
                if (reading?.needsCalibration == true) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Compass needs calibration — move the phone in a figure-8",
                        color = HudAmber,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}

/** The rotating dial: ticks every 5°, labels every 30°, N in pink; fixed pointer on top. */
@Composable
private fun CompassDial(heading: Float, locked: Float?, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(HudTealDark.copy(alpha = 0.5f), r, c)
        drawCircle(HudTeal.copy(alpha = 0.35f), r, c, style = Stroke(1.5.dp.toPx()))

        rotate(-heading, c) {
            for (deg in 0 until 360 step 5) {
                val major = deg % 30 == 0
                val len = if (major) 16.dp.toPx() else 8.dp.toPx()
                val a = Math.toRadians((deg - 90).toDouble())
                val outer = Offset(c.x + (r - 6.dp.toPx()) * cos(a).toFloat(), c.y + (r - 6.dp.toPx()) * sin(a).toFloat())
                val inner = Offset(c.x + (r - 6.dp.toPx() - len) * cos(a).toFloat(), c.y + (r - 6.dp.toPx() - len) * sin(a).toFloat())
                drawLine(
                    if (deg == 0) NorthColor else Color.White.copy(alpha = if (major) 0.9f else 0.4f),
                    inner, outer, strokeWidth = (if (major) 2.5f else 1.2f).dp.toPx(), cap = StrokeCap.Round
                )
                if (major) {
                    val label = when (deg) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "$deg" }
                    val cardinal = deg % 90 == 0
                    val layout = textMeasurer.measure(
                        label,
                        TextStyle(
                            color = if (deg == 0) NorthColor else if (cardinal) Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = if (cardinal) 20.sp else 12.sp,
                            fontWeight = if (cardinal) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    val lr = r - 6.dp.toPx() - len - 16.dp.toPx()
                    val p = Offset(c.x + lr * cos(a).toFloat(), c.y + lr * sin(a).toFloat())
                    // Labels stay upright relative to the dial's own north
                    rotate(deg.toFloat(), p) {
                        drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
                    }
                }
            }
            // Locked bearing marker on the dial
            locked?.let { b ->
                val a = Math.toRadians((b - 90).toDouble())
                val tip = Offset(c.x + (r - 2.dp.toPx()) * cos(a).toFloat(), c.y + (r - 2.dp.toPx()) * sin(a).toFloat())
                drawCircle(HudAmber, 6.dp.toPx(), tip)
            }
        }

        // Fixed pointer: where the phone points
        val pointer = Path().apply {
            moveTo(c.x, c.y - r - 2.dp.toPx())
            lineTo(c.x - 10.dp.toPx(), c.y - r - 18.dp.toPx())
            lineTo(c.x + 10.dp.toPx(), c.y - r - 18.dp.toPx())
            close()
        }
        drawPath(pointer, HudTeal)
        drawLine(HudTeal.copy(alpha = 0.8f), Offset(c.x, c.y - r + 8.dp.toPx()), Offset(c.x, c.y - r * 0.55f),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(HudTeal, 5.dp.toPx(), c)
        drawCircle(BrandDeep, 2.5.dp.toPx(), c)
    }
}
