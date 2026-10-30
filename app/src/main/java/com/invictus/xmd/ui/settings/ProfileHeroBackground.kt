package com.invictus.xmd.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private data class HeroDot(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val alpha: Float,
    val phase: Float,
    val glow: Boolean = false,
)

// Fixed positions (fractions of the card) so the sky looks the same every time.
private val HeroDots = listOf(
    HeroDot(0.13f, 0.76f, 2.6f, 0.95f, 0.00f, glow = true),
    HeroDot(0.82f, 0.56f, 2.4f, 0.95f, 0.35f, glow = true),
    HeroDot(0.66f, 0.18f, 2.0f, 0.80f, 0.60f),
    HeroDot(0.39f, 0.30f, 1.8f, 0.70f, 0.15f),
    HeroDot(0.46f, 0.38f, 1.6f, 0.65f, 0.80f),
    HeroDot(0.55f, 0.46f, 1.5f, 0.55f, 0.45f),
    HeroDot(0.20f, 0.88f, 1.7f, 0.70f, 0.25f),
    HeroDot(0.65f, 0.86f, 1.6f, 0.65f, 0.70f),
    HeroDot(0.96f, 0.82f, 2.0f, 0.85f, 0.55f),
    HeroDot(0.74f, 0.10f, 1.5f, 0.45f, 0.90f),
    HeroDot(0.30f, 0.14f, 1.3f, 0.35f, 0.40f),
    HeroDot(0.07f, 0.40f, 1.3f, 0.35f, 0.05f),
)

/**
 * Profile hero background, drawn entirely in code: a dark sky, layered
 * translucent waves, a glowing planet on the right, one bright curve crossing
 * it and twinkling dots. All colours come from the current theme's
 * colour scheme, so it follows every theme (Aurora gives the deep-blue look);
 * light themes get a dark, tinted version so white text stays readable.
 * Clips itself to [cornerRadius].
 */
@Composable
fun Modifier.profileHeroBackground(cornerRadius: Dp = 28.dp): Modifier {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.luminance() < 0.5f
    val base = if (isDark) lerp(cs.background, cs.primary, 0.16f) else lerp(cs.primary, Color.Black, 0.62f)
    val accent = if (isDark) cs.primary else lerp(cs.primary, Color.White, 0.30f)
    val bright = lerp(accent, Color.White, 0.28f)
    val deep = lerp(base, Color.Black, 0.40f)

    val twinkle = rememberInfiniteTransition(label = "heroTwinkle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "heroTwinkleValue",
    )

    return this
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind {
            val w = size.width
            val h = size.height

            // Sky: deep corner top-left fading to the theme tint, plus a soft glow.
            drawRect(
                Brush.linearGradient(
                    colors = listOf(deep, base),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                ),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.10f, h * 0.10f),
                    radius = w * 0.55f,
                ),
            )

            // Wave A: left ribbon sweeping down.
            val waveATop = Path().apply {
                moveTo(0f, h * 0.40f)
                cubicTo(w * 0.20f, h * 0.44f, w * 0.30f, h * 0.82f, w * 0.56f, h * 0.94f)
            }
            val waveAFill = Path().apply {
                addPath(waveATop)
                lineTo(w * 0.56f, h)
                lineTo(0f, h)
                close()
            }
            drawPath(waveAFill, accent.copy(alpha = 0.17f))
            drawPath(waveATop, accent.copy(alpha = 0.38f), style = Stroke(1.dp.toPx()))

            // Wave B: wide hill across the middle.
            val waveBTop = Path().apply {
                moveTo(w * 0.10f, h)
                cubicTo(w * 0.22f, h * 0.38f, w * 0.52f, h * 0.30f, w * 0.80f, h * 0.80f)
            }
            val waveBFill = Path().apply {
                addPath(waveBTop)
                lineTo(w * 0.80f, h)
                close()
            }
            drawPath(waveBFill, accent.copy(alpha = 0.11f))
            drawPath(waveBTop, accent.copy(alpha = 0.22f), style = Stroke(1.dp.toPx()))

            // Wave C: soft hill behind the planet.
            val waveC = Path().apply {
                moveTo(w * 0.56f, h)
                cubicTo(w * 0.66f, h * 0.56f, w * 0.80f, h * 0.36f, w * 0.96f, h * 0.34f)
                lineTo(w * 0.96f, h)
                close()
            }
            drawPath(waveC, accent.copy(alpha = 0.10f))

            // Planet: big circle on the right, lit from the top-left.
            val r = h * 1.30f
            val c = Offset(w * 0.93f, h * 1.0f)
            drawCircle(
                brush = Brush.radialGradient(
                    0.80f to Color.Transparent,
                    0.93f to accent.copy(alpha = 0.30f),
                    1f to Color.Transparent,
                    center = c,
                    radius = r * 1.25f,
                ),
                radius = r * 1.25f,
                center = c,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to bright.copy(alpha = 0.95f),
                    0.45f to accent.copy(alpha = 0.60f),
                    1f to base.copy(alpha = 0f),
                    center = Offset(c.x - r * 0.45f, c.y - r * 0.55f),
                    radius = r * 1.35f,
                ),
                radius = r,
                center = c,
            )
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(bright.copy(alpha = 0.9f), Color.Transparent),
                    start = Offset(c.x - r, c.y - r),
                    end = Offset(c.x, c.y),
                ),
                radius = r,
                center = c,
                style = Stroke(1.6.dp.toPx()),
            )

            // Glow curve crossing the whole card.
            val line = Path().apply {
                moveTo(-w * 0.02f, h * 0.93f)
                cubicTo(w * 0.35f, h * 1.02f, w * 0.68f, h * 0.72f, w * 1.02f, h * 0.14f)
            }
            drawPath(
                line,
                Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, bright.copy(alpha = 0.22f), bright.copy(alpha = 0.30f)),
                    startX = 0f,
                    endX = w,
                ),
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                line,
                Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, bright.copy(alpha = 0.9f), bright),
                    startX = 0f,
                    endX = w,
                ),
                style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round),
            )

            // Twinkling dots.
            val t = twinkle.value
            HeroDots.forEach { dot ->
                val pulse = 0.65f + 0.35f * sin(2.0 * PI * (t + dot.phase)).toFloat()
                val a = (dot.alpha * pulse).coerceIn(0f, 1f)
                val center = Offset(w * dot.x, h * dot.y)
                val rad = dot.radiusDp.dp.toPx()
                if (dot.glow) {
                    drawCircle(bright.copy(alpha = a * 0.28f), radius = rad * 3.2f, center = center)
                }
                drawCircle(bright.copy(alpha = a), radius = rad, center = center)
            }
        }
}
