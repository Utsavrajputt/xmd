package com.invictus.xmd.ui.history

import com.invictus.xmd.ui.browser.SavedPagesActivity
import com.invictus.xmd.ui.browser.SavedPagesDestination

/** Full-screen History (own window, opened from the browser menu). */
class HistoryActivity : SavedPagesActivity() {
    override val destination = SavedPagesDestination.History
}
