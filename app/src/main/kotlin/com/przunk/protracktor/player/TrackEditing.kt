/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.player

/**
 * Removing tracks, and putting them back.
 *
 * Separated from the controller so the one operation that can lose somebody's playlist has a test.
 * A bulk delete is **one edit, not twenty** (`docs/BACKLOG.md` A4), so undoing it must restore the
 * whole selection to the positions it came from — and "the positions it came from" stops being
 * obvious the moment more than one row goes at once.
 */
object TrackEditing {

    /** A track and the position it was taken from. */
    data class Removed(val index: Int, val track: TrackRef)

    /** What is left after taking [indices] out, and what was taken. */
    fun remove(tracks: List<TrackRef>, indices: Collection<Int>): Pair<List<TrackRef>, List<Removed>> {
        val doomed = indices.distinct().filter { it in tracks.indices }.sorted()
        if (doomed.isEmpty()) return tracks to emptyList()
        val taken = doomed.map { Removed(it, tracks[it]) }
        val kept = tracks.filterIndexed { i, _ -> i !in doomed.toSet() }
        return kept to taken
    }

    /**
     * Puts [removed] back where it came from.
     *
     * **Ascending order matters.** Inserting the lowest index first makes room for the next, so
     * every later insertion lands where it was taken from; going the other way puts each one a
     * place too early and the list comes back subtly wrong rather than obviously broken. Positions
     * are clamped because the list may have changed in between.
     */
    fun restore(tracks: List<TrackRef>, removed: List<Removed>): List<TrackRef> {
        val out = tracks.toMutableList()
        removed.sortedBy { it.index }.forEach { out.add(it.index.coerceIn(0, out.size), it.track) }
        return out
    }
}
