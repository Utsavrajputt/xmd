/*
 * "Hide element" picker. Injected by the app (evaluateJavascript) after a long-press menu
 * choice; starts on the element that was long-pressed (tracked by adblock_agent.js), lets the
 * user adjust it (parent / child / tap another), preview, then saves a `host##selector` rule
 * to "My rules" through the XmdCosmetic bridge. The token below is a one-time secret that the
 * app generates per run; the bridge refuses calls without it.
 */
(function () {
  'use strict';
  var TOKEN = '__XMD_TOKEN__';
  if (window.__xmdPicker) { try { window.__xmdPicker.stop(); } catch (e) {} }
  var B = window.XmdCosmetic;
  var doc = document;
  var root = doc.documentElement;

  // ── selector building ─────────────────────────────────────────────
  function esc(s) {
    return (window.CSS && CSS.escape) ? CSS.escape(s) : String(s).replace(/[^a-zA-Z0-9_-]/g, '\\$&');
  }
  function genId(s) { return /\d{4,}/.test(s) || /^[a-f0-9]{8,}$/i.test(s); }
  function genCls(c) {
    return c.length > 40 || /\d{3,}/.test(c) || /^(css|jsx|sc|svelte|styled|emotion)-/.test(c) ||
      (/^[a-z]{1,2}[A-Za-z0-9]{5,}$/.test(c) && /\d/.test(c));
  }
  function matches(sel) { try { return doc.querySelectorAll(sel); } catch (e) { return []; } }
  function unique(sel, el) { var r = matches(sel); return r.length === 1 && r[0] === el; }
  function part(el) {
    var cls = Array.prototype.filter.call(el.classList || [], function (c) { return !genCls(c); }).slice(0, 2);
    return el.localName + cls.map(function (c) { return '.' + esc(c); }).join('');
  }
  function nth(el) {
    var i = 1, s = el;
    while ((s = s.previousElementSibling)) { if (s.localName === el.localName) i++; }
    return i;
  }
  function selectorFor(el) {
    if (el.id && !genId(el.id)) { var byId = '#' + esc(el.id); if (unique(byId, el)) return byId; }
    var alone = part(el);
    if (unique(alone, el)) return alone;
    var path = [], cur = el;
    while (cur && cur.nodeType === 1 && cur !== root) {
      if (cur.id && !genId(cur.id)) {
        path.unshift('#' + esc(cur.id));
        var sid = path.join(' > ');
        if (unique(sid, el)) return sid;
        break;
      }
      var seg = part(cur);
      var sibs = cur.parentElement ? Array.prototype.filter.call(cur.parentElement.children, function (x) { return x.localName === cur.localName; }) : [];
      var withNth = sibs.length > 1 ? seg + ':nth-of-type(' + nth(cur) + ')' : seg;
      path.unshift(seg);
      if (unique(path.join(' > '), el)) return path.join(' > ');
      path[0] = withNth;
      if (unique(path.join(' > '), el)) return path.join(' > ');
      cur = cur.parentElement;
    }
    return path.join(' > ');
  }

  // ── page CSS helpers ──────────────────────────────────────────────
  function sheetFor(sel) {
    var css = sel + '{display:none!important}';
    try {
      var sh = new CSSStyleSheet();
      sh.replaceSync(css);
      return { on: function () { doc.adoptedStyleSheets = doc.adoptedStyleSheets.concat([sh]); },
               off: function () { doc.adoptedStyleSheets = doc.adoptedStyleSheets.filter(function (x) { return x !== sh; }); } };
    } catch (e) {
      var st = doc.createElement('style');
      st.textContent = css;
      return { on: function () { (doc.head || root).appendChild(st); }, off: function () { if (st.parentNode) st.parentNode.removeChild(st); } };
    }
  }

  // ── UI (closed off in a shadow root so page CSS can't touch it) ───
  var host = doc.createElement('div');
  host.style.cssText = 'all:initial;position:fixed;left:0;top:0;width:0;height:0;z-index:2147483647;';
  var shadow = host.attachShadow({ mode: 'open' });
  shadow.innerHTML =
    '<style>' +
    ':host{all:initial}' +
    '*{box-sizing:border-box;font-family:system-ui,-apple-system,Roboto,sans-serif}' +
    '.box{position:fixed;border:2px solid #e91e63;background:rgba(233,30,99,.16);border-radius:3px;pointer-events:none;display:none}' +
    '.bar{position:fixed;left:8px;right:8px;bottom:calc(8px + env(safe-area-inset-bottom,0px));padding:10px 12px;border-radius:16px;' +
    'background:#fff;color:#1b1b1f;box-shadow:0 4px 18px rgba(0,0,0,.35);pointer-events:auto;font-size:14px}' +
    '.sel{font:12px ui-monospace,Menlo,monospace;opacity:.8;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-bottom:8px}' +
    '.row{display:flex;gap:6px;flex-wrap:wrap}' +
    'button{flex:1 1 auto;min-height:40px;border:0;border-radius:20px;padding:0 14px;font-size:14px;font-weight:600;' +
    'background:#e7e7ef;color:#1b1b1f;touch-action:manipulation}' +
    'button.go{background:#e91e63;color:#fff}' +
    'button[disabled]{opacity:.4}' +
    '.toast{position:fixed;left:12px;right:12px;bottom:calc(12px + env(safe-area-inset-bottom,0px));display:flex;align-items:center;gap:10px;' +
    'padding:10px 14px;border-radius:14px;background:#2d2d32;color:#fff;font-size:14px;pointer-events:auto}' +
    '.toast span{flex:1}.toast button{flex:0 0 auto;background:transparent;color:#ffb4d1;min-height:36px}' +
    '@media (prefers-color-scheme:dark){.bar{background:#2b2b31;color:#e5e1e6}button{background:#3d3d45;color:#e5e1e6}}' +
    '</style>' +
    '<div class="box"></div>' +
    '<div class="bar"><div class="sel"></div><div class="row">' +
    '<button class="up">&#9650; Parent</button><button class="down">&#9660; Child</button>' +
    '<button class="prev">Preview</button><button class="go">Hide</button><button class="cancel">Cancel</button>' +
    '</div></div>';
  var box = shadow.querySelector('.box'), bar = shadow.querySelector('.bar'), selEl = shadow.querySelector('.sel');
  var bUp = shadow.querySelector('.up'), bDown = shadow.querySelector('.down'), bPrev = shadow.querySelector('.prev'),
      bGo = shadow.querySelector('.go'), bCancel = shadow.querySelector('.cancel');

  var usable = !!(B && typeof B.userRule === 'function');
  var cur = null, selector = '', downStack = [], previewSheet = null, stopped = false;

  function placeBox() {
    if (!cur) { box.style.display = 'none'; return; }
    var r = cur.getBoundingClientRect();
    box.style.display = 'block';
    box.style.left = r.left + 'px'; box.style.top = r.top + 'px';
    box.style.width = Math.max(r.width, 4) + 'px'; box.style.height = Math.max(r.height, 4) + 'px';
  }
  var raf = 0;
  function onMove() { if (!raf) raf = requestAnimationFrame(function () { raf = 0; placeBox(); }); }

  function pickable(el) { return el && el.nodeType === 1 && el !== root && el !== doc.body && el !== host; }

  function select(el, keepDown) {
    clearPreview();
    if (!keepDown) downStack = [];
    cur = pickable(el) ? el : null;
    selector = cur ? selectorFor(cur) : '';
    if (selector.length > 400) { selector = ''; }
    var n = selector ? matches(selector).length : 0;
    selEl.textContent = !cur ? 'Tap the element you want to hide'
      : !selector ? 'This element is too complex to describe'
      : selector + (n > 1 ? '  ·  ' + n + ' matches' : '');
    bGo.disabled = !selector || !usable;
    bPrev.disabled = !selector;
    bUp.disabled = !cur || !pickable(cur.parentElement);
    bDown.disabled = downStack.length === 0;
    placeBox();
  }
  function clearPreview() {
    if (previewSheet) { previewSheet.off(); previewSheet = null; }
    bPrev.textContent = 'Preview';
  }

  bUp.addEventListener('click', function () {
    if (cur && pickable(cur.parentElement)) { var c = cur; select(cur.parentElement, true); downStack.push(c); bDown.disabled = false; }
  });
  bDown.addEventListener('click', function () {
    var c = downStack.pop();
    if (c) { select(c, true); }
  });
  bPrev.addEventListener('click', function () {
    if (!selector) return;
    if (previewSheet) { clearPreview(); return; }
    previewSheet = sheetFor(selector); previewSheet.on();
    bPrev.textContent = 'Show again';
  });
  bCancel.addEventListener('click', function () { stop(); });
  bGo.addEventListener('click', function () {
    if (!selector) return;
    var sel = selector;
    var h = String(location.hostname || '').replace(/^www\./, '');
    var ok = false;
    try { ok = !!(B && B.userRule(TOKEN, h, sel, true)); } catch (e) {}
    if (!ok) { selEl.textContent = 'Could not save this rule'; return; }
    clearPreview();
    var hidden = sheetFor(sel); hidden.on();
    teardown();
    toast('Element hidden', 'Undo', function () {
      try { B.userRule(TOKEN, h, sel, false); } catch (e) {}
      hidden.off();
    });
  });

  // Taps on the page choose the element instead of activating it.
  function onClick(e) {
    var path = e.composedPath ? e.composedPath() : [];
    if (e.target === host || path.indexOf(host) >= 0) return;
    e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
    select(e.target, false);
  }
  // A tap inside an iframe is reported by the agent as a message that marks the iframe element.
  function onMessage(e) {
    if (e.data !== '__xmdTouch') return;
    setTimeout(function () { if (!stopped && window.__xmdLast && window.__xmdLast.localName === 'iframe') select(window.__xmdLast, false); }, 0);
  }

  function toast(text, action, onAction) {
    var t = doc.createElement('div');
    t.className = 'toast';
    var s = doc.createElement('span'); s.textContent = text; t.appendChild(s);
    var timer = setTimeout(done, 6000);
    function done() { clearTimeout(timer); stop(); }
    if (action) {
      var b = doc.createElement('button'); b.textContent = action;
      b.addEventListener('click', function () { try { onAction(); } catch (e) {} s.textContent = 'Undone'; t.removeChild(b); clearTimeout(timer); timer = setTimeout(done, 1500); });
      t.appendChild(b);
    }
    shadow.appendChild(t);
    if (!host.parentNode) (doc.body || root).appendChild(host);
  }

  function teardown() {
    stopped = true;
    doc.removeEventListener('click', onClick, true);
    window.removeEventListener('message', onMessage, false);
    window.removeEventListener('scroll', onMove, true);
    window.removeEventListener('resize', onMove, false);
    clearPreview();
    box.style.display = 'none';
    if (bar.parentNode) bar.parentNode.removeChild(bar);
  }
  function stop() {
    teardown();
    if (host.parentNode) host.parentNode.removeChild(host);
    if (window.__xmdPicker && window.__xmdPicker.stop === stop) window.__xmdPicker = null;
  }

  window.__xmdPicker = { stop: stop };
  doc.addEventListener('click', onClick, true);
  window.addEventListener('message', onMessage, false);
  window.addEventListener('scroll', onMove, true);
  window.addEventListener('resize', onMove, false);
  (doc.body || root).appendChild(host);
  select(window.__xmdLast, false);
  if (!usable) selEl.textContent = 'Hide element is not available on this page';
})();
