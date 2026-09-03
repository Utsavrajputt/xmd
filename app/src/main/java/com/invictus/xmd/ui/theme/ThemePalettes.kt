package com.invictus.xmd.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import com.invictus.xmd.core.Settings

private data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
)

private fun family(color: Long, onColor: Long, container: Long, onContainer: Long) =
    ColorFamily(Color(color), Color(onColor), Color(container), Color(onContainer))

private data class SurfaceRoles(
    val background: Color,
    val onBackground: Color,
    val dim: Color,
    val bright: Color,
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
    val onVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
)

private fun surfaces(
    background: Long,
    onBackground: Long,
    dim: Long,
    bright: Long,
    lowest: Long,
    low: Long,
    container: Long,
    high: Long,
    highest: Long,
    onVariant: Long,
    outline: Long,
    outlineVariant: Long,
) = SurfaceRoles(
    Color(background),
    Color(onBackground),
    Color(dim),
    Color(bright),
    Color(lowest),
    Color(low),
    Color(container),
    Color(high),
    Color(highest),
    Color(onVariant),
    Color(outline),
    Color(outlineVariant),
)

private data class InverseRoles(
    val surface: Color,
    val onSurface: Color,
    val primary: Color,
)

private fun inverse(surface: Long, onSurface: Long, primary: Long) =
    InverseRoles(Color(surface), Color(onSurface), Color(primary))

private fun fixedColorScheme(
    isDark: Boolean,
    primary: ColorFamily,
    secondary: ColorFamily,
    tertiary: ColorFamily,
    surfaces: SurfaceRoles,
    inverse: InverseRoles,
): ColorScheme {
    val error = if (isDark) {
        family(0xFFFFB4AB, 0xFF690005, 0xFF93000A, 0xFFFFDAD6)
    } else {
        family(0xFFBA1A1A, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF410002)
    }

    return ColorScheme(
        primary = primary.color,
        onPrimary = primary.onColor,
        primaryContainer = primary.container,
        onPrimaryContainer = primary.onContainer,
        inversePrimary = inverse.primary,
        secondary = secondary.color,
        onSecondary = secondary.onColor,
        secondaryContainer = secondary.container,
        onSecondaryContainer = secondary.onContainer,
        tertiary = tertiary.color,
        onTertiary = tertiary.onColor,
        tertiaryContainer = tertiary.container,
        onTertiaryContainer = tertiary.onContainer,
        background = surfaces.background,
        onBackground = surfaces.onBackground,
        surface = surfaces.background,
        onSurface = surfaces.onBackground,
        surfaceVariant = surfaces.high,
        onSurfaceVariant = surfaces.onVariant,
        surfaceTint = primary.color,
        inverseSurface = inverse.surface,
        inverseOnSurface = inverse.onSurface,
        error = error.color,
        onError = error.onColor,
        errorContainer = error.container,
        onErrorContainer = error.onContainer,
        outline = surfaces.outline,
        outlineVariant = surfaces.outlineVariant,
        scrim = Color.Black,
        surfaceBright = surfaces.bright,
        surfaceDim = surfaces.dim,
        surfaceContainer = surfaces.container,
        surfaceContainerHigh = surfaces.high,
        surfaceContainerHighest = surfaces.highest,
        surfaceContainerLow = surfaces.low,
        surfaceContainerLowest = surfaces.lowest,
    )
}

private val DefaultDark = fixedColorScheme(
    isDark = true,
    primary = family(0xFF7CD4FF, 0xFF00344A, 0xFF00496A, 0xFFC7E7FF),
    secondary = family(0xFFB7CAD6, 0xFF22323C, 0xFF3A4953, 0xFFD3E5F2),
    tertiary = family(0xFFFFB4A0, 0xFF5C1900, 0xFF7D2C0F, 0xFFFFDBCF),
    surfaces = surfaces(
        0xFF0E1521, 0xFFE1E2E8, 0xFF0E1521, 0xFF343B47,
        0xFF090E17, 0xFF161D2A, 0xFF1A2230, 0xFF25303F, 0xFF303B4A,
        0xFFC1C8D6, 0xFF8B93A3, 0xFF414A5A,
    ),
    inverse = inverse(0xFFE1E2E8, 0xFF1A2230, 0xFF00658F),
)

