package com.invictus.xmd.ui.browser

import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons

/** A page video the WebView couldn't play, handed to the in-app player. */
internal data class InAppPlayRequest(val url: String, val pageUrl: String?)

/**
 * Full-screen in-app player for videos the WebView can't play. Same
 * VideoView-in-a-black-dialog approach as the Status Saver preview, plus a
 * seek bar (MediaController) since these are usually full-length videos, and
 * the page's Referer / User-Agent / cookies so hotlink-protected files load.
 * If even this player can't decode the stream it offers the external-player
 * handoff instead of leaving a black screen.
 */
@Composable
internal fun InAppVideoPlayerDialog(
    request: InAppPlayRequest,
    userAgent: String?,
    onOpenExternal: () -> Unit,
    onDismiss: () -> Unit,
) {
    var failed by remember(request.url) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (!failed) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        FrameLayout(context).apply {
                            val video = VideoView(context)
                            val controller = MediaController(context)
                            controller.setAnchorView(this)
                            video.setMediaController(controller)
                            val headers = buildMap<String, String> {
                                request.pageUrl?.let { put("Referer", it) }
                                userAgent?.let { put("User-Agent", it) }
                                CookieManager.getInstance().getCookie(request.url)
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { put("Cookie", it) }
                            }
                            video.setVideoURI(Uri.parse(request.url), headers)
                            video.setOnPreparedListener { it.isLooping = false; video.start() }
                            video.setOnErrorListener { _, _, _ -> failed = true; true }
                            addView(
                                video,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    Gravity.CENTER,
                                ),
                            )
                        }
                    },
                    onRelease = { root -> (root.getChildAt(0) as? VideoView)?.stopPlayback() },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.inapp_player_error),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { onOpenExternal(); onDismiss() },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(stringResource(R.string.inapp_player_open_external))
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Close,
                    contentDescription = stringResource(R.string.status_close),
                    tint = Color.White,
                )
            }
        }
    }
}
