@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.domain.storage.StorageStats
import com.invictus.xmd.domain.storage.StorageStatsRepository
import com.invictus.xmd.ui.icons.AppIcon
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.XmdShapes
import com.invictus.xmd.utils.formatBytes
import kotlinx.coroutines.launch

/** Fixed palette for the storage categories, distinct across every theme. */
private object StorageColors {
    val Videos = Color(0xFF4285F4)
    val Audio = Color(0xFFAB47BC)
    val ArchivesApps = Color(0xFF66BB6A)
    val OtherDownloads = Color(0xFFFFB300)
    val StatusSaved = Color(0xFF25D366)
    val AppData = Color(0xFF5C6BC0)
    val NetworkCache = Color(0xFFFF7043)
    val BrowserCache = Color(0xFFEF5350)
    val Temporary = Color(0xFF78909C)
    val Leftover = Color(0xFFEC407A)
}

private data class StorageSegment(val key: String, val label: String, val bytes: Long, val color: Color)

/**
 * Settings > Storage, laid out like Morphe's storage page: a headline stat, a stacked
 * histogram with a legend, then per-cache rows with Clear buttons, a Clear all row and a
 * shortcut to the system app-info page.
 */
@Composable
fun SettingsStorageScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(StorageStats.Empty) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showClearAll by remember { mutableStateOf(false) }
    val loading = stats == StorageStats.Empty

    LaunchedEffect(refreshKey) { stats = StorageStatsRepository.read(context) }

    val nothingToClear = stringResource(R.string.storage_nothing_to_clear)
    val freedTemplate = stringResource(R.string.storage_freed)
    fun onCleared(freed: Long) {
        Toast.makeText(
            context,
            if (freed <= 0L) nothingToClear else freedTemplate.format(formatBytes(freed)),
            Toast.LENGTH_SHORT,
        ).show()
        refreshKey++
    }

    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text(stringResource(R.string.storage_clear_all)) },
            text = { Text(stringResource(R.string.storage_clear_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearAll = false
                    scope.launch { onCleared(StorageStatsRepository.clearAllCaches(context)) }
                }) { Text(stringResource(R.string.storage_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    val segments = listOf(
        StorageSegment("videos", stringResource(R.string.storage_videos), stats.videosBytes, StorageColors.Videos),
        StorageSegment("audio", stringResource(R.string.storage_audio), stats.audioBytes, StorageColors.Audio),
        StorageSegment("archives", stringResource(R.string.storage_archives_apps), stats.archivesAppsBytes, StorageColors.ArchivesApps),
        StorageSegment("other", stringResource(R.string.storage_other_downloads), stats.otherDownloadsBytes, StorageColors.OtherDownloads),
        StorageSegment("leftover", stringResource(R.string.storage_leftover_partials), stats.leftoverPartialBytes, StorageColors.Leftover),
        StorageSegment("status", stringResource(R.string.storage_status_saved), stats.statusSavedBytes, StorageColors.StatusSaved),
        StorageSegment("appdata", stringResource(R.string.storage_app_data), stats.appDataBytes, StorageColors.AppData),
        StorageSegment("network", stringResource(R.string.storage_network_cache), stats.networkCacheBytes, StorageColors.NetworkCache),
        StorageSegment("browser", stringResource(R.string.storage_browser_cache), stats.browserCacheBytes, StorageColors.BrowserCache),
        StorageSegment("temp", stringResource(R.string.storage_temporary), stats.temporaryBytes, StorageColors.Temporary),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSectionCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Headline: what XMD holds, over what is left on the phone.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(XmdShapes.M)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (loading) "…" else formatBytes(stats.appUsedBytes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (loading) stringResource(R.string.storage_used_by_xmd)
                        else stringResource(
                            R.string.storage_device_free,
                            formatBytes(stats.deviceFreeBytes),
                            formatBytes(stats.deviceTotalBytes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StorageHistogram(segments = segments, loading = loading)
            }
        }

        if (stats.leftoverPartialBytes > 0L) {
            SettingsSectionCard {
                CacheRow(
                    icon = Icons.DeleteSweep,
                    accent = StorageColors.Leftover,
                    title = stringResource(R.string.storage_leftover_partials),
                    description = stringResource(R.string.storage_leftover_partials_desc),
                    bytes = stats.leftoverPartialBytes,
                    onClear = { scope.launch { onCleared(StorageStatsRepository.clearLeftoverPartials()) } },
                )
            }
        }

        SettingsSectionHeader(stringResource(R.string.storage_caches))
        SettingsSectionCard {
            CacheRow(
                icon = Icons.Cloud,
                accent = StorageColors.NetworkCache,
                title = stringResource(R.string.storage_network_cache),
                description = stringResource(R.string.storage_network_cache_desc),
                bytes = stats.networkCacheBytes,
                onClear = { scope.launch { onCleared(StorageStatsRepository.clearNetworkCache(context)) } },
            )
            SettingsDivider()
            CacheRow(
                icon = Icons.Public,
                accent = StorageColors.BrowserCache,
                title = stringResource(R.string.storage_browser_cache),
                description = stringResource(R.string.storage_browser_cache_desc),
                bytes = stats.browserCacheBytes,
                onClear = { scope.launch { onCleared(StorageStatsRepository.clearBrowserCache(context)) } },
            )
            SettingsDivider()
            CacheRow(
                icon = Icons.Timer,
                accent = StorageColors.Temporary,
                title = stringResource(R.string.storage_temporary),
                description = stringResource(R.string.storage_temporary_desc),
                bytes = stats.temporaryBytes,
                onClear = { scope.launch { onCleared(StorageStatsRepository.clearTemporary(context)) } },
            )
        }

        // Apart from the caches it sums up, so it doesn't read as one more of them.
        SettingsSectionCard {
            CacheRow(
                icon = Icons.DeleteSweep,
                accent = MaterialTheme.colorScheme.error,
                title = stringResource(R.string.storage_clear_all),
                description = stringResource(R.string.storage_clear_all_desc),
                bytes = stats.totalCacheBytes,
                onClear = { showClearAll = true },
            )
        }

        SettingsSectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                AccentIcon(Icons.Info, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.storage_open_app_info),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.storage_open_app_info_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AccentIcon(icon: AppIcon, accent: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(accent.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CacheRow(
    icon: AppIcon,
    accent: Color,
    title: String,
    description: String,
    bytes: Long,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIcon(icon, accent)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val enabled = bytes > 0L
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(XmdShapes.S)
                .background(MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 0.14f else 0.06f))
                .clickable(enabled = enabled, onClick = onClear)
                .padding(vertical = 12.dp)
                .alpha(if (enabled) 1f else 0.5f),
        ) {
            Icon(
                imageVector = Icons.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${stringResource(R.string.storage_clear)} (${formatBytes(bytes)})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ── Histogram (stacked vertical bar + legend), ported from Morphe's StorageHistogram ──

@Composable
private fun StorageHistogram(segments: List<StorageSegment>, loading: Boolean) {
    val sum = remember(segments) { segments.sumOf { it.bytes }.coerceAtLeast(1L) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .width(56.dp)
                .height(maxOf(200.dp, (segments.size * 46).dp))
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
        ) {
            val barHeight = maxHeight
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                segments.forEach { segment ->
                    androidx.compose.runtime.key(segment.key) {
                        AnimatedSegment(
                            fraction = (segment.bytes.toFloat() / sum.toFloat()).coerceIn(0f, 1f),
                            barHeight = barHeight,
                            color = segment.color,
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .height(maxOf(200.dp, (segments.size * 46).dp)),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            segments.forEach { segment -> LegendItem(segment, loading) }
        }
    }
}

@Composable
private fun AnimatedSegment(fraction: Float, barHeight: androidx.compose.ui.unit.Dp, color: Color) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animate = true }
    val f by animateFloatAsState(
        targetValue = if (animate) fraction else 0f,
        animationSpec = tween(durationMillis = 700, easing = EaseOutCubic),
        label = "storageSegment",
    )
    if (f <= 0f) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight * f)
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.82f)))),
    )
}

@Composable
private fun LegendItem(segment: StorageSegment, loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (loading || segment.bytes > 0) 1f else 0.5f),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(segment.color),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = segment.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (loading) "…" else formatBytes(segment.bytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
