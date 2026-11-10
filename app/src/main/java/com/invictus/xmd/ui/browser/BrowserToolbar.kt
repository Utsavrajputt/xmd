@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.browser

import com.invictus.xmd.ui.theme.XmdShapes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.invictus.xmd.ui.icons.AppIcon
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R
import com.invictus.xmd.database.entities.Bookmark

/**
 * Phase E of the Compose migration: the toolbar/address-bar row that used
 * to be fragment_browser.xml's top MaterialCardView (home button, the
 * address pill -- lock icon + EditText + bookmark star -- new-tab button,
 * tabs button w/ count badge, overflow button) plus the
 * LinearProgressIndicator overlapping its bottom edge. Hosted the same way
 * AddressBarSuggestions already is: its own ComposeView (`browserToolbar`
 * in fragment_browser.xml), anchored in the same position the old
 * MaterialCardView + progress line occupied -- see
 * BrowserFragment.onViewCreated's browserToolbar.setContent.
 *
 * Everything here is purely presentational + input-handling for the row
 * itself; every tap routes back to the exact same BrowserFragment
 * functions the old View listeners called (addNewTab(), goHome(),
 * showTabsOverlay(), onBookmarkStarTapped(), Callbacks.openBrowserMenu()) --
 * none of that logic moved or changed. [onOverflowTap] doesn't take a View
 * argument like the old overflowButton.setOnClickListener did: the
 * individual overflow button is no longer a real View to anchor a
 * PopupMenu to, so BrowserFragment's lambda passes the browserToolbar
 * ComposeView itself (a real View) as Callbacks.openBrowserMenu's anchor
 * instead -- see that call site's comment in BrowserFragment.kt.
 *
 * [addressText]/[onAddressTextChange] mirror what urlInput.setText()/its
 * TextWatcher used to do. BrowserFragment still owns the actual text as a
 * `by mutableStateOf` field (addressBarText) and passes it down each
 * recomposition; this composable renders it and reports edits back up,
 * same "field on the Fragment, composable renders it" pattern
 * browserDialogHost/suggestionsCard/tabsListOverlay already use elsewhere
 * in that file. [clearFocusSignal] replaces the old
 * urlInput.clearFocus() + hideSoftInputFromWindow() call at the end of
 * loadUrl(): a plain Fragment function can't reach into a child
 * composable's internal focus state directly, so BrowserFragment just
 * bumps an Int and this composable's LaunchedEffect reacts to the change.
 * Select-all-on-focus (the old urlInput.setOnFocusChangeListener's `else`
 * branch) is handled entirely inside this composable's own
 * onFocusChanged instead of being reported up to the Fragment -- unlike
 * the old EditText, nothing outside this composable needs to know the
 * current selection range.
 *
 * Not independently verified -- no local Android SDK/Gradle in this
 * environment, same caveat every prior phase's summary notes.
 */
