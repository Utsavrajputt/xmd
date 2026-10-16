package com.invictus.xmd.ui.theme

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.invictus.xmd.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Wallpapers drawn in code (no image assets): smooth mesh-style gradients, a few soft shapes and a
 * touch of grain. A preset is stored as `preset:<id>` and rendered on demand by
 * [createWallpaperPresetBitmap]; the picker cards use the same [drawWallpaperPreset], so a card
 * always matches the real wallpaper. Everything is sized relative to the canvas.
 */
enum class WallpaperPreset(
    val id: String,
    @StringRes val labelRes: Int,
) {
    Aurora("aurora", R.string.wallpaper_preset_aurora),
    Sunset("sunset", R.string.wallpaper_preset_sunset),
    Ocean("ocean", R.string.wallpaper_preset_ocean),
    Midnight("midnight", R.string.wallpaper_preset_midnight),
    Mist("mist", R.string.wallpaper_preset_mist),
    Blossom("blossom", R.string.wallpaper_preset_blossom),
    Galaxy("galaxy", R.string.wallpaper_preset_galaxy),
    Neon("neon", R.string.wallpaper_preset_neon),
    ;

    val uri: String get() = PREFIX + id

    companion object {
        const val PREFIX = "preset:"
        fun isPresetUri(uri: String): Boolean = uri.startsWith(PREFIX, ignoreCase = true)
        fun fromUri(uri: String): WallpaperPreset? = entries.firstOrNull { uri.equals(it.uri, ignoreCase = true) }
    }
}

private const val PRESET_WIDTH_PX = 1080
private const val PRESET_HEIGHT_PX = 2340

fun createWallpaperPresetBitmap(
    preset: WallpaperPreset,
    width: Int = PRESET_WIDTH_PX,
    height: Int = PRESET_HEIGHT_PX,
): Bitmap {
    val image = ImageBitmap(width, height)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = ComposeCanvas(image),
        size = Size(width.toFloat(), height.toFloat()),
    ) {
        drawWallpaperPreset(preset)
    }
    return image.asAndroidBitmap()
}

fun DrawScope.drawWallpaperPreset(preset: WallpaperPreset) {
    when (preset) {
        WallpaperPreset.Aurora -> drawAurora()
        WallpaperPreset.Sunset -> drawSunset()
        WallpaperPreset.Ocean -> drawOcean()
        WallpaperPreset.Midnight -> drawMidnight()
        WallpaperPreset.Mist -> drawMist()
        WallpaperPreset.Blossom -> drawBlossom()
        WallpaperPreset.Galaxy -> drawGalaxy()
        WallpaperPreset.Neon -> drawNeon()
    }
}

// ── building blocks ───────────────────────────────────────────────────────

private fun c(argb: Long) = Color(argb)

private fun wave(t: Float, freq: Float, phase: Float): Float = sin((t * freq * 2f * PI + phase).toFloat())

/** Full-canvas vertical gradient with explicit stops. */
private fun DrawScope.sky(vararg stops: Pair<Float, Color>) {
    drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = size.height))
}

/** Soft radial colour blob; position and radius are fractions of the canvas width/height. */
private fun DrawScope.glow(color: Color, fx: Float, fy: Float, fr: Float, alpha: Float) {
    val center = Offset(size.width * fx, size.height * fy)
    val radius = size.width * fr
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.4f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** Wavy translucent band that fades out above and below. */
private fun DrawScope.ribbon(yc: Float, thick: Float, amp: Float, freq: Float, phase: Float, color: Color, alpha: Float) {
    val w = size.width
    val h = size.height
    val steps = 80
    val path = Path()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val y = h * (yc - thick / 2f) + h * amp * wave(t, freq, phase)
        if (i == 0) path.moveTo(0f, y) else path.lineTo(w * t, y)
    }
    for (i in steps downTo 0) {
        val t = i / steps.toFloat()
        path.lineTo(w * t, h * (yc + thick / 2f) + h * amp * wave(t, freq, phase + 0.7f))
    }
    path.close()
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
            startY = h * (yc - thick - amp),
            endY = h * (yc + thick + amp),
        ),
    )
}

