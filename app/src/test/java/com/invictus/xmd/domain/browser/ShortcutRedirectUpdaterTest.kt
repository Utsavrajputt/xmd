package com.invictus.xmd.domain.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutRedirectUpdaterTest {

    @Test
    fun movedDomainSameBrandIsUpdated() {
        assertEquals(
            "https://hdhub4u.fo/",
            ShortcutRedirectUpdater.newUrlFor("https://hdhub4u.tv/", "HDHub4u", "https://hdhub4u.fo/")
        )
    }

    @Test
    fun wwwAndPathOnNewHostAreNormalisedForRootTiles() {
        assertEquals(
            "https://hdhub4u.fo/",
            ShortcutRedirectUpdater.newUrlFor("https://www.hdhub4u.tv", "x", "https://hdhub4u.fo/home?x=1")
        )
    }

    @Test
    fun sameHostIsIgnored() {
        assertNull(ShortcutRedirectUpdater.newUrlFor("https://a.com/", "a", "https://www.a.com/x"))
    }

    @Test
    fun unrelatedHostIsIgnored() {
        assertNull(
            ShortcutRedirectUpdater.newUrlFor("https://hdhub4u.tv/", "HDHub4u", "https://accounts.google.com/login")
        )
    }

    @Test
    fun titleCanMatchNewHost() {
        assertEquals(
            "https://moviesmod.zip/",
            ShortcutRedirectUpdater.newUrlFor("https://old-site.xyz/", "MoviesMod", "https://moviesmod.zip/")
        )
    }
}
