package com.invictus.xmd.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.ui.theme.AppTheme

/**
 * Theme color + dark mode / AMOLED switches. [onThemeSelected]/
 * [onDarkModeChanged]/[onAmoledModeChanged] each persist to [Settings] and
 * trigger `activity.recreate()` on the caller side (see SettingsActivity's
 * AppearanceRoute) exactly like the old dialog/Fragment did -- recreate()
 * repaints the whole Activity with the new XML theme applied, so this
 * composable doesn't need to hold reactive color-scheme state itself.
 */
@Composable
fun SettingsAppearanceScreen(
    currentTheme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
    onThemeSelected: (AppTheme) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit,
    onAmoledModeChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SettingsSectionCard {
            Text(
                text = stringResource(R.string.settings_theme_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState())) {
                AppTheme.entries.forEach { theme ->
                    ThemeSwatchItem(
                        theme = theme,
                        isSelected = theme == currentTheme,
                        isDark = isDark,
                        isAmoled = isAmoled,
                        onClick = { onThemeSelected(theme) },
                    )
                }
            }

            SettingsDivider()

            SwitchSettingRow(
                title = stringResource(R.string.settings_dark_mode),
                subtitle = stringResource(R.string.settings_dark_mode_hint),
                checked = isDark,
                onCheckedChange = onDarkModeChanged,
            )

            SettingsDivider()

            SwitchSettingRow(
                title = stringResource(R.string.settings_amoled_mode),
                subtitle = stringResource(R.string.settings_amoled_mode_hint),
                checked = isAmoled,
                enabled = isDark,
                onCheckedChange = onAmoledModeChanged,
            )
        }
    }
}
