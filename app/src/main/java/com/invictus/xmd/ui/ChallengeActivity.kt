package com.invictus.xmd.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.invictus.xmd.R
import com.invictus.xmd.ui.theme.XmdTheme

/**
 * Shows the share page in a visible WebView so the user can clear Cloudflare
 * / Turnstile exactly as they would in the desktop app's browser window.
 * Once cleared, injected JS calls the same HTMX "/f/{id}/go" endpoint the
 * desktop app calls and returns the resulting direct URL.
 *
 * Kotlin port of ff_downloader/core/browser_resolver.py's interactive flow.
 *
 * Phase B conversion: chrome (toolbar + status line) moved to Compose,
 * `ComponentActivity` + `setContent {}` instead of `AppCompatActivity` +
 * `setContentView(R.layout.activity_challenge)` -- the first Activity in
 * this codebase to go Compose-first rather than hosting a ComposeView
 * inside an XML layout, deliberately picked as the smallest such
 * conversion (warm-up before Phase C's NavHost work). The WebView itself
 * is unchanged -- Compose has no native WebView, so it's wrapped in
 * [AndroidView] via `remember { WebView(context) }` instead of a
 * `lateinit var` field. All polling/JS-bridge/timeout logic below is
 * untouched from the pre-Compose version, only the View<->Compose plumbing
 * around it changed.
 *
 * Back handling uses [androidx.activity.OnBackPressedDispatcher] (matches
 * MainActivity's `addCallback` pattern) instead of overriding the
 * deprecated `onBackPressed()`, so predictive-back gestures still work.
 */
class ChallengeActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SHARE_URL = "extra_share_url"
        const val EXTRA_FILE_ID = "extra_file_id"
        const val EXTRA_DIRECT_URL = "extra_direct_url"
        const val EXTRA_ERROR = "extra_error"
        private const val POLL_INTERVAL_MS = 1500L
        private const val TIMEOUT_MS = 120_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var elapsedMs = 0L
    private var finished = false
    private lateinit var shareUrl: String
    private lateinit var fileId: String

    // Backs the status line -- written from the JS bridge (onLog) and the
    // poll timeout/error paths, same spots the old TextView.text write
    // happened. Compose recomposes the status Text() whenever this changes.
    private var statusMessage by mutableStateOf("")

    // Set once the WebView is created inside AndroidView's factory, so the
    // poll loop (started right after) and the JsBridge (methods on this
    // Activity) can both reach it without a lateinit race against Compose's
    // own composition timing.
    private var webViewRef: WebView? = null

    private lateinit var pollRunnable: Runnable

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        com.invictus.xmd.ui.theme.AppTheme.applyTo(this)
        super.onCreate(savedInstanceState)

        shareUrl = intent.getStringExtra(EXTRA_SHARE_URL) ?: run { finishWithError("Missing URL"); return }
        fileId = intent.getStringExtra(EXTRA_FILE_ID) ?: run { finishWithError("Missing file id"); return }

        onBackPressedDispatcher.addCallback(this) { finishCancelled() }

        statusMessage = getString(R.string.challenge_loading)

        pollRunnable = object : Runnable {
            override fun run() {
                if (finished) return
                elapsedMs += POLL_INTERVAL_MS
                if (elapsedMs >= TIMEOUT_MS) {
                    finishWithError("Timed out waiting for the challenge to clear. Try again.")
                    return
                }
                webViewRef?.evaluateJavascript(POLL_SCRIPT.replace("__FILE_ID__", fileId), null)
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }

        setContent {
            XmdTheme {
                ChallengeScreen(
                    statusMessage = statusMessage,
                    shareUrl = shareUrl,
                    onWebViewReady = { webView ->
                        webViewRef = webView
                        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
                    },
                    onNavigateBack = { finishCancelled() },
                )
            }
        }
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun onResolved(redirectUrl: String) {
            runOnUiThread { finishWithSuccess(redirectUrl) }
        }

        @JavascriptInterface
        fun onLog(message: String) {
            runOnUiThread { statusMessage = message }
        }
    }

    private fun finishWithSuccess(directUrl: String) {
        if (finished) return
        finished = true
        handler.removeCallbacks(pollRunnable)
        val result = Intent().putExtra(EXTRA_DIRECT_URL, directUrl)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun finishWithError(message: String) {
        if (finished) return
        finished = true
        handler.removeCallbacks(pollRunnable)
        val result = Intent().putExtra(EXTRA_ERROR, message)
        setResult(RESULT_CANCELED, result)
        finish()
    }

    private fun finishCancelled() = finishWithError("Cancelled by user")

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ChallengeScreen(
        statusMessage: String,
        shareUrl: String,
        onWebViewReady: (WebView) -> Unit,
        onNavigateBack: () -> Unit,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.challenge_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
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
                                addJavascriptInterface(JsBridge(), "Native")
                                loadUrl(shareUrl)
                            }.also(onWebViewReady)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Injected each poll tick. Mirrors _page_ready / _wait_for_turnstile / _post_go
 * from browser_resolver.py: if a challenge banner is present, do nothing
 * (user is solving it visibly). If the download widget is present and no
 * challenge remains, grab a turnstile token if one exists, then POST to the
 * HTMX "/go" endpoint and report the redirect back to Kotlin.
 */
private const val POLL_SCRIPT = """
(function() {
  try {
    var title = document.title || '';
    var body = (document.body && document.body.innerText) || '';
    var hasChallenge = title.indexOf('Just a moment') !== -1
        || body.indexOf('Verifying you are human') !== -1
        || !!document.querySelector('#challenge-running, #cf-challenge-running, .cf-browser-verification');
    if (hasChallenge) { return; }

    var hasDownload = !!document.querySelector('a.link-button')
        || !!document.querySelector('#cf-turnstile')
        || !!document.querySelector('meta[name="title"]')
        || /DOWNLOAD/i.test(body);
    if (!hasDownload) { return; }

    var tokenField = document.querySelector('[name="cf-turnstile-response"]');
    var token = window.turnstileToken || (tokenField && tokenField.value) || '';
    var hasWidget = !!document.getElementById('cf-turnstile');
    if (hasWidget && (!token || token.length <= 20)) {
      return; // widget present but not yet solved by the user
    }

    fetch('/f/__FILE_ID__/go', {
      method: 'POST',
      headers: {
        'content-type': 'application/x-www-form-urlencoded',
        'hx-request': 'true',
        'hx-current-url': location.href,
        'referer': location.href
      },
      body: new URLSearchParams({ 'cf-turnstile-response': token }).toString()
    }).then(function(response) {
      var redirect = response.headers.get('HX-Redirect') || response.headers.get('hx-redirect');
      if (redirect) {
        Native.onResolved(redirect);
      } else {
        Native.onLog('Waiting for confirmation...');
      }
    }).catch(function(err) {
      Native.onLog('Retrying...');
    });
  } catch (e) {}
})();
"""
