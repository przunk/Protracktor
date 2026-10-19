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
    val stereoMix: StereoMix = StereoMix.ORIGINAL,
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
    /** The only playback owner; queue remains the selected playlist's editable draft. */
    val playback: ActiveQueue? = null,
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
    /** Legacy persistence name: the selected editor playlist, not necessarily the playback owner. */
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
    /** An explicit notification save is pending; membership is not a persistence confirmation. */
    val notificationPlaylistSaving: Boolean = false,
    /** Shown to the user and cleared when acknowledged. Silence after a press is a defect. */
    val message: Message? = null,
) {
    /** A selected source playlist stays live; leaving its view captures its current order. */
    val activePlayback: ActiveQueue
        get() = playback?.let { if (it.belongsTo(activePlaylistId)) it.copy(order = queue) else it }
            ?: ActiveQueue(QueueSource.Playlist(activePlaylistId, activePlaylistName.orEmpty()), queue)
    val transportQueue: PlayQueue get() = activePlayback.order
    val current: TrackRef? get() = activePlayback.current
    val transient: TrackRef? get() = current.takeIf { randomMode || externalMode }
    val externalOpen: Boolean get() = externalMode
    val resultsQueue: PlayQueue? get() = transportQueue.takeIf { searchMode }
    val resultsFromHistory: Boolean get() = activePlayback.fromHistory
    val sessionSource: SessionSource? get() = (activePlayback.source as? QueueSource.Collection)?.origin
    val randomHasPrevious: Boolean get() = randomMode && activePlayback.hasPrevious

    fun withPlayback(next: ActiveQueue): PlayerUiState = copy(
        playback = next,
        queue = if (next.belongsTo(activePlaylistId)) next.order else queue,
    )

    fun preparingRandom(session: Long): PlayerUiState = withPlayback(ActiveQueue(
        QueueSource.Random(session),
        PlayQueue(emptyList(), shuffle = transportQueue.shuffle, repeat = transportQueue.repeat),
    )).copy(randomPicks = emptyList(), randomIndex = -1, randomExhausted = false,
        playing = false, loadingTrack = true, positionSeconds = 0.0, durationSeconds = 0.0,
        metadata = emptyMap())

    /** Labels may improve without changing playback identity, order or the selected source. */
    fun withLearnedTrack(improved: TrackRef): PlayerUiState {
        fun enrich(rows: List<TrackRef>) = rows.map { if (it.id == improved.id) improved else it }
        val active = activePlayback
        return copy(
            queue = queue.withTracks(enrich(queue.tracks)),
            playback = active.copy(order = active.order.withTracks(enrich(active.order.tracks))),
        )
    }

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
    val canGoNextFile: Boolean get() = activePlayback.hasNext
    val canGoPreviousFile: Boolean get() = activePlayback.hasPrevious

    val activePlaylistName: String?
        get() = playlists.firstOrNull { it.id == activePlaylistId }?.name

    val randomMode: Boolean get() = activePlayback.source is QueueSource.Random
    val externalMode: Boolean get() = activePlayback.source == QueueSource.External
    val searchMode: Boolean get() = activePlayback.source is QueueSource.Collection
    val awayFromPlaylist: Boolean get() = !activePlayback.belongsTo(activePlaylistId)

}
