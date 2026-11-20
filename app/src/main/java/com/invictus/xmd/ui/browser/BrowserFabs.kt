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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
 * Floating action button shown while the current page has detected videos (sniffed streams, or a
 * YouTube watch/shorts page). A round 56dp button with the download icon and a green count badge on its
 * top-right corner; while detections are still being verified a thin ring spins around it. Tapping opens
 * the list sheet (or, for a YouTube page, the quality picker) -- see BrowserFragment.onSniffedMediaFabTapped.
 *
 * It sits just above the bottom nav bar while that bar is showing and glides down to the screen edge when
 * the bar auto-hides / hides on scroll, so it never floats over empty space or covers the bar's buttons.
 * Meant to be hosted in BrowserScreen's `floatingActions` slot (a fillMaxSize Box scope).
 */
@Composable
fun BrowserMediaFab(
    visible: Boolean,
    text: String,
    count: Int,
    bottomBarShowing: Boolean,
    toolbarAtBottom: Boolean,
    onClick: () -> Unit,
    checking: Boolean = false,
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
            // Extra room around the 56dp circle so the badge can overhang its corner without clipping.
            Box(modifier = Modifier.padding(top = 8.dp, end = 8.dp).semantics { contentDescription = text }) {
                FloatingActionButton(
                    onClick = onClick,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(imageVector = Icons.Download, contentDescription = null)
                }
                if (checking) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-8).dp)
                            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(2.dp)
                            .background(Color(0xFF2E9E57), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (count > 99) "99+" else count.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp),
                        )
                    }
                }
            }
        }
    }
}
