package com.jhani.measurear.presentation

import com.jhani.measurear.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * First-run hint (per Google's AR onboarding guidance): a phone gently sweeping side to side
 * over a dotted surface, until ARCore finds the first surface.
 */
@Composable
fun OnboardingHint(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "onboarding")
    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(width = 200.dp, height = 140.dp)) {
            // Dotted surface in perspective
            for (row in 0 until 4) {
                val y = size.height * (0.72f + row * 0.08f)
                val spread = 0.55f + row * 0.15f
                for (col in -4..4) {
                    val x = size.width / 2f + col * (size.width / 10f) * spread
                    drawCircle(HudTeal.copy(alpha = 0.35f + row * 0.15f), radius = 2.dp.toPx(), center = Offset(x, y))
                }
            }

            // Phone sweeping above it
            val phoneW = 38.dp.toPx()
            val phoneH = 66.dp.toPx()
            val cx = size.width / 2f + sweep * size.width * 0.28f
            val top = size.height * 0.05f
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(cx - phoneW / 2f, top),
                size = Size(phoneW, phoneH),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - phoneW / 2f, top),
                size = Size(phoneW, phoneH),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(2.5.dp.toPx())
            )
            drawCircle(HudTeal, radius = 6.dp.toPx(), center = Offset(cx, top + phoneH / 2f), style = Stroke(2.dp.toPx()))
        }
        // Its instruction is shown in the coach line above the controls
    }
}
