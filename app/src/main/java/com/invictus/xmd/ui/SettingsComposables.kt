package com.invictus.xmd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.invictus.xmd.R
import com.invictus.xmd.ui.theme.AppTheme

/**
 * Card shell matching the `colorSurfaceContainerLow` + `ShapeAppearance.Xmd.Large`
 * card language used across every settings_*.xml layout being replaced here.
 * The 20dp corner radius is a hand-matched approximation of that shape
 * appearance (themes.xml defines it as a set of per-corner dimens rather
 * than one flat value) -- close enough visually; revisit if ShapeAppearance
 * ever changes.
 */
@Composable
fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** Full-width hairline divider between stacked settings rows inside a card. */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Bold title + switch on one line, muted caption below -- the recurring
 *  "toggle setting" row shape used by Appearance/Downloads/Browser. */
@Composable
fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Tappable row on the Settings root screen: tonal icon chip, title +
 *  subtitle, trailing chevron. Mirrors item_settings_category.xml. */
@Composable
fun CategoryRow(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Single swatch in the horizontally-scrolling theme picker -- ring (selection
 * border), rounded box (theme background), 3 dots (primary/secondary/tertiary),
 * checkmark when selected, name below. Colors are computed exactly like the
 * old SettingsAppearanceFragment's setupThemePicker (now SettingsActivity's
 * AppearanceRoute) did: SYSTEM resolves its
 * dots against the dynamic-color-wrapped context (Material You), every other
 * theme uses its own fixed swatch* hex strings from [AppTheme].
 */
@Composable
fun ThemeSwatchItem(
    theme: AppTheme,
    isSelected: Boolean,
    isDark: Boolean,
    isAmoled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val dynamicContext = remember(context) { DynamicColors.wrapContextIfAvailable(context) }

    fun resolvedColor(attr: Int, fallbackHex: String): Color =
        if (theme == AppTheme.SYSTEM) {
            Color(MaterialColors.getColor(dynamicContext, attr, android.graphics.Color.parseColor(fallbackHex)))
        } else {
            Color(android.graphics.Color.parseColor(fallbackHex))
        }

    val primaryColor = resolvedColor(com.google.android.material.R.attr.colorPrimary, theme.swatchPrimary)
    val secondaryColor = resolvedColor(com.google.android.material.R.attr.colorSecondary, theme.swatchSecondary)
    val tertiaryColor = resolvedColor(com.google.android.material.R.attr.colorTertiary, theme.swatchTertiary)
    val bgColor = when {
        isAmoled -> Color.Black
        !isDark -> Color(0xFFF5F7FA)
        theme == AppTheme.SYSTEM ->
            Color(MaterialColors.getColor(dynamicContext, android.R.attr.colorBackground, android.graphics.Color.parseColor(theme.swatchBackground)))
        else -> Color(android.graphics.Color.parseColor(theme.swatchBackground))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .padding(3.dp)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, primaryColor, RoundedCornerShape(13.dp))
                    } else {
                        Modifier
                    },
                )
                .background(bgColor, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Row {
                Box(Modifier.padding(2.dp).size(12.dp).background(primaryColor, CircleShape))
                Box(Modifier.padding(2.dp).size(12.dp).background(secondaryColor, CircleShape))
                Box(Modifier.padding(2.dp).size(12.dp).background(tertiaryColor, CircleShape))
            }
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .size(16.dp),
                )
            }
        }
        Text(
            text = stringResource(theme.titleRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 8dp transparent gap between root-screen category rows, matching
 *  drawable/divider_row_gap.xml (a spacer, not a visible rule). */
@Composable
fun CategoryRowGap() {
    Box(modifier = Modifier.padding(vertical = 4.dp))
}
