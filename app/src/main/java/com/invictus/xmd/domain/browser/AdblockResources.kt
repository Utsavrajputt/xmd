package com.invictus.xmd.domain.browser

import android.util.Base64
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Neutral stand-ins for blocked requests. A bare empty response makes some
 * video players and ad-gated scripts hang or error out; uBlock's `$redirect=`
 * rules instead serve a harmless stub of the right type (empty JS, 1x1 GIF, an
 * IMA SDK that immediately reports "no ads", ...) so the page carries on.
 */
object AdblockResources {

    private const val GIF_1X1 = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
    private const val PNG_1X1 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="

    private const val GA_STUB = """
(function(){var f=function(){};f.q=[];f.loaded=true;f.getAll=function(){return[]};
f.create=function(){return{get:function(){},set:function(){},send:function(){}}};
window.ga=window.ga||f;window.GoogleAnalyticsObject=window.GoogleAnalyticsObject||'ga';
window.dataLayer=window.dataLayer||[];})();"""

    private const val GPT_STUB = """
(function(){var n=function(){};var chain=function(){return o};
var o={};['addService','clearTargeting','collapseEmptyDivs','defineSizeMapping','display','enableSingleRequest',
'enableAsyncRendering','enableLazyLoad','disableInitialLoad','set','setCentering','setTargeting','setPrivacySettings',
'setRequestNonPersonalizedAds','setForceSafeFrame','updateCorrelator','refresh','clear','setLocation','addEventListener',
'removeEventListener','setPublisherProvidedId','setSafeFrameConfig','setTagForChildDirectedTreatment','getSlots',
'getTargeting','getTargetingKeys','get','getAttributeKeys','getSlotElementId','getAdUnitPath','setCollapseEmptyDiv',
'defineSlot','defineOutOfPageSlot','enableServices','pubads','companionAds','content','destroySlots','openConsole']
.forEach(function(k){o[k]=chain});
o.getSlots=function(){return[]};o.getTargetingKeys=function(){return[]};
var g=window.googletag=window.googletag||{};g.cmd=g.cmd||[];g.apiReady=true;g.pubadsReady=true;
g.defineSlot=chain;g.defineOutOfPageSlot=chain;g.pubads=chain;g.companionAds=chain;g.content=chain;
g.enableServices=n;g.display=n;g.destroySlots=function(){return true};g.sizeMapping=function(){return{addSize:chain,build:function(){return[]}}};
var q=g.cmd;g.cmd={push:function(f){try{typeof f==='function'&&f()}catch(e){}return 1},length:0};
q.forEach&&q.forEach(function(f){try{f()}catch(e){}});})();"""

    private const val ADSBYGOOGLE_STUB =
        "(function(){window.adsbygoogle=window.adsbygoogle||{};window.adsbygoogle.loaded=true;window.adsbygoogle.push=function(){};})();"

