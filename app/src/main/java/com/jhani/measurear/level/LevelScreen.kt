package com.jhani.measurear.level

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhani.measurear.presentation.HudTeal
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val LevelGreen = Color(0xFF00E676)
private val LevelBackground = Color(0xFF0E1414)

// Within this many degrees counts as level
private const val LEVEL_TOLERANCE_DEG = 0.5f

/**
 * Sensor-based level, like the iPhone Measure app's Level tab. Lay the phone flat for a
 * two-axis bubble, or stand it on an edge for a horizon line. Turns green (with a haptic tick)
 * within ±0.5°. Tap anywhere to use the current angle as the reference; tap again to reset.
 */
@Composable
fun LevelScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val sensor = remember { LevelSensor(context) }

    var reading by remember { mutableStateOf(LevelReading(LevelMode.FLAT, 0f, 0f)) }
    var reference by remember { mutableStateOf<LevelReading?>(null) }

    DisposableEffect(sensor) {
        sensor.start { reading = it }
        onDispose { sensor.stop() }
    }

    // Relative to the reference when one is set for the same mode
    val ref = reference?.takeIf { it.mode == reading.mode }
    val shown = if (ref != null) {
        LevelReading(reading.mode, reading.tiltX - ref.tiltX, reading.tiltY - ref.tiltY)
    } else {
        reading
    }
    val isLevel = shown.magnitude <= LEVEL_TOLERANCE_DEG

    LaunchedEffect(isLevel) {
        if (isLevel) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY
            )
        }
    }

    val accent by animateColorAsState(if (isLevel) LevelGreen else HudTeal, label = "level_accent")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LevelBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                reference = if (reference == null) reading else null
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
    ) {
        if (!sensor.isAvailable) {
            Text(
                text = "This phone has no motion sensor for the level.",
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                color = Color.White
            )
            return@Box
        }

        when (shown.mode) {
            LevelMode.FLAT -> BubbleLevel(shown, accent, isLevel)
            LevelMode.EDGE -> HorizonLevel(shown, accent, isLevel)
        }

        // Reading
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${shown.magnitude.roundToInt()}°",
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                color = Color.White
            )
            if (shown.mode == LevelMode.FLAT && !isLevel) {
                Text(
                    text = "↔ %.1f°   ↕ %.1f°".format(abs(shown.tiltX), abs(shown.tiltY)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // Header
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    isLevel -> "LEVEL"
                    shown.mode == LevelMode.FLAT -> "Lay the phone on the surface"
                    else -> "Hold the phone's edge against the surface"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = if (isLevel) 3.sp else 0.sp,
                color = accent
            )
            Text(
                text = if (ref != null) "Relative to your reference · tap to reset" else "Tap to set a reference angle",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/** Two-axis bubble for FLAT mode: fixed target rings with a bubble that drifts to the high side. */
@Composable
private fun BubbleLevel(reading: LevelReading, accent: Color, isLevel: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val c = center
        val ringRadius = size.minDimension * 0.34f
        val bubbleRadius = ringRadius * 0.34f

        // Target rings and crosshair
        drawCircle(Color.White.copy(alpha = 0.15f), radius = ringRadius, center = c, style = Stroke(1.5.dp.toPx()))
        drawCircle(accent.copy(alpha = 0.6f), radius = bubbleRadius * 1.15f, center = c, style = Stroke(2.dp.toPx()))
        drawLine(Color.White.copy(alpha = 0.15f), c - Offset(ringRadius, 0f), c + Offset(ringRadius, 0f), 1.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.15f), c - Offset(0f, ringRadius), c + Offset(0f, ringRadius), 1.dp.toPx())

        // Bubble: 10° reaches the outer ring
        val scale = (ringRadius - bubbleRadius) / 10f
        var dx = reading.tiltX * scale
        var dy = reading.tiltY * scale
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val maxDist = ringRadius - bubbleRadius
        if (dist > maxDist) {
            dx *= maxDist / dist
            dy *= maxDist / dist
        }
        val bubble = c + Offset(dx, dy)
        drawCircle(accent.copy(alpha = if (isLevel) 0.9f else 0.35f), radius = bubbleRadius, center = bubble)
        drawCircle(accent, radius = bubbleRadius, center = bubble, style = Stroke(2.5.dp.toPx()))
    }
}

/** One-axis horizon for EDGE mode: the lower half fills with the accent, tilted by the angle. */
@Composable
private fun HorizonLevel(reading: LevelReading, accent: Color, isLevel: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val diagonal = max(size.width, size.height) * 2f
        // Counter-rotate so the line stays parallel to the real horizon
        rotate(degrees = reading.tiltX, pivot = center) {
            val path = Path().apply {
                moveTo(center.x - diagonal, center.y)
                lineTo(center.x + diagonal, center.y)
                lineTo(center.x + diagonal, center.y + diagonal)
                lineTo(center.x - diagonal, center.y + diagonal)
                close()
            }
            drawPath(path, accent.copy(alpha = if (isLevel) 0.55f else 0.25f))
            drawLine(
                accent,
                Offset(center.x - diagonal, center.y),
                Offset(center.x + diagonal, center.y),
                strokeWidth = 2.dp.toPx()
            )
        }
        // Fixed reference line
        drawLine(
            Color.White.copy(alpha = 0.5f),
            Offset(0f, center.y),
            Offset(size.width, center.y),
            strokeWidth = 1.dp.toPx()
        )
    }
}
