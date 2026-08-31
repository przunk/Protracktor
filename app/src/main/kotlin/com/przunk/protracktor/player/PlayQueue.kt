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

/** What a repeat control cycles through. */
enum class RepeatMode {
    /** Stop at the end of the playlist. */
    OFF,

    /** Wrap around to the start. Reshuffles first if shuffle is on. */
    PLAYLIST,

    /** Replay the current track when it ends. Does not change what the next button does. */
    ONE,
    ;

    fun next(): RepeatMode = when (this) {
        OFF -> PLAYLIST
        PLAYLIST -> ONE
        ONE -> OFF
    }
}

/** Something playable, identified by whatever its source uses as an address. */
data class TrackRef(
    val id: String,
    val title: String,
    val subtitle: String = "",
)

/**
 * The play order and the position in it.
 *
 * The point of this class is R5: **backward returns to the track that was actually played before**,
 * not to another random one. That requires remembering what was played, so the model here is a
 * browser's: a history with a cursor. Going back moves the cursor; going forward moves it again if
 * there is history ahead, and only picks something new once the cursor is at the end.
 *
 * Immutable, because it is exactly the kind of state that gets restored (R2) and compared, and a
 * remembered version that mutates underneath is not a remembered version.
 *
 * Nothing here touches Android, on purpose. It is the part most worth testing and there is no
 * emulator in this environment.
 */
data class PlayQueue(
    val tracks: List<TrackRef>,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    private val order: List<Int> = tracks.indices.toList(),
    private val history: List<Int> = emptyList(),
    private val historyCursor: Int = -1,
) {
    /** Index into [tracks] of what is playing, or `null` before anything has started. */
    val currentIndex: Int? get() = history.getOrNull(historyCursor)

    val current: TrackRef? get() = currentIndex?.let(tracks::getOrNull)

    /** True when [previous] would move somewhere. */
    val hasPrevious: Boolean get() = historyCursor > 0

    /**
     * True when [next] would move somewhere. This goes false on the last track unless repeat is
     * [RepeatMode.PLAYLIST], which is what lets the UI disable the control visibly instead of
     * offering a button that does nothing (AGENTS.md §7).
     */
    val hasNext: Boolean get() = advanced() != null

    /** Starts (or restarts) at a specific track, discarding any forward history. */
    fun startAt(index: Int): PlayQueue {
        require(index in tracks.indices) { "no track at $index (size ${tracks.size})" }
        return copy(history = history.take(historyCursor + 1) + index, historyCursor = historyCursor + 1)
    }

    /**
     * The user pressed next. Returns the same queue when there is nowhere to go.
     *
     * [RepeatMode.ONE] deliberately does **not** apply here. It governs what happens when a track
     * reaches its end, not what the next button does -- otherwise next would be a button that
     * visibly does nothing, and there would be no way out of a repeating track except turning the
     * mode off first. See [onTrackEnded].
     */
    fun next(): PlayQueue = advanced() ?: this

    /**
     * The current track reached its end on its own.
     *
     * Returns the queue whose [current] the caller should now play from the start, or `null` when
     * playback should stop. Under [RepeatMode.ONE] that is this same queue -- the track plays
     * again without a new history entry, because twenty loops of one track are one thing the user
     * chose, not twenty things to step back through.
     */
    fun onTrackEnded(): PlayQueue? =
        if (repeat == RepeatMode.ONE && current != null) this else advanced()

    /** Steps back through what was actually played. Returns the same queue at the beginning. */
    fun previous(): PlayQueue =
        if (hasPrevious) copy(historyCursor = historyCursor - 1) else this

    fun withRepeat(mode: RepeatMode): PlayQueue = copy(repeat = mode)

    /**
     * Turns shuffle on or off.
     *
     * The current track keeps playing either way: reordering what comes next is not a reason to
     * interrupt what is playing now. History is left alone, so backward still walks the tracks that
     * were really played, across the change.
     */
    fun withShuffle(on: Boolean, random: kotlin.random.Random = kotlin.random.Random.Default): PlayQueue =
        if (on == shuffle) this else copy(shuffle = on, order = buildOrder(on, random))

    private fun buildOrder(on: Boolean, random: kotlin.random.Random): List<Int> =
        if (on) tracks.indices.shuffled(random) else tracks.indices.toList()

    private fun advanced(): PlayQueue? {
        if (tracks.isEmpty()) return null

        // Nothing started yet: begin at the front of the order.
        val currentIdx = currentIndex ?: return copy(
            history = listOf(order.first()),
            historyCursor = 0,
        )

        // Forward through history first -- this is what makes "back then forward" land where the
        // user left off rather than somewhere new.
        if (historyCursor < history.lastIndex) return copy(historyCursor = historyCursor + 1)

        val positionInOrder = order.indexOf(currentIdx)
        val nextPosition = positionInOrder + 1
        if (nextPosition < order.size) return appended(order[nextPosition])

        return when (repeat) {
            RepeatMode.PLAYLIST -> {
                // A fresh permutation each lap: replaying the same shuffled order forever is not
                // what anyone means by shuffle.
                val wrapped = if (shuffle) tracks.indices.shuffled() else tracks.indices.toList()
                copy(order = wrapped, history = history + wrapped.first(), historyCursor = historyCursor + 1)
            }
            // ONE repeats a track, not a playlist. At the end of the playlist it stops, like OFF.
            RepeatMode.OFF, RepeatMode.ONE -> null
        }
    }

    // Truncates the forward branch, the way startAt does. Reaching a genuinely new track after
    // stepping back abandons whatever was ahead, and appending without truncating would leave the
    // cursor pointing at a stale entry rather than at what was just added -- an inconsistency that
    // happens to be unreachable today only because the redo branch above runs first.
    private fun appended(index: Int): PlayQueue =
        copy(history = history.take(historyCursor + 1) + index, historyCursor = historyCursor + 1)
}
