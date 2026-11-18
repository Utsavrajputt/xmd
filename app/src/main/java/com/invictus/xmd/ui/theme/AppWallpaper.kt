package com.invictus.xmd.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.invictus.xmd.preferences.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class WallpaperScaleMode { Fit, Fill }

/** Everything the wallpaper needs. [uri] is "" (none), "preset:<id>" or "file:<path>". */
data class WallpaperConfig(
    val uri: String = "",
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scaleMode: WallpaperScaleMode = WallpaperScaleMode.Fit,
    val blur: Float = 0f,
    val alpha: Float = 1f,
)

/**
 * True only while the wallpaper bitmap is really on screen. Fragment-hosted ComposeViews live in a
 * separate composition (inside the ViewPager), so a CompositionLocal wouldn't reach them -- they
 * read this flow instead.
 */
object WallpaperState {
    private val _rendered = MutableStateFlow(false)
    val rendered: StateFlow<Boolean> = _rendered
    fun setRendered(value: Boolean) { _rendered.value = value }

    private val _webOpaque = MutableStateFlow(false)
    val webOpaque: StateFlow<Boolean> = _webOpaque

    /** True while a website is open in the Browser tab: chrome around it stays opaque. */
    fun setWebOpaque(value: Boolean) { _webOpaque.value = value }

    private val _lightTextOnWallpaper = MutableStateFlow(false)

    /**
     * True when the light theme is showing a dark wallpaper, so text/icons drawn directly on it must
     * be light. A flow (not a CompositionLocal) for the same reason as [rendered].
     */
    val lightTextOnWallpaper: StateFlow<Boolean> = _lightTextOnWallpaper
    fun setLightTextOnWallpaper(value: Boolean) { _lightTextOnWallpaper.value = value }
}

/** Use for text/icons drawn directly on the wallpaper (not inside cards, chips or bars). */
@Composable
fun Color.onWallpaper(): Color {
    val lightText by WallpaperState.lightTextOnWallpaper.collectAsState()
    val webOpaque by WallpaperState.webOpaque.collectAsState()
    return if (lightText && !webOpaque) lerp(this, Color.White, 0.9f).copy(alpha = this.alpha) else this
}

/** A decoded wallpaper plus its average luminance, so the scrim is right on the first frame. */
class LoadedWallpaper(val bitmap: Bitmap, val luminance: Float)

/**
 * Process-wide cache shared by the main window, the Settings card and the editor, so a wallpaper that
 * is already on screen is never decoded again (no blank frame / flicker while opening or leaving the
 * editor). Cached bitmaps are shared: nobody may recycle them.
 */
object WallpaperCache {
    private const val THUMB_WIDTH_PX = 240
    private val full = object : LruCache<String, LoadedWallpaper>(
        (Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(96L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: LoadedWallpaper): Int = value.bitmap.allocationByteCount
    }
    private val thumbs = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun peek(uri: String): LoadedWallpaper? = if (uri.isBlank()) null else full.get(uri)
    fun peekThumbnail(uri: String): Bitmap? = if (uri.isBlank()) null else thumbs.get(uri)

    /** Decodes (or returns the cached) wallpaper. Call off the main thread. */
    fun load(context: Context, uri: String): LoadedWallpaper? {
        if (uri.isBlank()) return null
        full.get(uri)?.let { return it }
        val bitmap = loadWallpaperBitmap(context, uri) ?: return null
        val loaded = LoadedWallpaper(bitmap, bitmap.estimateLuminance())
        full.put(uri, loaded)
        if (thumbs.get(uri) == null) {
            val scaled = if (bitmap.width <= THUMB_WIDTH_PX) {
                bitmap
            } else {
                val h = (bitmap.height * (THUMB_WIDTH_PX / bitmap.width.toFloat())).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, THUMB_WIDTH_PX, h, true)
            }
            thumbs.put(uri, scaled)
        }
        return loaded
    }

    /** Registers an already-decoded wallpaper under another URI (e.g. its freshly stored copy). */
    fun alias(uri: String, loaded: LoadedWallpaper) {
        if (uri.isBlank()) return
        full.put(uri, loaded)
        val src = loaded.bitmap
        val thumb = if (src.width <= THUMB_WIDTH_PX) src else {
            val h = (src.height * (THUMB_WIDTH_PX / src.width.toFloat())).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(src, THUMB_WIDTH_PX, h, true)
        }
        thumbs.put(uri, thumb)
    }

    fun evict(uri: String) {
        if (uri.isBlank()) return
        full.remove(uri)
        thumbs.remove(uri)
    }
}

