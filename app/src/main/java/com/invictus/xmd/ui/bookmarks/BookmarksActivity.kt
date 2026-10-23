package com.invictus.xmd.ui.bookmarks

import com.invictus.xmd.ui.browser.SavedPagesActivity
import com.invictus.xmd.ui.browser.SavedPagesDestination

/** Full-screen Bookmarks (own window, opened from the browser menu). */
class BookmarksActivity : SavedPagesActivity() {
    override val destination = SavedPagesDestination.Bookmarks
}
