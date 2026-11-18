@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.xmd.ui.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.utils.AppUiScale
import kotlin.math.roundToInt

/**
 * "UI size" card of Settings > Appearance (ported from Replay's App UI scale). Dragging only changes a
 * pending value with a live preview; Apply saves it and rebuilds the screen at the new size.
 */
@Composable
internal fun UiScaleCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val saved = remember { Settings.appUiScale() }
    var pending by remember { mutableFloatStateOf(saved) }
    val changed = pending != saved

    SettingsSectionCard(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_ui_scale_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_ui_scale_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = stringResource(R.string.settings_ui_scale_value, (pending * 100).roundToInt()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Slider(
            value = pending,
            onValueChange = { pending = AppUiScale.snap(it) },
            valueRange = AppUiScale.MIN..AppUiScale.MAX,
            steps = AppUiScale.SLIDER_STEPS,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        // Live preview: the same row drawn at pending/saved of the current density.
        val density = LocalDensity.current
        val ratio = pending / saved
        CompositionLocalProvider(LocalDensity provides Density(density.density * ratio, density.fontScale)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_ui_scale_preview),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                enabled = pending != AppUiScale.DEFAULT,
                onClick = { pending = AppUiScale.DEFAULT },
            ) { Text(stringResource(R.string.settings_ui_scale_reset)) }
            Button(
                shapes = ButtonDefaults.shapes(),
                enabled = changed,
                onClick = {
                    Settings.setAppUiScale(pending)
                    (context as? Activity)?.recreate()
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text(stringResource(R.string.settings_ui_scale_apply)) }
        }
    }
}
