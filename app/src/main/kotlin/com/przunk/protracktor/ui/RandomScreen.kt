// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

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
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.first

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
    LaunchedEffect(state.randomIndex, state.randomPicks.size) {
        val index = state.randomIndex
        if (index !in state.randomPicks.indices) return@LaunchedEffect

        // **Measured after the row exists, not before.** This effect restarts the moment the cursor
        // moves, which is the same moment the record grows — and on that pass the list has been
        // composed but not laid out, so `layoutInfo` still describes the list without the new row.
        // Deciding from that is deciding from the previous screen, and it was out by about the
        // height of the dock.
        snapshotFlow { listState.layoutInfo }.first { it.totalItemsCount > index }
        val layout = listState.layoutInfo
        val row = layout.visibleItemsInfo.firstOrNull { it.index == index }

        // **The part of the list you can actually see**, which is not the viewport. The viewport
        // runs under the content padding, and the bottom padding here is the dock -- several rows
        // tall. A row sitting behind it was inside `viewportEndOffset`, passed as "fully visible",
        // and was left there; that is the lag the owner counted, four presses of it.
        val top = layout.viewportStartOffset + layout.beforeContentPadding
        val bottom = layout.viewportEndOffset - layout.afterContentPadding
        val fullyVisible = row != null && row.offset >= top && row.offset + row.size <= bottom
        if (fullyVisible) return@LaunchedEffect

        // **Absolute, never relative.** `animateScrollBy` was the obvious way to move by exactly
        // the overhang and the wrong one: pressing next again cancels the animation part-way, so
        // each unfinished nudge left the list a little further behind and four presses went by
        // before the row caught up. Scrolling *to* an index cannot accumulate an error, because it
        // says where to end up rather than how far to travel.
        // The unobstructed height, for the same reason: landing the row at the bottom of the raw
        // viewport would land it behind the dock again.
        val viewport = bottom - top
        val height = row?.size ?: layout.visibleItemsInfo.firstOrNull()?.size ?: 0
        // **Which side it left by is decided from the index, not from whether the row was found.**
        // A row missing from `visibleItemsInfo` is missing whichever way it went: appended below
        // the last row, or scrolled off above the first by Previous. Treating "not found" as
        // "below" made Previous land the row at the *bottom* of the screen -- a whole page of
        // travel for a move of one row, and the opposite of what Next does going the other way
        // (owner, 2026-09-10).
        val first = layout.visibleItemsInfo.firstOrNull()?.index ?: 0
        val above = if (row != null) row.offset < top else index < first
        if (above) {
            // Off the top: the row goes to the top edge, which for a step of one is a scroll of one.
            listState.animateScrollToItem(index)
        } else {
            // Off the bottom, or appended: the row goes to the bottom edge. A negative offset is
            // what puts it there rather than at the top -- the smallest move that reveals it.
            listState.animateScrollToItem(index, -(viewport - height).coerceAtLeast(0))
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
