package com.invictus.xmd.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.*
import com.invictus.xmd.database.entities.Bookmark
import com.invictus.xmd.preferences.Settings

typealias XmdIcons = Icons

/**
 * Hand-built to match res/drawable/ic_magnet.xml exactly (same pathData,
 * stroke width, round caps/joins) -- built as an ImageVector instead of
 * loaded via vectorResource() so it slots into AppIcon/Icons the same way
 * as every Material Symbols entry below (no @Composable context needed for
 * the `by lazy` singletons, no inconsistent icon-loading path). The stroke
 * color here is irrelevant at render time -- Icon()'s `tint` recolors the
 * whole glyph via ColorFilter, same as the XML's `android:tint` comment.
 */
private fun magnetIcon(): ImageVector = ImageVector.Builder(
    name = "Magnet",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString("M5,19 L5,12 A7,7 0 0 1 19,12 L19,19").toNodes(),
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2.6f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
).build()

/** WhatsApp logo (bubble + handset), filled glyph; recolored by Icon()'s tint. */
private fun whatsappIcon(): ImageVector = ImageVector.Builder(
    name = "WhatsApp",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString("M 17.472 14.382 c -.297 -.149 -1.758 -.867 -2.03 -.967 -.273 -.099 -.471 -.148 -.67 .15 -.197 .297 -.767 .966 -.94 1.164 -.173 .199 -.347 .223 -.644 .075 -.297 -.15 -1.255 -.463 -2.39 -1.475 -.883 -.788 -1.48 -1.761 -1.653 -2.059 -.173 -.297 -.018 -.458 .13 -.606 .134 -.133 .298 -.347 .446 -.52 .149 -.174 .198 -.298 .298 -.497 .099 -.198 .05 -.371 -.025 -.52 -.075 -.149 -.669 -1.612 -.916 -2.207 -.242 -.579 -.487 -.5 -.669 -.51 -.173 -.008 -.371 -.01 -.57 -.01 -.198 0 -.52 .074 -.792 .372 -.272 .297 -1.04 1.016 -1.04 2.479 0 1.462 1.065 2.875 1.213 3.074 .149 .198 2.096 3.2 5.077 4.487 .709 .306 1.262 .489 1.694 .625 .712 .227 1.36 .195 1.871 .118 .571 -.085 1.758 -.719 2.006 -1.413 .248 -.694 .248 -1.289 .173 -1.413 -.074 -.124 -.272 -.198 -.57 -.347 m -5.421 7.403 h -.004 a 9.87 9.87 0 0 1 -5.031 -1.378 l -.361 -.214 -3.741 .982 .998 -3.648 -.235 -.374 a 9.86 9.86 0 0 1 -1.51 -5.26 c .001 -5.45 4.436 -9.884 9.888 -9.884 2.64 0 5.122 1.03 6.988 2.898 a 9.825 9.825 0 0 1 2.893 6.994 c -.003 5.45 -4.437 9.884 -9.885 9.884 m 8.413 -18.297 A 11.815 11.815 0 0 0 12.05 0 C 5.495 0 .16 5.335 .157 11.892 c 0 2.096 .547 4.142 1.588 5.945 L .057 24 l 6.305 -1.654 a 11.882 11.882 0 0 0 5.683 1.448 h .005 c 6.554 0 11.89 -5.335 11.893 -11.893 a 11.821 11.821 0 0 0 -3.48 -8.413Z").toNodes(),
    fill = SolidColor(Color.Black),
).build()

/** Chrome-omnibox style "fill into address bar" arrow (up-left). Stroke
 *  glyph so it needs no extra symbol dependency; tinted by Icon(). */
private fun fillArrowIcon(): ImageVector = ImageVector.Builder(
    name = "FillArrow",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString("M17,17 L7,7 M7,15.5 L7,7 L15.5,7").toNodes(),
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
).build()

/**
 * WhatsApp-status style glyph: a speech bubble (tail bottom-left) outlining a
 * ring broken into four dashes. Built as an ImageVector (same approach as
 * magnetIcon above) so it takes Icon()'s tint like every other entry in [Icons].
 */
