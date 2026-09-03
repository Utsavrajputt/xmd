package com.invictus.xmd.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import com.invictus.xmd.R
import kotlin.math.roundToInt

/**
 * Phase 5 (Browser) conversion of the old showLinkContextMenu() --
 * previously a platform PopupMenu inflating menu/link_context_menu.xml,
 * anchored to a throwaway 1x1 invisible View dropped into webViewContainer
 * at the last touch position. Compose's DropdownMenu has no equivalent of
 * "anchor to an arbitrary point with no real view there" -- this replaces
 * the invisible-anchor-View trick with a zero-size Box positioned via the
 * pixel-based `Modifier.offset { IntOffset }` overload at the same
 * (touchX, touchY), which DropdownMenu then anchors against the same way
 * it would a real button.
 *
 * [state] carries the same linkUrl/imageUrl-nullability shape
 * showLinkContextMenu()'s `when (result.type)` branch always produced --
 * this composable does no HitTestResult logic itself, just renders what
 * BrowserFragment already resolved.
 */
data class LinkContextMenuState(
    val touchX: Float,
    val touchY: Float,
    val linkUrl: String?,
    val imageUrl: String?,
)

@Composable
fun LinkContextMenu(
    state: LinkContextMenuState,
    onDismiss: () -> Unit,
    onOpenNewTab: (String) -> Unit,
    onOpenImageNewTab: (String) -> Unit,
    onDownloadImage: (String) -> Unit,
    onCopyLinkAddress: (String) -> Unit,
    onShareLink: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            // Pixel values, not dp -- touchX/touchY come straight from a raw
            // MotionEvent, same units the old 1x1-View-anchor trick used, so
            // the offset lambda overload (raw px IntOffset) is the right one
            // here rather than the Dp-based offset(x, y) overload.
            modifier = Modifier.offset {
                IntOffset(state.touchX.roundToInt(), state.touchY.roundToInt())
            },
        ) {
            DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
                val link = state.linkUrl
                val image = state.imageUrl
                if (!link.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.link_menu_open_new_tab)) },
                        onClick = { onOpenNewTab(link); onDismiss() },
                    )
                }
                if (!image.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.link_menu_open_image_new_tab)) },
                        onClick = { onOpenImageNewTab(image); onDismiss() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.link_menu_download_image)) },
                        onClick = { onDownloadImage(image); onDismiss() },
                    )
                }
                if (!link.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.link_menu_copy_link_address)) },
                        onClick = { onCopyLinkAddress(link); onDismiss() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.link_menu_share_link)) },
                        onClick = { onShareLink(link); onDismiss() },
                    )
                }
            }
        }
    }
}
