package com.invictus.xmd.ui.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.security.MessageDigest

/**
 * Shared logic for the "Add to Home screen" / "Remove from Home screen"
 * pinned-shortcut feature (see BrowserFragment.toggleCurrentPageAsApp()),
 * so the id derivation and pinned-state check aren't duplicated between
 * the toggle action and whatever else needs to know if a URL is pinned.
 */
object PinnedShortcutUtils {

    /** Same short, stable id scheme as before: a launcher-safe SHA-256
     *  prefix of the URL, since some OEM ShortcutManager implementations
     *  silently drop pin requests whose id is an entire (possibly very
     *  long, query-string-laden) URL. */
    fun shortcutIdFor(url: String): String =
        "webapp_" + MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)

    /** True if a shortcut for this URL is currently pinned to the Home
     *  screen. Cheap local lookup -- ShortcutManagerCompat.getShortcuts()
     *  reads from the system's own shortcut store, no network/IO. */
    fun isPinned(context: Context, url: String): Boolean {
        val id = shortcutIdFor(url)
        return ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .any { it.id == id }
    }

    /** Disables + requests removal of the pinned shortcut for this URL.
     *  disableShortcuts() first (so a stale shortcut can't be re-launched
     *  after this returns) then removeLongLivedShortcuts() to ask the
     *  launcher to actually drop the Home screen icon -- mirrors the
     *  documented two-step pattern for dynamic-pinned shortcut removal. */
    fun unpin(context: Context, url: String) {
        val id = shortcutIdFor(url)
        val ids = listOf(id)
        ShortcutManagerCompat.disableShortcuts(context, ids, null)
        ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
    }

    /**
     * Builds a crisp, non-stretched launcher icon from a (possibly small)
     * source bitmap: centers it with padding on a solid background at a
     * fixed target size, instead of handing a tiny favicon straight to
     * [IconCompat.createWithAdaptiveBitmap], which upscales/crops it into
     * the adaptive-icon mask and is what produced blurry/pixelated Home
     * screen icons before.
     */
    fun buildIcon(context: Context, favicon: Bitmap?, fallbackRes: Int): IconCompat {
        if (favicon == null) return IconCompat.createWithResource(context, fallbackRes)

        val canvasSize = ICON_CANVAS_PX
        val output = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Solid background so the padded favicon doesn't end up with a
        // transparent ring once the launcher applies its adaptive mask.
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(
            RectF(0f, 0f, canvasSize.toFloat(), canvasSize.toFloat()),
            canvasSize * 0.18f, canvasSize * 0.18f, bgPaint,
        )

        // Never upscale past the source's own resolution -- only ever
        // shrink to fit inside the padded content area, so a small
        // favicon stays sharp instead of being blown up and blurred.
        val contentBox = canvasSize - 2 * ICON_PADDING_PX
        val scale = minOf(
            contentBox.toFloat() / favicon.width,
            contentBox.toFloat() / favicon.height,
            1f,
        )
        val drawW = (favicon.width * scale)
        val drawH = (favicon.height * scale)
        val left = (canvasSize - drawW) / 2f
        val top = (canvasSize - drawH) / 2f

        val destRect = RectF(left, top, left + drawW, top + drawH)
        canvas.drawBitmap(favicon, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        return IconCompat.createWithAdaptiveBitmap(output)
    }

    private const val ICON_CANVAS_PX = 192
    private const val ICON_PADDING_PX = 28

    fun pin(
        context: Context,
        shortcut: ShortcutInfoCompat,
        callbackIntentSender: android.content.IntentSender,
    ): Boolean = ShortcutManagerCompat.requestPinShortcut(context, shortcut, callbackIntentSender)
}
