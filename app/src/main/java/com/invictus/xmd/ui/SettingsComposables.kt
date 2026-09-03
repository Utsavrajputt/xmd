package com.invictus.xmd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import com.invictus.xmd.ui.icons.AppIcon
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xmd.R
import com.invictus.xmd.ui.theme.AppTheme
import com.invictus.xmd.ui.theme.resolveXmdColorScheme

/**
 * Card shell using the shared Kotlin-owned Material color and shape system.
 * Defaults to compact vertical padding like mpvRx PreferenceCard.
 */
@Composable
fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isAmoled = MaterialTheme.colorScheme.background == Color.Black
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (isAmoled) BorderStroke(1.dp, Color(0xFF1F1F1F)) else null,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Hairline divider between stacked settings rows inside a card, matching mpvRx. */
@Composable
fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

/**
 * A section header for preferences, displayed outside cards, with the 42.dp accent highlight bar
 * matching mpvRx PreferenceSectionHeader.
 */
@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(2.dp),
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            )
        }
    }
}

/**
 * Compact setting row with title + subtitle on the left and switch on the right.
 * Entire row is clickable, matching mpvRx SwitchPreference.
 */
@Composable
fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/**
 * Tappable row on the Settings root screen: tonal icon chip, title +
 * subtitle, trailing chevron, and tablet selection highlight.
 */
@Composable
fun CategoryRow(
    icon: AppIcon,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isFirst: Boolean = false,
    isLast: Boolean = false,
) {
    val rowShape = when {
        isFirst && isLast -> RoundedCornerShape(20.dp)
        isFirst -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        isLast -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
        else -> androidx.compose.ui.graphics.RectangleShape
    }
    val rowBgColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = Icons.ChevronRight,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Single swatch in the horizontally-scrolling theme picker -- ring (selection
 * border), rounded box (theme background), 3 dots (primary/secondary/tertiary),
 * checkmark when selected, name below. Colors come from the same resolver as
 * the app itself, including Material You and AMOLED behavior.
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
    val colorScheme = remember(context, theme, isDark, isAmoled) {
        resolveXmdColorScheme(context, theme, isDark, isAmoled)
    }
    val primaryColor = colorScheme.primary
    val secondaryColor = colorScheme.secondary
    val tertiaryColor = colorScheme.tertiary
    val bgColor = colorScheme.background

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
                    imageVector = Icons.Check,
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

/** Hairline divider between root-screen category rows, matching mpvRx. */
@Composable
fun CategoryRowGap() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}
