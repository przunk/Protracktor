// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.BrowseState

/**
 * Where you were on each level of Browse, for as long as Browse is open.
 *
 * `docs/BACKLOG.md` A20: going into an author's folder and pressing back put you at the **top** of
 * the author list, whatever row you had come out of. The cause was that Browse kept no scroll state
 * at all -- every level was the same `LazyColumn` call site recomposed with different data, so the
 * position survived a level change by accident and was then clamped to whatever the shorter list
 * could hold.
 *
 * Two halves, and the second is the one that is easy to leave out:
 *
 * 1. **Each level keeps its own position.** One [LazyListState] per level key, so descending and
 *    returning restores the offset rather than inheriting somebody else's.
 * 2. **Coming back puts the row you came *from* on screen.** An offset stops naming the same
 *    place once the list underneath has changed -- a rescan, a re-index, a different sort. The
 *    row's own key does not, so that is what is remembered and what is looked for first.
 *
 * Lives for the life of the Browse session and dies with it, which is deliberate: leaving Browse
 * and coming back should start at the top (`docs/STATUS.md` C6), and state outliving its screen is
 * how that goes wrong.
 */
@Stable
internal class BrowseScroll {

    private val states = mutableMapOf<String, LazyListState>()
    private val entered = mutableMapOf<String, String>()

    /** The scroll state for one level, created once and kept. */
    fun stateFor(key: String): LazyListState = states.getOrPut(key) { LazyListState() }

    /** Records which row was tapped to leave [key], so returning can find it again. */
    fun descendingFrom(key: String, rowKey: String) {
        entered[key] = rowKey
    }

    /** The row to return to on [key], if one is waiting. Does not consume it. */
    fun pendingReturn(key: String): String? = entered[key]

    /** Forgets the pending return for [key] — once it has been used, or once it cannot be. */
    fun returned(key: String) {
        entered.remove(key)
    }

    /**
     * What a level should do about its pending return, given the list it currently has.
     *
     * A decision rather than an action, so it can be tested: the composable that acts on it needs
     * Compose and there is no emulator here.
     */
    fun restoreFor(key: String, rowKeys: List<String>, loading: Boolean): Restore {
        val target = entered[key] ?: return Restore.Nothing

        // **The list on screen may still belong to the level below.** Going back sets the new level
        // immediately and fetches its contents asynchronously, so for a moment the key says
        // "formats" while the list still holds authors. Reading that as "the row is gone" threw the
        // marker away and the real list arrived with nothing left to restore -- which is exactly
        // why back restored one level and landed at the top on the next.
        if (loading || rowKeys.isEmpty()) return Restore.Wait

        val index = rowKeys.indexOf(target)
        // Absent from a list that genuinely belongs here means the row really has gone -- a rescan,
        // a re-index, a deletion. Forget it, or a stale marker jumps the list on some later visit.
        return if (index >= 0) Restore.ScrollTo(index) else Restore.Forget
    }
}

/** What [BrowseScroll.restoreFor] concluded. */
internal sealed interface Restore {
    /** No pending return for this level. */
    data object Nothing : Restore

    /** The list is not this level's yet. Ask again when it is. */
    data object Wait : Restore

    data class ScrollTo(val index: Int) : Restore

    /** The row is gone. Drop the marker. */
    data object Forget : Restore
}

@Composable
internal fun rememberBrowseScroll(): BrowseScroll = remember { BrowseScroll() }

/**
 * Which level of Browse this is.
 *
 * Built from the state rather than passed around, so a level cannot be given the wrong key by a
 * caller that forgot to update it. Two different authors are two different levels; the same author
 * reached twice is the same one.
 */
internal fun BrowseState.levelKey(): String = when (domain) {
    BrowseDomain.ROOT -> "root"
    BrowseDomain.LOCAL -> openFolder?.let { "local/${it.uri}" } ?: "local"
    BrowseDomain.ONLINE ->
        listOfNotNull("online", openCatalogue?.id, openFormat, openAuthor).joinToString("/")
    // The query is part of it: searching for something else is a different list, not a scrolled
    // one. So is the **scope** -- "mod" in Amiga and "mod" in Commodore 64 are two lists, and
    // without this the first one's scroll position is restored into the second. Quietly, and only
    // sometimes, which is the worst kind.
    BrowseDomain.SEARCH -> "search/${searchScope}/$query"
    // Each page its own place: the next hundred start at their top, and back returns to where
    // the last page was left.
    BrowseDomain.HISTORY -> "history/$historyPage"
}