private val DefaultLight = fixedColorScheme(
    isDark = false,
    primary = family(0xFF00658F, 0xFFFFFFFF, 0xFFC7E7FF, 0xFF001E2C),
    secondary = family(0xFF4B6373, 0xFFFFFFFF, 0xFFD3E5F2, 0xFF0A1F27),
    tertiary = family(0xFF984836, 0xFFFFFFFF, 0xFFFFDBCF, 0xFF3A0900),
    surfaces = surfaces(
        0xFFFAFAFA, 0xFF1A1C1E, 0xFFDAD9DE, 0xFFFAFAFA,
        0xFFFFFFFF, 0xFFF3F3F6, 0xFFEDEDF0, 0xFFE7E7EB, 0xFFE1E2E5,
        0xFF43474E, 0xFF73777F, 0xFFC3C7CE,
    ),
    inverse = inverse(0xFF2F3033, 0xFFF1F0F4, 0xFF7CD4FF),
)

private val AuroraDark = fixedColorScheme(
    isDark = true,
    primary = family(0xFF5B93FF, 0xFF051C48, 0xFF093688, 0xFF7CA9FF),
    secondary = family(0xFF9FAEC9, 0xFF29303F, 0xFF4E5B77, 0xFFB2BED4),
    tertiary = family(0xFF97A8FF, 0xFF18204B, 0xFF2E3D8D, 0xFFACB9FF),
    surfaces = surfaces(
        0xFF04070F, 0xFFE1E2E8, 0xFF04070E, 0xFF3B4356,
        0xFF03060D, 0xFF070C17, 0xFF090F1D, 0xFF0D1527, 0xFF101B31,
        0xFFC1C8D6, 0xFF949FB4, 0xFF46597B,
    ),
    inverse = inverse(0xFFE1E2E8, 0xFF1A2230, 0xFF0A3C98),
)

private val AuroraLight = fixedColorScheme(
    isDark = false,
    primary = family(0xFF2E5FD9, 0xFFFFFFFF, 0xFFDCE3FF, 0xFF001551),
    secondary = family(0xFF56607A, 0xFFFFFFFF, 0xFFDAE2F9, 0xFF131C2E),
    tertiary = family(0xFF4A57C7, 0xFFFFFFFF, 0xFFE0E0FF, 0xFF0B144C),
    surfaces = surfaces(
        0xFFFAFAFF, 0xFF1A1B21, 0xFFDADAE1, 0xFFFAFAFF,
        0xFFFFFFFF, 0xFFF4F3FA, 0xFFEEEDF5, 0xFFE8E7F0, 0xFFE2E2EA,
        0xFF45464F, 0xFF767680, 0xFFC6C5D0,
    ),
    inverse = inverse(0xFF2F3036, 0xFFF1F0F8, 0xFFB4C4FF),
)

private val NordDark = fixedColorScheme(
    isDark = true,
    primary = family(0xFF88C0D0, 0xFF2A3A4D, 0xFF506E92, 0xFFA0CDD9),
    secondary = family(0xFFD8DEE9, 0xFF222730, 0xFF41495A, 0xFFE0E5ED),
    tertiary = family(0xFFD8A9C4, 0xFF51404E, 0xFF997993, 0xFFE0BAD0),
    surfaces = surfaces(
        0xFF2E3440, 0xFFE1E2E8, 0xFF2C313D, 0xFF5E6973,
        0xFF282D37, 0xFF313945, 0xFF333C49, 0xFF37424E, 0xFF3B4854,
        0xFFC1C8D6, 0xFFAEB5C2, 0xFF4F6272,
    ),
    inverse = inverse(0xFFE1E2E8, 0xFF1A2230, 0xFF597BA3),
)

