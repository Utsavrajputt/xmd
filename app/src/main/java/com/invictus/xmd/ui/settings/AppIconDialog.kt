package com.invictus.xmd.ui.settings

import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.invictus.xmd.R
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Name of an icon: "Default" for the shipped one, otherwise its background and logo. */
@Composable
internal fun appIconName(icon: LauncherIcon): String {
    if (icon == LauncherIcon.Default) return stringResource(R.string.app_icon_default_name)
    return stringResource(
        R.string.app_icon_name_combined,
        stringResource(icon.background.label),
        stringResource(icon.mark.label),
    )
}

/**
 * Launcher icon builder: a live preview, a swipeable row of backgrounds and the logos that read on
 * the chosen background. Nothing changes on the launcher until Apply is tapped.
 */
@Composable
internal fun AppIconDialog(onDismiss: () -> Unit, onApplied: () -> Unit = {}) {
    val context = LocalContext.current
    var current by remember {
        mutableStateOf(runCatching { LauncherIconManager.current(context) }.getOrDefault(LauncherIcon.Default))
    }
    var pending by remember { mutableStateOf(current) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = stringResource(R.string.settings_app_icon_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.app_icon_picker_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                IconBuilder(pending = pending, onPick = { pending = it })

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_icon_close))
                    }
                    Button(
                        onClick = {
                            try {
                                LauncherIconManager.select(context, pending)
                                onApplied()
                            } catch (_: Exception) {
                                Toast.makeText(context, R.string.app_icon_change_failed, Toast.LENGTH_LONG).show()
                            }
                            current = runCatching { LauncherIconManager.current(context) }.getOrDefault(current)
                            pending = current
                        },
                        enabled = pending != current,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.app_icon_apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun IconBuilder(pending: LauncherIcon, onPick: (LauncherIcon) -> Unit) {
    val backgrounds = IconBackground.entries
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = pending.background.ordinal) { backgrounds.size }
    val latest by rememberUpdatedState(pending)

    // The pager owns the background: a swipe on it or on the preview, or a tap on a swatch, all end up here.
    // A logo that does not read on the new background gives way to that background's own.
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page ->
            val background = backgrounds[page]
            if (background != latest.background) onPick(LauncherIcon.resolve(background, latest.mark))
        }
    }

    fun turn(step: Int) {
        val target = (pager.currentPage + step).coerceIn(0, backgrounds.lastIndex)
        scope.launch { pager.animateScrollToPage(target) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconPreview(
            background = pending.background,
            mark = pending.mark,
            size = 120.dp,
            modifier = Modifier.pointerInput(Unit) {
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = { if (abs(travelled) > 48.dp.toPx()) turn(if (travelled < 0f) 1 else -1) },
                ) { change, delta ->
                    change.consume()
                    travelled += delta
                }
            },
        )

        AnimatedContent(
            targetState = appIconName(pending),
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "iconName",
        ) { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        SectionLabel(stringResource(R.string.app_icon_section_background))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val swatch = 56.dp
            val gap = 24.dp
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(swatch + gap),
                // Centres the current swatch and lets its neighbours peek in from both sides
                contentPadding = PaddingValues(horizontal = (maxWidth - swatch - gap) / 2),
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val background = backgrounds[page]
                val distance = abs(page - pager.currentPage - pager.currentPageOffsetFraction).coerceAtMost(2f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val factor = 1f - 0.18f * distance
                            scaleX = factor
                            scaleY = factor
                            alpha = 1f - 0.25f * distance
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            scope.launch { pager.animateScrollToPage(page) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    IconPreview(background, LauncherIcon.resolve(background, pending.mark).mark, swatch)
                }
            }
        }

        SectionLabel(stringResource(R.string.app_icon_section_logo))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconMark.entries.forEach { mark ->
                val icon = LauncherIcon.of(pending.background, mark)
                MarkTile(
                    background = pending.background,
                    mark = mark,
                    selected = pending.mark == mark,
                    enabled = icon != null,
                    onClick = { icon?.let(onPick) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** A logo choice, drawn on the background it would sit on. A logo that would not read there stays off. */
@Composable
private fun MarkTile(
    background: IconBackground,
    mark: IconMark,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceContainer,
            )
            .border(border, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.alpha(if (enabled) 1f else 0.35f)) {
            IconPreview(background, mark, 44.dp)
        }
        Text(
            text = stringResource(mark.label),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * An icon drawn from its two layers, so a new background fades in under the logo and a new logo
 * pops in over the background. The corners match what a launcher gives the icon.
 */
@Composable
private fun IconPreview(
    background: IconBackground,
    mark: IconMark,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val fill by animateColorAsState(colorResource(background.color), tween(350), label = "iconBackground")
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = mark,
            transitionSpec = {
                (scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f)) +
                    fadeIn(tween(200))) togetherWith (scaleOut(targetScale = 1.2f) + fadeOut(tween(150)))
            },
            label = "iconMark",
        ) { shown ->
            MarkLayer(shown, size)
        }
    }
}

/**
 * One adaptive foreground layer. A layer is 108dp with the visible icon in its middle 72dp, so it is
 * drawn 1.5 times [size] and cropped by the parent, as a launcher does. The platform ImageView
 * renders the vector, since the logo uses gradient fills that Compose's painter may not handle.
 */
@Composable
private fun MarkLayer(mark: IconMark, size: Dp) {
    AndroidView(
        modifier = Modifier.requiredSize(size * 1.5f),
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageResource(mark.layer)
            }
        },
        update = { it.setImageResource(mark.layer) },
    )
}
