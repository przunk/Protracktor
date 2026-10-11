// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.SavedPlaylist

data class PlayerUiState(
    /** Read model for Browse/Random only; not part of saved Browse navigation snapshots. */
    val listenedIds: Set<String> = emptySet(),
    val learnedTitles: Map<String, String> = emptyMap(),
    val queue: PlayQueue = PlayQueue(tracks = emptyList()),
    val playing: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    /**
     * Where a seek under way is going, or null (`SeekProgress`). A SID seeks by running its machine
     * there, for seconds; meanwhile the bar shows this, not where the engine still says it is.
     */
    val seekingTo: Double? = null,
    /** The seek has run long enough to say so: the spinner in place of the elapsed time. */
    val seekSlow: Boolean = false,
    /** Which tune inside the file is playing, counted from zero. */
    val subsong: Int = 0,
    /** How many tunes the file holds. One for a format that holds one. */
    val subsongCount: Int = 1,
    /**
     * Whether to play every tune inside a file rather than only the first.
     *
     * A playback mode like shuffle and repeat: global, remembered, and about what happens next
     * rather than about any one row. Off by default -- a file reporting 256 subsongs would
     * otherwise take over a listening session the first time one turned up.
     */
    val playAllSubsongs: Boolean = false,
    /**
     * How long to play a tune whose length nothing knows, in seconds (`docs/STATUS.md` C56).
     *
     * **Deliberately not folded into [durationSeconds].** That field is what the app *knows*, and a
     * guess written into it would be shown on screen as a fact and would make an unseekable tune
     * look seekable. This is the point at which the app stops listening, which is a different
     * statement from how long the tune is.
     */
    val fallbackLengthSeconds: Int = FallbackLength.DEFAULT_SECONDS,
    /** When an opened folder's tracks are fetched ahead (A55, D4). */
    val cacheAhead: CacheAhead = CacheAhead.DEFAULT,
    /** The database is being migrated or re-decided, and the screen says so (A60). */
    val preparingDatabase: Boolean = false,
    /** The longest a tune shared as audio runs, in minutes (A62). */
    val shareAudioMinutes: Int = AudioExport.DEFAULT_LIMIT_MINUTES,
    val scanning: Boolean = false,
    /** A track is being read. Shown, because on a network share this is seconds, not milliseconds. */
    val loadingTrack: Boolean = false,
    /**
     * Playing, but not part of the playlist.
     *
     * What Random produces: it plays first, and keeping it is a separate decision taken after
     * hearing it.
     */
    val transient: TrackRef? = null,
    /**
     * Whether [transient] came from outside the app — a file another app handed us.
     *
     * A transient track is not necessarily a random pick: a tune opened from a file manager also
     * plays outside the playlist. Next must not roll the dice for it, and the scrim over the
     * playlist must not say "Next picks another", because next picks nothing. One file arrived,
     * and that is all there is.
     */
    val externalOpen: Boolean = false,
    /**
     * Playing from search results rather than from the playlist.
     *
     * The results become the queue while you are in them: next and previous walk what you found,
     * and the playlist is left exactly as it was. Playing something you searched for must not
     * rewrite the list you were keeping.
     */
    val resultsQueue: PlayQueue? = null,
    /**
     * Whether [resultsQueue] is History's list (A56). Set where a results list is made -- tapping a
     * row, or walking into an author's folder -- and not where it is walked, so next and previous
     * keep it. Meaningless once [resultsQueue] is gone, and [HistoryRecording] never reads it then.
     */
    val resultsFromHistory: Boolean = false,
    /**
     * A Random session waiting under a digression (`docs/BACKLOG.md` A41).
     *
     * "More from this author" from the dice moves playback to that folder; the record and the
     * cursor stay where they are, and this says there is something to go back to. Back out of the
     * folder returns to it; a playlist, an external file or a link end it for good.
     */
    val diceWaiting: Boolean = false,
    /**
     * A search waiting under a digression: "More from this author" from its results, the owner's
     * "identically" to [diceWaiting] (2026-09-24). Back out of the author's folder returns to the
     * results, their words and their scope as they were.
     */
    val searchWaiting: Boolean = false,
    /**
     * Which list in Browse is playing, for the playlist's cover to name and lead back to (A61).
     * Meaningful while [searchMode] -- a list is what plays -- and set where a list starts playing.
     */
    val sessionSource: SessionSource? = null,
    /** Whether Random has anything behind it. Kept in state so the dock can grey the button. */
    val randomHasPrevious: Boolean = false,
    /**
     * What the dice has actually given this session, oldest first.
     *
     * **Only what has played.** `READ_AHEAD` picks stand past the cursor at all times so a tune can
     * be fetched before it is wanted, and those are a fetching strategy rather than a promise —
     * showing them would turn a record into a schedule, and a schedule the dice is free to discard
     * whenever the scope changes (`docs/PLAN_RANDOM.md`).
     */
    val randomPicks: List<TrackRef> = emptyList(),
    /** Which of [randomPicks] is playing, or -1. */
    val randomIndex: Int = -1,
    /**
     * True when the dice looked and found nothing to pick from.
     *
     * The message says why — nothing indexed, or nothing indexed for that platform — but a message
     * is a snackbar and goes. Without this the Random view would sit on "Rolling…" for ever, which
     * is a screen lying about what it is doing.
     */
    val randomExhausted: Boolean = false,
    val playlists: List<SavedPlaylist> = emptyList(),
    val activePlaylistId: Long = 0L,
    /** False until the stored state has been read. Saving before then would erase it. */
    val restored: Boolean = false,
    /**
     * The playlist on screen differs from the one on disk.
     *
     * Adding and removing edit a working copy; nothing is written until the user says so. Leaving
     * without saving therefore loses the edits, which the UI has to say out loud.
     */
    val dirty: Boolean = false,
    /** Shown to the user and cleared when acknowledged. Silence after a press is a defect. */
    val message: Message? = null,
) {
    val current: TrackRef? get() = transient ?: resultsQueue?.current ?: queue.current

    /**
     * Whether the current backend can move to a position at all.
     *
     * Asked, not assumed. sc68 emulates a 68000 and has no way back except running the machine
     * again from the start, so offering a slider there would be offering a control that cannot be
     * honoured (docs/ARCHITECTURE.md §5).
     */
    /**
     * How far the seek bar runs: the length, or where playback will stop when nothing knows the
     * length (`BarLength`, the owner's variant (a)).
     */
    val bar: BarLength.Bar
        get() = if (metadata.isEmpty()) {
            // Not yet described: nothing is known, not even that the length is unknown. Without
            // this the bar would flash the fallback for the moment a tune takes to open.
            BarLength.Bar(0.0, approximate = false)
        } else {
            BarLength.of(durationSeconds, metadata["ends_at"]?.toDoubleOrNull(), fallbackLengthSeconds.toDouble())
        }

    val seekable: Boolean get() = metadata["seekable"] != "0" && current != null && bar.seconds > 0.0

    /**
     * The year the tune was released, or empty when nothing in the file says.
     *
     * Derived rather than stored, like [seekable] above: it is a reading of the metadata that is
     * already here, and a copy would only be a second thing to keep in step. See [ReleaseYear] for
     * why it takes a parser -- five backends record the year five ways and only one of them
     * records a year.
     */
    val releaseYear: String get() = ReleaseYear.from(metadata)

    /**
     * What the transport can do right now.
     *
     * Asked of the mode rather than always of the queue: during Random the buttons walk the random
     * history, and a queue that happens to be empty must not grey them out.
     */
    /**
     * Whether **next** would do anything — a tune inside the file, or the next file.
     *
     * The subsong half was missing, and it made the transport disagree with itself: on the last
     * track of a playlist `next()` would step to subsong two and the button that calls it was
     * disabled, so a file with 256 tunes in it could only be walked from the Now Playing strip.
     * The notification reads the same value, so it was wrong there too.
     */
    val canGoNext: Boolean
        get() = (playAllSubsongs && subsong + 1 < subsongCount) || canGoNextFile

    val canGoPrevious: Boolean
        get() = (playAllSubsongs && subsong > 0) || canGoPreviousFile

    /** Whether there is another **file** — what a long press on next asks for. */
    val canGoNextFile: Boolean
        get() = when {
            externalMode -> false
            randomMode -> true
            searchMode -> resultsQueue?.hasNext == true
            else -> queue.hasNext
        }

    val canGoPreviousFile: Boolean
        get() = when {
            externalMode -> false
            randomMode -> randomHasPrevious
            searchMode -> resultsQueue?.hasPrevious == true
            else -> queue.hasPrevious
        }

    val activePlaylistName: String?
        get() = playlists.firstOrNull { it.id == activePlaylistId }?.name

    /** True while Random is driving playback rather than the playlist. */
    val randomMode: Boolean get() = transient != null && !externalOpen

    /** True while a file handed to us by another app is playing. */
    val externalMode: Boolean get() = transient != null && externalOpen

    /**
     * True while search results are driving playback rather than the playlist.
     *
     * **A queue left behind is not a queue playing.** Playing a dice pick does not clear the list
     * Browse was last played from, so `resultsQueue != null` alone answered yes while the dice was
     * the only source — and the Random view, which closes itself when results take over
     * (`docs/STATUS.md` C49), closed in the frame it opened.
     *
     * The condition is the one [nextFile] already applies: a transient tune owns the transport
     * unless the dice is waiting under a list, in which case the list owns it and this is a
     * digression (`docs/BACKLOG.md` A41).
     */
    val searchMode: Boolean get() = resultsQueue != null && (transient == null || diceWaiting)

    /** True whenever what is playing did not come from the active playlist. */
    val awayFromPlaylist: Boolean get() = transient != null || searchMode
}