private val NordLight = fixedColorScheme(
    isDark = false,
    primary = family(0xFF5E81AC, 0xFFFFFFFF, 0xFFD8E5F0, 0xFF12232F),
    secondary = family(0xFF4C566A, 0xFFFFFFFF, 0xFFDDE3EC, 0xFF171C24),
    tertiary = family(0xFFB48EAD, 0xFFFFFFFF, 0xFFF0E1EC, 0xFF3B1F35),
    surfaces = surfaces(
        0xFFECEFF4, 0xFF2E3440, 0xFFD8DEE9, 0xFFECEFF4,
        0xFFFFFFFF, 0xFFE5E9F0, 0xFFE0E5EC, 0xFFDBE1E9, 0xFFD6DCE5,
        0xFF434C5E, 0xFF6B7789, 0xFFC4CCD6,
    ),
    inverse = inverse(0xFF2E3440, 0xFFECEFF4, 0xFF88C0D0),
)

private val DraculaDark = fixedColorScheme(
    isDark = true,
    primary = family(0xFFBD93F9, 0xFF2C334A, 0xFF53618B, 0xFFCAA9FA),
    secondary = family(0xFFFF79C6, 0xFF1F2028, 0xFF3A3C4C, 0xFFFF94D1),
    tertiary = family(0xFF8BE9FD, 0xFF247037, 0xFF44D469, 0xFFA2EDFD),
    surfaces = surfaces(
        0xFF282A36, 0xFFE1E2E8, 0xFF262833, 0xFF605C71,
        0xFF22242E, 0xFF2D2E3D, 0xFF313042, 0xFF37344A, 0xFF3D3951,
        0xFFC1C8D6, 0xFFBF87B3, 0xFF5A597A,
    ),
    inverse = inverse(0xFFE1E2E8, 0xFF1A2230, 0xFF5D6C9C),
)

private val DraculaLight = fixedColorScheme(
    isDark = false,
    primary = family(0xFF644AC9, 0xFFFFFFFF, 0xFFE7DEFF, 0xFF1F0060),
    secondary = family(0xFFA3144D, 0xFFFFFFFF, 0xFFFFD9E4, 0xFF3C0018),
    tertiary = family(0xFF036A96, 0xFFFFFFFF, 0xFFC2E8FF, 0xFF001E2C),
    surfaces = surfaces(
        0xFFF8F8F2, 0xFF1F1F1F, 0xFFDEDED8, 0xFFF8F8F2,
        0xFFFFFFFF, 0xFFF1F1EC, 0xFFEBEBE5, 0xFFE5E5DF, 0xFFDFDFD9,
        0xFF454540, 0xFF78786F, 0xFFC9C9BE,
    ),
    inverse = inverse(0xFF1F1F1F, 0xFFF8F8F2, 0xFFBD93F9),
)

private val CatppuccinDark = fixedColorScheme(
    isDark = true,
    primary = family(0xFF9BA8CF, 0xFF223045, 0xFF415B83, 0xFFAFB9D9),
    secondary = family(0xFFD4A5B8, 0xFF523040, 0xFF9C5B7A, 0xFFDDB7C6),
    tertiary = family(0xFF8AB8A8, 0xFF53351C, 0xFF9C6435, 0xFFA1C6B9),
    surfaces = surfaces(
        0xFF1E1E2E, 0xFFE1E2E8, 0xFF1C1C2C, 0xFF555666,
        0xFF1A1A28, 0xFF222334, 0xFF252638, 0xFF2A2C3E, 0xFF303145,
        0xFFC1C8D6, 0xFFAC9BAC, 0xFF535D71,
    ),
    inverse = inverse(0xFFE1E2E8, 0xFF1A2230, 0xFF486692),
)

