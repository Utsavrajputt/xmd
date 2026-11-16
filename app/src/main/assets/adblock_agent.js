/*
 * XMD page agent for the ad blocker. Runs at document start in every frame.
 * Asks the app (XmdCosmetic bridge) what applies to this host, then:
 *  - injects the element-hiding CSS (constructed stylesheet, so page CSP can't block it),
 *  - asks for generic class/id selectors the page's own DOM can match,
 *  - runs procedural rules (:has-text / :upward / :remove),
 *  - runs scriptlets (our own small library; names follow filter-list convention).
 * Every step is wrapped: a failing rule must never break the page.
 */
(function () {
  'use strict';
  if (window.__xmdCos) return;
  var host = '';
  try { host = location.hostname; } catch (e) {}
  if (!host) return;
  window.__xmdCos = true;

  var B = window.XmdCosmetic;

  // Remember what was touched last, for the long-press "Hide element" picker. Touches inside
  // an iframe never reach the parent document, so a frame reports upward and the parent
  // marks the iframe element itself.
  try {
    var isTop = window === window.top;
    window.addEventListener('touchstart', function (e) {
      if (isTop) window.__xmdLast = e.target;
      else { try { parent.postMessage('__xmdTouch', '*'); } catch (x) {} }
    }, { capture: true, passive: true });
    window.addEventListener('message', function (e) {
      if (e.data !== '__xmdTouch' || !e.source) return;
      var fs = document.getElementsByTagName('iframe');
      for (var i = 0; i < fs.length; i++) {
        if (fs[i].contentWindow === e.source) {
          if (isTop) window.__xmdLast = fs[i];
          else { try { parent.postMessage('__xmdTouch', '*'); } catch (x) {} }
          return;
        }
      }
    });
  } catch (e) {}

  // ── stylesheet helper ─────────────────────────────────────────────
  function addCss(css) {
    if (!css) return;
    try {
      var sheet = new CSSStyleSheet();
      sheet.replaceSync(css);
      document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sheet]);
      return;
    } catch (e) {}
    try {
      var st = document.createElement('style');
      st.textContent = css;
      (document.head || document.documentElement).appendChild(st);
    } catch (e2) {}
  }

  // ── DOM task runner (throttled MutationObserver) ──────────────────
  var domTasks = [], pending = [], timer = 0, observing = false, ready = false;

  function schedule(ms) {
    if (timer) return;
    timer = setTimeout(flush, ms);
  }
  function flush() {
    timer = 0;
    var nodes = pending;
    pending = [];
    var full = !ready;
    if (!ready) ready = true;
    for (var i = 0; i < domTasks.length; i++) {
      try { domTasks[i](full ? null : nodes); } catch (e) {}
    }
  }
  function ensureObserver() {
    if (observing) return;
    observing = true;
    try {
      var mo = new MutationObserver(function (recs) {
        for (var i = 0; i < recs.length; i++) {
          var r = recs[i];
          if (r.type === 'attributes') {
            pending.push(r.target);
          } else {
            for (var j = 0; j < r.addedNodes.length; j++) {
              var n = r.addedNodes[j];
              if (n.nodeType === 1) pending.push(n);
            }
          }
        }
        if (pending.length > 5000) pending.length = 5000;
        if (pending.length && ready) schedule(250);
      });
      mo.observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'id'] });
    } catch (e) {}
    var start = function () { ready = false; schedule(0); };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, { once: true });
    else start();
  }
  function onDom(fn) {
    domTasks.push(fn);
    ensureObserver();
    if (ready) schedule(0);
  }

  // ── scriptlet library ─────────────────────────────────────────────
  var noop = function () {};
  function rand() { return Math.random().toString(36).slice(2); }
  function mkNeedle(s) {
    if (s === undefined || s === null || s === '') return null;
    s = String(s);
    var m = /^\/(.+)\/([a-z]*)$/.exec(s);
    if (m) {
      try { var re = new RegExp(m[1], m[2].replace('g', '')); return function (t) { return re.test(t); }; } catch (e) {}
    }
    return function (t) { return String(t).indexOf(s) !== -1; };
  }
  function constVal(v) {
    switch (v) {
      case 'undefined': return undefined;
      case 'false': return false;
      case 'true': return true;
      case 'null': return null;
      case 'noopFunc': return function () {};
      case 'trueFunc': return function () { return true; };
      case 'falseFunc': return function () { return false; };
      case 'throwFunc': return function () { throw ''; };
      case 'emptyObj': return {};
      case 'emptyArr': return [];
      case 'emptyStr': case "''": case '': return '';
      case 'yes': return 'yes';
      case 'no': return 'no';
      case 'noopPromiseResolve': return function () { return Promise.resolve(typeof Response === 'function' ? new Response() : {}); };
      case 'noopPromiseReject': return function () { return Promise.reject(); };
    }
    if (/^-?\d+(\.\d+)?$/.test(v)) return +v;
    return v;
  }
  function isObj(v) { return v !== null && (typeof v === 'object' || typeof v === 'function'); }

  // Run `final(owner, prop)` on the last link of a dotted path, waiting for
  // missing intermediate objects to appear.
  function watch(path, final) {
    var parts = String(path).split('.');
    (function go(obj, i) {
      var p = parts[i], cur;
      if (i === parts.length - 1) { try { final(obj, p); } catch (e) {} return; }
      try { cur = obj[p]; } catch (e) { return; }
      if (isObj(cur)) { go(cur, i + 1); return; }
      var val = cur;
      try {
        Object.defineProperty(obj, p, {
          configurable: true, enumerable: true,
          get: function () { return val; },
          set: function (v) { val = v; if (isObj(v)) go(v, i + 1); }
        });
      } catch (e) {}
    })(window, 0);
  }

  function stackHas(needle) {
    if (!needle) return true;
    try { return needle(String(new Error().stack)); } catch (e) { return false; }
  }
  function scriptHas(needle) {
    var s = document.currentScript;
    if (!s) return false;
    if (!needle) return true;
    return needle(s.textContent || '') || needle(s.src || '');
  }

  function delOrHas(o, parts, i, del) {
    if (!isObj(o)) return false;
    var k = parts[i];
    if (i === parts.length - 1) {
      if (k === '*') { if (del) Object.keys(o).forEach(function (x) { delete o[x]; }); return true; }
      if (k in o) { if (del) delete o[k]; return true; }
      return false;
    }
    if (k === '[]' || k === '*') {
      var any = false;
      Object.keys(o).forEach(function (x) { if (delOrHas(o[x], parts, i + 1, del)) any = true; });
      return any;
    }
    return delOrHas(o[k], parts, i + 1, del);
  }

  var S = {};

  S['abort-on-property-read'] = function (prop) {
    var msg = rand();
    watch(prop, function (o, p) {
      Object.defineProperty(o, p, { configurable: true, get: function () { throw new ReferenceError(msg); }, set: noop });
    });
  };
  S['abort-on-property-write'] = function (prop) {
    var msg = rand();
    watch(prop, function (o, p) {
      var cur = o[p];
      Object.defineProperty(o, p, { configurable: true, get: function () { return cur; }, set: function () { throw new ReferenceError(msg); } });
    });
  };
  S['abort-current-script'] = function (prop, needleSrc) {
    var needle = mkNeedle(needleSrc), msg = rand();
    watch(prop, function (o, p) {
      var cur = o[p];
      Object.defineProperty(o, p, {
        configurable: true,
        get: function () { if (scriptHas(needle)) throw new ReferenceError(msg); return cur; },
        set: function (v) { if (scriptHas(needle)) throw new ReferenceError(msg); cur = v; }
      });
    });
  };
  S['abort-on-stack-trace'] = function (prop, needleSrc, stackSrc) {
    var needle = mkNeedle(stackSrc || needleSrc), msg = rand();
    watch(prop, function (o, p) {
      var cur = o[p];
      Object.defineProperty(o, p, {
        configurable: true,
        get: function () { if (stackHas(needle)) throw new ReferenceError(msg); return cur; },
        set: function (v) { cur = v; }
      });
    });
  };
  S['set-constant'] = function (prop, value) {
    var v = constVal(value);
    watch(prop, function (o, p) {
      Object.defineProperty(o, p, { configurable: true, enumerable: true, get: function () { return v; }, set: noop });
    });
  };
  S['prevent-window-open'] = function (match, delay, replacement) {
    var neg = false;
    if (match && match.charAt(0) === '!') { neg = true; match = match.slice(1); }
    var needle = mkNeedle(match), orig = window.open;
    window.open = function () {
      var hit = !needle || needle(String(arguments[0]));
      if (neg) hit = !hit;
      if (!hit) return orig.apply(this, arguments);
      if (replacement === 'obj') {
        return { closed: true, focus: noop, blur: noop, close: noop, postMessage: noop, location: {}, document: {} };
      }
      return null;
    };
  };
  S['nowebrtc'] = function () {
    var R = function () {};
    R.prototype = {
      createDataChannel: noop, close: noop, addEventListener: noop, removeEventListener: noop,
      createOffer: function () { return Promise.resolve({}); }, setLocalDescription: function () { return Promise.resolve(); }
    };
    ['RTCPeerConnection', 'webkitRTCPeerConnection', 'mozRTCPeerConnection'].forEach(function (n) {
      try { window[n] = R; } catch (e) {}
    });
  };
  S['noeval'] = function () { window.eval = function () {}; };
  S['json-prune'] = function (prunes, needles, stackSrc) {
    var prune = String(prunes || '').split(/ +/).filter(Boolean).map(function (p) { return p.split('.'); });
    var need = String(needles || '').split(/ +/).filter(Boolean).map(function (p) { return p.split('.'); });
    var stack = mkNeedle(stackSrc);
    function process(o) {
      if (!isObj(o) || (stack && !stackHas(stack))) return o;
      if (need.length && !need.every(function (p) { return delOrHas(o, p, 0, false); })) return o;
      prune.forEach(function (p) { delOrHas(o, p, 0, true); });
      return o;
    }
    var op = JSON.parse;
    JSON.parse = function () { return process(op.apply(this, arguments)); };
    if (typeof Response === 'function') {
      var oj = Response.prototype.json;
      Response.prototype.json = function () { return oj.apply(this, arguments).then(process); };
    }
  };
  function timerDefuser(kind) {
    return function (needleSrc, delay) {
      var needle = mkNeedle(needleSrc), neg = false, d = null;
      if (delay !== undefined && delay !== '') {
        if (String(delay).charAt(0) === '!') { neg = true; delay = String(delay).slice(1); }
        d = +delay;
      }
      var orig = window[kind];
      window[kind] = function (fn, ms) {
        var text = typeof fn === 'function' ? String(fn) : String(fn);
        var hit = (!needle || needle(text)) && (d === null || ((+ms === d) !== neg));
        if (hit) { var a = Array.prototype.slice.call(arguments); a[0] = function () {}; return orig.apply(this, a); }
        return orig.apply(this, arguments);
      };
    };
  }
  S['prevent-settimeout'] = timerDefuser('setTimeout');
  S['prevent-setinterval'] = timerDefuser('setInterval');
  function timerBoost(kind) {
    return function (needleSrc, delay, boost) {
      var needle = mkNeedle(needleSrc), d = (delay === undefined || delay === '' || delay === '*') ? null : +delay;
      var b = (boost === undefined || boost === '') ? 0.05 : +boost;
      if (!(b > 0) || b > 50) b = 0.05;
      var orig = window[kind];
      window[kind] = function (fn, ms) {
        var text = String(fn);
        if ((!needle || needle(text)) && (d === null || +ms === d)) {
          var a = Array.prototype.slice.call(arguments);
          a[1] = (+ms || 0) * b;
          return orig.apply(this, a);
        }
        return orig.apply(this, arguments);
      };
    };
  }
  S['adjust-settimeout'] = timerBoost('setTimeout');
  S['adjust-setinterval'] = timerBoost('setInterval');
  S['prevent-requestanimationframe'] = function (needleSrc) {
    var needle = mkNeedle(needleSrc), orig = window.requestAnimationFrame;
    window.requestAnimationFrame = function (fn) {
      if (!needle || needle(String(fn))) return orig.call(this, function () {});
      return orig.apply(this, arguments);
    };
  };
  S['prevent-addeventlistener'] = function (typeSrc, handlerSrc) {
    var t = mkNeedle(typeSrc), h = mkNeedle(handlerSrc), orig = EventTarget.prototype.addEventListener;
    EventTarget.prototype.addEventListener = function (type, fn) {
      try {
        var text = typeof fn === 'function' ? String(fn) : (fn && fn.handleEvent ? String(fn.handleEvent) : '');
        if ((!t || t(String(type))) && (!h || h(text))) return;
      } catch (e) {}
      return orig.apply(this, arguments);
    };
  };
  function parseMatchArgs(args) {
    var url = null, method = null;
    var joined = args.join(' ').trim();
    if (!joined) return { url: null, method: null };
    joined.split(/ +/).forEach(function (tok) {
      var i = tok.indexOf(':');
      if (i > 0 && (tok.slice(0, i) === 'url' || tok.slice(0, i) === 'method')) {
        if (tok.slice(0, i) === 'url') url = mkNeedle(tok.slice(i + 1)); else method = tok.slice(i + 1).toUpperCase();
      } else if (!url) {
        url = mkNeedle(tok);
      }
    });
    return { url: url, method: method };
  }
  S['prevent-fetch'] = function () {
    var m = parseMatchArgs(Array.prototype.slice.call(arguments));
    var orig = window.fetch;
    if (typeof orig !== 'function') return;
    window.fetch = function (input, init) {
      try {
        var u = typeof input === 'string' ? input : (input && input.url) || String(input);
        var meth = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
        if ((!m.url || m.url(u)) && (!m.method || m.method === meth)) {
          return Promise.resolve(new Response('', { status: 200, statusText: 'OK' }));
        }
      } catch (e) {}
      return orig.apply(this, arguments);
    };
  };
  S['prevent-xhr'] = function () {
    var m = parseMatchArgs(Array.prototype.slice.call(arguments));
    var oo = XMLHttpRequest.prototype.open, os = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (meth, url) {
      this.__xmdq = [String(meth).toUpperCase(), String(url)];
      return oo.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
      var q = this.__xmdq;
      if (q && (!m.url || m.url(q[1])) && (!m.method || m.method === q[0])) {
        var x = this;
        try {
          Object.defineProperties(x, {
            readyState: { value: 4, configurable: true }, status: { value: 200, configurable: true },
            statusText: { value: 'OK', configurable: true }, response: { value: '', configurable: true },
            responseText: { value: '', configurable: true }, responseURL: { value: q[1], configurable: true }
          });
        } catch (e) {}
        setTimeout(function () {
          ['readystatechange', 'load', 'loadend'].forEach(function (t) { try { x.dispatchEvent(new Event(t)); } catch (e) {} });
        }, 1);
        return;
      }
      return os.apply(this, arguments);
    };
  };
  S['remove-attr'] = function (attrs, selector) {
    var list = String(attrs || '').split('|').filter(Boolean);
    if (!list.length) return;
    var sel = selector || list.map(function (a) { return '[' + a + ']'; }).join(',');
    onDom(function () {
      document.querySelectorAll(sel).forEach(function (e) { list.forEach(function (a) { e.removeAttribute(a); }); });
    });
  };
  S['remove-class'] = function (classes, selector) {
    var list = String(classes || '').split('|').filter(Boolean);
    if (!list.length) return;
    var sel = selector || list.map(function (c) { return '.' + c; }).join(',');
    onDom(function () {
      document.querySelectorAll(sel).forEach(function (e) { list.forEach(function (c) { e.classList.remove(c); }); });
    });
  };
  S['set-attr'] = function (selector, attr, value) {
    if (!selector || !attr) return;
    onDom(function () {
      document.querySelectorAll(selector).forEach(function (e) { e.setAttribute(attr, value === undefined ? '' : value); });
    });
  };
  S['remove-node-text'] = function (nodeName, needleSrc) {
    var needle = mkNeedle(needleSrc), name = String(nodeName || '').toLowerCase();
    if (!name) return;
    onDom(function () {
      document.querySelectorAll(name).forEach(function (e) {
        if (e.textContent && (!needle || needle(e.textContent))) e.textContent = '';
      });
    });
  };
  S['set-cookie'] = function (name, value, path) {
    if (!name) return;
    try { document.cookie = name + '=' + constVal(value) + '; path=' + (path === 'none' ? '' : '/'); } catch (e) {}
  };
  function storageSetter(which) {
    return function (key, value) {
      if (!key) return;
      try {
        var st = window[which];
        if (value === '$remove$') st.removeItem(key); else st.setItem(key, String(constVal(value)));
      } catch (e) {}
    };
  }
  S['set-local-storage-item'] = storageSetter('localStorage');
  S['set-session-storage-item'] = storageSetter('sessionStorage');
  S['disable-newtab-links'] = function () {
    document.addEventListener('click', function (ev) {
      var a = ev.target && ev.target.closest ? ev.target.closest('a[target]') : null;
      if (a && a.target === '_blank') a.target = '_self';
    }, true);
  };
  S['prevent-refresh'] = function () {
    onDom(function () {
      document.querySelectorAll('meta[http-equiv="refresh" i]').forEach(function (m) { m.remove(); });
    });
  };
  S['no-topics'] = function () {
    try { delete Document.prototype.browsingTopics; } catch (e) {}
    try { Object.defineProperty(Document.prototype, 'browsingTopics', { configurable: true, value: undefined }); } catch (e2) {}
  };

  var ALIAS = {
    'aopr': 'abort-on-property-read', 'aopw': 'abort-on-property-write',
    'acs': 'abort-current-script', 'acis': 'abort-current-script', 'abort-current-inline-script': 'abort-current-script',
    'aost': 'abort-on-stack-trace',
    'set': 'set-constant',
    'nowoif': 'prevent-window-open', 'window.open-defuser': 'prevent-window-open', 'no-window-open-if': 'prevent-window-open',
    'nostif': 'prevent-settimeout', 'no-settimeout-if': 'prevent-settimeout', 'settimeout-defuser': 'prevent-settimeout',
    'nosiif': 'prevent-setinterval', 'no-setinterval-if': 'prevent-setinterval', 'setinterval-defuser': 'prevent-setinterval',
    'nano-stb': 'adjust-settimeout', 'nano-sib': 'adjust-setinterval',
    'norafif': 'prevent-requestanimationframe', 'no-requestanimationframe-if': 'prevent-requestanimationframe',
    'aeld': 'prevent-addeventlistener', 'addeventlistener-defuser': 'prevent-addeventlistener',
    'no-fetch-if': 'prevent-fetch', 'no-xhr-if': 'prevent-xhr',
    'ra': 'remove-attr', 'rc': 'remove-class', 'rmnt': 'remove-node-text',
    'noeval-silent': 'noeval', 'refresh-defuser': 'prevent-refresh'
  };

  function runScriptlet(entry) {
    var name = String(entry[0]).toLowerCase();
    name = ALIAS[name] || name;
    var fn = S[name];
    if (!fn) return;
    try { fn.apply(null, entry.slice(1)); } catch (e) {}
  }

  // ── procedural rules ──────────────────────────────────────────────
  function textMatcher(arg) {
    var m = /^\/(.+)\/([a-z]*)$/.exec(arg);
    if (m) {
      try { var re = new RegExp(m[1], m[2].replace('g', '')); return function (t) { return re.test(t); }; } catch (e) {}
    }
    return function (t) { return t.indexOf(arg) !== -1; };
  }
  function runProcedural(rules) {
    for (var i = 0; i < rules.length; i++) {
      var r = rules[i], els;
      try { els = Array.prototype.slice.call(document.querySelectorAll(r[0])); } catch (e) { continue; }
      var remove = false;
      for (var k = 1; k < r.length && els.length; k += 2) {
        var op = r[k], arg = r[k + 1];
        if (op === 'text') {
          var match = textMatcher(arg);
          els = els.filter(function (e) { return match(e.textContent || ''); });
        } else if (op === 'up') {
          els = els.map(function (e) {
            if (/^\d+$/.test(arg)) { var n = +arg; while (n-- > 0 && e) e = e.parentElement; return e; }
            try { return e.parentElement && e.parentElement.closest(arg); } catch (x) { return null; }
          }).filter(Boolean);
        } else if (op === 'rm') {
          remove = true;
        }
      }
      for (var j = 0; j < els.length; j++) {
        try {
          if (remove) els[j].remove(); else els[j].style.setProperty('display', 'none', 'important');
        } catch (e2) {}
      }
    }
  }

  // ── generic class / id selectors ──────────────────────────────────
  function startGeneric() {
    var seenC = {}, seenI = {};
    function take(el, outC, outI) {
      if (el.id && !seenI[el.id]) { seenI[el.id] = 1; outI.push(el.id); }
      var cl = el.classList;
      if (cl) for (var i = 0; i < cl.length; i++) {
        if (!seenC[cl[i]]) { seenC[cl[i]] = 1; outC.push(cl[i]); }
      }
    }
    onDom(function (nodes) {
      var c = [], ids = [];
      if (nodes === null) {
        var all = document.querySelectorAll('[class],[id]');
        for (var i = 0; i < all.length; i++) take(all[i], c, ids);
      } else {
        for (var j = 0; j < nodes.length; j++) {
          take(nodes[j], c, ids);
          if (nodes[j].querySelectorAll) {
            var d = nodes[j].querySelectorAll('[class],[id]');
            for (var k = 0; k < d.length && k < 1500; k++) take(d[k], c, ids);
          }
        }
      }
      var n = Math.max(c.length, ids.length);
      for (var off = 0; off < n; off += 700) {
        var css = B.forNames(host, JSON.stringify(c.slice(off, off + 700)), JSON.stringify(ids.slice(off, off + 700)));
        if (css) addCss(css);
      }
    });
  }

  // ── go ────────────────────────────────────────────────────────────
  function run() {
    var raw;
    try { raw = B.forHost(host); } catch (e) { return; }
    if (!raw) return;
    var cfg;
    try { cfg = JSON.parse(raw); } catch (e2) { return; }
    if (cfg.s) for (var i = 0; i < cfg.s.length; i++) runScriptlet(cfg.s[i]);
    if (cfg.c) addCss(cfg.c);
    if (cfg.p && cfg.p.length) {
      var rules = cfg.p;
      onDom(function () { runProcedural(rules); });
    }
    if (cfg.g) startGeneric();
  }

  if (B) run();
  else document.addEventListener('DOMContentLoaded', function () { B = window.XmdCosmetic; if (B) run(); }, { once: true });
})();