/** Use instead of `colorScheme.background` on main-tab screens so the wallpaper shows through. */
@Composable
fun wallpaperAwareBackgroundColor(): Color {
    val rendered by WallpaperState.rendered.collectAsState()
    val webOpaque by WallpaperState.webOpaque.collectAsState()
    return if (rendered && !webOpaque) Color.Transparent else MaterialTheme.colorScheme.background
}

/**
 * Top/bottom bar colour: a glassy translucent surface while a wallpaper is showing (so the image
 * reads through the bar and the status-bar area), the normal opaque surface otherwise.
 */
@Composable
fun wallpaperAwareBarColor(): Color {
    val rendered by WallpaperState.rendered.collectAsState()
    val webOpaque by WallpaperState.webOpaque.collectAsState()
    val lightText by WallpaperState.lightTextOnWallpaper.collectAsState()
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    // A dark wallpaper is shown untouched in the light theme, so the bar needs more body to keep its dark text readable.
    return if (rendered && !webOpaque) base.copy(alpha = if (lightText) 0.82f else 0.40f) else base
}

private const val MAX_WALLPAPER_BLUR_DP = 40f
private const val LIGHT_TEXT_LUMINANCE_THRESHOLD = 0.55f
private const val MAX_WALLPAPER_DIMENSION_PX = 2560
private const val PRESET_MAX_WIDTH_PX = 1080f
private const val PRESET_MAX_HEIGHT_PX = 2600f

/** Draws the wallpaper behind [content] and a readability scrim over it. */
@Composable
fun AppWallpaperHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val config by Settings.wallpaperFlow.collectAsState()
    // Starts from the cache and keeps showing the previous wallpaper while a new one decodes, so
    // saving/leaving the editor never drops to the plain background for a frame.
    var shown by remember { mutableStateOf(WallpaperCache.peek(config.uri)) }
    LaunchedEffect(config.uri) {
        val uri = config.uri
        if (uri.isBlank()) {
            shown = null
            return@LaunchedEffect
        }
        WallpaperCache.peek(uri)?.let { shown = it; return@LaunchedEffect }
        val loaded = withContext(Dispatchers.IO) { runCatching { WallpaperCache.load(context, uri) }.getOrNull() }
        shown = loaded
    }
    val bitmap = shown?.bitmap

    androidx.compose.runtime.DisposableEffect(bitmap != null) {
        WallpaperState.setRendered(bitmap != null)
        onDispose { WallpaperState.setRendered(false) }
    }

    val isLightTheme = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val lightText = shown?.let { isLightTheme && it.luminance < LIGHT_TEXT_LUMINANCE_THRESHOLD } ?: false
    androidx.compose.runtime.DisposableEffect(lightText) {
        WallpaperState.setLightTextOnWallpaper(lightText)
        onDispose { WallpaperState.setLightTextOnWallpaper(false) }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        shown?.let { loaded ->
            WallpaperImage(
                bitmap = loaded.bitmap,
                zoom = config.zoom,
                offsetX = config.offsetX,
                offsetY = config.offsetY,
                scaleMode = config.scaleMode,
                blurRadius = config.blur,
                imageAlpha = config.alpha,
                modifier = Modifier.fillMaxSize(),
            )
            val background = MaterialTheme.colorScheme.background
            val scrim = remember(background, loaded) { wallpaperScrimColor(background, loaded.luminance) }
            Box(modifier = Modifier.fillMaxSize().background(scrim))
        }
        content()
    }
}

@Composable
fun WallpaperImage(
    bitmap: Bitmap,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    scaleMode: WallpaperScaleMode,
    blurRadius: Float = 0f,
    imageAlpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val safeBlur = blurRadius.coerceIn(0f, MAX_WALLPAPER_BLUR_DP)
    val safeAlpha = imageAlpha.coerceIn(0f, 1f)
    Box(modifier = modifier.clipToBounds()) {
        if (scaleMode == WallpaperScaleMode.Fit) {
            // Blurred, cropped copy fills the bars around a letter-boxed image.
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.12f
                        scaleY = 1.12f
                        alpha = 0.72f * safeAlpha
                    }
                    .blur(28.dp),
            )
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = if (scaleMode == WallpaperScaleMode.Fit) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val safeZoom = zoom.coerceIn(1f, 3f)
                    scaleX = safeZoom
                    scaleY = safeZoom
                    translationX = offsetX.coerceIn(-1f, 1f) * size.width * 0.35f
                    translationY = offsetY.coerceIn(-1f, 1f) * size.height * 0.35f
                    alpha = safeAlpha
                }
                .then(if (safeBlur > 0f) Modifier.blur(safeBlur.dp) else Modifier),
        )
    }
}