private val CatppuccinLight = fixedColorScheme(
    isDark = false,
    primary = family(0xFF8839EF, 0xFFFFFFFF, 0xFFECDCFF, 0xFF280059),
    secondary = family(0xFFEA76CB, 0xFFFFFFFF, 0xFFFFD9F0, 0xFF3D0030),
    tertiary = family(0xFF179299, 0xFFFFFFFF, 0xFFB0F0EC, 0xFF00201F),
    surfaces = surfaces(
        0xFFEFF1F5, 0xFF4C4F69, 0xFFDCDFE4, 0xFFEFF1F5,
        0xFFFFFFFF, 0xFFE9EBF0, 0xFFE3E5EB, 0xFFDDE0E6, 0xFFD7DAE1,
        0xFF5C5F77, 0xFF8C8FA1, 0xFFCCCFDA,
    ),
    inverse = inverse(0xFF4C4F69, 0xFFEFF1F5, 0xFF9BA8CF),
)

private val TokyoNightDark = fixedColorScheme(
    isDark = true,
    primary = family(0xFF7AA2F7, 0xFF051F57, 0xFF093490, 0xFF92B4FB),
    secondary = family(0xFFBB9AF7, 0xFF230755, 0xFF3A0B8E, 0xFFB894FA),
    tertiary = family(0xFF9ECE6A, 0xFF2F4517, 0xFF4E7326, 0xFFC8E6A8),
    surfaces = surfaces(
        0xFF1A1B26, 0xFFE1E2E8, 0xFF1A1B26, 0xFF48536A,
        0xFF0E182D, 0xFF131F37, 0xFF15223D, 0xFF1B2946, 0xFF1F2F51,
        0xFFC1C8D6, 0xFF939DB4, 0xFF47577B,
    ),
    inverse = inverse(0xFFE1E2E8, 0xFF1A2230, 0xFF093490),
)

private val TokyoNightLight = fixedColorScheme(
    isDark = false,
    primary = family(0xFF34548A, 0xFFFFFFFF, 0xFFE0E8F5, 0xFF12233F),
    secondary = family(0xFF5A4A78, 0xFFFFFFFF, 0xFFE9E5F0, 0xFF251B37),
    tertiary = family(0xFF485E30, 0xFFFFFFFF, 0xFFEBF2E3, 0xFF2A3A18),
    surfaces = surfaces(
        0xFFE9E9EF, 0xFF1A1B26, 0xFFD1D4D9, 0xFFE9E9EF,
        0xFFFFFFFF, 0xFFDDE4F1, 0xFFD9E0EA, 0xFFD2D8E3, 0xFFCED3DC,
        0xFF45494F, 0xFF76797F, 0xFFC4C8CF,
    ),
    inverse = inverse(0xFF1A1B26, 0xFFE1E2E8, 0xFFC7D4EA),
)

private val GruvboxDark = fixedColorScheme(
    isDark = true,
    primary = family(0xFFFE8019, 0xFF572A05, 0xFF914608, 0xFFFFC18F),
    secondary = family(0xFFFABD2F, 0xFF573E05, 0xFF916808, 0xFFFFDD8F),
    tertiary = family(0xFF8EC07C, 0xFF263E1E, 0xFF406732, 0xFFBCDDB0),
    surfaces = surfaces(
        0xFF282828, 0xFFE1E2E8, 0xFF282828, 0xFF6A5848,
        0xFF3C230F, 0xFF462B15, 0xFF4C2E16, 0xFF55361D, 0xFF603D20,
        0xFFC1C8D6, 0xFFB4A293, 0xFF7B5E47,
    ),
    inverse = inverse(0xFFE1E2E8, 0xFF1A2230, 0xFF914608),
)

