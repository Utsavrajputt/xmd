package com.invictus.xmd.domain.browser

/**
 * JavaScript injected at document start in every frame (see BrowserFragment:
 * WebViewCompat.addDocumentStartJavaScript where supported, else
 * onPageStarted). It is what the network filter alone can't do:
 *
 *  1. Cosmetic filtering: element-hiding CSS for the page's host, plus generic
 *     rules looked up by the ids/classes actually present in the DOM
 *     (including nodes added later) -- like uBlock's cosmetic engine.
 *  2. uBlock scriptlets (`site##+js(name, args...)` rules from the lists):
 *     abort-on-property-read/write, abort-current-script, set-constant,
 *     prevent-window-open, no-setTimeout/Interval-if, remove-attr/class,
 *     json-prune, nowebrtc, prevent-addEventListener, prevent-fetch/xhr,
 *     noeval, disable-newtab-links. This is the part that defeats
 *     anti-adblock and pop-under scripts on streaming sites.
 *  3. Popup / pop-under guard and click guard: `window.open` only works right
 *     after a tap on a real control; taps on invisible full-page overlays and
 *     on links into ad hosts are swallowed (and the overlay removed).
 *  4. YouTube ads: prune ad payloads from player/next API responses, then
 *     skip/fast-forward anything that still renders.
 *
 * Every part is gated by `XmdAdblock.enabled(host)` so the Shields level and
 * per-site allowlist are honoured.
 *
 * NOTE: dollar signs in the JS go through ${'$'} -- this is a Kotlin raw string.
 */
object AdblockScripts {

    fun documentStart(): String = SCRIPT

