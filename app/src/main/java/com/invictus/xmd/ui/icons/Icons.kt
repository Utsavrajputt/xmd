package com.invictus.xmd.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
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

@Suppress("MemberVisibilityCanBePrivate")
object Icons {
    val Add by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Add) }
    val Article by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Article) }
    val ArrowBack by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Arrow_back) }
    val ArrowDown by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Arrow_downward) }
    val ArrowForward by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Arrow_forward) }
    val ArrowUp by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Arrow_upward) }
    val Bookmark by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Bookmark) }
    val BookmarkAdd by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Bookmark_add) }
    val Bookmarks by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Bookmarks) }
    val Cancel by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Cancel) }
    val Check by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Check) }
    val ChevronRight by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Chevron_right) }
    val Close by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Close) }
    val Code by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Code) }
    val Copy by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Content_copy) }
    val Delete by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Delete) }
    val DeleteSweep by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Delete_sweep) }
    val Desktop by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Desktop_windows) }
    val Dns by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Dns) }
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
    val Info by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Info) }
    val Language by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Language) }
    val Link by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Link) }
    val Lock by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Lock) }
    val LockOpen by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Lock_open) }
    val More by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.More_vert) }
    val Music by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Music_note) }
    val OpenInNew by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Open_in_new) }
    val Palette by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Palette) }
    val Paste by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Content_paste) }
    val Pause by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Pause) }
    val Person by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Person) }
    val Play by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Play_arrow) }
    val Public by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Public) }
    val Refresh by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Refresh) }
    val Schedule by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Schedule) }
    val Search by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Search) }
    val Settings by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Settings) }
    val Share by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Share) }
    val Shield by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Shield) }
    val Sort by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Sort) }
    val Star by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Star) }
    val Stop by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Stop) }
    val Sync by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Sync) }
    val Torrent by lazy(LazyThreadSafetyMode.NONE) { AppIcon(magnetIcon()) }
    val Video by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Video_library) }
    val ViewList by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.View_list) }
    val VisibilityOff by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Visibility_off) }
    val Wifi by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Wifi) }
    val Youtube by lazy(LazyThreadSafetyMode.NONE) { AppIcon(MaterialSymbols.RoundedFilled.Youtube_activity) }
}
