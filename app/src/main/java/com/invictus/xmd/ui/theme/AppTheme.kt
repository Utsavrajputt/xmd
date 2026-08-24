package com.invictus.xmd.ui.theme

import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.invictus.xmd.R

/**
 * The app's selectable color themes. Each one maps to a `Theme.Xmd.*` style
 * in themes.xml (applied at runtime via `Activity.setTheme()` before
 * `super.onCreate()`) plus a handful of swatch colors used to draw the
 * little preview card in the theme picker -- no need to inflate the real
 * style just to show what it looks like.
 *
 * Stored in [com.invictus.xmd.core.Settings] by [storageKey], so renaming an
 * enum entry is safe but changing [storageKey] is not.
 */
enum class AppTheme(
    val storageKey: String,
    @StringRes val titleRes: Int,
    @StyleRes val styleRes: Int,
    val swatchBackground: String,
    val swatchPrimary: String,
    val swatchSecondary: String,
    val swatchTertiary: String,
) {
    DEFAULT(
        storageKey = "default",
        titleRes = R.string.theme_default,
        styleRes = R.style.Theme_Xmd,
        swatchBackground = "#0E1521",
        swatchPrimary = "#7CD4FF",
        swatchSecondary = "#B7CAD6",
        swatchTertiary = "#FFB4A0",
    ),
    AURORA(
        storageKey = "aurora",
        titleRes = R.string.theme_aurora,
        styleRes = R.style.Theme_Xmd_Aurora,
        swatchBackground = "#04070F",
        swatchPrimary = "#5B93FF",
        swatchSecondary = "#9FAEC9",
        swatchTertiary = "#97A8FF",
    ),
    NORD(
        storageKey = "nord",
        titleRes = R.string.theme_nord,
        styleRes = R.style.Theme_Xmd_Nord,
        swatchBackground = "#2E3440",
        swatchPrimary = "#88C0D0",
        swatchSecondary = "#D8DEE9",
        swatchTertiary = "#D8A9C4",
    ),
    DRACULA(
        storageKey = "dracula",
        titleRes = R.string.theme_dracula,
        styleRes = R.style.Theme_Xmd_Dracula,
        swatchBackground = "#282A36",
        swatchPrimary = "#BD93F9",
        swatchSecondary = "#FF79C6",
        swatchTertiary = "#8BE9FD",
    ),
    CATPPUCCIN(
        storageKey = "catppuccin",
        titleRes = R.string.theme_catppuccin,
        styleRes = R.style.Theme_Xmd_Catppuccin,
        swatchBackground = "#1E1E2E",
        swatchPrimary = "#9BA8CF",
        swatchSecondary = "#D4A5B8",
        swatchTertiary = "#8AB8A8",
    ),
    ;

    companion object {
        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
