package com.invictus.xmd.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    onNewTabTap: () -> Unit,
    onTabsTap: () -> Unit,
    tabsCount: Int,
    onOverflowTap: () -> Unit,
    overflowMenu: @Composable () -> Unit,
    progress: Int,
    progressVisible: Boolean,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarIconButton(
                    iconRes = XmdIcons.Home,
                    contentDescription = stringResource(R.string.action_home),
                    onClick = onHomeTap,
                )
                AddressPill(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
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
                )
                ToolbarIconButton(
                    iconRes = XmdIcons.Add,
                    contentDescription = stringResource(R.string.action_new_tab),
                    onClick = onNewTabTap,
                    modifier = Modifier.padding(start = 2.dp),
                )
                TabsButton(
                    count = tabsCount,
                    contentDescription = stringResource(R.string.action_tabs),
                    onClick = onTabsTap,
                )
                Box {
                    ToolbarIconButton(
                        iconRes = XmdIcons.More,
                        contentDescription = stringResource(R.string.action_more),
                        onClick = onOverflowTap,
                    )
                    overflowMenu()
                }
            }
        }
        // Chrome-style thin progress line sitting on the address bar
        // card's own bottom edge -- same 8dp side margins + 8dp bottom
        // margin the old pageProgress had, so it spans the same width.
        // Track is transparent so only the moving indicator line shows.
        if (progressVisible) {
            val animatedProgress by animateFloatAsState(
                targetValue = (progress / 100f).coerceIn(0f, 1f),
                label = "toolbarProgress",
            )
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
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
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Local cursor/selection state, re-synced from [text] only when it
    // changed from *outside* this composable (onPageStarted,
    // applyTabUiState, a tapped suggestion, etc. -- see
    // BrowserFragment.addressBarText) so a real edit in progress here
    // doesn't get its cursor position clobbered on every recomposition.
    var fieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    LaunchedEffect(text) {
        if (text != fieldValue.text) {
            fieldValue = TextFieldValue(text, selection = TextRange(text.length))
        }
    }
    // Old urlInput.clearFocus() + hideSoftInputFromWindow(), now driven by
    // a one-shot signal bump from BrowserFragment.loadUrl() -- see this
    // file's doc comment above.
    LaunchedEffect(clearFocusSignal) {
        if (clearFocusSignal > 0) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (securityIconVisible) {
                Icon(
                    painter = painterResource(if (isSecure) XmdIcons.Lock else XmdIcons.LockOpen),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(16.dp),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { newValue ->
                        fieldValue = newValue
                        onTextChange(newValue.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 4.dp)
                        .onFocusChanged { state ->
                            // Chrome-style: focusing the bar selects the
                            // full text (was urlInput.selectAll() in the
                            // XML version's focus-change listener).
                            if (state.isFocused) {
                                fieldValue = fieldValue.copy(
                                    selection = TextRange(0, fieldValue.text.length)
                                )
                            }
                            onFocusChange(state.isFocused)
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
            if (bookmarkVisible) {
                IconButton(
                    onClick = onBookmarkTap,
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (bookmarkFilled) XmdIcons.Bookmark else XmdIcons.BookmarkAdd
                        ),
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
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Replicates bg_tabs_counter.xml (transparent fill, 1.6dp
 *  colorOnSurfaceVariant stroke, 6dp corner radius) around the live tab
 *  count, inside a 40dp tap target matching every other toolbar button. */
@Composable
private fun TabsButton(count: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(
                    width = 1.6.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
