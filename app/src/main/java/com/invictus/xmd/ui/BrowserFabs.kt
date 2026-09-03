package com.invictus.xmd.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R

/**
 * Phase F of the Compose migration: fragment_browser.xml's two independent
 * FloatingActionButtons -- addLinkFab (appears when a downloadable link is
 * found on the current page) and sniffedMediaFab (a count chip that
 * appears when MediaSniffer picks up a stream) -- are now one shared
 * ComposeView (`browserFabs` in fragment_browser.xml) hosting this single
 * composable, per the user's call on this phase's ComposeView-count
 * question. Each FAB keeps the exact fixed bottom|end margins the old XML
 * had (20dp/20dp for the download FAB, 20dp horizontal + 88dp bottom for
 * the sniffed-media chip) rather than being stacked in a Column, since
 * that's how the old layout positioned them -- independently, not
 * relative to each other, so sniffedMediaFab's gap above addLinkFab holds
 * even when addLinkFab itself is hidden.
 *
 * Colors/shape reproduce Widget.Xmd.FloatingActionButton (colorPrimaryContainer
 * background, colorOnPrimaryContainer content, 28dp "Expressive" corner
 * radius) since Compose's FloatingActionButton/ExtendedFloatingActionButton
 * don't pick up that XML style automatically.
 *
 * Both taps still route straight back to the same BrowserFragment functions
 * the old click listeners called (onAddLinkClicked(), showSniffedMediaSheet())
 * -- see BrowserFragment.onViewCreated's browserFabs.setContent.
 *
 * Not independently verified -- no local Android SDK/Gradle in this
 * environment, same caveat every prior phase's summary notes.
 */
@Composable
fun BrowserFabs(
    detectedLinkVisible: Boolean,
    onDetectedLinkTap: () -> Unit,
    sniffedMediaVisible: Boolean,
    sniffedMediaText: String,
    onSniffedMediaTap: () -> Unit,
) {
    val fabShape = RoundedCornerShape(28.dp)
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(modifier = Modifier.fillMaxSize()) {
        if (sniffedMediaVisible) {
            ExtendedFloatingActionButton(
                onClick = onSniffedMediaTap,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 88.dp),
                shape = fabShape,
                containerColor = containerColor,
                contentColor = contentColor,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_video),
                        contentDescription = null,
                    )
                },
                text = { Text(sniffedMediaText) },
            )
        }
        if (detectedLinkVisible) {
            FloatingActionButton(
                onClick = onDetectedLinkTap,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                shape = fabShape,
                containerColor = containerColor,
                contentColor = contentColor,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_download_link),
                    contentDescription = stringResource(R.string.action_add_to_downloads),
                )
            }
        }
    }
}
