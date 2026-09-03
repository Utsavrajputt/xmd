package com.invictus.xmd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xmd.R

/**
 * Phase F of the Compose migration: the Chrome-style find-in-page bar that
 * used to be fragment_browser.xml's findInPageBar MaterialCardView (an
 * EditText + match-count TextView + prev/next/close ImageButtons). Hosted
 * the same way findInPageOverlay's ComposeView sits in fragment_browser.xml
 * -- same wrap_content/top-gravity/8dp-margin position the old card had.
 * Renders nothing when [visible] is false, replacing the old
 * visibility="gone" default (same "composable renders nothing when hidden"
 * approach AddressBarSuggestions already uses for its own dropdown).
 *
 * [query]/[onQueryChange] mirror the old EditText + its TextWatcher --
 * BrowserFragment still owns the actual text as a `by mutableStateOf` field
 * (findInPageQuery) and passes it down each recomposition; the composable
 * renders it and reports edits back up. [matchText] is BrowserFragment's
 * "$current/$numberOfMatches" string, updated from the WebView's
 * FindListener the same way it always was -- just written to a Compose
 * state field instead of findInPageMatchCount.text now.
 *
 * The old EditText had imeOptions="actionNext" but no editor-action
 * listener was ever wired to it (dead attribute). This composable actually
 * wires ImeAction.Next to [onNext] -- the same findNext(true) call
 * findInPageNext's click listener already made -- restoring the behavior
 * the XML always signaled but never delivered, rather than carrying the
 * dead attribute forward as-is.
 *
 * [requestFocus] is bumped (like BrowserToolbarRow's clearFocusSignal) each
 * time showFindInPage() opens the bar, since a plain Fragment function
 * can't reach into this composable's internal focus state directly --
 * mirrors the old findInPageInput.requestFocus() +
 * imm.showSoftInput(SHOW_IMPLICIT) call.
 *
 * Not independently verified -- no local Android SDK/Gradle in this
 * environment, same caveat every prior phase's summary notes.
 */
@Composable
fun FindInPageBar(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    matchText: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    requestFocus: Int,
) {
    if (!visible) return

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(requestFocus) {
        if (requestFocus > 0) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { onNext() }),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.find_in_page_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            )
                        }
                        innerTextField()
                    },
                )
            }
            Text(
                text = matchText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 4.dp),
            )
            IconButton(onClick = onPrev, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(XmdIcons.ArrowUp),
                    contentDescription = stringResource(R.string.find_in_page_prev),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(XmdIcons.ArrowDown),
                    contentDescription = stringResource(R.string.find_in_page_next),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(XmdIcons.Close),
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Phase F: the navigation loading veil that used to be
 * fragment_browser.xml's navLoadingVeil FrameLayout + ProgressBar --
 * covers the content area the instant a new page starts loading over the
 * network so the outgoing page's pixels are never visible mid-navigation
 * (see showNavLoadingVeil()/hideNavLoadingVeil()'s doc comment in
 * BrowserFragment.kt, unchanged by this phase). Renders nothing when
 * [visible] is false, same "gone by default" behavior the old View had --
 * the composable's absence already means it can't intercept touches, so
 * the old android:clickable/focusable="true" pair (there only to block
 * touches reaching the WebView underneath while shown) has no Compose
 * equivalent to carry forward; a rendered Box already consumes touches
 * within its bounds by default.
 *
 * Hosted as the last child of fragment_browser.xml's content FrameLayout,
 * same reason the old View's bringToFront() call existed: paint order
 * alone (last child = topmost) is enough once nothing after it needs to
 * be on top, so there's nothing left to bring to front.
 */
@Composable
fun NavLoadingVeil(visible: Boolean) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
