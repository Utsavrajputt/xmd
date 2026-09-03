package com.invictus.xmd.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.invictus.xmd.R

internal enum class BrowserMenuAction {
    PrivateDns,
    Bookmarks,
    History,
    Settings,
}

internal data class BrowserDownloadPrompt(
    val url: String,
    val fileName: String,
)

@Composable
internal fun BrowserScreen(
    speedDialVisible: Boolean,
    toolbar: @Composable () -> Unit,
    onWebViewHostReady: (SwipeRefreshLayout, FrameLayout) -> Unit,
    speedDial: @Composable () -> Unit,
    suggestions: @Composable () -> Unit,
    findInPage: @Composable () -> Unit,
    loadingVeil: @Composable () -> Unit,
    floatingActions: @Composable BoxScope.() -> Unit,
    dialogs: @Composable BoxScope.() -> Unit,
    tabsOverlay: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            toolbar()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                BrowserWebViewHost(onReady = onWebViewHostReady)
                if (speedDialVisible) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        speedDial()
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    suggestions()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    findInPage()
                }
                loadingVeil()
            }
        }
        floatingActions()
        dialogs()
        tabsOverlay()
    }
}

@Composable
private fun BrowserWebViewHost(
    onReady: (SwipeRefreshLayout, FrameLayout) -> Unit,
) {
    AndroidView(
        factory = { context ->
            val webViewContainer = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            SwipeRefreshLayout(context).apply {
                addView(webViewContainer)
                onReady(this, webViewContainer)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal fun BrowserOverflowMenu(
    expanded: Boolean,
    desktopSiteEnabled: Boolean,
    currentPageAvailable: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onFindInPage: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onCopyPage: () -> Unit,
    onSharePage: () -> Unit,
    onClearBrowsingData: () -> Unit,
    onAction: (BrowserMenuAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        BrowserMenuItem(
            label = stringResource(R.string.browser_menu_refresh),
            icon = XmdIcons.Refresh,
            onClick = { onDismiss(); onRefresh() },
        )
        BrowserMenuItem(
            label = stringResource(R.string.find_in_page_menu),
            icon = XmdIcons.FindInPage,
            onClick = { onDismiss(); onFindInPage() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_menu_desktop_site)) },
            leadingIcon = {
                Icon(painterResource(XmdIcons.Desktop), contentDescription = null)
            },
            trailingIcon = {
                Checkbox(checked = desktopSiteEnabled, onCheckedChange = null)
            },
            onClick = { onDismiss(); onToggleDesktopSite() },
        )
        BrowserMenuItem(
            label = stringResource(R.string.link_menu_copy_link_address),
            icon = XmdIcons.Copy,
            enabled = currentPageAvailable,
            onClick = { onDismiss(); onCopyPage() },
        )
        BrowserMenuItem(
            label = stringResource(R.string.link_menu_share_link),
            icon = XmdIcons.Share,
            enabled = currentPageAvailable,
            onClick = { onDismiss(); onSharePage() },
        )
        HorizontalDivider()
        BrowserMenuItem(
            label = stringResource(R.string.browser_menu_private_dns),
            icon = XmdIcons.Dns,
            onClick = { onDismiss(); onAction(BrowserMenuAction.PrivateDns) },
        )
        BrowserMenuItem(
            label = stringResource(R.string.browser_menu_clear_data),
            icon = XmdIcons.DeleteSweep,
            onClick = { onDismiss(); onClearBrowsingData() },
        )
        HorizontalDivider()
        BrowserMenuItem(
            label = stringResource(R.string.browser_menu_bookmarks),
            icon = XmdIcons.Bookmarks,
            onClick = { onDismiss(); onAction(BrowserMenuAction.Bookmarks) },
        )
        BrowserMenuItem(
            label = stringResource(R.string.browser_menu_history),
            icon = XmdIcons.History,
            onClick = { onDismiss(); onAction(BrowserMenuAction.History) },
        )
        HorizontalDivider()
        BrowserMenuItem(
            label = stringResource(R.string.menu_settings),
            icon = XmdIcons.Settings,
            onClick = { onDismiss(); onAction(BrowserMenuAction.Settings) },
        )
    }
}

@Composable
private fun BrowserMenuItem(
    label: String,
    icon: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
internal fun ClearBrowsingDataDialog(
    onDismiss: () -> Unit,
    onClear: (history: Boolean, cookies: Boolean, cache: Boolean) -> Unit,
) {
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_data_title)) },
        text = {
            Column {
                ClearDataOption(
                    label = stringResource(R.string.clear_data_history),
                    checked = clearHistory,
                    onCheckedChange = { clearHistory = it },
                )
                ClearDataOption(
                    label = stringResource(R.string.clear_data_cookies),
                    checked = clearCookies,
                    onCheckedChange = { clearCookies = it },
                )
                ClearDataOption(
                    label = stringResource(R.string.clear_data_cache),
                    checked = clearCache,
                    onCheckedChange = { clearCache = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = clearHistory || clearCookies || clearCache,
                onClick = {
                    onClear(clearHistory, clearCookies, clearCache)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.clear_data_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun ClearDataOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
internal fun BrowserDownloadConfirmationDialog(
    prompt: BrowserDownloadPrompt,
    onDismiss: () -> Unit,
    onCopyLink: (String) -> Unit,
    onAddToDownloads: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_confirm_title)) },
        text = { Text(stringResource(R.string.download_confirm_message, prompt.fileName)) },
        confirmButton = {
            TextButton(onClick = { onAddToDownloads(prompt.url); onDismiss() }) {
                Text(stringResource(R.string.action_add_to_downloads))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onCopyLink(prompt.url); onDismiss() }) {
                    Text(stringResource(R.string.action_copy_link))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}