private fun statusRingIcon(): ImageVector {
    val cx = 12.0
    val cy = 11.5
    val ringRadius = 4.9
    val dashes = (0 until 4).joinToString(" ") { i ->
        val start = Math.toRadians(i * 90.0 + 18.0)
        val end = Math.toRadians(i * 90.0 + 72.0)
        String.format(
            java.util.Locale.US,
            "M%.2f,%.2f A%.2f,%.2f 0 0 1 %.2f,%.2f",
            cx + ringRadius * cos(start), cy + ringRadius * sin(start),
            ringRadius, ringRadius,
            cx + ringRadius * cos(end), cy + ringRadius * sin(end),
        )
    }
    val bubble = "M5.64,17.86 L3,21.2 L9.67,20.19 A9,9 0 1 0 5.64,17.86 Z"
    return ImageVector.Builder(
        name = "StatusBubble",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(bubble).toNodes(),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).addPath(
        pathData = PathParser().parsePathString(dashes).toNodes(),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}

@Suppress("MemberVisibilityCanBePrivate")
object Icons {
    val Add by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Add) }
    val AddToHomeScreen by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Add_to_home_screen) }
    val Android by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Android) }
    val Archive by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Folder_zip) }
    val RemoveFromHomeScreen by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Delete) }
    val Article by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Article) }
    val ArrowBack by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Arrow_back) }
    val ArrowDown by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Arrow_downward) }
    val ArrowForward by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Arrow_forward) }
    val ArrowUp by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Arrow_upward) }
    val Bookmark by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Bookmark) }
    val BookmarkAdd by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Bookmark_add) }
    val Bookmarks by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Bookmarks) }
    val Cancel by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Cancel) }
    val ChevronDown by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Keyboard_arrow_down) }
    val Check by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Check) }
    val ChevronRight by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Chevron_right) }
    val PieChart by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Pie_chart) }
    val Sell by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Sell) }
    val Description by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Description) }
    val Timer by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Timer) }
    val EventDone by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Event_available) }
    val Close by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Close) }
    val Code by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Code) }
    val Copy by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Content_copy) }
    val Delete by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Delete) }
    val DeleteSweep by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Delete_sweep) }
    val Desktop by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Desktop_windows) }
    val Dns by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Dns) }
    val Document by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Description) }
    val Download by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Download) }
    val Downloads by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Download_for_offline) }
    val DragHandle by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Drag_indicator) }
    val Edit by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Edit) }
    val FileOpen by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.File_open) }
    val Filter by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Filter_list) }
    val FindInPage by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Find_in_page) }
    val Folder by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Folder) }
    val Globe by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Globe) }
    val GridView by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Grid_view) }
    val History by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.History) }
    val Home by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Home) }
    val Image by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Image) }
    val Info by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Info) }
    val Language by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Language) }
    val Link by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Link) }
    val Lock by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Lock) }
    val LockOpen by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Lock_open) }
    val More by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.More_vert) }
    val MonetizationOn by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Monetization_on) }
    val Music by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Music_note) }
    val Notifications by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Notifications) }
    val OpenInNew by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Open_in_new) }
    val Palette by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Palette) }
    val Paste by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Content_paste) }
    val Pause by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Pause) }
    val Pdf by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Picture_as_pdf) }
    val Person by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Person) }
    val Play by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Play_arrow) }
    val PlayCircle by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Play_circle) }
    val Public by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Public) }
    val RotateLeft by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Rotate_left) }
    val RotateRight by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Rotate_right) }
    val Refresh by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Refresh) }
    val Schedule by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Schedule) }
    val Search by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Search) }
    val Settings by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Settings) }
    val Share by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Share) }
    val StatusSaver by lazy(LazyThreadSafetyMode.NONE) { AppIcon(statusRingIcon()) }
    val Subtitles by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Subtitles) }
    val DataUsage by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Data_usage) }
    val AutoDelete by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Auto_delete) }
    val Shield by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Shield) }
    val Block by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Block) }
    val Cloud by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Cloud) }
    val Tune by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Tune) }
    val Sort by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Sort) }
    val Star by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Star) }
    val Stop by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Stop) }
    val Sync by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Sync) }
    val Torrent by lazy(LazyThreadSafetyMode.NONE) { AppIcon(magnetIcon()) }
    val WhatsApp by lazy(LazyThreadSafetyMode.NONE) { AppIcon(whatsappIcon()) }
    val FillArrow by lazy(LazyThreadSafetyMode.NONE) { AppIcon(fillArrowIcon()) }
    val Video by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Video_library) }
    val ViewList by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.View_list) }
    val VisibilityOff by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Visibility_off) }
    val Wifi by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Wifi) }
    val Youtube by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Youtube_activity) }
}
