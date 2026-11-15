package com.invictus.xmd.domain.browser

/**
 * Helps the system autofill service (Bitwarden, Proton Pass, Google...) work inside the in-app browser.
 *
 *  - Gives password / username fields that have no `autocomplete` attribute a proper one, so the
 *    service recognises a login form (many sites ship fields with no hints at all).
 *  - Tells the app when a password field gets focus (to explain that no autofill service is turned on)
 *    and when a login form is submitted without a page load (single-page apps), so the app can finish the
 *    autofill session and the service can offer to save the password.
 *
 * Injected at document start in every frame; not used in private tabs.
 */
object AutofillAssistScript {
    fun script(): String = """
        (function(){
          if (window.__xmd_autofill__) return;
          window.__xmd_autofill__ = true;
          var isPw = function(e) { return e && e.tagName === 'INPUT' && e.type === 'password'; };
          var textish = function(e) {
            return e && e.tagName === 'INPUT' && /^(text|email|tel|search|)${'$'}/i.test(e.type || '') && !e.hidden;
          };
          var hintAll = function() {
            try {
              var pws = document.querySelectorAll('input[type=password]');
              pws.forEach(function(p) {
                var scope = p.form || document;
                var count = scope.querySelectorAll('input[type=password]').length;
                var label = ((p.name || '') + ' ' + (p.id || '')).toLowerCase();
                var isNew = count > 1 || /new|signup|sign-up|register|confirm|create/.test(label);
                if (!p.getAttribute('autocomplete')) p.setAttribute('autocomplete', isNew ? 'new-password' : 'current-password');
                // the nearest text-like field before the password is the username
                var inputs = Array.prototype.slice.call(scope.querySelectorAll('input'));
                var idx = inputs.indexOf(p);
                for (var i = idx - 1; i >= 0; i--) {
                  if (textish(inputs[i])) {
                    if (!inputs[i].getAttribute('autocomplete')) inputs[i].setAttribute('autocomplete', 'username');
                    break;
                  }
                }
              });
            } catch (e) {}
          };
          var timer = null;
          var schedule = function() { clearTimeout(timer); timer = setTimeout(hintAll, 200); };
          document.addEventListener('DOMContentLoaded', hintAll);
          try {
            new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
          } catch (e) {}
          document.addEventListener('focusin', function(ev) {
            if (isPw(ev.target)) { hintAll(); try { XmdAutofill.loginFieldFocused(); } catch (e) {} }
          }, true);
          var submitted = function(scope) {
            try {
              var pw = (scope || document).querySelector('input[type=password]');
              if (pw && pw.value) XmdAutofill.loginSubmitted();
            } catch (e) {}
          };
          document.addEventListener('submit', function(ev) { submitted(ev.target); }, true);
          document.addEventListener('click', function(ev) {
            var t = ev.target && ev.target.closest ? ev.target.closest('button,input[type=submit],[role=button]') : null;
            if (t) submitted(t.form || t.closest('form') || t.parentElement);
          }, true);
        })();
    """.trimIndent()
}
