package com.invictus.xmd.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.WallpaperCache
import com.invictus.xmd.ui.theme.WallpaperPreset
import com.invictus.xmd.ui.theme.drawWallpaperPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Wallpaper" row of Settings > Appearance (same layout as Replay): a small thumbnail of what is
 * applied right now, its name and a chevron. Tapping opens the wallpaper editor route.
 *
 * The thumbnail is seeded from [WallpaperCache], which the main window fills when it first shows the
 * wallpaper, so it is on screen on the first frame instead of popping in after a decode.
 */
@Composable
internal fun WallpaperPreferenceCard(
    wallpaperUri: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preset = remember(wallpaperUri) { WallpaperPreset.fromUri(wallpaperUri) }
    val isNone = wallpaperUri.isBlank()
    val isCustom = !isNone && preset == null
    val thumbnail: Bitmap? by produceState(
        initialValue = if (isCustom) WallpaperCache.peekThumbnail(wallpaperUri) else null,
        wallpaperUri,
    ) {
        value = if (!isCustom) {
            null
        } else {
            WallpaperCache.peekThumbnail(wallpaperUri) ?: withContext(Dispatchers.IO) {
                runCatching { WallpaperCache.load(context, wallpaperUri) }
                WallpaperCache.peekThumbnail(wallpaperUri)
            }
        }
    }
    val currentName = when {
        isNone -> stringResource(R.string.wallpaper_preset_none)
        preset != null -> stringResource(preset.labelRes)
        else -> stringResource(R.string.wallpaper_preset_custom)
    }
    val thumbShape = RoundedCornerShape(12.dp)

    SettingsSectionCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 84.dp)
                    .clip(thumbShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, thumbShape),
                contentAlignment = Alignment.Center,
            ) {
                val loadedThumbnail = thumbnail
                when {
                    isNone -> Icon(
                        Icons.Block,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    preset != null -> Canvas(modifier = Modifier.fillMaxSize()) { drawWallpaperPreset(preset) }
                    loadedThumbnail != null -> Image(
                        bitmap = loadedThumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.settings_wallpaper_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = currentName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_wallpaper_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
