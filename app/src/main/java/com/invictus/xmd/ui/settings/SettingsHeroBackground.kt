package com.invictus.xmd.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Calm, theme-tinted background for the Settings top hero (the Profile page keeps the sky look).
 *
 * Built from the theme's primary colour but always pulled well towards black, so the white name /
 * subtitle and the quick-action chips keep strong contrast on any theme. Decoration is only soft
 * light -- no shapes or animation -- so nothing competes with the text: a faint glow behind the
 * avatar, a fainter one in the opposite corner, and a slightly darker floor under the chip row.
 */
@Composable
fun Modifier.settingsHeroBackground(cornerRadius: Dp = 28.dp): Modifier {
    val primary = MaterialTheme.colorScheme.primary
    val top = lerp(primary, Color.Black, 0.62f)
    val bottom = lerp(primary, Color.Black, 0.86f)
    val shape = RoundedCornerShape(cornerRadius)

    return this
        .clip(shape)
        .drawBehind {
            val w = size.width
            val h = size.height
            drawRect(
                Brush.linearGradient(
                    colors = listOf(top, bottom),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                ),
            )
            // Soft glow behind the avatar.
            drawRect(
                Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(w * 0.12f, h * 0.30f),
                    radius = w * 0.45f,
                ),
            )
            // Fainter counter-glow, top right.
            drawRect(
                Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(w * 0.98f, 0f),
                    radius = w * 0.55f,
                ),
            )
            // Slightly darker floor so the chip row sits on a calm area.
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                    startY = h * 0.5f,
                    endY = h,
                ),
            )
        }
        .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
}
