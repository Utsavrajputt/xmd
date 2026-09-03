package com.invictus.xmd.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import com.invictus.xmd.R
import com.invictus.xmd.core.Settings

/**
 * Stable identities for the app's selectable Kotlin-owned color schemes.
 * Palette values live only in ThemePalettes.kt.
 *
 * Stored in [com.invictus.xmd.core.Settings] by [storageKey], so renaming an
 * enum entry is safe but changing [storageKey] is not. Dark/light mode is
 * orthogonal, stored separately via `Settings.isDarkMode()` and toggled by
 * double-tapping the app header; see MainActivity.toggleDarkMode().
 */
enum class AppTheme(
    val storageKey: String,
    @StringRes val titleRes: Int,
) {
    SYSTEM(
        storageKey = "system",
        titleRes = R.string.theme_system,
    ),
    DEFAULT(
        storageKey = "default",
        titleRes = R.string.theme_default,
    ),
    AURORA(
        storageKey = "aurora",
        titleRes = R.string.theme_aurora,
    ),
    NORD(
        storageKey = "nord",
        titleRes = R.string.theme_nord,
    ),
    DRACULA(
        storageKey = "dracula",
        titleRes = R.string.theme_dracula,
    ),
    CATPPUCCIN(
        storageKey = "catppuccin",
        titleRes = R.string.theme_catppuccin,
    ),
    TOKYO_NIGHT(
        storageKey = "tokyo_night",
        titleRes = R.string.theme_tokyo_night,
    ),
    GRUVBOX(
        storageKey = "gruvbox",
        titleRes = R.string.theme_gruvbox,
    ),
    // Renamed from mpvrx's "Default" theme (a purple/plum palette) so it
    // doesn't collide with XMD's own DEFAULT entry above.
    AMETHYST(
        storageKey = "amethyst",
        titleRes = R.string.theme_amethyst,
    ),
    ;

    companion object {
        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.storageKey == key } ?: SYSTEM

        /**
         * Applies the minimal dark/light window bootstrap required before
         * Compose renders the Kotlin-owned palette. Must run before
         * `super.onCreate()`.
         */
        fun applyTo(activity: Activity) {
            val isDark = Settings.isDarkMode()
            activity.setTheme(if (isDark) R.style.Theme_Xmd else R.style.Theme_Xmd_Light)
            activity.window.setBackgroundDrawable(
                ColorDrawable(resolveCurrentXmdColorScheme(activity).background.toArgb())
            )
        }
    }
}