/** Smooth rolling silhouette filled down to the bottom edge. */
private fun DrawScope.hills(baseY: Float, amp: Float, freq: Float, phase: Float, top: Color, bottom: Color) {
    val w = size.width
    val h = size.height
    val steps = 80
    val path = Path().apply { moveTo(0f, h) }
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        path.lineTo(w * t, h * (baseY + amp * wave(t, freq, phase)))
    }
    path.lineTo(w, h)
    path.close()
    drawPath(path, Brush.verticalGradient(listOf(top, bottom), startY = h * (baseY - amp), endY = h))
}

private fun DrawScope.waveLine(yc: Float, amp: Float, freq: Float, phase: Float, color: Color, width: Float) {
    val steps = 80
    val path = Path()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val y = size.height * (yc + amp * wave(t, freq, phase))
        if (i == 0) path.moveTo(0f, y) else path.lineTo(size.width * t, y)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

private fun DrawScope.stars(seed: Int, count: Int, maxY: Float, color: Color = Color.White, bright: Float = 1f) {
    val rnd = Random(seed)
    val unit = size.width / 1080f
    repeat(count) {
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * size.height * maxY
        val r = (0.6f + rnd.nextFloat() * rnd.nextFloat() * 2.6f) * unit
        val a = (0.25f + rnd.nextFloat() * 0.75f) * bright
        drawCircle(color.copy(alpha = a), radius = r.coerceAtLeast(0.5f), center = Offset(x, y))
    }
}

private fun DrawScope.sparkle(fx: Float, fy: Float, len: Float, color: Color) {
    val cx = size.width * fx
    val cy = size.height * fy
    val l = size.width * len
    val sw = (l / 12f).coerceAtLeast(0.6f)
    drawLine(color.copy(alpha = 0.8f), Offset(cx - l, cy), Offset(cx + l, cy), sw, StrokeCap.Round)
    drawLine(color.copy(alpha = 0.8f), Offset(cx, cy - l), Offset(cx, cy + l), sw, StrokeCap.Round)
    glow(color, fx, fy, len * 1.6f, 0.45f)
}

/** Fine deterministic grain; takes the banding out of big gradients. */
private fun DrawScope.grain(color: Color = Color.White, alpha: Float = 0.05f) {
    val rnd = Random(11)
    val count = ((size.width * size.height) / 700f).toInt().coerceIn(200, 3500)
    val pts = List(count) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
    drawPoints(
        pts,
        PointMode.Points,
        color.copy(alpha = alpha),
        strokeWidth = (size.width / 600f).coerceAtLeast(1f),
        cap = StrokeCap.Round,
    )
}

// ── presets ───────────────────────────────────────────────────────────────

private fun DrawScope.drawAurora() {
    sky(0f to c(0xFF040A1C), 0.55f to c(0xFF0A2238), 1f to c(0xFF03101A))
    glow(c(0xFF2DE2B6), 0.2f, 0.28f, 0.95f, 0.40f)
    glow(c(0xFF7B5CFF), 0.85f, 0.18f, 0.8f, 0.38f)
    stars(3, 140, 0.45f)
    ribbon(0.26f, 0.16f, 0.04f, 1.1f, 0.4f, c(0xFF3DFFB5), 0.42f)
    ribbon(0.33f, 0.12f, 0.035f, 1.6f, 2.1f, c(0xFF7B5CFF), 0.35f)
    ribbon(0.22f, 0.09f, 0.03f, 2.2f, 4.0f, c(0xFF5CE1FF), 0.28f)
    hills(0.86f, 0.035f, 0.9f, 1.2f, c(0xFF071723), c(0xFF020A10))
    hills(0.93f, 0.03f, 1.4f, 3.0f, c(0xFF040E16), c(0xFF01060A))
    grain()
}

private fun DrawScope.drawSunset() {
    sky(
        0f to c(0xFF1A0F3D), 0.35f to c(0xFF6A2A78), 0.6f to c(0xFFE0566B),
        0.78f to c(0xFFFF9A5A), 1f to c(0xFFFFD08A),
    )
    stars(5, 60, 0.3f, bright = 0.5f)
    glow(c(0xFFFFE2A8), 0.5f, 0.73f, 0.95f, 0.55f)
    val center = Offset(size.width * 0.5f, size.height * 0.73f)
    val r = size.width * 0.17f
    drawCircle(
        Brush.verticalGradient(listOf(c(0xFFFFF1C9), c(0xFFFFB067)), startY = center.y - r, endY = center.y + r),
        radius = r,
        center = center,
    )
    hills(0.80f, 0.03f, 0.8f, 0.5f, c(0xFF8A3170), c(0xFF3A1556))
    hills(0.87f, 0.035f, 1.2f, 2.2f, c(0xFF4A1B5E), c(0xFF22093A))
    hills(0.94f, 0.025f, 1.7f, 4.0f, c(0xFF220A36), c(0xFF0E031A))
    grain()
}

private fun DrawScope.drawOcean() {
    sky(0f to c(0xFF020E1C), 0.5f to c(0xFF04405E), 1f to c(0xFF0A7C94))
    glow(c(0xFF22D3EE), 0.75f, 0.2f, 0.9f, 0.28f)
    glow(c(0xFF1D4ED8), 0.1f, 0.55f, 0.9f, 0.30f)
    ribbon(0.62f, 0.2f, 0.03f, 1.0f, 1.0f, c(0xFF22D3EE), 0.18f)
    ribbon(0.82f, 0.2f, 0.03f, 1.4f, 3.0f, c(0xFF38BDF8), 0.15f)
    for (i in 0 until 8) {
        waveLine(
            yc = 0.40f + i * 0.07f,
            amp = 0.012f + i * 0.002f,
            freq = 0.9f + i * 0.12f,
            phase = i * 0.9f,
            color = Color.White.copy(alpha = 0.05f + 0.018f * i),
            width = size.width / 260f * (1f + i * 0.25f),
        )
    }
    grain()
}

private fun DrawScope.drawMidnight() {
    sky(0f to c(0xFF02030A), 0.6f to c(0xFF0A1030), 1f to c(0xFF1B1146))
    glow(c(0xFF4F46E5), 0.3f, 0.75f, 1.0f, 0.28f)
    glow(c(0xFFC7D2FE), 0.74f, 0.2f, 0.55f, 0.30f)
    stars(8, 220, 0.8f)
    val moon = Offset(size.width * 0.74f, size.height * 0.2f)
    val r = size.width * 0.065f
    drawCircle(
        Brush.radialGradient(
            listOf(c(0xFFFFFFFF), c(0xFFDDE3FF)),
            center = Offset(moon.x - r * 0.3f, moon.y - r * 0.3f),
            radius = r * 1.5f,
        ),
        radius = r,
        center = moon,
    )
    hills(0.90f, 0.03f, 0.7f, 1.0f, c(0xFF0B0F26), c(0xFF03040C))
    grain()
}

private fun DrawScope.drawMist() {
    sky(0f to c(0xFFF2F6FA), 0.5f to c(0xFFD9E5F0), 1f to c(0xFFBCCFE0))
    glow(Color.White, 0.3f, 0.15f, 0.9f, 0.9f)
    glow(c(0xFFB9C4F8), 0.85f, 0.4f, 0.8f, 0.55f)
    glow(c(0xFFBDEBE0), 0.15f, 0.7f, 0.8f, 0.5f)
    hills(0.62f, 0.04f, 0.7f, 0.5f, c(0x99B4C8DB), c(0x99A5BBD0))
    glow(Color.White, 0.5f, 0.7f, 0.8f, 0.25f)
    hills(0.74f, 0.045f, 1.0f, 2.0f, c(0xB38FA9C2), c(0xCC7C97B3))
    glow(Color.White, 0.3f, 0.82f, 0.7f, 0.22f)
    hills(0.86f, 0.04f, 1.3f, 3.5f, c(0xD96F8CA8), c(0xF05A7893))
    grain(Color.Black, 0.035f)
}

private fun DrawScope.drawBlossom() {
    sky(0f to c(0xFFFFF4F6), 0.55f to c(0xFFFFD9E4), 1f to c(0xFFF4B2CB))
    glow(c(0xFFFF8FB1), 0.85f, 0.18f, 0.85f, 0.45f)
    glow(c(0xFFFFC9A8), 0.1f, 0.5f, 0.8f, 0.45f)
    glow(c(0xFFD6B4FF), 0.55f, 0.95f, 0.9f, 0.4f)
    glow(Color.White, 0.3f, 0.1f, 0.7f, 0.8f)
    val rnd = Random(21)
    val w = size.width
    repeat(10) {
        glow(Color.White, rnd.nextFloat(), rnd.nextFloat(), 0.04f + rnd.nextFloat() * 0.08f, 0.22f)
    }
    repeat(26) {
        val cx = rnd.nextFloat() * w
        val cy = rnd.nextFloat() * size.height
        val pw = w * (0.03f + rnd.nextFloat() * 0.05f)
        val ph = pw * 1.6f
        val angle = rnd.nextFloat() * 360f
        val alpha = 0.35f + rnd.nextFloat() * 0.45f
        val color = if (rnd.nextFloat() < 0.5f) Color.White else c(0xFFFF9DBB)
        rotate(angle, Offset(cx, cy)) {
            drawOval(color.copy(alpha = alpha), Offset(cx - pw / 2f, cy - ph / 2f), Size(pw, ph))
        }
    }
    grain(c(0xFFB04A6E), 0.04f)
}

private fun DrawScope.drawGalaxy() {
    val w = size.width
    val h = size.height
    sky(0f to c(0xFF02010E), 0.5f to c(0xFF0E0830), 1f to c(0xFF040316))
    glow(c(0xFFB13DFF), 0.25f, 0.28f, 1.0f, 0.50f)
    glow(c(0xFF3D6BFF), 0.8f, 0.52f, 0.95f, 0.45f)
    glow(c(0xFFFF4FA3), 0.45f, 0.85f, 0.85f, 0.30f)
    glow(c(0xFF22D3EE), 0.9f, 0.1f, 0.5f, 0.22f)
    // Milky-way band across the sky.
    val bandCenterY = h * 0.45f
    rotate(-28f, Offset(w * 0.5f, bandCenterY)) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                startY = bandCenterY - w * 0.22f,
                endY = bandCenterY + w * 0.22f,
            ),
            topLeft = Offset(-w * 0.3f, bandCenterY - w * 0.22f),
            size = Size(w * 1.6f, w * 0.44f),
        )
    }
    stars(1, 320, 1f)
    stars(2, 60, 1f, color = c(0xFFBFD4FF))
    sparkle(0.2f, 0.12f, 0.03f, Color.White)
    sparkle(0.82f, 0.3f, 0.022f, c(0xFFBFD4FF))
    sparkle(0.35f, 0.62f, 0.018f, c(0xFFFFD6F0))
    // Ringed planet.
    val planet = Offset(w * 0.76f, h * 0.76f)
    val r = w * 0.13f
    val ringTopLeft = Offset(planet.x - r * 1.8f, planet.y - r * 0.45f)
    val ringSize = Size(r * 3.6f, r * 0.9f)
    val ringColor = c(0xFFE9D5FF).copy(alpha = 0.55f)
    rotate(-18f, planet) {
        drawOval(ringColor, ringTopLeft, ringSize, style = Stroke(width = r * 0.07f))
    }
    drawCircle(
        Brush.radialGradient(
            listOf(c(0xFFA78BFA), c(0xFF4C2A9E), c(0xFF140A3A)),
            center = Offset(planet.x - r * 0.35f, planet.y - r * 0.35f),
            radius = r * 1.5f,
        ),
        radius = r,
        center = planet,
    )
    rotate(-18f, planet) {
        drawArc(ringColor, 0f, 180f, false, ringTopLeft, ringSize, style = Stroke(width = r * 0.07f))
    }
    grain()
}

