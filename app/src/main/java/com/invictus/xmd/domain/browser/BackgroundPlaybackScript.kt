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
 * onPageFinished, like the old adblock cosmetic script) -- it has to land
 * before the page's own scripts run and read document.hidden or attach
 * their own visibilitychange listener, not after.
 *
 * The script also reports audible playback through the `XmdMedia` bridge so the
 * BackgroundPlaybackService notification only appears while media is really playing.
 *
 * Only a same-document override: doesn't grant a wake lock or start any
 * foreground service, so the WebView (and its audio) still only keeps
 * running as long as Android itself doesn't kill/suspend the app process
 * in the background -- same caveat as every other in-app-browser
 * implementation of this without a dedicated playback service.
 */
object BackgroundPlaybackScript {
    /** True when pages should believe they are visible and the window-hidden signal is swallowed. */
    fun active(): Boolean = Settings.backgroundPlaybackEnabled()

    fun script(): String = """
        (function(){
          if (window.__xmd_bg_playback__) return;
          window.__xmd_bg_playback__ = true;
          var swallow = function(e) {
            if (e && e.stopImmediatePropagation) e.stopImmediatePropagation();
          };
          var installed = false;
          // Page-visibility override (what stops sites pausing in the background), applied when the setting is on.
          var install = function() {
            if (installed) return;
            installed = true;
            try {
              Object.defineProperty(document, 'hidden', { configurable: true, get: function() { return false; } });
              Object.defineProperty(document, 'visibilityState', { configurable: true, get: function() { return 'visible'; } });
            } catch (e) {}
            document.addEventListener('visibilitychange', swallow, true);
            document.addEventListener('webkitvisibilitychange', swallow, true);
          };
          try { if (XmdMedia.enabled()) install(); } catch (e) {}

          // Playback reports. Every frame has its own id, so an iframe player
          // is tracked separately from the page around it.
          var last = null;
          var fid = Math.random().toString(36).slice(2);
          var audible = function() {
            var m = document.querySelectorAll('video,audio');
            for (var i = 0; i < m.length; i++) {
              var e = m[i];
              if (!e.paused && !e.ended && e.readyState > 2 && !e.muted && e.volume > 0) return true;
            }
            return false;
          };
          var report = function() {
            var p = audible();
            if (p === last) return;
            last = p;
            try { XmdMedia.setPlayingFrame(fid, p); } catch (e) {}
          };
          ['play', 'playing', 'pause', 'ended', 'emptied', 'volumechange'].forEach(function(n) {
            document.addEventListener(n, report, true);
          });
          window.addEventListener('pagehide', function() {
            last = false;
            try { XmdMedia.setPlayingFrame(fid, false); } catch (e) {}
          });
          report();
        })();
    """.trimIndent()
}
