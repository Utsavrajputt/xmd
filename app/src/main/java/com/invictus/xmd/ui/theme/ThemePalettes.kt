package com.invictus.xmd.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import com.invictus.xmd.preferences.Settings


/**
 * Resolves the active [ColorScheme] for the specified [theme], dark mode, and AMOLED flags.
 * Uses mpvRx dynamic schemes and generated palettes.
 */
fun resolveXmdColorScheme(
    context: Context,
    theme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
): ColorScheme = when {
    theme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        when {
            isDark && isAmoled -> dynamicDarkColorScheme(context).toAmoled()
            isDark -> dynamicDarkColorScheme(context)
            else -> dynamicLightColorScheme(context).withComfortableLightSurfaces()
        }
    }
    isDark && isAmoled -> theme.getAmoledColorScheme()
    isDark -> theme.getDarkColorScheme()
    else -> theme.getLightColorScheme()
}

/** Keeps wallpaper-derived accents while replacing near-white full-screen surfaces. */
private fun ColorScheme.withComfortableLightSurfaces(): ColorScheme {
    val base = Color(0xFFF7F5F8)

    fun tint(amount: Float) =
        androidx.compose.ui.graphics.lerp(base, primary, amount)

    return copy(
        background = tint(0.018f),
        surface = tint(0.018f),
        surfaceDim = tint(0.085f),
        surfaceBright = Color(0xFFFBF9FC),
        surfaceContainerLowest = Color(0xFFFBF9FC),
        surfaceContainerLow = tint(0.030f),
        surfaceContainer = tint(0.050f),
        surfaceContainerHigh = tint(0.072f),
        surfaceContainerHighest = tint(0.098f),
    )
}

internal fun resolveCurrentXmdColorScheme(context: Context): ColorScheme =
    resolveXmdColorScheme(
        context = context,
        theme = Settings.appTheme(),
        isDark = Settings.isDarkMode(),
        isAmoled = Settings.isAmoledMode(),
    )