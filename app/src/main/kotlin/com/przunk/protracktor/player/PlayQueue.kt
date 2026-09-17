// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
    /** Bytes, where the source knows. 0 means unknown. Part of [sameFileAs]. */
    val sizeBytes: Long = 0,
    /**
     * The file's own name, kept separately from [title].
     *
     * [title] is what the user should read, which is the tune's real name once we have opened it
     * and found one. Until then the two are the same. Keeping the filename regardless means the
     * metadata view can still say where a track came from, and search can still match on it --
     * people remember `4mat-elysium.mod` even when the tune calls itself something else.
     */
    val fileName: String = "",
    /**
     * Who made it, where that is known.
     *
     * Taken from the tune's metadata once it has been played. Until then the folder usually says
     * it -- this music is filed by author far more often than it is tagged with one -- so the
     * display falls back to the source folder rather than to nothing.
     */
    val author: String = "",
    /**
     * How many tunes the file holds, where that is already known.
     *
     * Known for a **scanned local file** — the scan opened it and the index kept the answer — and
     * **1 for everything else** until it has been played, because finding out means opening the
     * file and a catalogue's index is a list of names on somebody else's server. So this is a hint
     * for the list, never the authority: the authority is what the backend says once the file is
     * open (`docs/BACKLOG.md` A2).
     */
    val subsongs: Int = 1,
) {
    /**
     * Whether two references point at the same actual file.
     *
     * The id alone is not enough. The storage access framework hands out a **different** document
     * URI for the same file depending on how it was reached -- a folder grant and an individual
     * pick produce different strings -- so a playlist would happily hold the same track twice. Name
     * and size together settle it for anything local, and for a remote track the URL is already
     * unique.
     */
    /** The filename, falling back to the title for references made before it was recorded. */
    val fileNameOrTitle: String get() = fileName.ifBlank { title }

    /** What to show as the author: the tag if there is one, otherwise the folder it came from. */
    val displayAuthor: String
        get() = author.ifBlank { subtitle.substringAfterLast('/').substringAfterLast(" · ") }

    fun sameFileAs(other: TrackRef): Boolean = when {
        id == other.id -> true
        sizeBytes > 0 && other.sizeBytes > 0 ->
            // The FILE name, not the title: a title can be rewritten from metadata after the fact,
            // and identity must not change under a track that is already in a playlist.
            sizeBytes == other.sizeBytes &&
                fileNameOrTitle.equals(other.fileNameOrTitle, ignoreCase = true)
        else -> false
    }
}

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
    /** Fixes the shuffled permutation. A new value is a new shuffle. */
    private val shuffleSeed: Long = 0L,
    private val history: List<Int> = emptyList(),
    private val historyCursor: Int = -1,
) {
    /**
     * The playback order, **derived** rather than stored.
     *
     * A constructor property defaulting to `tracks.indices` would be a trap: `copy()` does not
     * re-evaluate default arguments, so `copy(tracks = …)` on a queue built empty would carry the
     * empty order forward and the first call to next() would index into nothing. Deriving it makes
     * the two impossible to disagree.
     */
    private val order: List<Int> by lazy(LazyThreadSafetyMode.NONE) {
        if (shuffle) tracks.indices.shuffled(kotlin.random.Random(shuffleSeed)) else tracks.indices.toList()
    }

    /** Index into [tracks] of what is playing, or `null` before anything has started. */
    val currentIndex: Int? get() = history.getOrNull(historyCursor)

    val current: TrackRef? get() = currentIndex?.let(tracks::getOrNull)

    /** True when [previous] would move somewhere. */
    val hasPrevious: Boolean get() = previousQueue() != null

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
     * What [next] would land on, without moving.
     *
     * Exists so the player can start loading the following track while this one plays, which is
     * what R9 actually needs: the wait is a read, not a decode.
     */
    val upcoming: TrackRef? get() = if (hasNext) next().current else null

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

    /**
     * Goes back one.
     *
     * **What "back" means depends on shuffle**, and getting this wrong is what made the controls
     * feel random. With shuffle on, back returns to the track that was actually played before --
     * that is R5, and there is no other sensible reading of "previous" in a random order. With
     * shuffle off, back means the row above, every time: the list is right there on screen and a
     * button that disagrees with it looks broken, however defensible its bookkeeping.
     */
    fun previous(): PlayQueue = previousQueue() ?: this

    private fun previousQueue(): PlayQueue? {
        if (shuffle) return if (historyCursor > 0) copy(historyCursor = historyCursor - 1) else null

        val position = order.indexOf(currentIndex ?: return null)
        return when {
            position > 0 -> appended(order[position - 1])
            repeat == RepeatMode.PLAYLIST -> order.lastOrNull()?.let { appended(it) }
            else -> null
        }
    }

    fun withRepeat(mode: RepeatMode): PlayQueue = copy(repeat = mode)

    /**
     * Turns shuffle on or off.
     *
     * The current track keeps playing either way: reordering what comes next is not a reason to
     * interrupt what is playing now. History is left alone, so backward still walks the tracks that
     * were really played, across the change.
     */
    fun withShuffle(on: Boolean, seed: Long = kotlin.random.Random.nextLong()): PlayQueue =
        if (on == shuffle) this else copy(shuffle = on, shuffleSeed = seed)

    /**
     * Replaces the track list.
     *
     * History is remapped **by track identity, not by position**. Removing a track shifts every
     * later index by one, so history kept as raw positions would silently start pointing at the
     * neighbours of what was really played -- the same class of bug as the order that copy() left
     * behind, and just as invisible on screen. Entries whose track is gone are dropped, and the
     * cursor moves with them.
     */
    fun withTracks(newTracks: List<TrackRef>): PlayQueue {
        val newIndexById = newTracks.withIndex().associate { (index, track) -> track.id to index }
        val remapped = mutableListOf<Int>()
        var newCursor = -1

        history.forEachIndexed { position, oldIndex ->
            val newIndex = tracks.getOrNull(oldIndex)?.id?.let(newIndexById::get) ?: return@forEachIndexed
            remapped += newIndex
            if (position <= historyCursor) newCursor = remapped.lastIndex
        }

        return copy(tracks = newTracks, history = remapped, historyCursor = newCursor)
    }

    private fun advanced(): PlayQueue? {
        if (tracks.isEmpty()) return null

        // Nothing started yet: begin at the front of the order.
        val currentIdx = currentIndex ?: return order.firstOrNull()?.let {
            copy(history = listOf(it), historyCursor = 0)
        }

        // Forward through history first -- this is what makes "back then forward" land where the
        // user left off rather than somewhere new. Only under shuffle: in list order, next means
        // the row below, and a redo branch there would contradict what the screen is showing.
        if (shuffle && historyCursor < history.lastIndex) return copy(historyCursor = historyCursor + 1)

        val positionInOrder = order.indexOf(currentIdx)
        val nextPosition = positionInOrder + 1
        if (nextPosition < order.size) return appended(order[nextPosition])

        return when (repeat) {
            RepeatMode.PLAYLIST -> {
                // A fresh permutation each lap: replaying the same shuffled order forever is not
                // what anyone means by shuffle.
                val lap = if (shuffle) copy(shuffleSeed = kotlin.random.Random.nextLong()) else this
                lap.order.firstOrNull()?.let {
                    lap.copy(history = history + it, historyCursor = historyCursor + 1)
                }
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
