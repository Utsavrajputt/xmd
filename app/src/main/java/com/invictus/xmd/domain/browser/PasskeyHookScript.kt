package com.invictus.xmd.domain.browser

/**
 * Passkeys (WebAuthn) cannot be completed inside this browser: Android only lets system / allow-listed
 * browsers hand the site's origin to the passkey provider (Bitwarden, Proton Pass, Google...).
 *
 * Instead of letting such a request hang or fail silently, this hook answers `navigator.credentials`
 * calls that ask for a passkey: the site gets a normal "not allowed" error (so it falls back to its
 * password / other sign-in options) and the app offers to open the page in a browser that supports it.
 * Background "conditional" requests (passkey autofill) are left pending with no prompt.
 */
object PasskeyHookScript {
    fun script(): String = """
        (function(){
          if (window.__xmd_pk__) return;
          window.__xmd_pk__ = true;
          try {
            var cr = navigator.credentials;
            if (!cr) return;
            var wrap = function(name) {
              var orig = cr[name];
              if (typeof orig !== 'function') return;
              cr[name] = function(opts) {
                if (opts && opts.publicKey) {
                  if (opts.mediation === 'conditional') return new Promise(function() {});
                  try { XmdPasskey.requested(); } catch (e) {}
                  return Promise.reject(new DOMException('Passkeys are not available in this browser.', 'NotAllowedError'));
                }
                return orig.apply(cr, arguments);
              };
            };
            wrap('get');
            wrap('create');
          } catch (e) {}
        })();
    """.trimIndent()
}
