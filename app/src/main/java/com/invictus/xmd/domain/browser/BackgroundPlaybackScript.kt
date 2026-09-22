package com.invictus.xmd.domain.browser

import com.invictus.xmd.preferences.Settings

/**
 * Backs the "Background playback" browser setting ([Settings.backgroundPlaybackEnabled]).
 *
 * WebView itself doesn't stop audio/video just because the app is backgrounded
 * or the user switches away from the Browser tab -- what actually pauses
 * playback on most sites (YouTube included) is the site's *own* JS reacting
 * to the Page Visibility API: it sees `document.hidden` flip to true and a
 * `visibilitychange` event fire, and pauses itself. [script] neutralizes
 * that by making the page believe it's always visible.
 *
 * Injected from BrowserFragment's WebViewClient.onPageStarted (not
 * onPageFinished, like AdblockFilter.cosmeticHideScript) -- it has to land
 * before the page's own scripts run and read document.hidden or attach
 * their own visibilitychange listener, not after.
 *
 * Only a same-document override: doesn't grant a wake lock or start any
 * foreground service, so the WebView (and its audio) still only keeps
 * running as long as Android itself doesn't kill/suspend the app process
 * in the background -- same caveat as every other in-app-browser
 * implementation of this without a dedicated playback service.
 */
object BackgroundPlaybackScript {
    fun script(): String = """
        (function(){
          if (window.__xmd_bg_playback__) return;
          window.__xmd_bg_playback__ = true;
          try {
            Object.defineProperty(document, 'hidden', {
              configurable: true,
              get: function() { return false; }
            });
            Object.defineProperty(document, 'visibilityState', {
              configurable: true,
              get: function() { return 'visible'; }
            });
          } catch (e) {}
          var swallow = function(e) {
            if (e && e.stopImmediatePropagation) e.stopImmediatePropagation();
          };
          document.addEventListener('visibilitychange', swallow, true);
          document.addEventListener('webkitvisibilitychange', swallow, true);
        })();
    """.trimIndent()
}
