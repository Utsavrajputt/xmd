@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.invictus.xmd.R

/**
 * Phase 5 (Browser) conversion of the old suggestionsCard MaterialCardView +
 * suggestionsList RecyclerView/SuggestionAdapter. Hosted in-place of that
 * card in fragment_browser.xml (same id, same top+8dp-margin position in
 * the FrameLayout stack) -- so this composable owns only the dropdown's
 * *content*; BrowserFragment.scheduleSuggest()/hideSuggestions() still
 * decide *whether* it's showing, now by setting [suggestions] to
 * empty/non-empty instead of flipping View.GONE/VISIBLE.
 *
 * Caller passes a fully-merged, ready-to-render list -- this composable
 * does no filtering/debouncing itself. Order (see
 * BrowserFragment.scheduleSuggest/quickSuggestions): [CurrentPage] (if a
 * page is loaded), then [Clipboard] (if the clipboard holds an http(s)
 * link), then History matches, then Search phrases -- matching what
 * Chrome's own omnibox shows the instant you tap it, before you've typed
 * anything of your own.
 */
sealed class Suggestion {
    abstract val text: String
    data class Search(override val text: String) : Suggestion()
    data class History(override val text: String, val url: String) : Suggestion()

    /** The page currently loaded in this tab -- shown so the copy/share/
     *  edit affordances are reachable the instant you tap the address bar,
     *  without having to open the overflow menu. Tapping the row itself
     *  (as opposed to one of its three trailing icons) just dismisses the
     *  dropdown -- the full URL is already sitting in the field, editable,
     *  same as tapping [Suggestion.Edit] on it. */
    data class CurrentPage(override val text: String, val url: String) : Suggestion()

    /** An http(s) link currently sitting in the clipboard -- Chrome's
     *  "Link that you copied" row. Tapping it fills+navigates like History/
     *  Search do. */
    data class Clipboard(override val text: String, val url: String) : Suggestion()
}

@Composable
fun AddressBarSuggestions(
    suggestions: List<Suggestion>,
    onTap: (Suggestion) -> Unit,
    onAddTap: (String) -> Unit,
    onCopyTap: (String) -> Unit,
    onShareTap: (String) -> Unit,
    onEditTap: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            items(suggestions) { item ->
                SuggestionRow(
                    item = item,
                    onTap = onTap,
                    onAddTap = onAddTap,
                    onCopyTap = onCopyTap,
                    onShareTap = onShareTap,
                    onEditTap = onEditTap,
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    item: Suggestion,
    onTap: (Suggestion) -> Unit,
    onAddTap: (String) -> Unit,
    onCopyTap: (String) -> Unit,
    onShareTap: (String) -> Unit,
    onEditTap: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap(item) }
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (item) {
                    is Suggestion.History -> Icons.History
                    is Suggestion.CurrentPage -> Icons.Public
                    is Suggestion.Clipboard -> Icons.Link
                    is Suggestion.Search -> Icons.Search
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(15.dp),
            )
        }
        if (item is Suggestion.Clipboard) {
            // Two-line "Link that you copied" / actual URL, like Chrome's
            // clipboard suggestion -- a bare URL with no label would look
            // identical to a History row and lose the "this came from your
            // clipboard, not your browsing" context.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.address_bar_clipboard_suggestion_label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = item.text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
        }
        when (item) {
            is Suggestion.Search -> {
                IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), onClick = { onAddTap(item.text) }) {
                    Icon(
                        imageVector = Icons.Add,
                        contentDescription = stringResource(R.string.action_add_shortcut),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            is Suggestion.CurrentPage -> {
                IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
                    onClick = { onCopyTap(item.url) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Copy,
                        contentDescription = stringResource(R.string.action_copy_link),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
                    onClick = { onShareTap(item.url) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Share,
                        contentDescription = stringResource(R.string.action_share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(shapes = androidx.compose.material3.IconButtonDefaults.shapes(), 
                    onClick = { onEditTap(item.url) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Edit,
                        contentDescription = stringResource(R.string.action_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            else -> Unit
        }
    }
}