private fun DrawScope.drawNeon() {
    val w = size.width
    val h = size.height
    val horizon = 0.64f
    val hy = h * horizon
    // Sky above the horizon, dark ground below it.
    drawRect(
        Brush.verticalGradient(
            0f to c(0xFF05010F), 0.5f to c(0xFF220A4A), 1f to c(0xFFFF2E93),
            startY = 0f, endY = hy,
        ),
        size = Size(w, hy),
    )
    drawRect(
        Brush.verticalGradient(listOf(c(0xFF1A0436), c(0xFF05010F)), startY = hy, endY = h),
        topLeft = Offset(0f, hy),
        size = Size(w, h - hy),
    )
    glow(c(0xFF22D3EE), 0.15f, 0.3f, 0.7f, 0.18f)
    stars(4, 90, horizon * 0.7f, bright = 0.7f)
    // Striped retro sun.
    val sun = Offset(w * 0.5f, h * (horizon - 0.07f))
    val r = w * 0.22f
    clipPath(Path().apply { addOval(Rect(sun, r)) }) {
        drawRect(
            Brush.verticalGradient(listOf(c(0xFFFFE45C), c(0xFFFF2E93)), startY = sun.y - r, endY = sun.y + r),
            topLeft = Offset(sun.x - r, sun.y - r),
            size = Size(r * 2f, r * 2f),
        )
        for (k in 0..6) {
            drawRect(
                c(0xFFB01E7A),
                topLeft = Offset(sun.x - r, sun.y + r * (0.05f + k * 0.15f)),
                size = Size(r * 2f, r * 0.02f * (k + 1)),
            )
        }
    }
    // Skyline silhouette, taller toward the edges.
    val rnd = Random(9)
    var x = 0f
    while (x < w) {
        val bw = w * (0.04f + rnd.nextFloat() * 0.06f)
        val edge = abs(x / w - 0.5f) * 2f
        val bh = h * (0.03f + rnd.nextFloat() * 0.07f) * (0.5f + edge * 1.6f)
        drawRect(c(0xFF0A0220), Offset(x, hy - bh), Size(bw, bh + 1f))
        repeat(6) {
            val wx = x + rnd.nextFloat() * bw
            val wy = hy - bh + rnd.nextFloat() * bh
            val color = if (rnd.nextBoolean()) c(0xFF22D3EE) else c(0xFFFF2E93)
            drawCircle(color.copy(alpha = 0.8f), radius = (w / 400f).coerceAtLeast(0.6f), center = Offset(wx, wy))
        }
        x += bw
    }
    // Perspective grid on the ground.
    val line = (w / 450f).coerceAtLeast(0.6f)
    for (k in 1..9) {
        val t = k / 9f
        val y = hy + (h - hy) * t * t
        drawLine(c(0xFFFF2E93).copy(alpha = 0.2f + 0.5f * t), Offset(0f, y), Offset(w, y), line * (0.6f + t))
    }
    for (i in -12..12) {
        drawLine(
            c(0xFFB347FF).copy(alpha = 0.45f),
            Offset(w * 0.5f + i * w * 0.012f, hy),
            Offset(w * 0.5f + i * w * 0.28f, h),
            line,
        )
    }
    glow(c(0xFFFF2E93), 0.5f, horizon, 0.95f, 0.40f)
    grain()
}