@Composable
fun BrowserToolbarRow(
    addressText: String,
    addressBarFocused: Boolean,
    onAddressTextChange: (String) -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    onGo: () -> Unit,
    clearFocusSignal: Int,
    securityIconVisible: Boolean,
    isSecure: Boolean,
    bookmarkVisible: Boolean,
    bookmarkFilled: Boolean,
    onBookmarkTap: () -> Unit,
    onHomeTap: () -> Unit,
    homeVisible: Boolean = true,
    onNewTabTap: () -> Unit,
    onTabsTap: () -> Unit,
    tabsCount: Int,
    onOverflowTap: () -> Unit,
    overflowMenu: @Composable () -> Unit,
    progress: Int,
    progressVisible: Boolean,
    modifier: Modifier = Modifier,
    onBackTap: () -> Unit = {},
    canGoBack: Boolean = false,
    onForwardTap: () -> Unit = {},
    canGoForward: Boolean = false,
    onDownloadsTap: () -> Unit = {},
    downloadsVisible: Boolean = true,
    isBottomBar: Boolean = false,
    navButtonsVisible: Boolean = false,
    linkActionVisible: Boolean = false,
    onLinkActionTap: () -> Unit = {},
    onScanQrTap: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            shape = XmdShapes.M,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!addressBarFocused && navButtonsVisible) {
                    ToolbarIconButton(
                        icon = Icons.ArrowBack,
                        contentDescription = stringResource(R.string.browser_bottom_back),
                        onClick = onBackTap,
                        enabled = canGoBack,
                    )
                    ToolbarIconButton(
                        icon = Icons.ArrowForward,
                        contentDescription = stringResource(R.string.browser_bottom_forward),
                        onClick = onForwardTap,
                        enabled = canGoForward,
                    )
                }
                if (homeVisible) {
                    ToolbarIconButton(
                        icon = Icons.Home,
                        contentDescription = stringResource(R.string.action_home),
                        onClick = onHomeTap,
                    )
                } else if (!addressBarFocused) {
                    // Bottom bar has Home, so New tab takes the leftmost slot.
                    ToolbarIconButton(
                        icon = Icons.Add,
                        contentDescription = stringResource(R.string.action_new_tab),
                        onClick = onNewTabTap,
                    )
                }
                AddressPill(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (homeVisible || !addressBarFocused) 4.dp else 0.dp),
                    text = addressText,
                    onTextChange = onAddressTextChange,
                    onFocusChange = onAddressFocusChange,
                    onGo = onGo,
                    clearFocusSignal = clearFocusSignal,
                    securityIconVisible = securityIconVisible,
                    isSecure = isSecure,
                    bookmarkVisible = bookmarkVisible,
                    bookmarkFilled = bookmarkFilled,
                    onBookmarkTap = onBookmarkTap,
                    onScanQrTap = onScanQrTap,
                    onNewTabTap = onNewTabTap,
                )
                if (!addressBarFocused) {
                    if (homeVisible) {
                        ToolbarIconButton(
                            icon = Icons.Add,
                            contentDescription = stringResource(R.string.action_new_tab),
                            onClick = onNewTabTap,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                    TabsButton(
                        count = tabsCount,
                        contentDescription = stringResource(R.string.action_tabs),
                        onClick = onTabsTap,
                    )
                    // Only present while the page has a downloadable link (detected videos
                    // now surface as a floating action button, see BrowserFabs).
                    if (linkActionVisible) {
                        ToolbarIconButton(
                            icon = Icons.Link,
                            contentDescription = stringResource(R.string.action_add_to_downloads),
                            onClick = onLinkActionTap,
                        )
                    }
                    if (downloadsVisible) {
                        ToolbarIconButton(
                            icon = Icons.Download,
                            contentDescription = stringResource(R.string.browser_bottom_downloads),
                            onClick = onDownloadsTap,
                        )
                    }
                    Box {
                        ToolbarIconButton(
                            icon = Icons.More,
                            contentDescription = stringResource(R.string.action_more),
                            onClick = onOverflowTap,
                        )
                        overflowMenu()
                    }
                }
            }
        }
        // Chrome-style thin progress line sitting on the address bar
        // card's edge -- same 8dp side margins + 8dp top/bottom margin.
        // Track is transparent so only the moving indicator line shows.
        if (progressVisible) {
            val animatedProgress by animateFloatAsState(
                targetValue = (progress / 100f).coerceIn(0f, 1f),
                label = "toolbarProgress",
            )
            // Hand-drawn 2dp line instead of LinearProgressIndicator: the Material
            // indicator pins its own 4dp height, which read as too chunky on the
            // address bar edge.
            Box(
                modifier = Modifier
                    .align(if (isBottomBar) Alignment.TopCenter else Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 17.dp)
                    .padding(
                        top = if (isBottomBar) 8.dp else 0.dp,
                        bottom = if (isBottomBar) 0.dp else 8.dp,
                    )
                    .height(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun AddressPill(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onGo: () -> Unit,
    clearFocusSignal: Int,
    securityIconVisible: Boolean,
    isSecure: Boolean,
    bookmarkVisible: Boolean,
    bookmarkFilled: Boolean,
    onBookmarkTap: () -> Unit,
    onScanQrTap: () -> Unit,
    onNewTabTap: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hostView = LocalView.current

    // Local cursor/selection state, re-synced from [text] only when it
    // changed from *outside* this composable (onPageStarted,
    // applyTabUiState, a tapped suggestion, etc. -- see
    // BrowserFragment.addressBarText) so a real edit in progress here
    // doesn't get its cursor position clobbered on every recomposition.
    var fieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    var fieldFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(text) {
        if (text != fieldValue.text) {
            // Cursor at the start on programmatic update so the domain stays visible;
            // while editing (e.g. the suggestion's fill-in arrow) at the end, to keep typing.
            fieldValue = TextFieldValue(text, selection = TextRange(if (fieldFocused) text.length else 0))
        }
    }
    // Old urlInput.clearFocus() + hideSoftInputFromWindow(), now driven by
    // a one-shot signal bump from BrowserFragment.loadUrl() -- see this
    // file's doc comment above.
    // Only react to a *new* bump. The old `clearFocusSignal > 0` check was true
    // forever after the first page load, so every time this pill (re)entered
    // composition it replayed a stale clear and could eat a fresh tap's focus.
    var handledSignal by remember { mutableStateOf(clearFocusSignal) }
    LaunchedEffect(clearFocusSignal) {
        if (clearFocusSignal != handledSignal) {
            handledSignal = clearFocusSignal
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
    // After the user touches a loaded page, Android view focus sits on the
    // WebView, not on the ComposeView. A Compose requestFocus() in that state
    // can be dropped (no onFocusChanged -> no clear, no info card, no IME), so
    // pull view focus back to the Compose root first.
    val requestAddressFocus: () -> Unit = {
        if (!hostView.hasFocus()) hostView.requestFocus()
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        // Focused: drop the inner fill so the outer card is the only layer
        // (no "double layered" look), like Chrome's single omnibox pill.
        color = if (fieldFocused) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                // The text field itself is only one text line tall, so taps on the
                // rest of the 40dp pill (above/below the text, on the lock icon,
                // in the side padding) never focused it -- focus "sometimes
                // worked". Make the whole pill request focus.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    requestAddressFocus()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fieldFocused) {
                // Editing: Chrome's "+" on the left = new tab (same 24dp glyph as the Lens on the right).
                IconButton(
                    shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onNewTabTap()
                    },
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Add,
                        contentDescription = stringResource(R.string.action_new_tab),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else if (securityIconVisible) {
                Icon(
                    imageVector = if (isSecure) Icons.Lock else Icons.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(16.dp),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Taps landing on the text field itself never reach the Row's
                    // clickable (the field consumes them), so they skipped the
                    // view-focus fix above. Watch the down event on the Initial
                    // pass (without consuming it) and run the same path.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (!fieldFocused) requestAddressFocus()
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { newValue ->
                        fieldValue = newValue
                        onTextChange(fieldValue.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            val gainedFocus = state.isFocused && !fieldFocused
                            fieldFocused = state.isFocused
                            onFocusChange(state.isFocused)
                            // Chrome: tapping the omnibox empties it (hint shows,
                            // cursor ready) -- the current URL stays reachable via
                            // the "current page" row's Edit action.
                            if (gainedFocus && fieldValue.text.isNotEmpty()) {
                                fieldValue = TextFieldValue("")
                                onTextChange("")
                            }
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { onGo() }),
                    decorationBox = { innerTextField ->
                        if (fieldValue.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.hint_url),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    },
                )
            }
            if (fieldFocused) {
                if (fieldValue.text.isEmpty()) {
                    // Nothing typed: QR scanner.
                    IconButton(
                        shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
                        onClick = onScanQrTap,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(36.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(R.drawable.ic_qr_scan),
                            contentDescription = stringResource(R.string.action_scan_qr),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    // Anything typed: Lens turns into a clear (cross) button.
                    IconButton(
                        shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
                        onClick = {
                            fieldValue = TextFieldValue("")
                            onTextChange("")
                        },
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else if (bookmarkVisible) {
                IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
                    onClick = onBookmarkTap,
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(36.dp),
                ) {
                    Icon(
                        imageVector = if (bookmarkFilled) Icons.Bookmark else Icons.BookmarkAdd,
                        contentDescription = stringResource(R.string.action_add_shortcut),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: AppIcon,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(36.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .alpha(if (enabled) 1f else 0.38f),
        )
    }
}

/** Replicates bg_tabs_counter.xml (transparent fill, 1.6dp
 *  colorOnSurfaceVariant stroke, 6dp corner radius) around the live tab
 *  count, inside a 36dp tap target matching every other toolbar button. */
@Composable
private fun TabsButton(count: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = 1.6.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(5.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 99) ":D" else count.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (count > 9) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
