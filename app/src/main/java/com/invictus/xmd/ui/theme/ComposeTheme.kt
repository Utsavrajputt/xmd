package com.invictus.xmd.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.invictus.xmd.R
import com.invictus.xmd.core.Settings
import kotlinx.coroutines.flow.first

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

    val view = LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.LaunchedEffect(isDark, isAmoled, colorScheme) {
            if (transitionState.isAnimating) {
                androidx.compose.runtime.snapshotFlow {
                    transitionState.animationProgress.value to transitionState.isAnimating
                }.first { (progress, isAnimating) ->
                    !isAnimating || progress >= 0.55f
                }
            }
            val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
            window.statusBarColor = colorScheme.surfaceContainerLow.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalThemeTransitionState provides transitionState,
    ) {
        ThemeTransitionOverlay(state = transitionState) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = ExpressiveTypography,
                shapes = ExpressiveShapes,
                content = content,
            )
        }
    }
}
