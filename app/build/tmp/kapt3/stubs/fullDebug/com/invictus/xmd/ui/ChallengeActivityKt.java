package com.invictus.xmd.ui;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000\b\n\u0000\n\u0002\u0010\u000e\n\u0000\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0002"}, d2 = {"POLL_SCRIPT", "", "app_fullDebug"})
public final class ChallengeActivityKt {
    
    /**
     * Injected each poll tick. Mirrors _page_ready / _wait_for_turnstile / _post_go
     * from browser_resolver.py: if a challenge banner is present, do nothing
     * (user is solving it visibly). If the download widget is present and no
     * challenge remains, grab a turnstile token if one exists, then POST to the
     * HTMX "/go" endpoint and report the redirect back to Kotlin.
     */
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String POLL_SCRIPT = "\n(function() {\n  try {\n    var title = document.title || '';\n    var body = (document.body && document.body.innerText) || '';\n    var hasChallenge = title.indexOf('Just a moment') !== -1\n        || body.indexOf('Verifying you are human') !== -1\n        || !!document.querySelector('#challenge-running, #cf-challenge-running, .cf-browser-verification');\n    if (hasChallenge) { return; }\n\n    var hasDownload = !!document.querySelector('a.link-button')\n        || !!document.querySelector('#cf-turnstile')\n        || !!document.querySelector('meta[name=\"title\"]')\n        || /DOWNLOAD/i.test(body);\n    if (!hasDownload) { return; }\n\n    var tokenField = document.querySelector('[name=\"cf-turnstile-response\"]');\n    var token = window.turnstileToken || (tokenField && tokenField.value) || '';\n    var hasWidget = !!document.getElementById('cf-turnstile');\n    if (hasWidget && (!token || token.length <= 20)) {\n      return; // widget present but not yet solved by the user\n    }\n\n    fetch('/f/__FILE_ID__/go', {\n      method: 'POST',\n      headers: {\n        'content-type': 'application/x-www-form-urlencoded',\n        'hx-request': 'true',\n        'hx-current-url': location.href,\n        'referer': location.href\n      },\n      body: new URLSearchParams({ 'cf-turnstile-response': token }).toString()\n    }).then(function(response) {\n      var redirect = response.headers.get('HX-Redirect') || response.headers.get('hx-redirect');\n      if (redirect) {\n        Native.onResolved(redirect);\n      } else {\n        Native.onLog('Waiting for confirmation\u2026');\n      }\n    }).catch(function(err) {\n      Native.onLog('Retrying\u2026');\n    });\n  } catch (e) {}\n})();\n";
}