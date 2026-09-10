// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.Platforms
import com.przunk.protracktor.player.RandomScope
import com.przunk.protracktor.player.TrackRef

/**
 * What the dice has given this session.
 *
 * **This screen replaced a blank** (`docs/PLAN_RANDOM.md`). Random used to draw `AwayScrim` — a
 * sheet of glass over the playlist with the words "Playing at random" on it and nothing else. Its
 * own comment admitted what it was: *"a mode you can enter but cannot obviously leave is a trap."*
 * A defence, not a design. The playlist was hidden because it would have lied, and nothing was put
 * in its place.
 *
 * The words are the same words. They now stand over the record instead of over nothing, and the
 * three things that were missing are here: what has played, a way back to any of it, and the scope
 * as a control rather than as a long press nothing advertises.
 *
 * **A record, not a queue.** `READ_AHEAD` picks stand past the cursor at all times so a tune can be
 * fetched before it is wanted; they are not shown, because showing them would turn this into a
 * schedule — and one the dice may discard the moment the scope changes.
 */
@Composable
fun RandomScreen(
    state: PlayerUiState,
    browse: BrowseState,
    listState: LazyListState,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onFilter: () -> Unit,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onAddToOtherPlaylist: (TrackRef) -> Unit,
    onAddSelectedToPlaylist: (List<TrackRef>) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // **Always show the row that is playing** (owner, 2026-09-10: "przesuwanie next/prev powinno
    // zawsze pokazywać na widoku zaznaczony track, niezależnie czy idę w lewo czy prawo").
    //
    // Keyed on the cursor, which covers both things it has to do: a new pick moves the cursor to
    // the end, so the record follows itself downwards, and stepping back moves it up, so previous
    // shows what it is playing instead of leaving you looking at the bottom of the list.
    //
    // I first keyed this on the *length*, reasoning that pressing a row should not yank the view.
    // The reasoning was wrong: a row you can press is a row you can see, so scrolling to it moves
    // nothing. It only ever cost the case he is describing.
    LaunchedEffect(state.randomIndex) {
        val index = state.randomIndex
        if (index !in state.randomPicks.indices) return@LaunchedEffect

        val layout = listState.layoutInfo
        val row = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val below = row != null && row.offset + row.size > layout.viewportEndOffset
        when {
            // Fully on screen: **leave it alone**. Scrolling a list that already shows what you
            // asked for is the list moving for its own reasons, which is what he objected to.
            row != null && row.offset >= layout.viewportStartOffset && !below -> Unit
            // Off the bottom, or hanging over it: down by exactly the overhang, so the row it was
            // showing stays where it was and one more appears under it.
            below -> listState.animateScrollBy((row.offset + row.size - layout.viewportEndOffset).toFloat())
            // Off the top, or not laid out at all -- which is what a freshly appended row looks
            // like on this pass. Top-aligning is right for the first and harmless for the second,
            // since the last row cannot be scrolled past the end of the list.
            else -> listState.animateScrollToItem(index)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        RandomHeader(browse = browse, onFilter = onFilter, contentPadding = contentPadding)

        if (state.randomPicks.isEmpty()) {
            // Two different empties, and telling them apart is the whole of this branch. "Rolling"
            // is momentary -- entering rolls at once, so it is what a slow first fetch looks like
            // rather than a state anybody sits in. "Nothing to pick from" is a dead end, and a
            // screen that says "Rolling…" for ever while nothing arrives is a screen telling lies.
            Text(
                text = stringResource(
                    if (state.randomExhausted) R.string.random_nothing_to_pick
                    else R.string.random_rolling
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
            return@Column
        }

        PlaylistBody(
            tracks = state.randomPicks,
            listState = listState,
            currentIndex = state.randomIndex.takeIf { it >= 0 },
            onPlayAt = onPlayAt,
            onRemoveAt = onRemoveAt,
            // Nothing to reorder: the order is the dice's, and a handle that answers nothing is
            // worse than no handle.
            onMove = { _, _ -> },
            onShowNeighbours = onShowNeighbours,
            onShareFile = onShareFile,
            onShareLink = onShareLink,
            onAddToOtherPlaylist = onAddToOtherPlaylist,
            onAddSelectedToPlaylist = onAddSelectedToPlaylist,
            // Removing many at once is the playlist's; here the whole list goes when you leave.
            onRemoveMany = {},
            // The top padding belongs to the header, which has already spent it.
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            enabled = true,
            reorderable = false,
            // By position, because the dice may legitimately give the same tune twice out of a
            // small pool and two rows with one key is a crash, not a blemish.
            keyOf = { index, track -> "$index:${track.id}" },
        )
    }
}

/**
 * The heading, and the scope as something you can see and press.
 *
 * **The scope used to be a long press on the Random row in Browse.** Nothing on screen said so;
 * `a11y_choose_random_scope` exists, so a screen reader announced it and an eye did not. A button
 * with a word on it is the whole of the fix.
 */
@Composable
private fun RandomHeader(
    browse: BrowseState,
    onFilter: () -> Unit,
    contentPadding: PaddingValues,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = contentPadding.calculateTopPadding())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = PlayerIcons.Dice,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.random_playing_title),
                style = MaterialTheme.typography.titleMedium,
            )
            // What is set, not what is playing. A pick read ahead under the old scope keeps that
            // scope, so a line reading "playing: Amiga" could be wrong for up to three tunes.
            Text(
                text = randomScopeLabel(browse),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LabelledAction(
            icon = PlayerIcons.Filter,
            label = stringResource(R.string.random_filter),
            onClick = onFilter,
        )
    }
}

/** What the dice is set to pick from, in words, for wherever that has to be shown. */
@Composable
internal fun randomScopeLabel(browse: BrowseState): String = when (val scope = browse.randomScope) {
    is RandomScope.Everything -> stringResource(R.string.random_scope_everything)
    is RandomScope.Favourites -> stringResource(R.string.random_scope_favourites)
    // A platform whose name we cannot resolve is a stored id from a build that knew it. Saying
    // "Everything" would be a lie; saying the id would be plumbing.
    is RandomScope.OnPlatform -> Platforms.byId(scope.platformId)?.name
        ?: stringResource(R.string.random_scope_everything)
}
