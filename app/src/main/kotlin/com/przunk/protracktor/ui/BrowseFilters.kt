// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.przunk.protracktor.R

/**
 * Browse's Search, one query per level, for as long as Browse is open (2026-11-09).
 *
 * Kept like [BrowseScroll] keeps positions: by level key, so narrowing a format's authors to
 * "nork", opening Nork and coming back finds the authors still narrowed. A level with no entry has
 * Search closed; an entry, even an empty one, has it open. Cleared when Browse closes.
 */
@Stable
class BrowseFilters {
    private val queries = mutableStateMapOf<String, String>()

    /** Whether the field has focus, so Back can drop the keyboard before closing Search. */
    var focused by mutableStateOf(false)

    /** The query at [level], or null while Search is closed there. */
    fun queryAt(level: String): String? = queries[level]

    fun open(level: String) {
        if (level !in queries) queries[level] = ""
    }

    fun change(level: String, query: String) {
        queries[level] = query
    }

    fun close(level: String) {
        queries.remove(level)
        focused = false
    }

    fun clear() {
        queries.clear()
        focused = false
    }
}

/** The field that takes the title's place in Browse's bar while Search is open there. */
@Composable
internal fun BrowseFilterField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    /** What the empty field says; History's says what it searches (2026-11-12). */
    hint: Int = R.string.browse_filter_hint,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(hint), maxLines = 1) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                Icon(PlayerIcons.Close, stringResource(R.string.playlist_search_clear))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth().testTag("browse-filter-field")
            .focusRequester(focus).onFocusChanged { onFocus(it.isFocused) },
    )
    // Opened by a press of Search, which means typing next; a return to a narrowed level does
    // not reopen the keyboard over the list being come back to.
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
}

/** A changed query starts its list from the top; arriving with one (a return) keeps the place. */
@Composable
internal fun TopOnNewFilter(listState: LazyListState, query: String) {
    var seen by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query != seen) {
            seen = query
            listState.scrollToItem(0)
        }
    }
}

/**
 * The tunes below this level that match Search, as they stand: [tracks] for the words last
 * answered here, and [pending] while newer words wait for theirs.
 */
@androidx.compose.runtime.Immutable
internal data class TunesBelow(val tracks: List<com.przunk.protracktor.player.TrackRef>, val pending: Boolean) {
    companion object { val NONE = TunesBelow(emptyList(), pending = false) }
}

/**
 * Asks [search] for the tunes below [level] whenever the words change there, a moment after the
 * typing stops so one search answers a word rather than each letter of it; the answer arrives in
 * [found] (`BrowseState.below`).
 *
 * The answer is kept with the level and the words it belongs to. While newer words wait, the older
 * answer stays on screen rather than the section blinking out per letter; on another level it is
 * never shown, so a list cannot borrow the tunes of the one it was opened from. [contents] asks
 * again when what the level holds changes under it -- a list that finished loading, a folder scanned.
 */
@Composable
internal fun rememberTunesBelow(
    level: String,
    query: String,
    enabled: Boolean,
    contents: Any?,
    found: com.przunk.protracktor.player.TunesFound?,
    search: suspend (level: String, query: String) -> Unit,
): TunesBelow {
    val asking = enabled && com.przunk.protracktor.data.SearchTerms.of(query).isNotEmpty()
    // What the level held when it was last asked about here. Null on arriving, which is when an
    // answer already in the state -- one a way back to the source restored -- is taken as it is.
    var askedOf by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(level, query, asking, contents) {
        if (!asking) return@LaunchedEffect
        val answered = found?.level == level && found.query == query
        if (answered && (askedOf == null || askedOf == contents)) { askedOf = contents; return@LaunchedEffect }
        kotlinx.coroutines.delay(BELOW_PAUSE_MS)
        search(level, query)
        askedOf = contents
    }
    if (!asking) return TunesBelow.NONE
    val here = found?.takeIf { it.level == level }
    return TunesBelow(here?.tracks.orEmpty(), pending = here?.query != query)
}

/** Long enough to be between words, short enough not to read as waiting. */
internal const val BELOW_PAUSE_MS = 250L