/** Scrim strength follows how bright the wallpaper is, tinted with the theme background. */
fun wallpaperScrimColor(background: Color, wallpaperLuminance: Float): Color {
    val lum = wallpaperLuminance.coerceIn(0f, 1f)
    return if (background.luminance() < 0.5f) {
        Color.Black.copy(alpha = 0.16f + 0.30f * lum)
    } else if (lum < LIGHT_TEXT_LUMINANCE_THRESHOLD) {
        // Dark wallpaper in light theme: shown untouched (no milky wash); text drawn on it turns light instead.
        Color.Transparent
    } else {
        lerp(background, Color.White, 0.35f).copy(alpha = 0.10f + 0.20f * lum)
    }
}

internal fun Bitmap.estimateLuminance(): Float = runCatching {
    if (isRecycled) return@runCatching 0.4f
    val small = Bitmap.createScaledBitmap(this, 16, 16, true)
    val pixels = IntArray(16 * 16)
    small.getPixels(pixels, 0, 16, 0, 0, 16, 16)
    if (small !== this) small.recycle()
    var sum = 0f
    for (px in pixels) {
        val r = ((px shr 16) and 0xFF) / 255f
        val g = ((px shr 8) and 0xFF) / 255f
        val b = (px and 0xFF) / 255f
        sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
    sum / pixels.size
}.getOrDefault(0.4f)

/** Portrait window size, so code-drawn presets match the device's aspect ratio. Capped for memory. */
private fun presetTargetSize(context: Context): Pair<Int, Int> {
    val m = context.resources.displayMetrics
    val w = minOf(m.widthPixels, m.heightPixels).coerceAtLeast(1)
    val h = maxOf(m.widthPixels, m.heightPixels).coerceAtLeast(1)
    val scale = minOf(1f, PRESET_MAX_WIDTH_PX / w, PRESET_MAX_HEIGHT_PX / h)
    return (w * scale).toInt().coerceAtLeast(2) to (h * scale).toInt().coerceAtLeast(2)
}

/**
 * Turns a picked image into a stored wallpaper. Presets are kept as their tiny "preset:<id>"
 * reference; a picked image is downscaled and copied to app storage as "file:<path>" (so it keeps
 * working after the picker's URI permission goes away). The previous copy is deleted.
 */
suspend fun saveWallpaperCopy(context: Context, sourceUri: String, previousUri: String): String =
    withContext(Dispatchers.IO) {
        if (sourceUri.isBlank() || WallpaperPreset.isPresetUri(sourceUri)) {
            deleteStoredWallpaper(previousUri)
            return@withContext sourceUri
        }
        // Already our own stored copy -- nothing to re-encode.
        if (sourceUri == previousUri && sourceUri.startsWith("file:")) return@withContext sourceUri
        // The decoded bitmap is shared through the cache, so it is never recycled here; it is also
        // registered under the stored copy's URI so the new wallpaper appears with no re-decode gap.
        val loaded = requireNotNull(WallpaperCache.load(context, sourceUri)) { "decode failed" }
        val dir = File(context.filesDir, "wallpaper").apply { mkdirs() }
        val out = File(dir, "wallpaper_${System.currentTimeMillis()}.png")
        out.outputStream().use { check(loaded.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "compress failed" } }
        deleteStoredWallpaper(previousUri)
        val storedUri = Uri.fromFile(out).toString()
        WallpaperCache.alias(storedUri, loaded)
        storedUri
    }

fun deleteStoredWallpaper(uri: String) {
    if (!uri.startsWith("file:")) return
    WallpaperCache.evict(uri)
    runCatching { Uri.parse(uri).path?.let { File(it).delete() } }
}

fun loadWallpaperBitmap(context: Context, wallpaperUri: String): Bitmap? = runCatching {
    WallpaperPreset.fromUri(wallpaperUri)?.let {
        val (w, h) = presetTargetSize(context)
        return@runCatching createWallpaperPresetBitmap(it, w, h)
    }
    val uri = Uri.parse(wallpaperUri)
    val isFile = uri.scheme.equals("file", ignoreCase = true)
    fun decode(options: BitmapFactory.Options): Bitmap? =
        if (isFile) {
            BitmapFactory.decodeFile(uri.path, options)
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_WALLPAPER_DIMENSION_PX) sampleSize *= 2
    decode(BitmapFactory.Options().apply { inSampleSize = sampleSize })
}.getOrNull()
