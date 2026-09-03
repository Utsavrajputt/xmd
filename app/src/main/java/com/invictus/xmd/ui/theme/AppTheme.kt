package com.invictus.xmd.ui.theme

import android.app.Activity
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.google.android.material.color.DynamicColors
import com.invictus.xmd.R
import com.invictus.xmd.core.Settings

/**
 * The app's selectable color themes. Each one maps to a dark `Theme.Xmd.*`
 * and a light `Theme.Xmd.*.Light` style in themes.xml (applied at runtime
 * via `Activity.setTheme()` before `super.onCreate()`, resolved through
 * [resolvedStyleRes] against the separately-stored dark/light mode flag)
 * plus a handful of swatch colors used to draw the little preview card in
 * the theme picker -- no need to inflate the real style just to show what
 * it looks like.
 *
 * Stored in [com.invictus.xmd.core.Settings] by [storageKey], so renaming an
 * enum entry is safe but changing [storageKey] is not. Dark/light mode is
 * orthogonal, stored separately via `Settings.isDarkMode()` and toggled by
 * double-tapping the app header; see MainActivity.toggleDarkMode().
 */
enum class AppTheme(
    val storageKey: String,
    @StringRes val titleRes: Int,
    @StyleRes val styleResDark: Int,
    @StyleRes val styleResLight: Int,
    val swatchBackground: String,
    val swatchPrimary: String,
    val swatchSecondary: String,
    val swatchTertiary: String,
) {
    SYSTEM(
        storageKey = "system",
        titleRes = R.string.theme_system,
        styleResDark = R.style.Theme_Xmd,
        styleResLight = R.style.Theme_Xmd_Light,
        swatchBackground = "#0E1521",
        swatchPrimary = "#7CD4FF",
        swatchSecondary = "#B7CAD6",
        swatchTertiary = "#FFB4A0",
    ),
    DEFAULT(
        storageKey = "default",
        titleRes = R.string.theme_default,
        styleResDark = R.style.Theme_Xmd,
        styleResLight = R.style.Theme_Xmd_Light,
        swatchBackground = "#0E1521",
        swatchPrimary = "#7CD4FF",
        swatchSecondary = "#B7CAD6",
        swatchTertiary = "#FFB4A0",
    ),
    AURORA(
        storageKey = "aurora",
        titleRes = R.string.theme_aurora,
        styleResDark = R.style.Theme_Xmd_Aurora,
        styleResLight = R.style.Theme_Xmd_Aurora_Light,
        swatchBackground = "#04070F",
        swatchPrimary = "#5B93FF",
        swatchSecondary = "#9FAEC9",
        swatchTertiary = "#97A8FF",
    ),
    NORD(
        storageKey = "nord",
        titleRes = R.string.theme_nord,
        styleResDark = R.style.Theme_Xmd_Nord,
        styleResLight = R.style.Theme_Xmd_Nord_Light,
        swatchBackground = "#2E3440",
        swatchPrimary = "#88C0D0",
        swatchSecondary = "#D8DEE9",
        swatchTertiary = "#D8A9C4",
    ),
    DRACULA(
        storageKey = "dracula",
        titleRes = R.string.theme_dracula,
        styleResDark = R.style.Theme_Xmd_Dracula,
        styleResLight = R.style.Theme_Xmd_Dracula_Light,
        swatchBackground = "#282A36",
        swatchPrimary = "#BD93F9",
        swatchSecondary = "#FF79C6",
        swatchTertiary = "#8BE9FD",
    ),
    CATPPUCCIN(
        storageKey = "catppuccin",
        titleRes = R.string.theme_catppuccin,
        styleResDark = R.style.Theme_Xmd_Catppuccin,
        styleResLight = R.style.Theme_Xmd_Catppuccin_Light,
        swatchBackground = "#1E1E2E",
        swatchPrimary = "#9BA8CF",
        swatchSecondary = "#D4A5B8",
        swatchTertiary = "#8AB8A8",
    ),
    TOKYO_NIGHT(
        storageKey = "tokyo_night",
        titleRes = R.string.theme_tokyo_night,
        styleResDark = R.style.Theme_Xmd_TokyoNight,
        styleResLight = R.style.Theme_Xmd_TokyoNight_Light,
        swatchBackground = "#1A1B26",
        swatchPrimary = "#7AA2F7",
        swatchSecondary = "#BB9AF7",
        swatchTertiary = "#9ECE6A",
    ),
    GRUVBOX(
        storageKey = "gruvbox",
        titleRes = R.string.theme_gruvbox,
        styleResDark = R.style.Theme_Xmd_Gruvbox,
        styleResLight = R.style.Theme_Xmd_Gruvbox_Light,
        swatchBackground = "#282828",
        swatchPrimary = "#FE8019",
        swatchSecondary = "#FABD2F",
        swatchTertiary = "#8EC07C",
    ),
    // Renamed from mpvrx's "Default" theme (a purple/plum palette) so it
    // doesn't collide with XMD's own DEFAULT entry above.
    AMETHYST(
        storageKey = "amethyst",
        titleRes = R.string.theme_amethyst,
        styleResDark = R.style.Theme_Xmd_Amethyst,
        styleResLight = R.style.Theme_Xmd_Amethyst_Light,
        swatchBackground = "#161217",
        swatchPrimary = "#E8B5EF",
        swatchSecondary = "#D6C0D6",
        swatchTertiary = "#F5B7B0",
    ),
    ;

    /** Resolves this color theme against the current dark/light mode. */
    @StyleRes
    fun resolvedStyleRes(isDark: Boolean): Int = if (isDark) styleResDark else styleResLight

    companion object {
        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.storageKey == key } ?: SYSTEM

        /**
         * Applies the active [AppTheme], dynamic Monet colors (if SYSTEM theme),
         * and AMOLED pure-black overlay (if enabled in dark mode) to the given [Activity].
         * Must be called in `Activity.onCreate()` before `setContentView()`.
         */
        fun applyTo(activity: Activity) {
            val theme = Settings.appTheme()
            val isDark = Settings.isDarkMode()
            val isAmoled = Settings.isAmoledMode()

            activity.setTheme(theme.resolvedStyleRes(isDark))

            if (theme == SYSTEM) {
                DynamicColors.applyToActivityIfAvailable(activity)
            }

            if (isDark && isAmoled) {
                activity.theme.applyStyle(R.style.ThemeOverlay_Xmd_Amoled, true)
            }
        }
    }
}
