@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.invictus.xmd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.invictus.xmd.ui.theme.wallpaperAwareBarColor

/**
 * Title style shared by every screen header (Material 3 Expressive emphasized
 * headline), so Downloads, History, Status Saver, Settings... all read the same.
 */
@Composable
fun xmdTopBarTitleStyle(): TextStyle =
    MaterialTheme.typography.headlineSmallEmphasized.copy(fontWeight = FontWeight.ExtraBold)

/** Flat bar colors shared by every screen header. */
@Composable
fun xmdTopBarColors(containerColor: Color = wallpaperAwareBarColor()): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = containerColor,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

/**
 * The one header used by every screen: a flat Material 3 top app bar with the
 * shared emphasized title and colors. [bottomContent] (e.g. a tab row) is drawn
 * on the same surface directly below the bar so both read as a single header.
 */
@Composable
fun XmdTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors? = null,
    bottomContent: (@Composable () -> Unit)? = null,
) {
    val barColors = colors ?: xmdTopBarColors(
        if (bottomContent != null) Color.Transparent else wallpaperAwareBarColor(),
    )
    val bar: @Composable () -> Unit = {
        TopAppBar(
            title = { ProvideTextStyle(xmdTopBarTitleStyle()) { title() } },
            modifier = if (bottomContent == null) modifier else Modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            windowInsets = windowInsets,
            colors = barColors,
        )
    }
    if (bottomContent == null) {
        bar()
    } else {
        Column(modifier.background(wallpaperAwareBarColor())) {
            bar()
            bottomContent()
        }
    }
}
