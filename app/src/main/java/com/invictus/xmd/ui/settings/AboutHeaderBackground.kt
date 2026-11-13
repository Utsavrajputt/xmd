package com.invictus.xmd.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.sin

/**
 * About-header backdrop: deep dark base, one glowing light sweep rising to the right with faint
 * parallel hairlines and twinkling dots. Scene ported from Replay's header; [primary]/[tertiary]
 * tint it (Aurora passes its fixed blue/violet, other themes pass their own colours).
 */
@Composable
fun AboutHeaderBackground(primary: Color, tertiary: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "about_header")
    val drift by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val glowPulse by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val twinkle by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "twinkle",
    )
    Canvas(modifier = modifier) { drawHeaderScene(primary, tertiary, drift, glowPulse, twinkle) }
}

private const val HEADER_SCENE_W = 853f
private const val HEADER_SCENE_H = 1115f

// Center line of the sweep, traced from the reference artwork (scene px).
private val HeaderRidge =
  listOf(
    -40f to 775f, 80f to 739f, 200f to 702f, 320f to 650f, 440f to 567f,
    560f to 458f, 680f to 334f, 800f to 229f, 920f to 160f,
  )

// x, y, radius (scene px)
private val HeaderDots =
  listOf(
    Triple(587f, 238f, 3.2f), Triple(790f, 397f, 3.6f), Triple(707f, 541f, 2.6f),
    Triple(533f, 628f, 2.2f), Triple(508f, 628f, 1.5f), Triple(172f, 830f, 1.8f),
  )

/** Ridge points pushed `off0` px vertically at the left end and `off1` px at the right end. */
private fun headerPoints(off0: Float, off1: Float, sway: Float): List<Offset> =
  HeaderRidge.map { (x, y) -> Offset(x, y + sway + off0 + (off1 - off0) * ((x + 40f) / 960f)) }

/** Smooth curve through [points]; the path's current point must already be `points.first()`. */
private fun Path.splineThrough(points: List<Offset>) {
  for (i in 0 until points.lastIndex) {
    val p0 = points[maxOf(i - 1, 0)]
    val p1 = points[i]
    val p2 = points[i + 1]
    val p3 = points[minOf(i + 2, points.lastIndex)]
    cubicTo(
      p1.x + (p2.x - p0.x) / 6f,
      p1.y + (p2.y - p0.y) / 6f,
      p2.x - (p3.x - p1.x) / 6f,
      p2.y - (p3.y - p1.y) / 6f,
      p2.x,
      p2.y,
    )
  }
}

private fun headerLine(points: List<Offset>) =
  Path().apply {
    moveTo(points.first().x, points.first().y)
    splineThrough(points)
  }

private fun headerBand(upper: List<Offset>, lower: List<Offset>) =
  Path().apply {
    moveTo(upper.first().x, upper.first().y)
    splineThrough(upper)
    lineTo(lower.last().x, lower.last().y)
    splineThrough(lower.asReversed())
    close()
  }

/**
 * The scene is designed on an 853x1115 canvas and scaled to cover the card (center-cropped),
 * so the sweep keeps its curve no matter how tall the header becomes.
 */
private fun DrawScope.drawHeaderScene(
  primary: Color,
  tertiary: Color,
  drift: Float,
  glowPulse: Float,
  twinkle: Float,
) {
  drawRect(color = lerp(Color.Black, primary, 0.13f), size = size)
  val fit = maxOf(size.width / HEADER_SCENE_W, size.height / HEADER_SCENE_H)
  val dx = (size.width - HEADER_SCENE_W * fit) / 2f
  val dy = (size.height - HEADER_SCENE_H * fit) / 2f
  translate(left = dx, top = dy) {
    scale(scaleX = fit, scaleY = fit, pivot = Offset.Zero) {
      drawHeaderSweep(primary, tertiary, drift, glowPulse, twinkle)
    }
  }
}

