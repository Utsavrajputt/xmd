package com.invictus.xmd.ui.browser

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.ui.theme.AppTheme
import com.invictus.xmd.ui.theme.XmdTheme
import com.invictus.xmd.ui.theme.rememberThemeTransitionState

/**
 * Standalone full-screen host for History / Bookmarks. Being its own window
 * (instead of an overlay over the browser + bottom bar) means nothing from the
 * main screen can overlap or receive taps behind it. A tapped entry is
 * returned to MainActivity through [EXTRA_URL].
 */
abstract class SavedPagesActivity : ComponentActivity() {

    internal abstract val destination: SavedPagesDestination

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyTo(this)
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(Settings.isDarkMode())

        setContent {
            val themeTransitionState = rememberThemeTransitionState()
            XmdTheme(transitionState = themeTransitionState) {
                SavedPagesOverlay(
                    destination = destination,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onOpenUrl = { url ->
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_URL, url))
                        finish()
                    },
                )
            }
        }
    }

    private fun applyEdgeToEdge(isDarkMode: Boolean) {
        val barStyle = SystemBarStyle.auto(
            lightScrim = Color(0xFFF4F6F9).toArgb(),
            darkScrim = Color(0xFF0E1521).toArgb(),
        ) { isDarkMode }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    companion object {
        const val EXTRA_URL = "saved_pages_url"
    }
}
