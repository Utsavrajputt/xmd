@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.settings

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.invictus.xmd.R
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.WallpaperConfig
import com.invictus.xmd.ui.theme.WallpaperImage
import com.invictus.xmd.ui.theme.WallpaperPreset
import com.invictus.xmd.ui.theme.WallpaperScaleMode
import com.invictus.xmd.ui.theme.deleteStoredWallpaper
import com.invictus.xmd.ui.theme.drawWallpaperPreset
import com.invictus.xmd.ui.theme.loadWallpaperBitmap
import com.invictus.xmd.ui.theme.saveWallpaperCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Full-screen wallpaper editor, same options as mpvRx: presets or a picked image, Fit/Fill,
 * pinch/drag, zoom/position/blur/transparency. Nothing is applied until Save.
 */
@Composable
internal fun WallpaperDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { Settings.wallpaper() }

    var selectedSource by rememberSaveable { mutableStateOf(saved.uri) }
    var zoom by rememberSaveable { mutableStateOf(saved.zoom) }
    var offsetX by rememberSaveable { mutableStateOf(saved.offsetX) }
    var offsetY by rememberSaveable { mutableStateOf(saved.offsetY) }
    var scaleMode by rememberSaveable { mutableStateOf(saved.scaleMode) }
    var blur by rememberSaveable { mutableStateOf(saved.blur) }
    var alpha by rememberSaveable { mutableStateOf(saved.alpha) }
    var previewLocked by rememberSaveable { mutableStateOf(false) }
    var previewAspect by rememberSaveable { mutableStateOf<Float?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val screenConfig = LocalConfiguration.current
    val deviceRatio = minOf(screenConfig.screenWidthDp, screenConfig.screenHeightDp).toFloat() /
        maxOf(screenConfig.screenWidthDp, screenConfig.screenHeightDp).toFloat()

    val loaded by produceState<Pair<String, Bitmap?>?>(initialValue = null, selectedSource) {
        val source = selectedSource
        val bmp = if (source.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching { loadWallpaperBitmap(context, source) }.getOrNull()
        }
        value = source to bmp
    }
    // Ignore a bitmap that belongs to the previously selected source while the new one loads.
    val bitmap = loaded?.takeIf { it.first == selectedSource }?.second
    val isNoneSelected = selectedSource.isBlank()
    val isCustomSelected = selectedSource.isNotBlank() && !WallpaperPreset.isPresetUri(selectedSource)

    fun resetPosition() {
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            selectedSource = uri.toString()
            resetPosition()
            scaleMode = WallpaperScaleMode.Fit
        }
    }

    fun save() {
        isSaving = true
        scope.launch {
            try {
                val finalUri = if (isNoneSelected) {
                    deleteStoredWallpaper(saved.uri)
                    ""
                } else {
                    saveWallpaperCopy(context, selectedSource, saved.uri)
                }
                Settings.setWallpaper(
                    if (finalUri.isBlank()) WallpaperConfig()
                    else WallpaperConfig(finalUri, zoom, offsetX, offsetY, scaleMode, blur, alpha),
                )
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, R.string.wallpaper_save_failed, Toast.LENGTH_LONG).show()
            } finally {
                isSaving = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !isSaving),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), enabled = !isSaving, onClick = onDismiss) {
                        Icon(Icons.Close, contentDescription = stringResource(R.string.wallpaper_cancel))
                    }
                    Text(
                        text = stringResource(R.string.wallpaper_editor_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    TextButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), 
                        enabled = (bitmap != null || isNoneSelected) && !isSaving,
                        onClick = ::save,
                    ) { Text(stringResource(R.string.wallpaper_save)) }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ScaleModeToggle(
                        selected = scaleMode,
                        onSelect = {
                            scaleMode = it
                            resetPosition()
                        },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
                            onClick = { previewLocked = !previewLocked },
                            modifier = Modifier.background(
                                if (previewLocked) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                CircleShape,
                            ),
                        ) {
                            Icon(
                                if (previewLocked) Icons.Lock else Icons.LockOpen,
                                contentDescription = stringResource(
                                    if (previewLocked) R.string.wallpaper_preview_locked
                                    else R.string.wallpaper_preview_unlocked,
                                ),
                                tint = if (previewLocked) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ASPECT_PRESETS, key = { it.label }) { preset ->
                                FilterChip(
                                    selected = previewAspect == preset.ratio,
                                    onClick = { previewAspect = preset.ratio },
                                    label = {
                                        Text(
                                            if (preset.ratio == null) stringResource(R.string.wallpaper_aspect_device)
                                            else preset.label,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // Preview frame shaped like the real screen, so Fit/Fill/zoom/position match 1:1.
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val ratio = previewAspect ?: deviceRatio
                        val maxPreviewHeight = 340.dp
                        val fitsByHeight = maxPreviewHeight * ratio <= maxWidth
                        val frameHeight = if (fitsByHeight) maxPreviewHeight else maxWidth / ratio
                        val frameWidth = if (fitsByHeight) maxPreviewHeight * ratio else maxWidth
                        val frameShape = RoundedCornerShape(22.dp)
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(frameWidth)
                                .height(frameHeight)
                                .clip(frameShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .border(3.dp, MaterialTheme.colorScheme.outline, frameShape)
                                .pointerInput(previewLocked) {
                                    if (previewLocked) return@pointerInput
                                    detectTransformGestures { _, pan, gestureZoom, _ ->
                                        zoom = (zoom * gestureZoom).coerceIn(1f, 3f)
                                        if (size.width > 0 && size.height > 0) {
                                            offsetX = (offsetX + pan.x / (size.width * 0.35f)).coerceIn(-1f, 1f)
                                            offsetY = (offsetY + pan.y / (size.height * 0.35f)).coerceIn(-1f, 1f)
                                        }
                                    }
                                },
                        ) {
                            bitmap?.let {
                                WallpaperImage(
                                    bitmap = it,
                                    zoom = zoom,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    scaleMode = scaleMode,
                                    blurRadius = blur,
                                    imageAlpha = alpha,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            if (isNoneSelected) {
                                Text(
                                    text = stringResource(R.string.wallpaper_none_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.wallpaper_gesture_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = stringResource(R.string.wallpaper_presets_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "none") {
                            PresetCard(
                                label = stringResource(R.string.wallpaper_preset_none),
                                isSelected = isNoneSelected,
                                onClick = {
                                    selectedSource = ""
                                    resetPosition()
                                },
                            ) {
                                Icon(
                                    Icons.Close,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.Center).size(30.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(WallpaperPreset.entries, key = { it.id }) { preset ->
                            PresetCard(
                                label = stringResource(preset.labelRes),
                                isSelected = selectedSource == preset.uri,
                                onClick = {
                                    selectedSource = preset.uri
                                    resetPosition()
                                    scaleMode = WallpaperScaleMode.Fit
                                },
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) { drawWallpaperPreset(preset) }
                            }
                        }
                        item(key = "custom") {
                            PresetCard(
                                label = stringResource(R.string.wallpaper_preset_custom),
                                isSelected = isCustomSelected,
                                onClick = { picker.launch(arrayOf("image/*")) },
                            ) {
                                if (isCustomSelected && bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.55f))
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color.White,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.wallpaper_replace),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            maxLines = 1,
                                        )
                                    }
                                } else {
                                    Icon(
                                        Icons.Add,
                                        contentDescription = null,
                                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    if (!isNoneSelected) {
                        WallpaperSlider(stringResource(R.string.wallpaper_zoom), zoom, 1f..3f, { zoom = it }) {
                            "${(it * 100).roundToInt()}%"
                        }
                        WallpaperSlider(stringResource(R.string.wallpaper_horizontal), offsetX, -1f..1f, { offsetX = it }) {
                            "${(it * 100).roundToInt()}%"
                        }
                        WallpaperSlider(stringResource(R.string.wallpaper_vertical), offsetY, -1f..1f, { offsetY = it }) {
                            "${(it * 100).roundToInt()}%"
                        }
                        WallpaperSlider(stringResource(R.string.wallpaper_blur), blur, 0f..40f, { blur = it }) {
                            "${it.roundToInt()}"
                        }
                        WallpaperSlider(stringResource(R.string.wallpaper_transparency), alpha, 0f..1f, { alpha = it }) {
                            "${(it * 100).roundToInt()}%"
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), 
                            onClick = {
                                resetPosition()
                                scaleMode = WallpaperScaleMode.Fit
                                blur = 0f
                                alpha = 1f
                            },
                        ) { Text(stringResource(R.string.wallpaper_reset)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    valueText: (Float) -> String,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = valueText(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

/** Fit / Fill switch: the selected side is a solid primary pill with a check mark. */
@Composable
private fun ScaleModeToggle(selected: WallpaperScaleMode, onSelect: (WallpaperScaleMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val options = listOf(
        WallpaperScaleMode.Fit to stringResource(R.string.wallpaper_fit),
        WallpaperScaleMode.Fill to stringResource(R.string.wallpaper_fill),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (mode, label) ->
            val isSelected = selected == mode
            val shape = RoundedCornerShape(50)
            val content = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(shape)
                    .background(if (isSelected) colors.primary else colors.surfaceContainerHighest)
                    .then(if (isSelected) Modifier else Modifier.border(1.dp, colors.outlineVariant, shape))
                    .clickable { onSelect(mode) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    Icon(Icons.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = content)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = content,
                )
            }
        }
    }
}

@Composable
private fun PresetCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 112.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                )
                .clickable(onClick = onClick),
            content = content,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private data class AspectPreset(val label: String, val ratio: Float?)

private val ASPECT_PRESETS = listOf(
    AspectPreset("Free", null),
    AspectPreset("9:16", 9f / 16f),
    AspectPreset("9:19.5", 9f / 19.5f),
    AspectPreset("3:4", 3f / 4f),
    AspectPreset("1:1", 1f),
    AspectPreset("16:9", 16f / 9f),
)