private fun DrawScope.drawHeaderSweep(
  primary: Color,
  tertiary: Color,
  drift: Float,
  glowPulse: Float,
  twinkle: Float,
) {
  val core = lerp(primary, Color.White, 0.40f)
  val violet = lerp(primary, tertiary, 0.35f)

  // Blue haze on the left, like the artwork's soft background glow.
  drawCircle(
    brush =
      Brush.radialGradient(
        colors = listOf(primary.copy(alpha = 0.34f), Color.Transparent),
        center = Offset(150f, 300f),
        radius = 480f,
      ),
    radius = 480f,
    center = Offset(150f, 300f),
  )

  val sway = 14f * (drift - 0.5f)
  fun line(off0: Float, off1: Float) = headerPoints(off0, off1, sway)

  // Alpha envelopes along x: soft entry from the left, strongest on the right.
  fun env(color: Color, alpha: Float) =
    Brush.horizontalGradient(
      0f to Color.Transparent,
      0.25f to color.copy(alpha = alpha * 0.25f),
      0.60f to color.copy(alpha = alpha),
      1f to color.copy(alpha = alpha * 0.75f),
      startX = 0f,
      endX = HEADER_SCENE_W,
    )
  fun ramp(color: Color, alpha: Float) =
    Brush.horizontalGradient(
      0f to Color.Transparent,
      0.18f to color.copy(alpha = alpha * 0.12f),
      0.42f to color.copy(alpha = alpha * 0.45f),
      0.62f to color.copy(alpha = alpha),
      1f to color.copy(alpha = alpha),
      startX = 0f,
      endX = HEADER_SCENE_W,
    )
  val peak = 0.35f + 0.60f * drift
  fun shimmer(color: Color, alpha: Float) =
    Brush.horizontalGradient(
      0f to Color.Transparent,
      (peak - 0.25f) to Color.Transparent,
      peak to color.copy(alpha = alpha),
      minOf(peak + 0.25f, 0.99f) to Color.Transparent,
      1f to Color.Transparent,
      startX = 0f,
      endX = HEADER_SCENE_W,
    )

  // Wide halo: many faint layers so the glow falls off smoothly instead of in steps.
  val breathe = 0.92f + 0.16f * glowPulse
  for (k in 8 downTo 1) {
    val f = k / 8f * breathe
    drawPath(headerBand(line(-60f * f, -150f * f), line(60f * f, 150f * f)), env(primary, 0.085f))
  }
  // Brighter glow hugging the core, a little heavier on the lower side.
  for (k in 5 downTo 1) {
    val f = k / 5f * breathe
    drawPath(headerBand(line(-12f * f, -20f * f), line(26f * f, 64f * f)), env(violet, 0.20f))
  }

  // The single main line: soft body, crisp core, and a light that travels along it.
  drawPath(headerLine(line(0f, 0f)), ramp(core, 0.70f), style = Stroke(width = 10f, cap = StrokeCap.Round))
  drawPath(headerLine(line(0f, 0f)), ramp(core, 0.80f + 0.20f * glowPulse), style = Stroke(width = 3.6f, cap = StrokeCap.Round))
  drawPath(headerLine(line(0f, 0f)), shimmer(lerp(core, Color.White, 0.5f), 0.55f), style = Stroke(width = 2.4f, cap = StrokeCap.Round))

  // Everything else just fades out: thin, faint lines that open up toward the right.
  drawPath(headerLine(line(-34f, -62f)), env(core, 0.50f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
  drawPath(headerLine(line(30f, 80f)), env(violet, 0.45f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
  drawPath(headerLine(line(60f, 177f)), env(primary, 0.38f), style = Stroke(width = 2f, cap = StrokeCap.Round))
  drawPath(headerLine(line(-80f, -120f)), env(primary, 0.20f), style = Stroke(width = 1.8f, cap = StrokeCap.Round))

  HeaderDots.forEachIndexed { index, (x, y, r) ->
    val phase = (twinkle + index * 0.23f) % 1f
    val a = (0.35f + 0.65f * sin(phase * Math.PI).toFloat()).coerceIn(0f, 1f)
    drawCircle(color = core.copy(alpha = 0.20f * a), radius = r * 3.4f, center = Offset(x, y))
    drawCircle(color = core.copy(alpha = a), radius = r, center = Offset(x, y))
  }
}

