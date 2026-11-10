package com.invictus.xmd.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.XmdShapes

/**
 * Floating action button shown while the current page has detected videos
 * (sniffed streams, or a YouTube watch/shorts page). Label is the video
 * count ("1 video" / "3 videos"); tapping it opens the list dialog (or, for
 * a YouTube page, the quality picker) -- see BrowserFragment.onSniffedMediaFabTapped.
 *
 * It sits just above the bottom nav bar while that bar is showing and glides
 * down to the screen edge when the bar auto-hides / hides on scroll, so it
 * never floats over empty space or covers the bar's buttons.
 *
 * Same colors/shape as Widget.Xmd.FloatingActionButton (primaryContainer,
 * 28dp "Expressive" corners). Meant to be hosted in BrowserScreen's
 * `floatingActions` slot (a fillMaxSize Box scope).
 */
@Composable
fun BrowserMediaFab(
    visible: Boolean,
    text: String,
    bottomBarShowing: Boolean,
    toolbarAtBottom: Boolean,
    onClick: () -> Unit,
) {
    // 52dp bar + 16dp gap | 16dp from the edge | 88dp over a bottom address bar.
    val targetBottom = when {
        toolbarAtBottom -> 88.dp
        bottomBarShowing -> 68.dp
        else -> 16.dp
    }
    val bottom by animateDpAsState(targetValue = targetBottom, label = "mediaFabBottom")

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = bottom),
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            ExtendedFloatingActionButton(
                onClick = onClick,
                shape = XmdShapes.L,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(imageVector = Icons.Download, contentDescription = null) },
                text = { Text(text) },
            )
        }
    }
}
