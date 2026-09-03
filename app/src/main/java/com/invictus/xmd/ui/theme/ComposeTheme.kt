package com.invictus.xmd.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.MaterialColors

/**
 * Bridges the app's existing XML theme system (see [AppTheme], applied via
 * `AppTheme.applyTo(activity)` before `setContentView`/`setContent`) into a
 * Compose [ColorScheme], instead of hand-porting all 9 themes x dark/light
 * palettes into duplicate Kotlin color tables.
 *
 * Because every `Theme.Xmd.*` style in themes.xml already defines the full
 * M3 attr set (colorPrimary, colorSurfaceContainer, etc.), reading those
 * resolved attrs off the [Context]'s already-applied theme means a new XML
 * theme (or an edit to an existing one) is picked up by Compose screens
 * automatically -- no second place to update.
 *
 * Wrap Compose screen content in [XmdTheme] instead of the raw
 * `MaterialTheme { ... }` so it inherits the correct palette:
 * ```
 * setContent {
 *     XmdTheme {
 *         SettingsScreen(...)
 *     }
 * }
 * ```
 */
@Composable
fun XmdTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember(context.theme) { context.toComposeColorScheme() }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}

private fun Context.toComposeColorScheme(): ColorScheme {
    fun attr(attrResId: Int): Color = Color(MaterialColors.getColor(this, attrResId, Color.Magenta.toArgb()))

    return ColorScheme(
        primary = attr(com.google.android.material.R.attr.colorPrimary),
        onPrimary = attr(com.google.android.material.R.attr.colorOnPrimary),
        primaryContainer = attr(com.google.android.material.R.attr.colorPrimaryContainer),
        onPrimaryContainer = attr(com.google.android.material.R.attr.colorOnPrimaryContainer),
        inversePrimary = attr(com.google.android.material.R.attr.colorPrimaryInverse),
        secondary = attr(com.google.android.material.R.attr.colorSecondary),
        onSecondary = attr(com.google.android.material.R.attr.colorOnSecondary),
        secondaryContainer = attr(com.google.android.material.R.attr.colorSecondaryContainer),
        onSecondaryContainer = attr(com.google.android.material.R.attr.colorOnSecondaryContainer),
        tertiary = attr(com.google.android.material.R.attr.colorTertiary),
        onTertiary = attr(com.google.android.material.R.attr.colorOnTertiary),
        tertiaryContainer = attr(com.google.android.material.R.attr.colorTertiaryContainer),
        onTertiaryContainer = attr(com.google.android.material.R.attr.colorOnTertiaryContainer),
        background = attr(android.R.attr.colorBackground),
        onBackground = attr(com.google.android.material.R.attr.colorOnBackground),
        surface = attr(com.google.android.material.R.attr.colorSurface),
        onSurface = attr(com.google.android.material.R.attr.colorOnSurface),
        surfaceVariant = attr(com.google.android.material.R.attr.colorSurfaceVariant),
        onSurfaceVariant = attr(com.google.android.material.R.attr.colorOnSurfaceVariant),
        surfaceTint = attr(com.google.android.material.R.attr.colorPrimary),
        inverseSurface = attr(com.google.android.material.R.attr.colorSurfaceInverse),
        inverseOnSurface = attr(com.google.android.material.R.attr.colorOnSurfaceInverse),
        error = attr(com.google.android.material.R.attr.colorError),
        onError = attr(com.google.android.material.R.attr.colorOnError),
        errorContainer = attr(com.google.android.material.R.attr.colorErrorContainer),
        onErrorContainer = attr(com.google.android.material.R.attr.colorOnErrorContainer),
        outline = attr(com.google.android.material.R.attr.colorOutline),
        outlineVariant = attr(com.google.android.material.R.attr.colorOutlineVariant),
        // No stable public "colorScrim" attr to read across AppCompat/M3
        // parent themes reliably; every Theme.Xmd.* variant uses the same
        // m3_scrim (#000000) anyway, so this is hardcoded rather than
        // looked up.
        scrim = Color.Black,
        surfaceBright = attr(com.google.android.material.R.attr.colorSurfaceBright),
        surfaceDim = attr(com.google.android.material.R.attr.colorSurfaceDim),
        surfaceContainer = attr(com.google.android.material.R.attr.colorSurfaceContainer),
        surfaceContainerHigh = attr(com.google.android.material.R.attr.colorSurfaceContainerHigh),
        surfaceContainerHighest = attr(com.google.android.material.R.attr.colorSurfaceContainerHighest),
        surfaceContainerLow = attr(com.google.android.material.R.attr.colorSurfaceContainerLow),
        surfaceContainerLowest = attr(com.google.android.material.R.attr.colorSurfaceContainerLowest),
    )
}
