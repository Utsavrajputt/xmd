package com.invictus.xmd.ui.browser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.invictus.xmd.R

/**
 * Registered as the callback IntentSender on
 * [androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut] (see
 * BrowserFragment.addCurrentPageAsApp()). The system only invokes this --
 * broadcasting [ACTION_SHORTCUT_PINNED] -- once the shortcut has actually
 * been placed by the launcher, i.e. strictly after the user has confirmed
 * the "Add to Home screen" system dialog. requestPinShortcut()'s own
 * return value only means "the request reached the launcher", not that the
 * user accepted it or that placement finished, so that's not a safe place
 * to tell the user it's done -- this receiver is.
 */
class PinnedShortcutReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SHORTCUT_PINNED = "com.invictus.xmd.action.SHORTCUT_PINNED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, R.string.add_to_home_screen_added, Toast.LENGTH_SHORT).show()
    }
}
