package com.invictus.xmd.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.AppIcon
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

/**
 * Via-style bottom navigation shown while a web page is open:
 * Back, Forward, Home, Bookmarks, Downloads. Tabs and the overflow menu stay
 * in the top toolbar. Back mirrors the system back button (history, then
 * closes a child tab, then returns to the speed dial).
 */
@Composable
internal fun BrowserBottomBar(
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onBookmarks: () -> Unit,
    onDownloads: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(Icons.ArrowBack, R.string.browser_bottom_back, onBack)
            BarButton(Icons.ArrowForward, R.string.browser_bottom_forward, onForward, enabled = canGoForward)
            BarButton(Icons.Home, R.string.browser_bottom_home, onHome)
            BarButton(Icons.Bookmarks, R.string.browser_bottom_bookmarks, onBookmarks, iconSize = 20.dp)
            BarButton(Icons.Download, R.string.browser_bottom_downloads, onDownloads)
        }
    }
}

@Composable
private fun BarButton(
    icon: AppIcon,
    label: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(label),
            modifier = Modifier.size(iconSize).alpha(if (enabled) 1f else 0.38f),
        )
    }
}
