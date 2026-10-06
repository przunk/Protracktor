// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The played record and invisible read-ahead share storage, but not the same boundary. */
internal class RandomHistory {
    private val tracks = mutableListOf<TrackRef>()
    private val fillMutex = Mutex()
    var cursor: Int = -1
        private set
    private var played = -1
    private var freshPickPending = false
    var generation: Long = 0
        private set
    private val nextIndex: Int get() = if (freshPickPending) played + 1 else cursor + 1

    val current: TrackRef? get() = tracks.getOrNull(cursor)
    val playedPicks: List<TrackRef> get() = tracks.take(played + 1)
    val hasNext: Boolean get() = nextIndex <= tracks.lastIndex

    fun clear() {
        generation++
        tracks.clear()
        cursor = -1
        played = -1
        freshPickPending = false
    }

    fun select(index: Int): TrackRef? {
        if (index < 0 || index > played || index > tracks.lastIndex) return null
        generation++
        cursor = index
        freshPickPending = false
        return current
    }

    fun previous(): TrackRef? = if (cursor > 0) select(cursor - 1) else null

    fun remove(index: Int) {
        if (index < 0 || index > played || index > tracks.lastIndex) return
        generation++
        tracks.removeAt(index)
        if (index <= cursor) cursor--
        played--
    }

    fun reroll() {
        changeScope()
        freshPickPending = true
    }

    fun changeScope() {
        generation++
        // The cursor can be anywhere in the heard record. Only its unplayed tail is disposable.
        tracks.subList(played + 1, tracks.size).clear()
        freshPickPending = false
    }

    private fun needed(ahead: Int): Int = (ahead - (tracks.size - played - 1)).coerceAtLeast(0)

    private fun append(picks: List<TrackRef>) { tracks += picks }

    fun advance(): TrackRef? {
        if (nextIndex > tracks.lastIndex) return null
        generation++
        cursor = nextIndex
        played = maxOf(played, cursor)
        freshPickPending = false
        return current
    }

    fun upcoming(count: Int): List<TrackRef> = tracks.drop(nextIndex).take(count)

    /** A result from a superseded scope, cursor or session must never enter the live record. */
    suspend fun fill(ahead: Int, overdraw: Int, sample: suspend (Int) -> List<TrackRef>) {
        val requestedAt = generation
        // A foreground Next may arrive while background read-ahead is still querying.
        fillMutex.withLock {
            if (requestedAt != generation) return
            val missing = needed(ahead)
            if (missing == 0) return
            val seen = tracks.mapTo(mutableSetOf()) { it.id }
            val candidates = sample(missing * overdraw)
            if (requestedAt != generation) return
            val unique = candidates.filter { seen.add(it.id) }.take(missing)
            val picks = unique.ifEmpty { sample(missing).take(missing) }
            // The small-pool fallback suspends too.
            if (requestedAt != generation) return
            append(picks)
        }
    }
}
