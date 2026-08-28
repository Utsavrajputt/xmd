package com.invictus.xmd.core

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A real bookmark: a page the user saved via the star button in the
 * Browser toolbar, most-recent first, viewed in its own Bookmarks screen
 * (Browser overflow menu -> Bookmarks). Distinct from [Shortcut], which is
 * the speed-dial tile shown on the new-tab page -- saving a bookmark can
 * optionally also create a matching Shortcut, but the two lists are
 * independent from then on.
 */
@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey
    val id: String,
    val title: String,
    val url: String,
    val faviconUrl: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)