    private val SCRIPT = """
(function () {
  if (window.__xmdAdb) return;
  var B = window.XmdAdblock;
  var on = false;
  try { on = !!B && B.enabled(location.hostname); } catch (e) {}
  if (!on) return;
  window.__xmdAdb = 1;

  var host = location.hostname;
  var nativeParse = JSON.parse, nativeStringify = JSON.stringify;
  function counted() { try { B.popupBlocked(); } catch (e) {} }
  function rand() { return Math.random().toString(36).slice(2, 10); }
  function domReady(fn) {
    if (document.readyState !== 'loading') fn();
    else document.addEventListener('DOMContentLoaded', fn);
  }

  /* ---------------- 1. cosmetic filtering ---------------- */
  var pendingCss = [], waiting = false;
  function flushCss() {
    var t = document.head || document.documentElement;
    if (!t) return false;
    if (!pendingCss.length) return true;
    var s = document.createElement('style');
    s.textContent = pendingCss.join('');
    pendingCss = [];
    t.appendChild(s);
    return true;
  }
  function addCss(css) {
    if (!css) return;
    pendingCss.push(css);
    if (flushCss() || waiting) return;
    waiting = true;
    var mo = new MutationObserver(function () { if (flushCss()) { mo.disconnect(); waiting = false; } });
    mo.observe(document, { childList: true, subtree: true });
  }
  try { addCss(B.cosmetic(host)); } catch (e) {}

  var seenId = {}, seenCls = {}, q = { ids: [], cls: [] }, timer = 0;
  function scanEl(el) {
    if (el.id && !seenId[el.id]) { seenId[el.id] = 1; q.ids.push(el.id); }
    var cl = el.classList;
    if (cl) for (var i = 0; i < cl.length; i++) {
      var c = cl[i];
      if (!seenCls[c]) { seenCls[c] = 1; q.cls.push(c); }
    }
  }
  function sendQueue() {
    timer = 0;
    if (!q.ids.length && !q.cls.length) return;
    var ids = q.ids, cls = q.cls;
    q = { ids: [], cls: [] };
    try { addCss(B.genericCss(host, nativeStringify(ids), nativeStringify(cls))); } catch (e) {}
  }
  function scanTree(root) {
    if (!root || (root.nodeType !== 1 && root.nodeType !== 9)) return;
    if (root.nodeType === 1) scanEl(root);
    var nl = root.querySelectorAll ? root.querySelectorAll('[id],[class]') : [];
    for (var i = 0; i < nl.length; i++) scanEl(nl[i]);
    if (!timer) timer = setTimeout(sendQueue, 200);
  }
  domReady(function () {
    scanTree(document);
    try {
      new MutationObserver(function (muts) {
        for (var i = 0; i < muts.length; i++) {
          var m = muts[i];
          if (m.type === 'attributes') { scanEl(m.target); if (!timer) timer = setTimeout(sendQueue, 200); }
          else for (var j = 0; j < m.addedNodes.length; j++) scanTree(m.addedNodes[j]);
        }
      }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'id'] });
    } catch (e) {}
  });

  /* ---------------- 2. uBlock scriptlets ---------------- */
  function matcher(p) {
    if (p === undefined || p === null || p === '') return function () { return true; };
    var neg = false;
    if (p.charAt(0) === '!') { neg = true; p = p.slice(1); }
    var m = /^\/(.+)\/([gimsuy]*)${'$'}/.exec(p), f;
    if (m) {
      var re = null;
      try { re = new RegExp(m[1], m[2].replace(/[gy]/g, '')); } catch (e) {}
      f = function (s) { return !!re && re.test(String(s)); };
    } else {
      f = function (s) { return String(s).indexOf(p) !== -1; };
    }
    return neg ? function (s) { return !f(s); } : f;
  }
  function isObj(v) { return v !== null && (typeof v === 'object' || typeof v === 'function'); }
  function defineChain(root, chain, def) {
    var parts = chain.split('.');
    (function walk(obj, i) {
      var p = parts[i];
      if (i === parts.length - 1) { def(obj, p); return; }
      var cur;
      try { cur = obj[p]; } catch (e) { return; }
      if (isObj(cur)) { walk(cur, i + 1); return; }
      var held = cur;
      try {
        Object.defineProperty(obj, p, {
          configurable: true, enumerable: true,
          get: function () { return held; },
          set: function (v) { held = v; if (isObj(v)) walk(v, i + 1); }
        });
      } catch (e) {}
    })(root, 0);
  }
  function watch(fn) {
    var pending = false;
    function run() { pending = false; try { fn(); } catch (e) {} }
    domReady(function () {
      run();
      try {
        new MutationObserver(function () { if (!pending) { pending = true; setTimeout(run, 120); } })
          .observe(document.documentElement, { childList: true, subtree: true, attributes: true });
      } catch (e) {}
    });
  }
  function fakeWindow() {
    return {
      closed: true, close: function () {}, focus: function () {}, blur: function () {},
      postMessage: function () {}, opener: null,
      document: { write: function () {}, writeln: function () {}, open: function () {}, close: function () {} },
      location: { href: 'about:blank', assign: function () {}, replace: function () {} }
    };
  }
  function constVal(v) {
    if (v === undefined) return undefined;
    switch (v) {
      case 'undefined': return undefined;
      case 'false': return false;
      case 'true': return true;
      case 'null': return null;
      case 'noopFunc': return function () {};
      case 'trueFunc': return function () { return true; };
      case 'falseFunc': return function () { return false; };
      case 'throwFunc': return function () { throw 0; };
      case 'emptyStr': case '': return '';
      case 'emptyArr': return [];
      case 'emptyObj': return {};
      case 'yes': return 'yes';
      case 'no': return 'no';
    }
    if (/^-?\d+(\.\d+)?${'$'}/.test(v)) return parseFloat(v);
    return v;
  }
  function prunePath(obj, path) {
    var parts = path.split('.');
    (function go(o, i) {
      if (!o || typeof o !== 'object') return;
      var p = parts[i], last = i === parts.length - 1;
      if (p === '[]' || p === '*') {
        if (last) return;
        var keys = Object.keys(o);
        for (var k = 0; k < keys.length; k++) go(o[keys[k]], i + 1);
        return;
      }
      if (last) { if (p in o) { try { delete o[p]; } catch (e) {} } return; }
      go(o[p], i + 1);
    })(obj, 0);
  }
  function timerDefuser(name) {
    return function (a) {
      if (!a[0]) return;
      var m = matcher(a[0]);
      var dm = (a[1] !== undefined && a[1] !== '') ? matcher(a[1]) : null;
      var real = window[name];
      window[name] = function (fn, d) {
        var s = typeof fn === 'function' ? Function.prototype.toString.call(fn) : String(fn);
        if (m(s) && (!dm || dm(d))) return 0;
        return real.apply(this, arguments);
      };
    };
  }

  var S = {};
  S.aopr = function (a) {
    defineChain(window, a[0], function (o, p) {
      var held; try { held = o[p]; } catch (e) {}
      try {
        Object.defineProperty(o, p, {
          configurable: true,
          get: function () { throw new ReferenceError(rand()); },
          set: function (v) { held = v; }
        });
      } catch (e) {}
    });
  };
  S.aopw = function (a) {
    defineChain(window, a[0], function (o, p) {
      var held; try { held = o[p]; } catch (e) {}
      try {
        Object.defineProperty(o, p, {
          configurable: true,
          get: function () { return held; },
          set: function () { throw new ReferenceError(rand()); }
        });
      } catch (e) {}
    });
  };
  S.acs = function (a) {
    var m = matcher(a[1]);
    defineChain(window, a[0], function (o, p) {
      var held; try { held = o[p]; } catch (e) {}
      function check() {
        var cs = document.currentScript;
        if (!cs) return;
        if (!a[1] || m(cs.textContent || '') || m(cs.src || '')) throw new ReferenceError(rand());
      }
      try {
        Object.defineProperty(o, p, {
          configurable: true,
          get: function () { check(); return held; },
          set: function (v) { check(); held = v; }
        });
      } catch (e) {}
    });
  };
  S.set = function (a) {
    var val = constVal(a[1]);
    defineChain(window, a[0], function (o, p) {
      try {
        Object.defineProperty(o, p, { configurable: true, get: function () { return val; }, set: function () {} });
      } catch (e) {}
    });
  };
  S.nowoif = function (a) {
    var m = matcher(a[0]), real = window.open;
    window.open = function (u) {
      var url = String(u === undefined ? '' : u);
      if (!a[0] || m(url)) { counted(); return a[2] === 'obj' ? fakeWindow() : null; }
      return real.apply(window, arguments);
    };
  };
  S.nostif = timerDefuser('setTimeout');
  S.nosiif = timerDefuser('setInterval');
  S.ra = function (a) {
    var attrs = (a[0] || '').split('|').filter(Boolean);
    if (!attrs.length) return;
    var sel = a[1] || attrs.map(function (x) { return '[' + x + ']'; }).join(',');
    watch(function () {
      var nl = document.querySelectorAll(sel);
      for (var i = 0; i < nl.length; i++) for (var j = 0; j < attrs.length; j++) nl[i].removeAttribute(attrs[j]);
    });
  };
  S.rc = function (a) {
    var cls = (a[0] || '').split('|').filter(Boolean);
    if (!cls.length) return;
    var sel = a[1] || cls.map(function (x) { return '.' + x; }).join(',');
    watch(function () {
      var nl = document.querySelectorAll(sel);
      for (var i = 0; i < nl.length; i++) for (var j = 0; j < cls.length; j++) nl[i].classList.remove(cls[j]);
    });
  };
  S.jp = function (a) {
    var paths = (a[0] || '').split(/\s+/).filter(Boolean);
    var need = (a[1] || '').split(/\s+/).filter(Boolean);
    if (!paths.length) return;
    function has(o, path) {
      var c = o, ps = path.split('.');
      for (var i = 0; i < ps.length; i++) {
        if (c === null || typeof c !== 'object' || !(ps[i] in c)) return false;
        c = c[ps[i]];
      }
      return true;
    }
    function apply(o) {
      if (!o || typeof o !== 'object') return o;
      for (var i = 0; i < need.length; i++) if (!has(o, need[i])) return o;
      for (var j = 0; j < paths.length; j++) prunePath(o, paths[j]);
      return o;
    }
    var np = JSON.parse;
    JSON.parse = function () { return apply(np.apply(this, arguments)); };
    var nj = Response.prototype.json;
    Response.prototype.json = function () { return nj.apply(this, arguments).then(apply); };
  };
  S.nowebrtc = function () {
    var F = function () {};
    F.prototype = {
      close: function () {}, createDataChannel: function () {}, createOffer: function () {},
      setLocalDescription: function () {}, addEventListener: function () {}
    };
    ['RTCPeerConnection', 'webkitRTCPeerConnection', 'mozRTCPeerConnection'].forEach(function (n) {
      if (n in window) window[n] = function () { return new F(); };
    });
  };
  S.aell = function (a) {
    if (!a[0] && !a[1]) return;
    var tm = matcher(a[0]), hm = matcher(a[1]);
    var real = EventTarget.prototype.addEventListener;
    EventTarget.prototype.addEventListener = function (t, h) {
      try {
        var hs = typeof h === 'function' ? Function.prototype.toString.call(h)
          : (h && h.handleEvent ? Function.prototype.toString.call(h.handleEvent) : String(h));
        if (tm(t) && hm(hs)) return;
      } catch (e) {}
      return real.apply(this, arguments);
    };
  };
  S.noeval = function () { window.eval = function () {}; };
  S.nofetch = function (a) {
    var m = matcher((a[0] || '').replace(/^url:/, '')), real = window.fetch;
    if (!real) return;
    window.fetch = function (input) {
      var u = typeof input === 'string' ? input : (input && input.url) || '';
      if (!a[0] || m(u)) return Promise.resolve(new Response('', { status: 200 }));
      return real.apply(this, arguments);
    };
  };
  S.noxhr = function (a) {
    var m = matcher((a[0] || '').replace(/^url:/, ''));
    var open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (mt, u) { this.__xmdBlock = !a[0] || m(String(u)); return open.apply(this, arguments); };
    XMLHttpRequest.prototype.send = function () {
      if (!this.__xmdBlock) return send.apply(this, arguments);
      var x = this;
      setTimeout(function () {
        try {
          Object.defineProperty(x, 'readyState', { value: 4 });
          Object.defineProperty(x, 'status', { value: 200 });
          Object.defineProperty(x, 'responseText', { value: '' });
          Object.defineProperty(x, 'response', { value: '' });
        } catch (e) {}
        ['readystatechange', 'load', 'loadend'].forEach(function (t) { try { x.dispatchEvent(new Event(t)); } catch (e) {} });
        try { if (typeof x.onreadystatechange === 'function') x.onreadystatechange(); } catch (e) {}
        try { if (typeof x.onload === 'function') x.onload(); } catch (e) {}
      }, 0);
    };
  };
  S.dnl = function () {
    document.addEventListener('click', function (e) {
      var a = e.target && e.target.closest ? e.target.closest('a[target]') : null;
      if (a && /^_blank${'$'}/i.test(a.target)) a.target = '_self';
    }, true);
  };

  var ALIAS = {
    'abort-on-property-read': 'aopr', 'aopr': 'aopr',
    'abort-on-property-write': 'aopw', 'aopw': 'aopw',
    'abort-current-script': 'acs', 'acs': 'acs', 'abort-current-inline-script': 'acs', 'acis': 'acs',
    'set-constant': 'set', 'set': 'set',
    'prevent-window-open': 'nowoif', 'nowoif': 'nowoif', 'no-window-open-if': 'nowoif', 'window.open-defuser': 'nowoif',
    'no-setTimeout-if': 'nostif', 'nostif': 'nostif', 'setTimeout-defuser': 'nostif',
    'no-setInterval-if': 'nosiif', 'nosiif': 'nosiif', 'setInterval-defuser': 'nosiif',
    'remove-attr': 'ra', 'ra': 'ra', 'remove-class': 'rc', 'rc': 'rc',
    'json-prune': 'jp', 'nowebrtc': 'nowebrtc',
    'prevent-addEventListener': 'aell', 'aell': 'aell', 'addEventListener-defuser': 'aell',
    'noeval': 'noeval', 'no-eval': 'noeval',
    'prevent-fetch': 'nofetch', 'no-fetch-if': 'nofetch',
    'prevent-xhr': 'noxhr', 'no-xhr-if': 'noxhr',
    'disable-newtab-links': 'dnl'
  };
  try {
    var list = nativeParse(B.scriptlets(host));
    for (var si = 0; si < list.length; si++) {
      var entry = list[si];
      var fn = S[ALIAS[String(entry[0]).replace(/\.js${'$'}/, '')]];
      if (fn) { try { fn(entry.slice(1)); } catch (e) {} }
    }
  } catch (e) {}

  /* ---------------- 3. popup / click guards ---------------- */
  var lastTrusted = 0;
  var CONTROL = 'a[href],button,input,select,textarea,label,summary,[role=button],[role=link],[role=menuitem]';
  function onUser(e) {
    try {
      if (!e.isTrusted) return;
      var t = e.target;
      if (t && t.closest && t.closest(CONTROL)) lastTrusted = Date.now();
    } catch (x) {}
  }
  ['click', 'touchend', 'mouseup', 'pointerup', 'keydown'].forEach(function (n) {
    window.addEventListener(n, onUser, true);
  });
  var realOpen = window.open;
  window.open = function () {
    if (Date.now() - lastTrusted < 1200) return realOpen.apply(window, arguments);
    counted();
    return fakeWindow();
  };

  function overlayLike(el) {
    try {
      var cs = getComputedStyle(el);
      if (cs.position !== 'fixed' && cs.position !== 'absolute') return false;
      var r = el.getBoundingClientRect();
      if (r.width < innerWidth * 0.6 || r.height < innerHeight * 0.5) return false;
      var clear = (cs.backgroundColor === 'rgba(0, 0, 0, 0)' || cs.backgroundColor === 'transparent') && cs.backgroundImage === 'none';
      var faint = parseFloat(cs.opacity) < 0.1;
      var empty = !(el.textContent || '').trim() && !el.querySelector('img,video,svg,canvas,iframe,input,button');
      return empty && (clear || faint);
    } catch (e) { return false; }
  }
  var PLAYERISH = 'video,[class*="player" i],[id*="player" i],.jw-wrapper,.video-js,.plyr';
  function onTap(e) {
    try {
      if (!e.isTrusted) return;
      var t = e.target;
      if (!t || t.nodeType !== 1) return;
      var a = t.closest ? t.closest('a[href]') : null;
      if (a) {
        var h = a.href, hh = '';
        if (h && /^https?:/i.test(h)) { try { hh = new URL(h).hostname; } catch (x) {} }
        if (hh && hh !== host) {
          var bad = false;
          try { bad = B.isAdUrl(h, host); } catch (x) {}
          var ov = overlayLike(a);
          if (bad || ov) {
            e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
            if (ov) { try { a.remove(); } catch (x) {} }
            counted();
          }
        }
        return;
      }
      if (/^(DIV|SPAN|IFRAME)${'$'}/.test(t.tagName) && overlayLike(t) && !(t.closest && t.closest(PLAYERISH))) {
        var z = parseInt(getComputedStyle(t).zIndex, 10);
        if (z >= 900) {
          e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
          try { t.remove(); } catch (x) {}
          counted();
        }
      }
    } catch (x) {}
  }
  window.addEventListener('click', onTap, true);

  /* ---------------- 4. YouTube ads ---------------- */
  if (!/(^|\.)youtube\.com${'$'}/.test(host)) return;

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
    var prevParse = JSON.parse;
    JSON.parse = function () {
      var r = prevParse.apply(this, arguments);
      try { if (r && typeof r === 'object') prune(r); } catch (e) {}
      return r;
    };
  } catch (e) {}
  try {
    var prevJson = Response.prototype.json;
    Response.prototype.json = function () {
      var url = this.url || '';
      return prevJson.apply(this, arguments).then(function (j) {
        try { if (url.indexOf('/youtubei/') !== -1) prune(j); } catch (e) {}
        return j;
      });
    };
  } catch (e) {}

  addCss([
    '.ytp-ad-overlay-container', '.ytp-ad-image-overlay', '.ytp-ad-text-overlay',
    'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-video-renderer', 'ytm-companion-ad-renderer',
    'ytm-ad-slot-renderer', 'ytm-display-ad-renderer', 'ytm-banner-promo-renderer',
    'ytd-ad-slot-renderer', 'ytd-display-ad-renderer', 'ytd-promoted-sparkles-web-renderer',
    'ytd-banner-promo-renderer', '#masthead-ad', '#player-ads', '.ad-container'
  ].join(',') + '{display:none!important}');

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
        if (isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.1) v.currentTime = v.duration;
      } else if (mutedByUs && v) {
        v.muted = prevMuted;
        mutedByUs = false;
      }
    } catch (e) {}
  }, 300);
})();
""".trimIndent()
}
