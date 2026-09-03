package com.invictus.xmd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import com.invictus.xmd.R

internal enum class MainDestination {
    Downloads,
    Browser,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MainShell(
    destination: MainDestination,
    activeDownloadCount: Int,
    searchActive: Boolean,
    searchQuery: String,
    snackbarHostState: SnackbarHostState,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onDestinationSelected: (MainDestination) -> Unit,
    onAddDownload: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    onContainerReady: (FragmentContainerView) -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (destination == MainDestination.Downloads) {
                DownloadsTopBar(
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    onSearchActiveChange = onSearchActiveChange,
                    onSearchQueryChange = onSearchQueryChange,
                    onOpenSettings = onOpenSettings,
                    onToggleTheme = onToggleTheme,
                )
            }
        },
        bottomBar = {
            if (!imeVisible) {
                MainNavigationBar(
                    destination = destination,
                    activeDownloadCount = activeDownloadCount,
                    onDestinationSelected = onDestinationSelected,
                    onAddDownload = onAddDownload,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            AndroidView(
                factory = { context ->
                    FragmentContainerView(context).apply {
                        id = R.id.fragmentContainer
                        onContainerReady(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            overlay()
        }
    }
}

@Composable
private fun DownloadsTopBar(
    searchActive: Boolean,
    searchQuery: String,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchActive) {
        if (searchActive) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    if (searchActive) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { onSearchActiveChange(false) }) {
                    Icon(
                        painter = painterResource(XmdIcons.ArrowBack),
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.queue_search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            },
            actions = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            painter = painterResource(XmdIcons.Close),
                            contentDescription = stringResource(R.string.action_clear),
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        )
        return
    }

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_header_title),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onToggleTheme),
            )
        },
        actions = {
            IconButton(onClick = { onSearchActiveChange(true) }) {
                Icon(
                    painter = painterResource(XmdIcons.Search),
                    contentDescription = stringResource(R.string.action_search),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(XmdIcons.Settings),
                    contentDescription = stringResource(R.string.menu_settings),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainNavigationBar(
    destination: MainDestination,
    activeDownloadCount: Int,
    onDestinationSelected: (MainDestination) -> Unit,
    onAddDownload: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShortNavigationBar(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp)),
                windowInsets = WindowInsets(0, 0, 0, 0),
                arrangement = ShortNavigationBarArrangement.EqualWeight,
            ) {
                ShortNavigationBarItem(
                    selected = destination == MainDestination.Downloads,
                    onClick = { onDestinationSelected(MainDestination.Downloads) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeDownloadCount > 0) {
                                    Badge {
                                        Text(if (activeDownloadCount > 99) "99+" else activeDownloadCount.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(XmdIcons.Downloads),
                                contentDescription = null,
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.tab_downloads)) },
                )
                ShortNavigationBarItem(
                    selected = destination == MainDestination.Browser,
                    onClick = { onDestinationSelected(MainDestination.Browser) },
                    icon = {
                        Icon(
                            painter = painterResource(XmdIcons.Public),
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.tab_browser)) },
                )
            }

            FloatingActionButton(
                onClick = onAddDownload,
                modifier = Modifier.size(56.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painter = painterResource(XmdIcons.Add),
                    contentDescription = stringResource(R.string.tab_add),
                )
            }
        }
    }
}