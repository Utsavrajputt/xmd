package com.invictus.xmd.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.invictus.xmd.R
import com.invictus.xmd.domain.browser.MediaSniffer
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.XmdTheme

/**
 * IDM-style "fetch from website" recovery for a direct link that's gone
 * stale (expired token, dead CDN link, etc.) with no share-link resolver
 * of its own to fall back on. Unlike [ChallengeActivity] -- which speaks
 * FuckingFast's specific HTMX endpoint -- this is completely site-agnostic:
 * it just re-opens the page the link was originally captured from (see
 * [com.invictus.xmd.database.entities.QueueItem.pageUrl]) in a real WebView
 * and waits for that page to hand out a fresh download link, the same way
 * a person would manually re-visit the page and click Download again.
 *
 * Two ways a fresh link can surface, both already used elsewhere in the
 * app for the same signal (see BrowserFragment):
 *  - the WebView's native "start a download" callback ([WebView.setDownloadListener]),
 *    e.g. an `<a download>` click or a Content-Disposition redirect
 *  - a sub-resource request the page makes that itself looks like a
 *    direct media file ([MediaSniffer.classifyUrl]) -- covers sites that
 *    play/serve the file (e.g. into a `<video>` tag) rather than
 *    triggering a real browser download
 *
 * Whichever fires first wins; the user can also just back out (Cancel) if
 * the page turns out to be dead too.
 */
class LinkRefetchActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PAGE_URL = "extra_page_url"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_DIRECT_URL = "extra_direct_url"
        const val EXTRA_ERROR = "extra_error"
    }

    private lateinit var pageUrl: String
    private var finished = false

    private var statusMessage by mutableStateOf("")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        com.invictus.xmd.ui.theme.AppTheme.applyTo(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pageUrl = intent.getStringExtra(EXTRA_PAGE_URL) ?: run { finishWithError("Missing page URL"); return }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)

        onBackPressedDispatcher.addCallback(this) { finishCancelled() }

        statusMessage = if (fileName != null) {
            getString(R.string.link_refetch_hint_named, fileName)
        } else {
            getString(R.string.link_refetch_hint)
        }

        setContent {
            XmdTheme {
                LinkRefetchScreen(
                    statusMessage = statusMessage,
                    pageUrl = pageUrl,
                    onDownloadCaptured = { url -> finishWithSuccess(url) },
                    onNavigateBack = { finishCancelled() },
                )
            }
        }
    }

    private fun finishWithSuccess(directUrl: String) {
        if (finished) return
        finished = true
        val result = Intent().putExtra(EXTRA_DIRECT_URL, directUrl)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun finishWithError(message: String) {
        if (finished) return
        finished = true
        val result = Intent().putExtra(EXTRA_ERROR, message)
        setResult(RESULT_CANCELED, result)
        finish()
    }

    private fun finishCancelled() = finishWithError("Cancelled by user")

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LinkRefetchScreen(
        statusMessage: String,
        pageUrl: String,
        onDownloadCaptured: (String) -> Unit,
        onNavigateBack: () -> Unit,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.link_refetch_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.ArrowBack,
                                contentDescription = stringResource(android.R.string.cancel),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                Text(
                    statusMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Box(Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                // A fresh generated link is almost always
                                // gated behind the page's own captcha/timer
                                // widget just like the original one was --
                                // matching BrowserFragment's own WebView
                                // setup here (rather than a stripped-down
                                // config) so those widgets actually render.
                                webViewClient = object : WebViewClient() {
                                    // Called on a background thread -- MediaSniffer.classifyUrl
                                    // itself is pure/cheap (see its doc comment) so that part is
                                    // fine here, but onDownloadCaptured ends in Activity.finish(),
                                    // which must happen on the main thread, hence view.post.
                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): WebResourceResponse? {
                                        if (!request.isForMainFrame) {
                                            val url = request.url?.toString()
                                            val sniffed = url?.let { MediaSniffer.classifyUrl(it) }
                                            if (sniffed != null) view.post { onDownloadCaptured(sniffed.url) }
                                        }
                                        return null
                                    }
                                }
                                setDownloadListener { url, _, _, _, _ -> onDownloadCaptured(url) }
                                loadUrl(pageUrl)
                            }
                        },
                    )
                }
            }
        }
    }
}
