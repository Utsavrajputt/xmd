package com.invictus.xmd.domain.browser.adblock

import android.util.Base64

/**
 * Stand-ins served for `$redirect=name` rules: instead of an empty reply, a
 * blocked script/image/frame is answered with a harmless stub so the page's
 * own code keeps working (a missing `ga()` or `googletag` would otherwise
 * throw and can break a whole site). Names follow the filter-list
 * convention (`noopjs`, `1x1.gif`, `google-analytics.com/analytics.js`, ...);
 * the implementations are our own minimal ones.
 */
object RedirectResources {

    class Resource(val mime: String, val bytes: ByteArray)

    private fun text(mime: String, body: String) = Resource(mime, body.toByteArray(Charsets.UTF_8))
    private fun js(body: String) = text("application/javascript", body)

    private val png by lazy {
        Resource(
            "image/png",
            Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==", Base64.DEFAULT),
        )
    }
    private val gif by lazy {
        Resource("image/gif", Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT))
    }
    private val mp3 by lazy {
        // A few silent MPEG-1 Layer III frames (128 kbps, 44.1 kHz, 417 bytes each).
        val frame = ByteArray(417)
        frame[0] = 0xFF.toByte(); frame[1] = 0xFB.toByte(); frame[2] = 0x90.toByte(); frame[3] = 0x00
        val out = ByteArray(417 * 5)
        for (i in 0 until 5) System.arraycopy(frame, 0, out, i * 417, 417)
        Resource("audio/mpeg", out)
    }

    private val noopJs by lazy { js("") }
    private val empty by lazy { text("text/plain", "") }

    private val analytics by lazy {
        js(
            "(function(){var n=function(){};" +
                "var t={get:function(){},set:n,send:n,require:n,provide:n};" +
                "function ga(){var a=arguments,l=a[a.length-1];" +
                "if(l&&typeof l.hitCallback==='function'){try{l.hitCallback()}catch(e){}}}" +
                "ga.create=function(){return t};ga.getByName=function(){return t};ga.getAll=function(){return[t]};" +
                "ga.remove=n;ga.loaded=true;" +
                "var name=window.GoogleAnalyticsObject||'ga';var q=window[name]&&window[name].q;" +
                "window[name]=ga;if(q){for(var i=0;i<q.length;i++){try{ga.apply(null,q[i])}catch(e){}}}" +
                "var gaq=window._gaq;window._gaq={push:function(a){if(typeof a==='function'){try{a()}catch(e){}}}," +
                "_getAsyncTracker:function(){return{}},_createAsyncTracker:function(){return{}}};" +
                "if(gaq&&gaq.length){for(var j=0;j<gaq.length;j++){window._gaq.push(gaq[j])}}})();"
        )
    }
    private val gtm by lazy {
        js(
            "(function(){window.dataLayer=window.dataLayer||[];" +
                "window.google_tag_manager=window.google_tag_manager||{};})();"
        )
    }
    private val adsByGoogle by lazy {
        js(
            "(function(){var a=window.adsbygoogle;var q=(a&&a.length)?a:[];" +
                "window.adsbygoogle={loaded:true,push:function(){},length:0};})();"
        )
    }
    private val gpt by lazy {
        js(
            "(function(){var P;var f=function(){return P};" +
                "P=new Proxy(function(){},{get:function(t,k){if(k==='then'||typeof k==='symbol')return undefined;return f}," +
                "apply:function(){return P}});" +
                "var base={apiReady:true,pubadsReady:true,cmd:{push:function(c){try{if(typeof c==='function')c()}catch(e){}return 1}}," +
                "getVersion:function(){return'0'},sizeMapping:function(){return P}};" +
                "var old=window.googletag&&window.googletag.cmd;" +
                "window.googletag=new Proxy(base,{get:function(t,k){if(k in t)return t[k];if(typeof k==='symbol')return undefined;return f}});" +
                "if(old&&old.length){for(var i=0;i<old.length;i++){try{old[i]()}catch(e){}}}})();"
        )
    }
    private val beacon by lazy {
        js("(function(){window.COMSCORE={beacon:function(){},purge:function(){}};window._comscore=window._comscore||[];})();")
    }
    private val apstag by lazy {
        js(
            "(function(){var n=function(){};window.apstag={init:n,fetchBids:function(c,cb){if(typeof cb==='function'){setTimeout(function(){cb([])},0)}}," +
                "setDisplayBids:n,targetingKeys:function(){return[]},renewBids:n,rb:n};})();"
        )
    }
    private val adStatus by lazy { js("(function(){window.google_ad_status=1;})();") }
    private val prebid by lazy { js("(function(){window.canRunAds=true;window.isAdsDisplayed=true;window.adblock=false;})();") }
    private val fab by lazy {
        js(
            "(function(){function F(){}" +
                "F.prototype.setOption=function(){return this};F.prototype.check=function(){return false};" +
                "F.prototype.on=function(d,cb){if(!d&&typeof cb==='function'){try{cb()}catch(e){}}return this};" +
                "F.prototype.onDetected=function(){return this};" +
                "F.prototype.onNotDetected=function(cb){if(typeof cb==='function'){try{cb()}catch(e){}}return this};" +
                "F.prototype.emitEvent=function(){return this};F.prototype.clearEvent=function(){return this};" +
                "var i=new F();window.FuckAdBlock=window.BlockAdBlock=F;window.fuckAdBlock=window.blockAdBlock=i;})();"
        )
    }

    /** The resource for a `$redirect=` [name], or null if we have no stand-in for it. */
    fun get(name: String): Resource? = when (name.lowercase().removeSuffix(":10")) {
        "noopjs", "noop.js" -> noopJs
        "noop.css", "noopcss" -> text("text/css", "")
        "noop.html", "noopframe" -> text("text/html", "<!DOCTYPE html><html><head></head><body></body></html>")
        "noop.txt", "nooptext" -> empty
        "noop.json", "noopjson" -> text("application/json", "{}")
        "1x1.gif", "1x1-transparent.gif" -> gif
        "2x2.png", "2x2-transparent.png", "3x2.png", "3x2-transparent.png", "32x32.png", "32x32-transparent.png" -> png
        "noop-0.1s.mp3", "noopmp3-0.1s" -> mp3
        "noop-1s.mp4", "noopmp4-1s" -> Resource("video/mp4", ByteArray(0))
        "empty" -> empty
        "google-analytics.com/analytics.js", "google-analytics_analytics.js", "google-analytics",
        "google-analytics.com/ga.js", "google-analytics_ga.js", "google-analytics.com/inpage_linkid.js",
        "google-analytics_inpage_linkid.js", "google-analytics.com/cx/api.js", "google-analytics_cx_api.js" -> analytics
        "googletagmanager.com/gtm.js", "googletagmanager_gtm.js" -> gtm
        "googlesyndication.com/adsbygoogle.js", "googlesyndication_adsbygoogle.js",
        "googlesyndication.com/pagead/show_ads.js" -> adsByGoogle
        "googletagservices.com/gpt.js", "googletagservices_gpt.js" -> gpt
        "scorecardresearch.com/beacon.js", "scorecardresearch_beacon.js" -> beacon
        "amazon-adsystem.com/aax2/apstag.js", "amazon_apstag.js" -> apstag
        "doubleclick.net/instream/ad_status.js", "doubleclick_instream_ad_status.js" -> adStatus
        "prebid-ads.js" -> prebid
        "fuckadblock.js-3.2.0", "nofab.js", "nobab.js", "blockadblock.js" -> fab
        else -> null
    }
}
