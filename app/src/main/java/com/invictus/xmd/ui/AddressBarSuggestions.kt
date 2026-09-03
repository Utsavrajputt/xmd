package com.invictus.xmd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
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
 * Caller passes a fully-merged, ready-to-render list (history matches then
 * search results, same order scheduleSuggest() always built) -- this
 * composable does no filtering/debouncing itself.
 */
sealed class Suggestion {
    abstract val text: String
    data class Search(override val text: String) : Suggestion()
    data class History(override val text: String, val url: String) : Suggestion()
}

@Composable
fun AddressBarSuggestions(
    suggestions: List<Suggestion>,
    onTap: (Suggestion) -> Unit,
    onAddTap: (String) -> Unit,
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
                SuggestionRow(item = item, onTap = onTap, onAddTap = onAddTap)
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    item: Suggestion,
    onTap: (Suggestion) -> Unit,
    onAddTap: (String) -> Unit,
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
                painter = painterResource(
                    if (item is Suggestion.History) XmdIcons.History else XmdIcons.Search
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(15.dp),
            )
        }
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
        if (item is Suggestion.Search) {
            IconButton(onClick = { onAddTap(item.text) }) {
                Icon(
                    painter = painterResource(XmdIcons.Add),
                    contentDescription = stringResource(R.string.action_add_shortcut),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
