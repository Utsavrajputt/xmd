package com.invictus.xmd.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.core.Settings
import com.invictus.xmd.ui.theme.AppTheme

/**
 * Theme color + dark mode / AMOLED switches, plus bottom-nav tab config
 * (reorder / hide / default tab). [onThemeSelected]/[onDarkModeChanged]/
 * [onAmoledModeChanged] each persist to [Settings] and trigger
 * `activity.recreate()` on the caller side (see SettingsActivity's
 * AppearanceRoute) exactly like the old dialog/Fragment did -- recreate()
 * repaints the whole Activity with the new XML theme applied, so this
 * composable doesn't need to hold reactive color-scheme state itself.
 *
 * The tab config section doesn't need a recreate() from here -- it only
 * writes to Settings; MainActivity notices the change and recreates itself
 * on its own next onResume (same mechanism it already uses for theme
 * changes), so backing out of Settings repaints the nav bar automatically.
 */
@Composable
fun SettingsAppearanceScreen(
    currentTheme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
    onThemeSelected: (AppTheme) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit,
    onAmoledModeChanged: (Boolean) -> Unit,
    tabOrder: List<String>,
    hiddenTabs: Set<String>,
    defaultTab: String,
    onMoveTab: (fromIndex: Int, toIndex: Int) -> Unit,
    onToggleTabVisible: (tabId: String, visible: Boolean) -> Unit,
    onDefaultTabSelected: (tabId: String) -> Unit,
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

        SettingsSectionCard(modifier = Modifier.padding(top = 16.dp)) {
            TabsSection(
                tabOrder = tabOrder,
                hiddenTabs = hiddenTabs,
                defaultTab = defaultTab,
                onMoveTab = onMoveTab,
                onToggleTabVisible = onToggleTabVisible,
                onDefaultTabSelected = onDefaultTabSelected,
            )
        }
    }
}

private data class TabMeta(val iconRes: Int, val labelRes: Int)

private val TAB_META = mapOf(
    Settings.TabId.HOME to TabMeta(XmdIcons.Home, R.string.tab_home),
    Settings.TabId.DOWNLOADS to TabMeta(XmdIcons.Downloads, R.string.tab_downloads),
    Settings.TabId.ADD to TabMeta(XmdIcons.Add, R.string.tab_add),
    Settings.TabId.BROWSER to TabMeta(XmdIcons.Public, R.string.tab_browser),
)

@Composable
private fun TabsSection(
    tabOrder: List<String>,
    hiddenTabs: Set<String>,
    defaultTab: String,
    onMoveTab: (fromIndex: Int, toIndex: Int) -> Unit,
    onToggleTabVisible: (tabId: String, visible: Boolean) -> Unit,
    onDefaultTabSelected: (tabId: String) -> Unit,
) {
    val context = LocalContext.current

    Text(
        text = stringResource(R.string.settings_tabs_title),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.settings_tabs_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )

    tabOrder.forEachIndexed { index, tabId ->
        val meta = TAB_META[tabId] ?: return@forEachIndexed
        TabConfigRow(
            iconRes = meta.iconRes,
            label = stringResource(meta.labelRes),
            visible = tabId !in hiddenTabs,
            canMoveUp = index > 0,
            canMoveDown = index < tabOrder.lastIndex,
            onMoveUp = { onMoveTab(index, index - 1) },
            onMoveDown = { onMoveTab(index, index + 1) },
            onVisibleChange = { visible ->
                if (!visible && tabId in Settings.TabId.PAGES) {
                    val remainingVisiblePages = Settings.TabId.PAGES.count { it != tabId && it !in hiddenTabs }
                    if (remainingVisiblePages == 0) {
                        Toast.makeText(context, R.string.settings_tabs_last_visible_toast, Toast.LENGTH_SHORT).show()
                        return@TabConfigRow
                    }
                }
                onToggleTabVisible(tabId, visible)
            },
        )
    }

    SettingsDivider()

    Text(
        text = stringResource(R.string.settings_default_tab_title),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.settings_default_tab_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
    )

    tabOrder
        .filter { it in Settings.TabId.PAGES && it !in hiddenTabs }
        .forEach { tabId ->
            val meta = TAB_META[tabId] ?: return@forEach
            DefaultTabRadioRow(
                iconRes = meta.iconRes,
                label = stringResource(meta.labelRes),
                selected = tabId == defaultTab,
                onSelect = { onDefaultTabSelected(tabId) },
            )
        }
}

/** One row in the reorder/hide list: icon + label, up/down move buttons,
 *  trailing visibility switch. Up/down arrows (not drag) -- simplest robust
 *  reorder gesture for a 4-item list; this project's only precedent for
 *  Compose drag-to-reorder is ShortcutsScreen's 2D grid, whose bounds-
 *  crossing math is grid-specific and not worth adapting for four rows. */
@Composable
private fun TabConfigRow(
    iconRes: Int,
    label: String,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(
                    painter = painterResource(XmdIcons.ArrowUp),
                    contentDescription = stringResource(R.string.action_move_up),
                    tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(
                    painter = painterResource(XmdIcons.ArrowDown),
                    contentDescription = stringResource(R.string.action_move_down),
                    tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp).size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = visible, onCheckedChange = onVisibleChange)
    }
}

@Composable
private fun DefaultTabRadioRow(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp).size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
