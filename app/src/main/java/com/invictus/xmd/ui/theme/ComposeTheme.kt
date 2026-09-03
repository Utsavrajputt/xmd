package com.invictus.xmd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.core.Settings

private val HeadingFont = FontFamily(
    Font(R.font.space_grotesk_semibold, weight = FontWeight.SemiBold),
)

private val ExpressiveShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)

private val DefaultTypography = Typography()

private fun TextStyle.xmd(fontFamily: FontFamily = this.fontFamily ?: FontFamily.Default): TextStyle =
    copy(fontFamily = fontFamily, letterSpacing = 0.sp)

private val ExpressiveTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.xmd(HeadingFont),
    displayMedium = DefaultTypography.displayMedium.xmd(HeadingFont),
    displaySmall = DefaultTypography.displaySmall.xmd(HeadingFont),
    headlineLarge = DefaultTypography.headlineLarge.xmd(HeadingFont),
    headlineMedium = DefaultTypography.headlineMedium.xmd(HeadingFont),
    headlineSmall = DefaultTypography.headlineSmall.xmd(HeadingFont),
    titleLarge = DefaultTypography.titleLarge.xmd(HeadingFont),
    titleMedium = DefaultTypography.titleMedium.xmd(HeadingFont),
    titleSmall = DefaultTypography.titleSmall.xmd(HeadingFont),
    bodyLarge = DefaultTypography.bodyLarge.xmd(),
    bodyMedium = DefaultTypography.bodyMedium.xmd(),
    bodySmall = DefaultTypography.bodySmall.xmd(),
    labelLarge = DefaultTypography.labelLarge.xmd(),
    labelMedium = DefaultTypography.labelMedium.xmd(),
    labelSmall = DefaultTypography.labelSmall.xmd(),
)

/**
 * Applies the active Kotlin-owned color palette, typography, and shapes.
 * XML retains only the minimal activity-window bootstrap required before
 * Compose creates the first frame.
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
    val theme = Settings.appTheme()
    val isDark = Settings.isDarkMode()
    val isAmoled = Settings.isAmoledMode()
    val colorScheme = remember(context, theme, isDark, isAmoled) {
        resolveXmdColorScheme(context, theme, isDark, isAmoled)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        content = content,
    )
}