private val GruvboxLight = fixedColorScheme(
    isDark = false,
    primary = family(0xFFAF3A03, 0xFFFFFFFF, 0xFFFFE3D6, 0xFF521A00),
    secondary = family(0xFFB57614, 0xFFFFFFFF, 0xFFFCEED9, 0xFF4E3104),
    tertiary = family(0xFF427B58, 0xFFFFFFFF, 0xFFE3F2E9, 0xFF183925),
    surfaces = surfaces(
        0xFFFBF1C7, 0xFF282828, 0xFFCFC8C5, 0xFFFBF1C7,
        0xFFFFFFFF, 0xFFEAD7CE, 0xFFE2D3CB, 0xFFDACBC4, 0xFFD3C7C2,
        0xFF4F4845, 0xFF7F7976, 0xFFCFC8C4,
    ),
    inverse = inverse(0xFF282828, 0xFFE1E2E8, 0xFFFECBB4),
)

private val AmethystDark = fixedColorScheme(
    isDark = true,
    primary = family(0xFFE8B5EF, 0xFF44104B, 0xFF721B7E, 0xFFE4A0EE),
    secondary = family(0xFFD6C0D6, 0xFF382438, 0xFF5D3C5D, 0xFFD6B8D6),
    tertiary = family(0xFFF5B7B0, 0xFF51120A, 0xFF881D11, 0xFFF5A299),
    surfaces = surfaces(
        0xFF161217, 0xFFE1E2E8, 0xFF161217, 0xFF5D4261,
        0xFF170B19, 0xFF201123, 0xFF251328, 0xFF2E1931, 0xFF381E3B,
        0xFFC1C8D6, 0xFFB093B4, 0xFF75477B,
    ),
    inverse = inverse(0xFFE1E2E8, 0xFF1A2230, 0xFF721B7E),
)

private val AmethystLight = fixedColorScheme(
    isDark = false,
    primary = family(0xFF794F81, 0xFFFFFFFF, 0xFFEFE5F1, 0xFF321B37),
    secondary = family(0xFF6A596C, 0xFFFFFFFF, 0xFFEDE8EE, 0xFF2F2131),
    tertiary = family(0xFF82524D, 0xFFFFFFFF, 0xFFF1E6E4, 0xFF371D1A),
    surfaces = surfaces(
        0xFFF7F5F8, 0xFF161217, 0xFFE2DCE3, 0xFFF7F5F8,
        0xFFFFFFFF, 0xFFF5ECF7, 0xFFF0E7F2, 0xFFE9DFEB, 0xFFE3DBE5,
        0xFF4D454F, 0xFF7E767F, 0xFFCDC4CF,
    ),
    inverse = inverse(0xFF161217, 0xFFE1E2E8, 0xFFDFD0E2),
)

internal fun resolveXmdColorScheme(
    context: Context,
    theme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
): ColorScheme {
    val base = if (theme == AppTheme.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        when (theme) {
            AppTheme.SYSTEM, AppTheme.DEFAULT -> if (isDark) DefaultDark else DefaultLight
            AppTheme.AURORA -> if (isDark) AuroraDark else AuroraLight
            AppTheme.NORD -> if (isDark) NordDark else NordLight
            AppTheme.DRACULA -> if (isDark) DraculaDark else DraculaLight
            AppTheme.CATPPUCCIN -> if (isDark) CatppuccinDark else CatppuccinLight
            AppTheme.TOKYO_NIGHT -> if (isDark) TokyoNightDark else TokyoNightLight
            AppTheme.GRUVBOX -> if (isDark) GruvboxDark else GruvboxLight
            AppTheme.AMETHYST -> if (isDark) AmethystDark else AmethystLight
        }
    }

    return if (isDark && isAmoled) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
        )
    } else {
        base
    }
}

internal fun resolveCurrentXmdColorScheme(context: Context): ColorScheme =
    resolveXmdColorScheme(
        context = context,
        theme = Settings.appTheme(),
        isDark = Settings.isDarkMode(),
        isAmoled = Settings.isAmoledMode(),
    )