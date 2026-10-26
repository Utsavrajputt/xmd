@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.preferences.Settings

private val HeadingFont = FontFamily(
    Font(R.font.space_grotesk_semibold, weight = FontWeight.SemiBold),
)

/** Single source of truth for corner radii; also feeds [MaterialTheme.shapes]. */
object XmdShapes {
    val Xs = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    val S = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val M = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    val L = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    val Xl = androidx.compose.foundation.shape.RoundedCornerShape(36.dp)
}

private val ExpressiveShapes = Shapes(
    extraSmall = XmdShapes.Xs,
    small = XmdShapes.S,
    medium = XmdShapes.M,
    large = XmdShapes.L,
    extraLarge = XmdShapes.Xl,
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
 * Applies the active Kotlin-owned color palette, typography, shapes, and transition overlay.
 */
@Composable
fun XmdTheme(
    transitionState: ThemeTransitionState = rememberThemeTransitionState(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val theme by Settings.themeFlow.collectAsState()
    val isDark by Settings.darkModeFlow.collectAsState()
    val isAmoled by Settings.amoledModeFlow.collectAsState()

    val colorScheme = remember(context, theme, isDark, isAmoled) {
        resolveXmdColorScheme(context, theme, isDark, isAmoled)
    }

    CompositionLocalProvider(
        LocalThemeTransitionState provides transitionState,
    ) {
        ThemeTransitionOverlay(state = transitionState) {
            MaterialExpressiveTheme(
                colorScheme = colorScheme,
                motionScheme = MotionScheme.expressive(),
                typography = ExpressiveTypography,
                shapes = ExpressiveShapes,
                content = content,
            )
        }
    }
}
