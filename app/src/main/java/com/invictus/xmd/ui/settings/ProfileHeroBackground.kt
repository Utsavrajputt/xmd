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

// Colours sampled from the reference artwork (Aurora hero banner).
private val HeroSkyLeft = Color(0xFF081C4F)
private val HeroSkyMid = Color(0xFF08132C)
private val HeroSkyRight = Color(0xFF091B40)
private val HeroBottomLift = Color(0xFF0B2457)
private val HeroWaveLight = Color(0xFF0E3386)
private val HeroWaveDark = Color(0xFF07256D)
private val HeroWaveEdge = Color(0xFF1B4FC0)
private val HeroPlanetInner = Color(0xFF051E51)
private val HeroRimDeep = Color(0xFF1142C3)
private val HeroRimBlue = Color(0xFF1685FD)
private val HeroCyan = Color(0xFF2ED7FE)
private val HeroLine = Color(0xFF2DB3FF)
private val HeroLineBright = Color(0xFF38D3FF)
private val HeroDotBright = Color(0xFF43D9FF)

/**
 * Profile hero background, drawn entirely in code from the reference artwork's
 * exact colours: a navy sky, two soft left waves, ONE planet on the right with a
 * bright blue-to-cyan rim, a thin glow curve across it and twinkling dots.
 * No black fade at the bottom -- it lifts to the reference's blue instead, so the
 * chip row sits on calm blue. Clips itself to [cornerRadius].
 */
@Composable
fun Modifier.profileHeroBackground(cornerRadius: Dp = 28.dp): Modifier {
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

            // Sky: left blue -> dark centre -> right navy, then a blue lift at the bottom.
            drawRect(
                Brush.horizontalGradient(
                    0f to HeroSkyLeft,
                    0.45f to HeroSkyMid,
                    1f to HeroSkyRight,
                ),
            )
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, HeroBottomLift.copy(alpha = 0.85f)),
                    startY = h * 0.45f,
                    endY = h,
                ),
            )

            // Wave A: lighter ribbon sweeping down the left.
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
            drawPath(waveAFill, HeroWaveLight.copy(alpha = 0.55f))

            // Wave B: darker hill across the middle.
            val waveBTop = Path().apply {
                moveTo(w * 0.10f, h)
                cubicTo(w * 0.22f, h * 0.38f, w * 0.52f, h * 0.30f, w * 0.80f, h * 0.80f)
            }
            val waveBFill = Path().apply {
                addPath(waveBTop)
                lineTo(w * 0.80f, h)
                close()
            }
            drawPath(waveBFill, HeroWaveDark.copy(alpha = 0.55f))

            // The planet (only one): its left rim crosses the top edge at ~77% of the width
            // and the bottom edge at ~60%. Dark inside, lit along the rim.
            val r = h * 0.95f
            val c = Offset(w * 1.02f, h * 0.78f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to HeroRimBlue.copy(alpha = 0.80f),
                    0.45f to HeroRimDeep.copy(alpha = 0.50f),
                    1f to HeroPlanetInner.copy(alpha = 0f),
                    center = Offset(c.x - r * 0.62f, c.y - r * 0.72f),
                    radius = r * 1.0f,
                ),
                radius = r,
                center = c,
            )
            drawCircle(
                // The bright rim lives in the top ~half and fades to nothing before the
                // chip row (Status Saver / Storage), so no line runs behind the chips.
                brush = Brush.verticalGradient(
                    0f to HeroCyan,
                    0.46f to HeroRimBlue.copy(alpha = 0.85f),
                    0.58f to Color.Transparent,
                    startY = 0f,
                    endY = h,
                ),
                radius = r,
                center = c,
                style = Stroke(1.6.dp.toPx()),
            )

            // Thin glow curve from the left edge up to the top-right.
            val line = Path().apply {
                moveTo(-w * 0.02f, h * 0.40f)
                cubicTo(w * 0.30f, h * 0.50f, w * 0.62f, h * 0.42f, w * 1.02f, h * 0.08f)
            }
            drawPath(
                line,
                Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, HeroLine.copy(alpha = 0.14f), HeroLineBright.copy(alpha = 0.26f)),
                    startX = 0f,
                    endX = w,
                ),
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                line,
                Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, HeroLine, HeroLineBright),
                    startX = 0f,
                    endX = w,
                ),
                style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round),
            )

            // Twinkling dots (upper part only).
            val t = twinkle.value
            HeroDots.forEach { dot ->
                val pulse = 0.65f + 0.35f * sin(2.0 * PI * (t + dot.phase)).toFloat()
                val a = (dot.alpha * pulse).coerceIn(0f, 1f)
                val center = Offset(w * dot.x, h * dot.y * 0.60f)
                val rad = dot.radiusDp.dp.toPx()
                val tint = if (dot.glow) HeroDotBright else HeroLine
                if (dot.glow) {
                    drawCircle(tint.copy(alpha = a * 0.28f), radius = rad * 3.2f, center = center)
                }
                drawCircle(tint.copy(alpha = a), radius = rad, center = center)
            }
        }
}
