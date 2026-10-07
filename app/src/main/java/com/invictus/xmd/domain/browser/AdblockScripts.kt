package com.invictus.xmd.domain.browser

/**
 * JavaScript injected at document start (see BrowserFragment: via
 * WebViewCompat.addDocumentStartJavaScript where supported, else from
 * onPageStarted). Two jobs the network-level filter can't do:
 *
 *  1. Popup / pop-under guard: `window.open` only works right after a real
 *     tap on a real control (link, button, ...). Streaming sites typically
 *     fire `window.open` from a click handler on an invisible full-page
 *     overlay -- that target isn't a control, so it is denied and the caller
 *     gets an inert stand-in window instead.
 *  2. YouTube ads: prune ad payloads out of the player/next API responses
 *     (what uBlock's json-prune does), then fall back to skipping/fast-
 *     forwarding any ad that still renders and hiding ad containers.
 *
 * Gate: every part asks the native bridge `XmdAdblock.enabled(host)` so the
 * Shields level and per-site allowlist are honoured. YouTube changes its
 * player often, so the YouTube half is best-effort by nature.
 *
 * NOTE: no dollar signs in the JS -- this is a Kotlin raw string.
 */
object AdblockScripts {

    fun documentStart(): String = SCRIPT

    private val SCRIPT = """
(function () {
  if (window.__xmdAdb) return;
  var on = false;
  try { on = !!window.XmdAdblock && XmdAdblock.enabled(location.hostname); } catch (e) {}
  if (!on) return;
  window.__xmdAdb = 1;

  /* ---------------- 1. popup / pop-under guard ---------------- */
  var lastTrusted = 0;
  var CONTROL = 'a[href],button,input,select,textarea,label,summary,[role=button],[role=link],[role=menuitem]';
  function onUser(e) {
    try {
      if (!e.isTrusted) return;
      var t = e.target;
      var ctl = t && t.closest ? t.closest(CONTROL) : null;
      if (ctl) lastTrusted = Date.now();
    } catch (x) {}
  }
  ['click', 'touchend', 'mouseup', 'pointerup', 'keydown'].forEach(function (n) {
    window.addEventListener(n, onUser, true);
  });
  var realOpen = window.open;
  var fake = function () {
    var w = {
      closed: true, close: function () {}, focus: function () {}, blur: function () {},
      postMessage: function () {}, opener: null,
      document: { write: function () {}, writeln: function () {}, open: function () {}, close: function () {} },
      location: { href: 'about:blank', assign: function () {}, replace: function () {} }
    };
    return w;
  };
  window.open = function (u, n, f) {
    if (Date.now() - lastTrusted < 1200) return realOpen.apply(window, arguments);
    try { XmdAdblock.popupBlocked(); } catch (e) {}
    return fake();
  };

  /* ---------------- 2. YouTube ads ---------------- */
  if (!/(^|\.)youtube\.com${'$'}/.test(location.hostname)) return;

  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];
  function prune(o, depth) {
    if (!o || typeof o !== 'object' || (depth || 0) > 6) return o;
    for (var i = 0; i < AD_KEYS.length; i++) { if (AD_KEYS[i] in o) { try { delete o[AD_KEYS[i]]; } catch (e) {} } }
    if (o.playerResponse) prune(o.playerResponse, (depth || 0) + 1);
    if (o.response) prune(o.response, (depth || 0) + 1);
    return o;
  }

  try {
    var held;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function () { return held; },
      set: function (v) { held = prune(v); }
    });
  } catch (e) {}

  try {
    var nativeParse = JSON.parse;
    JSON.parse = function () {
      var r = nativeParse.apply(this, arguments);
      try { if (r && typeof r === 'object') prune(r); } catch (e) {}
      return r;
    };
  } catch (e) {}

  try {
    var nativeJson = Response.prototype.json;
    Response.prototype.json = function () {
      var url = this.url || '';
      return nativeJson.apply(this, arguments).then(function (j) {
        try { if (url.indexOf('/youtubei/') !== -1) prune(j); } catch (e) {}
        return j;
      });
    };
  } catch (e) {}

  /* Fallback: anything that still renders as an ad gets skipped. */
  var css = [
    '.ytp-ad-overlay-container', '.ytp-ad-image-overlay', '.ytp-ad-text-overlay',
    'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-video-renderer', 'ytm-companion-ad-renderer',
    'ytm-ad-slot-renderer', 'ytm-display-ad-renderer', 'ytm-banner-promo-renderer',
    'ytd-ad-slot-renderer', 'ytd-display-ad-renderer', 'ytd-promoted-sparkles-web-renderer',
    'ytd-banner-promo-renderer', '#masthead-ad', '#player-ads', '.ad-container'
  ].join(',') + '{display:none!important}';
  function addCss() {
    if (document.getElementById('__xmd_yt_css')) return;
    var s = document.createElement('style');
    s.id = '__xmd_yt_css';
    s.textContent = css;
    (document.head || document.documentElement).appendChild(s);
  }
  addCss();
  document.addEventListener('DOMContentLoaded', addCss);

  var mutedByUs = false, prevMuted = false;
  setInterval(function () {
    try {
      var skip = document.querySelectorAll(
        '.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-ad-skip-button-container button');
      for (var i = 0; i < skip.length; i++) skip[i].click();

      var player = document.querySelector('#movie_player, .html5-video-player');
      var v = document.querySelector('video.html5-main-video, video');
      var showing = player && player.classList && player.classList.contains('ad-showing');
      if (showing && v) {
        if (!mutedByUs) { prevMuted = v.muted; mutedByUs = true; }
        v.muted = true;
        if (isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.1) {
          v.currentTime = v.duration;
        }
      } else if (mutedByUs && v) {
        v.muted = prevMuted;
        mutedByUs = false;
      }
    } catch (e) {}
  }, 300);
})();
""".trimIndent()
}
