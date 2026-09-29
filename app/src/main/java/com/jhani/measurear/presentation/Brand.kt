package com.jhani.measurear.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand colors, shared with the launcher icon (res/drawable/ic_launcher_*.xml). */
val BrandDeep = Color(0xFF050F0E)
val BrandGlow = Color(0xFF12433E)

/** Full-screen deep-teal backdrop with a soft glow behind the content. */
@Composable
fun BrandBackdrop(modifier: Modifier = Modifier, credit: Boolean = true, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(BrandDeep)
            .background(Brush.radialGradient(listOf(BrandGlow, BrandDeep), radius = 1400f)),
        contentAlignment = Alignment.Center
    ) {
        content()
        if (credit) MadeWithLove(Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The logo (crosshair above a ruler), drawn in vector like the launcher icon. [sweep] adds a
 * rotating teal arc around the crosshair for loading states (0..360, degrees).
 */
@Composable
fun BrandLogo(modifier: Modifier = Modifier, sweep: Float? = null) {
    Canvas(modifier) {
        // Same 108-unit geometry as the launcher icon, cropped to its central 60 units
        val u = size.minDimension / 60f
        fun p(x: Float, y: Float) = Offset((x - 24f) * u, (y - 24f) * u)
        val teal = HudTeal
        val cross = p(54f, 45f)

        sweep?.let {
            drawCircle(teal.copy(alpha = 0.12f), radius = 17f * u, center = cross, style = Stroke(1.5f * u))
            drawArc(
                teal, startAngle = it, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(cross.x - 17f * u, cross.y - 17f * u), size = Size(34f * u, 34f * u),
                style = Stroke(1.8f * u, cap = StrokeCap.Round)
            )
        }
        drawCircle(teal, radius = 11f * u, center = cross, style = Stroke(3.5f * u))
        listOf(p(54f, 28f) to p(54f, 32f), p(54f, 58f) to p(54f, 61f), p(35f, 45f) to p(39f, 45f), p(69f, 45f) to p(73f, 45f))
            .forEach { (a, b) -> drawLine(teal, a, b, 3f * u, StrokeCap.Round) }
        drawCircle(Color.White, radius = 3f * u, center = cross)

        drawLine(Color.White, p(36f, 71f), p(72f, 71f), 3f * u, StrokeCap.Round)
        listOf(44f to 4f, 49f to 2.5f, 54f to 5f, 59f to 2.5f, 64f to 4f).forEach { (x, h) ->
            drawLine(Color.White.copy(alpha = 0.85f), p(x, 71f), p(x, 71f - h), 1.8f * u, StrokeCap.Round)
        }
        listOf(36f, 72f).forEach { x ->
            drawCircle(Color.White, radius = 5.4f * u, center = p(x, 71f))
            drawCircle(teal, radius = 3.6f * u, center = p(x, 71f))
        }
    }
}

@Composable
private fun Wordmark() {
    Text(
        buildWordmark(),
        fontSize = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        color = Color.White
    )
}

private fun buildWordmark() = androidx.compose.ui.text.buildAnnotatedString {
    append("Measure")
    pushStyle(androidx.compose.ui.text.SpanStyle(color = HudTeal))
    append("AR")
    pop()
}

/** "Built with ♥ by Jhani", pinned to the bottom of branded screens. */
@Composable
fun MadeWithLove(modifier: Modifier = Modifier) {
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
        modifier = modifier.navigationBarsPadding().padding(bottom = 28.dp),
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
        color = Color.White.copy(alpha = 0.55f)
    )
}

/** Branded loading screen: animated logo, wordmark, tagline and a status line. */
@Composable
fun BrandedLoader(status: String) {
    val transition = rememberInfiniteTransition(label = "loader")
    val sweep by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sweep"
    )
    val dots by transition.animateFloat(
        0f, 3.99f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "dots"
    )
    BrandBackdrop {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLogo(Modifier.size(150.dp), sweep = sweep - 90f)
            Spacer(Modifier.height(20.dp))
            Wordmark()
            Spacer(Modifier.height(6.dp))
            Text("Measure anything with your camera", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(48.dp))
            Text(
                status + ".".repeat(dots.toInt()),
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
                color = HudTeal.copy(alpha = 0.9f),
                modifier = Modifier.widthIn(min = 220.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Branded full-screen message (permission, unsupported device, errors): logo, title,
 * explanation and an optional primary action.
 */
@Composable
fun BrandStateScreen(
    title: String,
    description: String,
    buttonText: String?,
    onButtonClick: () -> Unit
) {
    BrandBackdrop {
        Column(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandLogo(Modifier.size(110.dp))
            Spacer(Modifier.height(28.dp))
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                description,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            if (buttonText != null) {
                Spacer(Modifier.height(32.dp))
                Text(
                    buttonText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(HudTeal)
                        .clickable(onClick = onButtonClick)
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