    // Minimal IMA SDK: every ad request fails immediately with a normal
    // "ad error", which makes well-behaved players fall straight through to
    // the real content instead of waiting on an ad that will never come.
    private const val IMA_STUB = """
(function(){
var noop=function(){};
function Err(){this.getErrorCode=function(){return 1009};this.getMessage=function(){return 'blocked'};
 this.getType=function(){return 'adLoadError'};this.getInnerError=function(){return null};}
function Evt(t,d){this.type=t;this.getError=function(){return new Err()};this.getUserRequestContext=function(){return null};
 this.getAdsManager=function(){return null};}
function Emitter(){var l={};this.addEventListener=function(t,f){(l[t]=l[t]||[]).push(f)};
 this.removeEventListener=function(t,f){l[t]=(l[t]||[]).filter(function(x){return x!==f})};
 this.fire=function(t){var e=new Evt(t);(l[t]||[]).slice().forEach(function(f){try{f(e)}catch(x){}})};}
function AdDisplayContainer(){this.initialize=noop;this.destroy=noop;}
function AdsLoader(){var e=new Emitter();var self=this;
 this.addEventListener=e.addEventListener;this.removeEventListener=e.removeEventListener;
 this.requestAds=function(){setTimeout(function(){e.fire('adError')},0)};
 this.contentComplete=noop;this.destroy=noop;this.getSettings=function(){return new ImaSettings()};}
function AdsRequest(){}
function ImaSettings(){this.setVpaidMode=noop;this.setLocale=noop;this.setNumRedirects=noop;this.setPlayerType=noop;
 this.setPlayerVersion=noop;this.setPpid=noop;this.setAutoPlayAdBreaks=noop;this.setDisableCustomPlaybackForIOS10Plus=noop;}
var ima={AdDisplayContainer:AdDisplayContainer,AdsLoader:AdsLoader,AdsRequest:AdsRequest,
 AdsRenderingSettings:function(){},ImaSdkSettings:ImaSettings,
 settings:new ImaSettings(),VERSION:'3.0.0',
 AdError:{ErrorCode:{VAST_EMPTY_RESPONSE:1009,UNKNOWN_AD_RESPONSE:1010},Type:{AD_LOAD:'adLoadError',AD_PLAY:'adPlayError'}},
 AdErrorEvent:{Type:{AD_ERROR:'adError'}},
 AdsManagerLoadedEvent:{Type:{ADS_MANAGER_LOADED:'adsManagerLoaded'}},
 AdEvent:{Type:{ALL_ADS_COMPLETED:'allAdsCompleted',CONTENT_RESUME_REQUESTED:'contentResumeRequested',
 CONTENT_PAUSE_REQUESTED:'contentPauseRequested',LOADED:'loaded',STARTED:'started',COMPLETE:'complete'}},
 ViewMode:{NORMAL:'normal',FULLSCREEN:'fullscreen'},OmidVerificationVendor:{}};
window.google=window.google||{};window.google.ima=ima;
})();"""

    private val EMPTY = ByteArray(0)

    private fun response(mime: String, body: ByteArray): WebResourceResponse =
        WebResourceResponse(
            mime, "UTF-8", 200, "OK",
            mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store"),
            ByteArrayInputStream(body),
        )

    private fun js(text: String) = response("application/javascript", text.toByteArray())

    /** Empty response matching the URL's apparent type (plain block, no redirect rule). */
    fun blank(path: String): WebResourceResponse {
        val ext = path.substringBefore('?').substringAfterLast('.', "").lowercase()
        return when (ext) {
            "js", "mjs" -> js("")
            "css" -> response("text/css", EMPTY)
            "html", "htm" -> response("text/html", EMPTY)
            else -> response("text/plain", EMPTY)
        }
    }

    /** Stub for a uBlock `$redirect=<name>` resource, or null if the name isn't known. */
    fun redirect(name: String): WebResourceResponse? {
        val n = name.lowercase()
        return when {
            n.contains("google-ima") || n.contains("ima3") -> js(IMA_STUB)
            n.contains("analytics") -> js(GA_STUB)
            n.contains("gpt") || n.contains("googletagservices") -> js(GPT_STUB)
            n.contains("adsbygoogle") -> js(ADSBYGOOGLE_STUB)
            n.contains("frame") || n.endsWith("html") -> response("text/html", "<!DOCTYPE html>".toByteArray())
            n.endsWith("css") -> response("text/css", EMPTY)
            n.endsWith("gif") -> response("image/gif", Base64.decode(GIF_1X1, Base64.DEFAULT))
            n.endsWith("png") -> response("image/png", Base64.decode(PNG_1X1, Base64.DEFAULT))
            n.contains("mp3") -> response("audio/mpeg", EMPTY)
            n.contains("mp4") -> response("video/mp4", EMPTY)
            n.endsWith("txt") || n.contains("text") -> response("text/plain", EMPTY)
            n.contains("js") -> js("(function(){})();")
            else -> null
        }
    }
}
