@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.invictus.xmd.ui.browser

import com.invictus.xmd.ui.theme.XmdShapes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.invictus.xmd.utils.FaviconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.invictus.xmd.R
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.invictus.xmd.network.SuggestImageLoader

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
    /** [subtitle]/[imageUrl] are set for Google "entity" rows (Chrome's rows
     *  with a picture); [typed] marks the first "what you typed" row. */
    data class Search(
        override val text: String,
        val subtitle: String? = null,
        val imageUrl: String? = null,
        val typed: Boolean = false,
        /** A past search from history: clock icon instead of the magnifier. */
        val history: Boolean = false,
    ) : Suggestion()
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

/**
 * Chrome-style full-body suggestion list: fills the whole area under the
 * address bar (the caller paints the opaque background behind it), one
 * rounded theme-coloured row per suggestion with a small gap between rows.
 * [reverse] flips the order for a bottom toolbar so the best match sits
 * next to the bar.
 */
@Composable
fun AddressBarSuggestions(
    suggestions: List<Suggestion>,
    query: String,
    reverse: Boolean,
    onTap: (Suggestion) -> Unit,
    onFillTap: (String) -> Unit,
    onAddTap: (String) -> Unit,
    onCopyTap: (String) -> Unit,
    onShareTap: (String) -> Unit,
    onEditTap: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        reverseLayout = reverse,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(suggestions) { item ->
            SuggestionRow(
                item = item,
                query = query,
                onTap = onTap,
                onFillTap = onFillTap,
                onAddTap = onAddTap,
                onCopyTap = onCopyTap,
                onShareTap = onShareTap,
                onEditTap = onEditTap,
            )
        }
    }
}

/** Address line colour: the theme blue, softened toward the secondary text colour on dark themes so it
 *  reads lighter and doesn't out-shout the title. */
@Composable
private fun addressColor(): Color {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    return if (dark) lerp(cs.primary, cs.onSurfaceVariant, 0.55f) else cs.primary
}

/** "https://www.github.com/a/b/" -> "github.com/a/b" (how Chrome prints an address in a suggestion). */
private fun displayUrl(url: String): String =
    url.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

/** Typed part normal, the part Google added bold -- like Chrome. */
private fun boldCompletion(text: String, query: String): AnnotatedString {
    val q = query.trim()
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    return buildAnnotatedString {
        if (q.isNotEmpty() && text.startsWith(q, ignoreCase = true)) {
            append(text.substring(0, q.length))
            withStyle(bold) { append(text.substring(q.length)) }
        } else {
            withStyle(bold) { append(text) }
        }
    }
}

@Composable
private fun SuggestionThumb(url: String) {
    val bitmap = produceState<android.graphics.Bitmap?>(
        initialValue = SuggestImageLoader.peek(url),
        key1 = url,
    ) {
        value = withContext(Dispatchers.IO) { SuggestImageLoader.load(url) } ?: value
    }.value
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(XmdShapes.Xs)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    item: Suggestion,
    query: String,
    onTap: (Suggestion) -> Unit,
    onFillTap: (String) -> Unit,
    onAddTap: (String) -> Unit,
    onCopyTap: (String) -> Unit,
    onShareTap: (String) -> Unit,
    onEditTap: (String) -> Unit,
) {
    val search = item as? Suggestion.Search
    val thumbUrl = search?.imageUrl
    val subtitle = search?.subtitle
    val tall = thumbUrl != null || subtitle != null || item is Suggestion.Clipboard || item is Suggestion.History
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(XmdShapes.S)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                onClick = { onTap(item) },
                // Search rows lost their old "+" (the arrow replaced it), so
                // long-press keeps "add to shortcuts" reachable.
                onLongClick = if (search != null && !search.typed) {
                    { onAddTap(search.text) }
                } else null,
            )
            .padding(
                start = 12.dp,
                end = 4.dp,
                top = if (tall) 8.dp else 4.dp,
                bottom = if (tall) 8.dp else 4.dp,
            )
            .heightIn(min = 44.dp),
    ) {
        // Fixed-width leading slot so every row's text lines up.
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            when {
                thumbUrl != null -> SuggestionThumb(thumbUrl)
                search != null -> Icon(
                    imageVector = if (search.history) Icons.History else Icons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                else -> {
                    val faviconUrl = when (item) {
                        is Suggestion.History -> item.url
                        is Suggestion.CurrentPage -> item.url
                        is Suggestion.Clipboard -> item.url
                        is Suggestion.Search -> null
                    }
                    val favicon = if (faviconUrl != null) {
                        produceState<android.graphics.Bitmap?>(
                            initialValue = FaviconLoader.peek(faviconUrl),
                            key1 = faviconUrl,
                        ) {
                            value = withContext(Dispatchers.IO) { FaviconLoader.load(faviconUrl, fast = true) }
                                ?: value
                        }.value
                    } else null
                    if (favicon != null) {
                        Image(
                            bitmap = favicon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(5.dp),
                        )
                    } else {
                        Icon(
                            imageVector = when (item) {
                                is Suggestion.History -> Icons.Public
                                is Suggestion.CurrentPage -> Icons.Public
                                is Suggestion.Clipboard -> Icons.Link
                                is Suggestion.Search -> Icons.Search
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
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
        } else if (search != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = boldCompletion(search.text, query),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else if (item is Suggestion.History) {
            // Chrome's page row: title, then the address in the accent colour.
            val address = displayUrl(item.url)
            val showTitle = item.text.isNotBlank() && displayUrl(item.text) != address
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                if (showTitle) {
                    Text(
                        text = item.text,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = address,
                    color = addressColor(),
                    style = if (showTitle) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = item.text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
        }
        when (item) {
            is Suggestion.Search -> {
                if (item.typed) {
                    Spacer(Modifier.size(12.dp))
                } else {
                    // Chrome's "↖": copy this suggestion into the address bar to keep editing it.
                    IconButton(
                        shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
                        onClick = { onFillTap(item.text) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.FillArrow,
                            contentDescription = stringResource(R.string.action_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            is Suggestion.History -> {
                IconButton(
                    shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
                    onClick = { onFillTap(item.url) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.FillArrow,
                        contentDescription = stringResource(R.string.action_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
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
