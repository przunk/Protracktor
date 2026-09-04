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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.os.Process
import android.text.format.DateUtils
import com.przunk.protracktor.R
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.data.LibraryStore
import com.przunk.protracktor.data.SavedPlayerState
import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueStore
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.CatalogueTrack
import com.przunk.protracktor.data.SavedPlaylist
import com.przunk.protracktor.data.SchemaSql
import com.przunk.protracktor.data.HistoryStore
import com.przunk.protracktor.data.LibraryIndexStore
import com.przunk.protracktor.data.PlaylistFile
import com.przunk.protracktor.data.IndexedFile
import com.przunk.protracktor.data.SongLengthStore
import com.przunk.protracktor.data.SongLengths
import com.przunk.protracktor.net.CacheBudget
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.RemoteFiles
import com.przunk.protracktor.engine.NativeEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Something to tell the user.
 *
 * Carries an id so that two identical texts in a row are two separate notices rather than one that
 * silently fails to reappear. [actionLabel] is the hook the undo work will hang on; nothing sets it
 * yet.
 */
data class Message(
    val text: String,
    val actionLabel: String? = null,
    val id: Long = nextMessageId(),
)

private var messageCounter = 0L

private fun nextMessageId(): Long = ++messageCounter

data class PlayerUiState(
    val queue: PlayQueue = PlayQueue(tracks = emptyList()),
    val playing: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
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
    val scanning: Boolean = false,
    /** A track is being read. Shown, because on a network share this is seconds, not milliseconds. */
    val loadingTrack: Boolean = false,
    /**
     * Playing, but not part of the playlist.
     *
     * What Random produces. The owner asked for it to play rather than land in the list, so that
     * keeping it is a decision he makes after hearing it -- which is the only order that makes
     * sense for something picked at random.
     */
    val transient: TrackRef? = null,
    /**
     * Playing from search results rather than from the playlist.
     *
     * The results become the queue while you are in them: next and previous walk what you found,
     * and the playlist is left exactly as it was. Playing something you searched for must not
     * rewrite the list you were keeping.
     */
    val resultsQueue: PlayQueue? = null,
    /** Whether Random has anything behind it. Kept in state so the dock can grey the button. */
    val randomHasPrevious: Boolean = false,
    val playlists: List<SavedPlaylist> = emptyList(),
    val activePlaylistId: Long = 0L,
    /** False until the stored state has been read. Saving before then would erase it. */
    val restored: Boolean = false,
    /**
     * The playlist on screen differs from the one on disk.
     *
     * Adding and removing edit a working copy; nothing is written until the user says so. That is
     * what the owner asked for -- a list you can rearrange without committing to it -- and it means
     * leaving without saving loses the edits, which the UI has to say out loud.
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
    val seekable: Boolean get() = metadata["seekable"] != "0" && durationSeconds > 0.0

    /**
     * What the transport can do right now.
     *
     * Asked of the mode rather than always of the queue: during Random the buttons walk the random
     * history, and a queue that happens to be empty must not grey them out.
     */
    val canGoNext: Boolean
        get() = when {
            randomMode -> true
            searchMode -> resultsQueue?.hasNext == true
            else -> queue.hasNext
        }

    val canGoPrevious: Boolean
        get() = when {
            randomMode -> randomHasPrevious
            searchMode -> resultsQueue?.hasPrevious == true
            else -> queue.hasPrevious
        }

    val activePlaylistName: String?
        get() = playlists.firstOrNull { it.id == activePlaylistId }?.name

    /** True while Random is driving playback rather than the playlist. */
    val randomMode: Boolean get() = transient != null

    /** True while search results are driving playback rather than the playlist. */
    val searchMode: Boolean get() = resultsQueue != null

    /** True whenever what is playing did not come from the active playlist. */
    val awayFromPlaylist: Boolean get() = randomMode || searchMode
}

/** Which part of Browse is on screen. Back moves one step towards [ROOT]. */
enum class BrowseDomain { ROOT, LOCAL, ONLINE, SEARCH, HISTORY }

/**
 * What the Browse screen is looking at.
 *
 * Separate from [PlayerUiState] because it is a different lifetime: browsing comes and goes while
 * playback does not, and folding it in would mean every scan tick recomposing the player.
 */
data class BrowseState(
    val domain: BrowseDomain = BrowseDomain.ROOT,
    val loading: Boolean = false,

    // Local files
    val folders: List<GrantedFolder> = emptyList(),
    val openFolder: GrantedFolder? = null,

    // Online catalogues
    val catalogues: List<CatalogueSummary> = emptyList(),
    val openCatalogue: CatalogueSummary? = null,
    val openFormat: String? = null,
    val openAuthor: String? = null,
    val groups: List<CatalogueGroup> = emptyList(),
    /** Non-null while an index is downloading; carries something to show the user. */
    val indexing: String? = null,
    /** Non-null while a folder is being scanned: files probed so far, and how many there are. */
    val scanProgress: Pair<Int, Int>? = null,
    /** True when the open folder has never been scanned. */
    val folderUnscanned: Boolean = false,
    /** True when the open folder's index was built by a different set of decoders. */
    val folderStale: Boolean = false,
    /**
     * True when this level was reached by "more from this author" rather than by browsing down to
     * it. Back has to undo the jump instead of walking up a hierarchy the user never walked down.
     */
    val arrivedByJump: Boolean = false,
    /** How many SID tunes HVSC has given us a length for. Zero until the database is downloaded. */
    val songLengthCount: Int = 0,
    /** Bytes in the fetched-file cache, and bytes in permanent downloads. */
    val storageBytes: Pair<Long, Long> = 0L to 0L,
    /** Bytes each downloaded catalogue archive holds, by catalogue id. Only what exists is listed. */
    val archiveBytes: Map<String, Long> = emptyMap(),
    /** Bytes the database file holds — playlists, history, and every index that is rows not a file. */
    val databaseBytes: Long = 0L,
    /** Which decoders this build has, for telling a stale catalogue index from a current one. */
    val backends: String = "",

    // What has been played
    val history: List<TrackRef> = emptyList(),

    // Search
    val query: String = "",
    val searchLocal: Boolean = true,
    val searchOnline: Boolean = true,
    val searchCatalogues: Set<String> = emptySet(),

    /** Whatever the current level lists, in the form the playlist takes. */
    val tracks: List<TrackRef> = emptyList(),
)

class PlaybackController private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: PlaybackController? = null

        fun get(context: Context): PlaybackController =
            instance ?: synchronized(this) {
                instance ?: PlaybackController(context.applicationContext).also { instance = it }
            }

        // Fast enough that a progress bar does not visibly step, slow enough to be free.
        private const val POLL_INTERVAL_MS = 200L
        private const val SAVE_DEBOUNCE_MS = 400L

        /** How often the background metadata pass looks to see whether the user has stopped. */
        private const val IDLE_CHECK_MS = 500L

        /** Between files, so resolving hundreds does not saturate a network share. */
        private const val RESOLVE_GAP_MS = 120L

        /**
         * How long the track list waits before being written.
         *
         * Longer than [RESOLVE_GAP_MS] by an order of magnitude, so a run of resolutions collapses
         * into one write rather than one write each.
         */
        private const val TRACK_WRITE_DEBOUNCE_MS = 1500L
        const val DEFAULT_PLAYLIST_NAME = "Playlist"

        /** Recognised by the UI, which turns it into the localised label on the snackbar action. */
        const val UNDO = "undo"

        /**
         * Which archive catalogue a reference belongs to, if any.
         *
         * Asked of the catalogue list rather than matched against one name: every archive catalogue
         * uses its own id as the scheme, so a hard-coded prefix here would need editing for each new
         * one -- and the one after that would be added without anybody noticing this line existed.
         */
        /**
         * Where HVSC's song lengths come from -- the collection's own site, re-checked 2026-09-02
         * and still serving the 5,205,150 bytes `docs/ARCHITECTURE.md` recorded on 2026-08-31.
         *
         * HVSC is distributed through mirrors and this address may one day stop answering;
         * `hvsc.brona.dk/HVSC/C64Music/DOCUMENTS/Songlengths.md5` served a byte-identical copy on
         * the same day and is the first place to look if it does. A dead URL here costs a message
         * about a failed download, not a crash and not a wrong duration.
         */
        private const val SONG_LENGTHS_URL =
            "https://www.hvsc.c64.org/download/C64Music/DOCUMENTS/Songlengths.md5"

        private const val SONG_LENGTHS_LABEL = "SID song lengths"

        private fun archiveCatalogueOf(id: String): Catalogue? =
            Catalogue.all.firstOrNull { it.isArchive && id.startsWith("${it.id}://") }

        /** Four megabytes. Comfortably above any tracker module and below anything worth holding. */
        private const val MAX_PREFETCH_BYTES = 4 * 1024 * 1024

        /** Total held for reading ahead. Three of the per-file limit would be twelve megabytes. */
        private const val MAX_PREFETCH_TOTAL_BYTES = 8 * 1024 * 1024

        /**
         * How far ahead Random decides and fetches.
         *
         * Three rather than "a few": each one is a file fetched for a tune that may never be
         * played, and on a metered connection that is the cost of the feature. Three covers the
         * gap between tracks at the speeds measured (`docs/ARCHITECTURE.md` §8) without turning one
         * listen into a handful of downloads.
         */
        private const val READ_AHEAD = 3
    }

    // Main.immediate so a press and the state change it causes land in the same frame; the work
    // itself moves to IO where it belongs.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * For work nobody asked for: identifying tracks in the background, and scanning a library.
     *
     * **`Dispatchers.IO` is the wrong tool for this.** Its threads run at default priority, so
     * opening a decoder — which is real CPU work, and for sc68 means building a 68000 emulator —
     * competes with the UI thread on equal terms. On a phone that is a list which stutters while
     * the work runs, which is what the owner reported for the first twenty seconds after launch.
     *
     * A single thread at `THREAD_PRIORITY_BACKGROUND` puts this in Android's background cgroup,
     * where it gets a small share of the processor and *cannot* starve drawing however long it
     * takes. One thread rather than a pool, because these tasks are sequential by nature and two of
     * them would only contend with each other.
     */
    private val backgroundWork = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "protracktor-background")
    }.asCoroutineDispatcher()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /**
     * "Look here" — a row index the playlist should scroll to.
     *
     * An event rather than state: scrolling somewhere is something that happens once, and a value
     * left sitting in state would scroll again on every recomposition that touched it. Extra buffer
     * capacity with DROP_OLDEST so an emit never suspends and never queues a stale destination.
     */
    private val _reveal = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val reveal: SharedFlow<Int> = _reveal.asSharedFlow()

    /**
     * Asks the UI to show Browse at whatever [BrowseState] now says.
     *
     * An event rather than a flag, because "show it" happens once. The browse sheet normally resets
     * to the top when it opens; this exists so a jump can put it somewhere first.
     */
    private val _showBrowse = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val showBrowse: SharedFlow<Unit> = _showBrowse.asSharedFlow()

    /**
     * A share the UI should put in front of the user.
     *
     * Emitted rather than started here: choosing an app is an activity, and this class holds the
     * application context. Preparing what is shared is work with a network fetch in it, and belongs
     * on this side; showing the chooser does not.
     */
    private val _share = MutableSharedFlow<Intent>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val share: SharedFlow<Intent> = _share.asSharedFlow()

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()

    /** The open module. Owned here because native memory is invisible to the garbage collector. */
    private var track: NativeEngine.Track? = null

    /**
     * HVSC's lengths for the open file, one per tune, or empty when it did not supply any.
     *
     * Kept because the backend cannot be asked twice. libsidplayfp reports no length at all — the
     * database is the only source — and a subsong switch used to clear the duration and wait for a
     * backend that would never answer, so tune two onwards showed no length and, once the player
     * started stopping at a known length, would have played for ever again.
     */
    private var openSongLengths: List<Double> = emptyList()

    /**
     * The in-flight open. Cancelled before a new one starts.
     *
     * Two quick presses of next used to start two audio streams at once: each press launched its
     * own open, and the second overwrote `track` without closing the first, which went on playing
     * with nobody holding it.
     */
    private var openJob: Job? = null

    /**
     * The next track's bytes, read while the current one plays.
     *
     * R9 -- "playback starts immediately" -- is a reading problem, not a decoding one. A module is
     * kilobytes and decodes in milliseconds; the wait the owner measured at five to thirty seconds
     * was opening the file, and his library sits on an SMB share where that means a network round
     * trip. One entry is enough: it is the next track people wait for.
     */
    /**
     * Tracks read ahead, keyed by reference id, in the order they were read.
     *
     * More than one because Random reads several ahead (`docs/BACKLOG.md` A11), and a single slot
     * could only ever hold the very next one.
     */
    private val prefetched = LinkedHashMap<String, ByteArray>()
    private var prefetchJob: Job? = null
    private var scanJob: Job? = null

    /** The background metadata pass. Cancelled and restarted whenever the track list changes. */
    private var resolveJob: Job? = null

    private val store = LibraryStore(context)
    private val catalogues = CatalogueStore(context)
    private val remoteFiles = RemoteFiles(context)
    private val songLengths = SongLengthStore(context)
    private val history = HistoryStore(context)
    private val libraryIndex = LibraryIndexStore(context)

    private val audioFocus = AudioFocus(
        context = context,
        onPause = { pause() },
        onDuck = { gain -> track?.setGain(gain) },
    )

    /**
     * What Random has played, and where in it we are.
     *
     * Random needs its own history: "previous" during Random has to mean the previous random pick,
     * not the previous row of a playlist the user is not listening to.
     */
    private val randomHistory = mutableListOf<TrackRef>()
    private var randomCursor = -1

    /**
     * How far into [randomHistory] has actually been played. Anything past it was picked ahead and
     * never heard -- which is the difference between history and speculation, and the dice needs it.
     */
    private var randomPlayed = -1

    /** Which playlist is being edited. Resolved during [restore]; there is only one so far. */
    private var playlistId: Long = 0L

    /** Debounces writes. Every transport press changes state; the disk does not need each one. */
    private var saveJob: Job? = null
    private var trackWriteJob: Job? = null

    init {
        // Before anything can be opened: sc68 reads its replay binaries from a path, and an asset
        // inside an APK does not have one.
        // Once at start-up, so an installation that grew past the ceiling before the limit existed
        // converges on it instead of staying over forever. Nothing is in use yet, which is exactly
        // why this is the cheapest moment to do it.
        scope.launch(Dispatchers.IO) { runCatching { remoteFiles.enforceBudget() } }

        scope.launch(Dispatchers.IO) {
            runCatching {
                val version = context.packageManager
                    .getPackageInfo(context.packageName, 0).longVersionCode.toString()
                NativeEngine.setDataPath(
                    com.przunk.protracktor.engine.NativeData.ensureUnpacked(context, version).absolutePath
                )
            }
        }

        restore()
        // One loop drives both the progress bar and end-of-track handling. The native side flags
        // completion rather than calling back, so something has to look; since the progress bar
        // needs a tick anyway, this is that tick.
        scope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val open = track ?: continue
                if (!_state.value.playing) continue

                if (open.isFinished()) {
                    handleTrackEnded()
                    continue
                }

                val position = open.positionSeconds()
                _state.update {
                    it.copy(
                        positionSeconds = position,
                        // Picked up here because a subsong switch is applied on the audio
                        // thread: the new tune's length does not exist until it has been.
                        durationSeconds = if (it.durationSeconds <= 0.0) {
                            open.durationSeconds()
                        } else {
                            it.durationSeconds
                        },
                    )
                }

                // **A tune that never ends still ends when we know how long it is.**
                //
                // `isFinished` is set by the engine when a backend renders a short buffer, and some
                // never do: libsidplayfp is running a 6502 in a loop and has no idea the music is
                // over, so a SID played until the user pressed something. Since HVSC started
                // supplying lengths the app *knows* the answer and was not acting on it — the
                // owner's observation on 2026-09-04, and it applies to every format, not just SID.
                //
                // Whatever supplied the duration is trusted to be right: HVSC for SID, sc68's
                // database for SNDH, the file itself elsewhere. A wrong entry cuts a tune short,
                // which is the same trade every player using these databases makes, and the
                // alternative is the one being fixed — playing for ever.
                //
                // `handleTrackEnded` and not something of its own, so repeat, shuffle, subsongs and
                // Random all behave exactly as they do at a real end of tune.
                val known = _state.value.durationSeconds
                if (known > 0.0 && position >= known) handleTrackEnded()
            }
        }
    }

    // --- persistence --------------------------------------------------------------------------

    private fun restore() {
        scope.launch {
            val saved = store.loadPlayerState()
            val known = store.playlists()
            // The stored active playlist, unless it has since been deleted.
            playlistId = known.firstOrNull { it.id == saved?.activePlaylistId }?.id
                ?: store.defaultPlaylistId(DEFAULT_PLAYLIST_NAME)
            val playlists = store.playlists()
            val tracks = store.tracksIn(playlistId)

            _state.update { current ->
                var queue = current.queue
                    .withTracks(tracks)
                    .withRepeat(saved?.repeat ?: RepeatMode.OFF)
                if (saved?.shuffle == true) queue = queue.withShuffle(true)

                // The track it was on is made current but NOT started. Coming back to the app is
                // not a request to make noise, and R2 asks for the view to be restored, not the
                // playback (docs/OPEN_QUESTIONS.md Q6 is still open on resuming a position).
                val index = tracks.indexOfFirst { it.id == saved?.currentTrackId }
                if (index >= 0) queue = queue.startAt(index)

                current.copy(
                    queue = queue,
                    playlists = playlists,
                    activePlaylistId = playlistId,
                    playAllSubsongs = saved?.playAllSubsongs ?: false,
                    restored = true,
                )
            }
            resolveMetadataInBackground()
        }
    }

    /**
     * Writes the track list, shortly.
     *
     * **Debounced, and it has to be.** `replaceTracks` deletes every row of the playlist and
     * reinserts it — two inserts per track — and background metadata resolution used to call it
     * once per track it identified, every 120 ms. On a playlist of three hundred that is some six
     * hundred inserts eight times a second, into the same database the list is being read from, for
     * as long as the resolution runs. The owner reported it as the list stuttering for the first ten
     * to twenty seconds after launch, and was right that it had nothing to do with the scrollbar he
     * had just been given.
     *
     * The state still updates per track, so titles appear as they are learned. It is only the disk
     * that waits.
     */
    private fun scheduleTrackWrite() {
        trackWriteJob?.cancel()
        trackWriteJob = scope.launch {
            delay(TRACK_WRITE_DEBOUNCE_MS)
            store.replaceTracks(playlistId, _state.value.queue.tracks)
        }
    }

    /**
     * Writes the current state, shortly.
     *
     * Debounced because every press of shuffle, repeat, next and previous changes something worth
     * keeping, and none of them is worth a disk write on its own.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveNow()
        }
    }

    /**
     * Writes the settings, never the track list.
     *
     * Shuffle, repeat and where playback got to are the app's own business and are saved as they
     * change. The track list belongs to the user's editing session and waits for [savePlaylist].
     */
    private suspend fun saveNow() {
        if (!_state.value.restored) return // never overwrite stored state with an empty start-up one
        val snapshot = _state.value
        store.savePlayerState(
            SavedPlayerState(
                activePlaylistId = playlistId,
                // The queue's track, never a transient one: a random pick is not where the user
                // was, and restoring into it would be restoring somewhere they never chose to be.
                currentTrackId = snapshot.queue.current?.id,
                shuffle = snapshot.queue.shuffle,
                repeat = snapshot.queue.repeat,
                playAllSubsongs = snapshot.playAllSubsongs,
            )
        )
    }

    /** Commits the edited list. The diskette button. */
    fun savePlaylist() {
        scope.launch {
            val snapshot = _state.value
            store.replaceTracks(playlistId, snapshot.queue.tracks)
            // Saved silently. The Save button disappearing is the confirmation, and it is on
            // screen already.
            _state.update { it.copy(dirty = false) }
        }
    }

    /** Throws the edits away and goes back to what is on disk. */
    fun discardChanges() {
        scope.launch {
            val stored = store.tracksIn(playlistId)
            _state.update {
                it.copy(queue = it.queue.withTracks(stored), dirty = false, message = Message("Changes discarded."))
            }
        }
    }

    // --- playlists ----------------------------------------------------------------------------

    fun createPlaylist(name: String) {
        scope.launch {
            val id = store.createPlaylist(name.ifBlank { DEFAULT_PLAYLIST_NAME })
            _state.update { it.copy(playlists = store.playlists()) }
            switchToPlaylist(id)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        scope.launch {
            store.renamePlaylist(id, name)
            _state.update { it.copy(playlists = store.playlists()) }
        }
    }

    /**
     * Deletes a playlist. The tracks stay in the library; only the list goes.
     *
     * The last playlist cannot be deleted -- an app with nowhere to put anything is a state with no
     * way out, and "delete" is not a request to be stranded.
     */
    fun deletePlaylist(id: Long) {
        scope.launch {
            if (store.playlists().size <= 1) {
                _state.update { it.copy(message = Message("The last playlist cannot be deleted.")) }
                return@launch
            }
            store.deletePlaylist(id)
            val remaining = store.playlists()
            _state.update { it.copy(playlists = remaining) }
            if (id == playlistId) switchToPlaylist(remaining.first().id)
        }
    }

    /**
     * Makes another playlist the active one.
     *
     * Playback stops. The queue *is* the active playlist, so carrying a playing track into a list
     * that does not contain it would leave next and previous disagreeing with the screen -- the
     * exact fault that made the transport feel random before.
     */
    /**
     * Makes another playlist the active one, abandoning any unsaved edits to this one.
     *
     * The UI asks first when there are edits to lose; this method is what it calls after the answer.
     */
    fun switchToPlaylist(id: Long) {
        scope.launch {
            saveNow()
            stopPlayback()
            playlistId = id
            val tracks = store.tracksIn(id)
            _state.update {
                it.copy(
                    queue = PlayQueue(tracks = tracks, shuffle = it.queue.shuffle, repeat = it.queue.repeat),
                    playlists = store.playlists(),
                    activePlaylistId = id,
                    playing = false,
                    dirty = false,
                    metadata = emptyMap(),
                    positionSeconds = 0.0,
                    durationSeconds = 0.0,
                )
            }
            scheduleSave()
            resolveMetadataInBackground()
        }
    }

    // --- browsing -----------------------------------------------------------------------------

    fun openDomain(domain: BrowseDomain) {
        _browse.update { BrowseNavigation.enteringDomain(it, domain) }
        when (domain) {
            BrowseDomain.LOCAL -> refreshFolders()
            BrowseDomain.ONLINE, BrowseDomain.SEARCH -> refreshCatalogues()
            BrowseDomain.HISTORY -> openHistory()
            BrowseDomain.ROOT -> Unit
        }
    }

    /** One step back up the browse hierarchy. Returns false when already at the top. */
    fun browseBack(): Boolean {
        val current = _browse.value

        // A jump is one step, not a descent. "More from this author" puts you three levels deep
        // without your having passed through any of them, so back should return you to where you
        // actually were -- the playlist -- rather than making you climb out of a hierarchy you
        // never climbed into. Reported by the owner, who had to press back four times.
        if (current.arrivedByJump) {
            _browse.update { it.copy(arrivedByJump = false) }
            return false
        }

        val next = when {
            current.openAuthor != null -> current.copy(openAuthor = null, tracks = emptyList())
                .also { openFormat(current.openFormat.orEmpty()) }
            current.openFormat != null -> current.copy(openFormat = null, tracks = emptyList())
                .also { current.openCatalogue?.let(::openCatalogue) }
            current.openCatalogue != null -> current.copy(openCatalogue = null, groups = emptyList())
            current.openFolder != null -> current.copy(openFolder = null, tracks = emptyList())
            current.domain != BrowseDomain.ROOT -> current.copy(domain = BrowseDomain.ROOT, tracks = emptyList())
            else -> return false
        }
        _browse.update { next }
        return true
    }

    fun refreshFolders() {
        scope.launch { _browse.update { it.copy(folders = store.grantedFolders()) } }
    }

    fun rememberFolder(treeUri: Uri) {
        scope.launch {
            MediaScanner.persistPermission(context, treeUri, isTree = true)
            val folder = GrantedFolder(treeUri.toString(), MediaScanner.labelOf(treeUri))
            store.rememberFolder(folder)
            _browse.update { it.copy(folders = store.grantedFolders()) }
            openFolder(folder)
            // Granting a folder *is* the user asking for it to be usable, so this is the one moment
            // a scan starts without a second press. Nothing else starts one: not launching, not
            // returning to the app, not opening the folder again.
            scanFolder(folder)
        }
    }

    fun forgetFolder(uri: String) {
        scope.launch {
            store.forgetFolder(uri)
            // The index goes with the grant. Keeping rows for a tree we can no longer read would
            // leave a browsable list of files that cannot be opened.
            libraryIndex.forgetFolder(uri)
            _browse.update { it.copy(folders = store.grantedFolders(), openFolder = null, tracks = emptyList()) }
        }
    }

    /**
     * Shows what a folder holds, from the index rather than by walking it again.
     *
     * It used to re-scan the tree every time, which on the owner's network share is seconds of
     * waiting for an answer that has not changed -- and it decided what was playable from filenames
     * (`docs/STATUS.md` C4). Now the scan happens once, on purpose, and this reads the result.
     */
    fun openFolder(folder: GrantedFolder) {
        scope.launch {
            _browse.update {
                it.copy(
                    openFolder = folder, loading = true, tracks = emptyList(),
                    folderUnscanned = false, folderStale = false,
                )
            }
            val found = libraryIndex.tracksIn(folder.uri)
            val stale = found.isNotEmpty() &&
                libraryIndex.isStale(folder.uri, NativeEngine.backendsFingerprint())
            _browse.update {
                it.copy(
                    tracks = found,
                    loading = false,
                    folderUnscanned = found.isEmpty(),
                    folderStale = stale,
                )
            }
        }
    }

    /**
     * Scans a folder by opening every file in it.
     *
     * **Always an explicit action.** Nothing calls this on launch, on returning to the app, or on
     * opening a folder: it reads every file in the tree, which on a network share is minutes, and
     * doing that because somebody switched back to the app would be indefensible.
     *
     * **It decides by content, not by name** (`docs/BACKLOG.md` A6, `docs/STATUS.md` C4). Each file
     * is handed to the same `NativeEngine.open` that playback uses, and what comes back -- the
     * backend that claimed it, the tune's own title and author, its length and subsong count -- is
     * what gets stored. A file called `.txt` that is really a module is indexed; a file called
     * `.mod` that is really a photograph is not.
     *
     * Playing is not interrupted. That used to be impossible: sc68 2.2.1 kept its emulator in
     * global state, so opening a second instance while one played clobbered it. 3.0.0b is
     * instance-based and was measured safe across four concurrent threads
     * (`native/probe/sc68/probe_concurrency.c`), which is what makes this a background job rather
     * than something the user has to stop the music for.
     */
    fun scanFolder(folder: GrantedFolder) {
        scanJob?.cancel()
        scanJob = scope.launch {
            _browse.update { it.copy(scanProgress = 0 to 0, folderUnscanned = false, folderStale = false) }
            val candidates = withContext(Dispatchers.IO) {
                MediaScanner.listFiles(context, Uri.parse(folder.uri))
            }
            _browse.update { it.copy(scanProgress = 0 to candidates.size) }

            val fingerprint = NativeEngine.backendsFingerprint()
            val indexed = mutableListOf<IndexedFile>()
            var done = 0
            for (candidate in candidates) {
                ensureActive()
                withContext(backgroundWork) { probe(candidate, folder.uri) }?.let(indexed::add)
                done++
                // Every file would be a state update per file and a recomposition per file; every
                // twenty-fifth is still movement on screen and costs almost nothing.
                if (done % 25 == 0 || done == candidates.size) {
                    _browse.update { it.copy(scanProgress = done to candidates.size) }
                }
            }

            libraryIndex.replaceFolder(folder.uri, indexed, fingerprint)
            _browse.update { current ->
                current.copy(
                    scanProgress = null,
                    tracks = if (current.openFolder?.uri == folder.uri) {
                        indexed.map(::toTrackRef)
                    } else {
                        current.tracks
                    },
                    folderUnscanned = false,
                    folderStale = false,
                )
            }
            _state.update {
                it.copy(
                    message = Message(
                        "Scanned ${folder.displayName}: ${indexed.size} playable of ${candidates.size}."
                    )
                )
            }
        }
    }

    /**
     * Asks the decoders what one file is. Null when none of them claims it.
     *
     * The bytes are read in full because that is what opening a module means -- these formats are
     * not streamed and a header is not enough to know a backend will accept the rest.
     */
    private fun probe(candidate: MediaScanner.Candidate, folderUri: String): IndexedFile? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(Uri.parse(candidate.uri))?.use { it.readBytes() }
        }.getOrNull() ?: return null

        val opened = NativeEngine.open(bytes, candidate.fileName).track ?: return null
        return try {
            val described = opened.describe()
            IndexedFile(
                uri = candidate.uri,
                folderUri = folderUri,
                path = candidate.path,
                fileName = candidate.fileName,
                sizeBytes = if (candidate.sizeBytes > 0) candidate.sizeBytes else bytes.size.toLong(),
                backend = described["format"].orEmpty(),
                format = described["format"].orEmpty(),
                title = described["title"]?.trim().orEmpty(),
                author = (described["artist"]?.trim()?.ifBlank { null }
                    ?: described["composer"]?.trim()).orEmpty(),
                durationMs = (opened.durationSeconds() * 1000).toLong(),
                subsongs = described["subsongs"]?.toIntOrNull() ?: 1,
            )
        } finally {
            opened.close()
        }
    }

    private fun toTrackRef(entry: IndexedFile): TrackRef = TrackRef(
        id = entry.uri,
        title = entry.title.ifBlank { entry.fileName },
        subtitle = entry.path,
        sizeBytes = entry.sizeBytes,
        fileName = entry.fileName,
        author = entry.author,
        subsongs = entry.subsongs,
    )

    fun closeFolder() = _browse.update { it.copy(openFolder = null, tracks = emptyList()) }

    /**
     * The format an online catalogue files a track under, if it came from one.
     *
     * Read from the index rather than guessed from the filename — the filename is what made this
     * confusing in the first place.
     */
    private suspend fun catalogueFormatOf(ref: TrackRef): String? {
        val catalogue = Catalogue.owning(ref.id) ?: return null
        val path = catalogue.pathFrom(ref.id) ?: return null
        return catalogues.locate(catalogue.id, path)?.format?.takeIf { it.isNotBlank() }
    }

    // --- playlists as files -----------------------------------------------------------------

    /**
     * Writes one playlist out and offers it to be shared.
     *
     * Through the same copy-and-share path a track uses (`docs/ARCHITECTURE.md` §16), because a
     * playlist is a file like any other and nothing else here can hand a file to another app.
     */
    fun exportPlaylist(id: Long) {
        scope.launch {
            val current = _state.value
            // The open playlist is held in the queue, where reordering and removal have already
            // happened; only for any other one is the database the truth.
            val tracks =
                if (id == current.activePlaylistId) current.queue.tracks else store.tracksIn(id)
            if (tracks.isEmpty()) {
                _state.update { it.copy(message = Message("There is nothing in this playlist.")) }
                return@launch
            }
            val label = current.playlists.firstOrNull { it.id == id }?.name ?: "playlist"
            val name = label.replace(Regex("[^\\w -]"), "_")
            val bytes = withContext(Dispatchers.Default) { PlaylistFile.write(tracks).toByteArray() }
            val uri = remoteFiles.shareableCopy("$name.m3u8", bytes)
            if (uri == null) {
                _state.update { it.copy(message = Message("Could not prepare the playlist.")) }
                return@launch
            }
            _share.tryEmit(
                Intent(Intent.ACTION_SEND).apply {
                    // The registered type for M3U. Other players offer to open it; anything else
                    // treats it as the text file it is.
                    type = "audio/x-mpegurl"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }
    }

    /**
     * Reads a playlist file into a new playlist of its own.
     *
     * **A new one, never the current one.** An import that appended to whatever happened to be open
     * would be an edit nobody asked for, and undoing it means finding which rows were new.
     *
     * Each line gets two chances. Its recorded id first, which is exact and works on the device that
     * wrote the file — a backup restored after a reinstall is the common case and deserves to be
     * perfect. Failing that, a match on filename and size against the scanned library, which is how
     * a list written on another phone finds the same tunes here. A catalogue URL needs neither: it
     * means the same everywhere.
     */
    fun importPlaylist(uri: Uri) {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()?.toString(Charsets.UTF_8)
            }
            if (text.isNullOrBlank()) {
                _state.update { it.copy(message = Message("Could not read that file.")) }
                return@launch
            }

            val entries = withContext(Dispatchers.Default) { PlaylistFile.read(text) }
            if (entries.isEmpty()) {
                _state.update { it.copy(message = Message("No tracks in that file.")) }
                return@launch
            }

            val known = store.allTracks().associateBy { it.id }
            val found = entries.mapNotNull { entry -> resolve(entry, known) }

            val name = MediaScanner.labelOf(uri).substringBeforeLast('.').ifBlank { "Imported" }
            val playlistId = store.createPlaylist(name)
            store.replaceTracks(playlistId, found)

            _state.update { it.copy(playlists = store.playlists()) }
            _state.update {
                it.copy(
                    message = Message(
                        if (found.size == entries.size) {
                            "Imported ${found.size} tracks into \"$name\"."
                        } else {
                            // Said, not swallowed. A playlist that silently arrived shorter than
                            // the file it came from is worse than one that explains itself.
                            "Imported ${found.size} of ${entries.size} into \"$name\"; " +
                                "the rest are not on this device."
                        }
                    )
                )
            }
        }
    }

    private suspend fun resolve(entry: PlaylistFile.Entry, known: Map<String, TrackRef>): TrackRef? {
        // A catalogue URL, or an id this device issued: exact either way.
        entry.id?.let { id ->
            known[id]?.let { return it }
            if (id.startsWith("http") || Catalogue.owning(id) != null) {
                return TrackRef(
                    id = id,
                    title = entry.title,
                    subtitle = entry.location.substringBeforeLast('/', ""),
                    sizeBytes = entry.sizeBytes,
                    fileName = entry.fileName,
                    author = entry.author,
                )
            }
        }
        if (entry.location.startsWith("http")) {
            return TrackRef(
                id = entry.location,
                title = entry.title,
                subtitle = entry.location.substringBeforeLast('/', ""),
                sizeBytes = entry.sizeBytes,
                fileName = entry.fileName,
                author = entry.author,
            )
        }
        return libraryIndex.findByFile(entry.fileName, entry.sizeBytes)
    }

    // --- sharing ------------------------------------------------------------------------------

    /**
     * Sends the file itself.
     *
     * Works for anything playable, local or downloaded, because it fetches the bytes the same way
     * playing does — a track from a catalogue is fetched now if it is not already cached. These
     * formats are kilobytes, which is what makes sending one a reasonable thing to do at all.
     */
    fun shareFile(ref: TrackRef) {
        scope.launch {
            val bytes = loadBytes(ref)
            if (bytes == null) {
                _state.update { it.copy(message = Message("Could not read ${ref.title}")) }
                return@launch
            }
            val uri = remoteFiles.shareableCopy(ref.fileNameOrTitle, bytes)
            if (uri == null) {
                _state.update { it.copy(message = Message("Could not prepare ${ref.title} for sharing.")) }
                return@launch
            }
            _share.tryEmit(
                Intent(Intent.ACTION_SEND).apply {
                    // Not audio/*: no chat app can play a .mod, and claiming an audio type invites
                    // the receiving end to try and fail. These are files, and octet-stream is what
                    // a file with no registered type is.
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, ref.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }
    }

    /**
     * Sends a link instead of a file, so the other person fetches it themselves.
     *
     * **Catalogue tracks only**, and what is sent depends on what the catalogue publishes. Modland
     * serves every file over HTTP, so the link is the file. ASMA publishes one archive and no
     * per-file address at all, so the link is the collection and the path inside it — which is a
     * real thing to act on, and better than an `asma://` reference that means nothing off this
     * device. Deciding that was the point: the alternative was an action that looked like it
     * worked.
     */
    fun shareLink(ref: TrackRef) {
        val catalogue = Catalogue.owning(ref.id)
        val path = catalogue?.pathFrom(ref.id)
        if (catalogue == null || path == null) {
            _state.update {
                it.copy(message = Message("Only tracks from an online catalogue have a link."))
            }
            return
        }
        val text = catalogue.webUrlFor(path)
            ?: "${ref.title} — ${catalogue.displayName}, at $path — ${catalogue.homeUrl}"
        _share.tryEmit(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, ref.title)
            }
        )
    }

    /**
     * Opens Browse where a track came from: the author's folder in its catalogue.
     *
     * "Something played at random, it was good, what else did they write" -- and until now the only
     * way to ask was to remember the name and go and search for it.
     *
     * **Catalogue tracks only, and that is a real limit rather than an oversight.** A local file's
     * neighbours would be the directory it sits in, and the local browser does not list directories
     * at all: it lists a whole granted tree, flat. Jumping to "the folder" would mean building
     * directory-level browsing first, which is its own piece of work — `docs/WISHLIST.md` B2 says
     * so rather than leaving the action to do nothing on half the library.
     */
    fun showNeighboursOf(ref: TrackRef) {
        scope.launch {
            // Which catalogue it is from is asked of the catalogues, each of which recognises its
            // own references and no others.
            val from = Catalogue.owning(ref.id)
            val located = from?.pathFrom(ref.id)?.let { path -> catalogues.locate(from.id, path) }
            if (from == null || located == null) {
                _state.update {
                    it.copy(message = Message("Only tracks from an online catalogue can do that."))
                }
                return@launch
            }

            val summary = catalogues.summaries().firstOrNull { it.id == from.id } ?: return@launch
            _browse.update {
                it.copy(
                    domain = BrowseDomain.ONLINE,
                    openCatalogue = summary,
                    openFormat = located.format,
                    openAuthor = located.author,
                    groups = emptyList(),
                    tracks = emptyList(),
                    loading = true,
                    arrivedByJump = true,
                )
            }
            _showBrowse.tryEmit(Unit)

            val found = catalogues.tracks(located.catalogueId, located.format, located.author)
                .map(::toTrackRef)
            _browse.update { it.copy(tracks = found, loading = false) }
        }
    }

    // --- what has been played ------------------------------------------------------------------

    /**
     * Notes that a tune was played.
     *
     * Every play, wherever it came from: the playlist, Random, a search result. Random is the
     * reason this matters most — it is the only place that plays music nobody chose, and "what was
     * that" is a question you can only ask afterwards.
     */
    private fun recordPlayed(ref: TrackRef) {
        scope.launch {
            history.record(
                trackId = ref.id,
                title = ref.title,
                subtitle = ref.subtitle,
                fileName = ref.fileNameOrTitle,
                author = ref.author,
                sizeBytes = ref.sizeBytes,
            )
            // Only if the user is looking at it. Refreshing a list nobody has open is a database
            // read per track played, for nothing.
            if (_browse.value.domain == BrowseDomain.HISTORY) refreshHistory()
        }
    }

    fun openHistory() {
        scope.launch {
            _browse.update { it.copy(domain = BrowseDomain.HISTORY, loading = true, tracks = emptyList()) }
            refreshHistory()
        }
    }

    private suspend fun refreshHistory() {
        val played = history.recent().map { entry ->
            TrackRef(
                id = entry.trackId,
                title = entry.title,
                // When, then where. "That tune two days ago" is the question history exists to
                // answer (`docs/ARCHITECTURE.md` §15) and the ordering alone only says "before that
                // other one" -- which the owner said plainly when he chose this over reordering the
                // list on a replay (`docs/BACKLOG.md` A18).
                //
                // It borrows the source line rather than adding a third. Day headings might read
                // better still and are the open half of `docs/WISHLIST.md` B15; this is the part
                // that costs nothing.
                subtitle = listOf(whenPlayed(entry.playedAt), entry.subtitle)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                sizeBytes = entry.sizeBytes,
                fileName = entry.fileName,
                author = entry.author,
            )
        }
        // Also the `tracks` list, because that is what the add-to-playlist machinery reads and
        // there is no reason history should be the one list you cannot add from.
        _browse.update { it.copy(history = played, tracks = played, loading = false) }
    }

    /**
     * "yesterday, 21:14", in the user's language and clock format.
     *
     * `DateUtils` rather than a pattern of our own: it says "yesterday" where a date would read
     * worse, switches to a date once that stops being useful, and is translated by the system into
     * languages this project does not ship.
     */
    private fun whenPlayed(millis: Long): String =
        DateUtils.getRelativeDateTimeString(
            context,
            millis,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            0,
        ).toString()

    fun clearHistory() {
        scope.launch {
            history.clear()
            _browse.update { it.copy(history = emptyList(), tracks = emptyList()) }
            _state.update { it.copy(message = Message("History cleared.")) }
        }
    }

    // --- online catalogues --------------------------------------------------------------------

    fun refreshCatalogues() {
        scope.launch {
            val summaries = catalogues.summaries()
            val lengths = songLengths.count()
            val storage = withContext(Dispatchers.IO) {
                remoteFiles.cacheBytes() to remoteFiles.permanentBytes()
            }
            val archives = withContext(Dispatchers.IO) {
                Catalogue.all.associate { it.id to remoteFiles.archiveBytes(it.id) }
                    .filterValues { it > 0 }
            }
            // Measured as a file, because that is the only honest measurement available. Only an
            // archive catalogue is a file of its own; Modland's index is rows in this database,
            // sharing its pages, its indexes and its free list with the playlists and the history.
            // Splitting that number between catalogues would be a guess presented as a size.
            val database = withContext(Dispatchers.IO) {
                context.getDatabasePath(SchemaSql.NAME).let { if (it.isFile) it.length() else 0L }
            }
            _browse.update { current ->
                current.copy(
                    catalogues = summaries,
                    songLengthCount = lengths,
                    storageBytes = storage,
                    archiveBytes = archives,
                    databaseBytes = database,
                    backends = NativeEngine.backendsFingerprint(),
                    // Everything indexed is searched until the user says otherwise. Starting with
                    // none ticked would make the first search return nothing and look broken.
                    searchCatalogues = current.searchCatalogues.ifEmpty {
                        summaries.filter { it.indexed }.map { it.id }.toSet()
                    },
                )
            }
        }
    }

    // --- giving the disk back -----------------------------------------------------------------

    /**
     * Everything here deletes something the app can fetch again.
     *
     * That is the rule, not a coincidence: the storage screen may only offer to delete what is a
     * copy. The user's playlists, their granted folders and their history are not on it, and the
     * one thing that looks like a copy but is not — a local library index, which is a record of
     * files only that phone has seen — is only ever *rebuilt*, never dropped.
     *
     * Each of these ends in `refreshCatalogues()` so the numbers the user is looking at become
     * true immediately. A delete that leaves the old size on screen reads as a delete that failed.
     */
    fun clearFetchedCache() {
        scope.launch {
            val freed = withContext(backgroundWork) {
                val before = remoteFiles.cacheBytes()
                remoteFiles.clearCache()
                before - remoteFiles.cacheBytes()
            }
            _state.update { it.copy(message = Message(freedMessage(freed))) }
            refreshCatalogues()
        }
    }

    /**
     * Throws away one catalogue's index.
     *
     * **Playback is deliberately left alone, and this comment used to claim otherwise** (round 6
     * review R3). A queued catalogue track carries its own id, and the URL to fetch it is derived
     * from that id by `Catalogue.urlFor` rather than read from `catalogue_tracks` — so a track that
     * is playing keeps playing, and one further down the playlist still plays when it is reached.
     * What actually degrades is the format label (`catalogueFormatOf` finds nothing and returns
     * null) and "more from this author". Both are cosmetic and both come back with the index.
     *
     * **An archive catalogue's index and its archive are one thing**, so this deletes both. ASMA
     * publishes a single zip that `indexCatalogue` stores whole and parses in place; removing the
     * rows and keeping the zip left a catalogue reporting its full track count, browsing normally
     * and playing nothing (review R4).
     */
    fun deleteCatalogueIndex(catalogueId: String) {
        scope.launch {
            catalogues.clearIndex(catalogueId)
            val freed = withContext(backgroundWork) {
                if (Catalogue.byId(catalogueId)?.isArchive != true) return@withContext 0L
                val before = remoteFiles.archiveBytes(catalogueId)
                if (remoteFiles.deleteArchive(catalogueId)) before else 0L
            }
            _browse.update { current ->
                current.copy(
                    searchCatalogues = current.searchCatalogues - catalogueId,
                    openCatalogue = current.openCatalogue?.takeIf { it.id != catalogueId },
                )
            }
            // The rows cost no disk worth naming; the archive is 20 MB and the user just asked
            // where their storage went, so it is the number worth saying when there is one.
            _state.update {
                it.copy(
                    message = Message(
                        if (freed > 0L) freedMessage(freed) else "Index deleted. Download it again any time."
                    )
                )
            }
            refreshCatalogues()
        }
    }

    /** Throws away the HVSC song lengths. SID durations go unknown until they are fetched again. */
    fun clearSongLengths() {
        scope.launch {
            songLengths.clear()
            _state.update { it.copy(message = Message("Song lengths deleted.")) }
            refreshCatalogues()
        }
    }

    /** The wording for what [CacheBudget.describeFreed] worked out. */
    private fun freedMessage(bytes: Long): String =
        when (val freed = CacheBudget.describeFreed(bytes)) {
            CacheBudget.Freed.NOTHING -> "There was nothing to delete."
            CacheBudget.Freed.LESS_THAN_A_MEGABYTE -> "Freed less than 1 MB."
            is CacheBudget.Freed.Megabytes -> "Freed ${freed.count} MB."
        }

    /**
     * Downloads a catalogue's index and stores it.
     *
     * Only entries whose filename a backend might handle are kept. Modland lists about half a
     * million files across formats nothing here can play yet; indexing them all would cost minutes
     * and disk to browse a list of tracks that cannot be opened. Re-run this after adding a backend
     * -- noted in docs/BACKLOG.md so it is not discovered by wondering where the SIDs went.
     */
    fun indexCatalogue(id: String) {
        val catalogue = Catalogue.byId(id) ?: return
        scope.launch {
            _browse.update { it.copy(indexing = catalogue.displayName) }
            val bytes = remoteFiles.fetchIndex(catalogue.indexUrl)
            if (bytes == null) {
                _browse.update { it.copy(indexing = null) }
                _state.update { it.copy(message = Message("Could not download the ${catalogue.displayName} index.")) }
                return@launch
            }
            // An archive catalogue's "index" IS the archive, so it is kept rather than parsed and
            // discarded -- afterwards both browsing and playing work with no network at all.
            if (catalogue.isArchive && !remoteFiles.storeArchive(catalogue.id, bytes)) {
                _browse.update { it.copy(indexing = null) }
                _state.update {
                    it.copy(message = Message("Could not store the ${catalogue.displayName} archive."))
                }
                return@launch
            }

            val entries = withContext(Dispatchers.Default) {
                // By name, and here that is right rather than a shortcut. A catalogue index is a
                // list of filenames on somebody else's server; deciding by content would mean
                // downloading half a million files to find out. The local library is the opposite
                // case and is scanned by opening (`docs/BACKLOG.md` A6).
                catalogue.parseIndex(bytes) { name -> SupportedFormats.looksPlayable(name) }
            }
            catalogues.replaceIndex(catalogue, entries, NativeEngine.backendsFingerprint())
            _browse.update { it.copy(indexing = null, catalogues = catalogues.summaries()) }
            _state.update { it.copy(message = Message("Indexed ${entries.size} tracks from ${catalogue.displayName}.")) }
        }
    }

    /**
     * Downloads HVSC's song length database.
     *
     * Offered as its own action rather than fetched when the first SID plays, because it is 5 MB
     * and the moment somebody presses play on a tune is the wrong moment to spend it. It is not a
     * catalogue either -- nothing in it can be played, it only answers "how long" about SIDs that
     * came from somewhere else, so it sits below the catalogues rather than among them.
     */
    fun downloadSongLengths() {
        scope.launch {
            _browse.update { it.copy(indexing = SONG_LENGTHS_LABEL) }
            val bytes = remoteFiles.fetchIndex(SONG_LENGTHS_URL)
            if (bytes == null) {
                _browse.update { it.copy(indexing = null) }
                _state.update { it.copy(message = Message("Could not download the song lengths.")) }
                return@launch
            }
            val entries = withContext(Dispatchers.Default) {
                SongLengths.parse(bytes.toString(Charsets.ISO_8859_1))
            }
            if (entries.isEmpty()) {
                _browse.update { it.copy(indexing = null) }
                _state.update { it.copy(message = Message("The song length database was empty.")) }
                return@launch
            }
            songLengths.replaceAll(entries)
            _browse.update { it.copy(indexing = null, songLengthCount = entries.size) }
            _state.update { it.copy(message = Message("Song lengths for ${entries.size} SID tunes.")) }
        }
    }

    fun openCatalogue(summary: CatalogueSummary) {
        scope.launch {
            _browse.update {
                it.copy(
                    openCatalogue = summary, openFormat = null, openAuthor = null,
                    loading = true, tracks = emptyList(), arrivedByJump = false,
                )
            }
            val formats = catalogues.formats(summary.id)
            _browse.update { it.copy(groups = formats, loading = false) }
        }
    }

    fun openFormat(format: String) {
        val catalogueId = _browse.value.openCatalogue?.id ?: return
        scope.launch {
            _browse.update {
                it.copy(
                    openFormat = format, openAuthor = null, loading = true,
                    tracks = emptyList(), arrivedByJump = false,
                )
            }
            val authors = catalogues.authors(catalogueId, format)
            _browse.update { it.copy(groups = authors, loading = false) }
        }
    }

    fun openAuthor(author: String) {
        val current = _browse.value
        val catalogueId = current.openCatalogue?.id ?: return
        val format = current.openFormat ?: return
        scope.launch {
            _browse.update {
                it.copy(openAuthor = author, loading = true, tracks = emptyList(), arrivedByJump = false)
            }
            val found = catalogues.tracks(catalogueId, format, author).map(::toTrackRef)
            _browse.update { it.copy(tracks = found, loading = false) }
        }
    }

    /** Picks something at random from the indexed catalogues and plays it, without adding it. */
    /**
     * The dice: a tune nobody here has heard, now.
     *
     * Picks read ahead but never played are **speculation**, and the dice means "surprise me", so
     * they go and everything actually played stays. Stepping back through the history and then
     * pressing the dice therefore re-rolls, while pressing next -- which means forward -- walks
     * into the queue as it should. The old code truncated at the cursor and so threw away real
     * history; keeping [randomPlayed] is what lets it throw away only the guesses.
     */
    fun playRandom() {
        scope.launch {
            while (randomHistory.lastIndex > randomPlayed) {
                randomHistory.removeAt(randomHistory.lastIndex)
            }
            randomCursor = randomPlayed
            advanceRandom()
        }
    }

    private fun randomNext() {
        scope.launch { advanceRandom() }
    }

    private fun randomPrevious() {
        if (randomCursor <= 0) return
        randomCursor--
        playTransient(randomHistory[randomCursor])
    }

    /**
     * Moves Random forward one, having decided what comes after it first.
     *
     * The order is the point of the whole item. Random used to pick at the moment you pressed it,
     * so there was never anything to fetch in advance -- not because the read-ahead was missing but
     * because nothing had been decided for it to read. Deciding early is what makes the wait go.
     */
    private suspend fun advanceRandom() {
        fillRandomQueue()
        if (randomCursor >= randomHistory.lastIndex) {
            _state.update {
                it.copy(message = Message("Nothing is indexed yet. Index a catalogue first."))
            }
            return
        }

        randomCursor++
        randomPlayed = maxOf(randomPlayed, randomCursor)
        // Topped up before playing rather than after: `load` reads ahead when it finishes, and it
        // can only read what has already been decided.
        fillRandomQueue()
        playTransient(randomHistory[randomCursor])
    }

    /** Tops the queue up so [READ_AHEAD] picks stand past the cursor. */
    private suspend fun fillRandomQueue() {
        val short = READ_AHEAD - (randomHistory.lastIndex - randomCursor)
        if (short <= 0) return
        randomHistory += catalogues.randomSample(short).map(::toTrackRef)
    }

    /**
     * Plays a search result, without adding it to anything.
     *
     * The point of a search is finding out what something is, and that used to require adding it to
     * the playlist first — which is backwards. The results become the queue while you are in them,
     * so next and previous walk what you found and the playlist is untouched.
     */
    fun playFromResults(results: List<TrackRef>, index: Int) {
        if (index !in results.indices) return
        playFromResultsQueue(PlayQueue(tracks = results).startAt(index))
    }

    private fun playFromResultsQueue(results: PlayQueue) {
        val ref = results.current ?: return
        _state.update {
            it.copy(
                resultsQueue = results,
                transient = null,
                randomHasPrevious = false,
                playing = false,
                positionSeconds = 0.0,
            )
        }
        load(ref)
    }

    /**
     * Keeps what is playing, without leaving Random.
     *
     * The owner asked for these to be separate: adding a track you like should not end the sequence
     * you are listening through.
     */
    fun keepTransient() {
        // Anything playing that is not the playlist: a Random pick, a search result, a track tapped
        // while browsing. All three are "I am hearing something I did not choose to keep", and the
        // moment just after hearing it is when a person decides. It used to work for Random alone,
        // which is where the idea came from and not where it belongs.
        val ref = _state.value.transient ?: _state.value.resultsQueue?.current ?: return
        // Its own wording, because this one knows the track's name and the general notice does not.
        //
        // It used to add through `addToPlaylist` and then overwrite the message that produced --
        // which worked only because `appendTracks` happens to be synchronous, and briefly put a
        // second notice into a conflated flow on the way (round 6 review R5). Saying it once is
        // both simpler and true regardless of ordering.
        appendTracks(listOf(ref)) { added, _ ->
            if (added > 0) {
                Message("Added \"${ref.title}\" to the playlist.")
            } else {
                Message("\"${ref.title}\" is already in this playlist.")
            }
        }
    }

    /**
     * Leaves whatever is playing outside the playlist — Random or a search — and goes back to it.
     *
     * Playback stops. The playlist is exactly where it was left, which is the whole point of never
     * having written to it.
     */
    fun returnToPlaylist() {
        if (!_state.value.awayFromPlaylist) return
        stopPlayback()
        randomHistory.clear()
        randomCursor = -1
        randomPlayed = -1
        _state.update {
            it.copy(
                transient = null,
                resultsQueue = null,
                randomHasPrevious = false,
                playing = false,
                metadata = emptyMap(),
                positionSeconds = 0.0,
                durationSeconds = 0.0,
            )
        }
    }

    // --- search -------------------------------------------------------------------------------

    fun setQuery(query: String) = _browse.update { it.copy(query = query) }

    fun toggleSearchLocal() = _browse.update { it.copy(searchLocal = !it.searchLocal) }

    /** Turning the whole online side off leaves the individual choices as they were. */
    fun toggleSearchOnline() = _browse.update { it.copy(searchOnline = !it.searchOnline) }

    fun toggleSearchCatalogue(id: String) = _browse.update {
        it.copy(
            searchCatalogues = if (id in it.searchCatalogues) it.searchCatalogues - id else it.searchCatalogues + id
        )
    }

    fun runSearch() {
        val current = _browse.value
        if (current.query.isBlank()) return
        scope.launch {
            _browse.update { it.copy(loading = true, tracks = emptyList()) }

            // The scanned library first: it knows tunes by their real titles, and it knows files
            // nobody has added to any playlist. Then the playlists, for anything indexed folders do
            // not cover -- an individually picked file, or a folder whose grant is gone.
            val fromIndex = if (current.searchLocal) {
                libraryIndex.search(current.query, limit = 200)
            } else {
                emptyList()
            }

            val fromLocal = if (current.searchLocal) {
                // The library, meaning every playlist's tracks -- searching only the active one
                // would answer a question nobody asked.
                store.allTracks().filter {
                    it.title.contains(current.query, ignoreCase = true) ||
                        it.fileName.contains(current.query, ignoreCase = true)
                }
            } else {
                emptyList()
            }

            // Explicit. "No catalogue ticked means all of them" was the earlier rule and it made
            // the filter look broken: unticking Modland searched Modland anyway. Nothing ticked now
            // means nothing searched, which is what unticking a box has always meant.
            val dbCatalogues = current.searchCatalogues.filter { it != com.przunk.protracktor.net.ModArchive.id }.toSet()
            val fromOnline = if (current.searchOnline && dbCatalogues.isNotEmpty()) {
                catalogues.search(current.query, dbCatalogues).map(::toTrackRef)
            } else {
                emptyList()
            }

            val fromModArchive = if (current.searchOnline && com.przunk.protracktor.net.ModArchive.id in current.searchCatalogues) {
                com.przunk.protracktor.net.ModArchive.search(current.query)
            } else {
                emptyList()
            }

            // The index first and de-duplicated against it by id: a file can be both indexed and
            // sitting in a playlist, and showing it twice makes a search look broken.
            val indexedIds = fromIndex.mapTo(mutableSetOf()) { it.id }
            val local = fromIndex + fromLocal.filterNot { it.id in indexedIds }

            _browse.update { it.copy(tracks = local + fromOnline + fromModArchive, loading = false) }
        }
    }

    private fun toTrackRef(track: CatalogueTrack): TrackRef {
        val catalogue = Catalogue.byId(track.catalogueId)
        return TrackRef(
            // The URL is the identity. A catalogue track has no document URI and never will, and
            // the URL is what both the cache and the player key on.
            id = catalogue?.urlFor(track.path) ?: track.path,
            title = track.title,
            // The source, as a path, the same shape a local file's is: "Modland/Protracker/4-Mat".
            // It used to be "format · author", which reads well in a row and badly everywhere else
            // -- the information panel needs to say where a file came from, and a search result has
            // to answer "which one of these is it". The author still has its own field, so nothing
            // is lost by making this a path.
            subtitle = listOf(catalogue?.displayName, track.path.substringBeforeLast('/', ""))
                .filter { !it.isNullOrBlank() }
                .joinToString("/"),
            sizeBytes = track.size,
            fileName = track.title,
            // Catalogues file by author, so this is known before the file is ever opened.
            author = track.author,
        )
    }

    /** Adds a chosen set to the active playlist. Duplicates are ignored rather than doubled. */
    fun addToPlaylist(tracks: List<TrackRef>) {
        if (tracks.isEmpty()) return
        appendTracks(tracks, ::describeAdded)
    }

    // `addToPlaylistAndSay` lived here: it added to the current playlist and announced it, for the
    // row menu that stayed in Browse (C7). That menu item is gone -- one "Add to playlist..." opens
    // the picker instead (`docs/BACKLOG.md` A17) -- and the picker's own path already reports back
    // and names the target, which is strictly better. Removed rather than kept for a caller that no
    // longer exists.

    fun addToPlaylist(targetPlaylistId: Long, tracks: List<TrackRef>) {
        if (tracks.isEmpty()) return
        if (targetPlaylistId == playlistId) {
            addToPlaylist(tracks)
            return
        }
        scope.launch {
            val targetName = store.playlists().firstOrNull { it.id == targetPlaylistId }?.name
                ?: return@launch
            val existing = store.tracksIn(targetPlaylistId).toMutableList()
            var added = 0
            tracks.forEach { candidate ->
                if (existing.none { it.sameFileAs(candidate) }) {
                    existing.add(candidate)
                    added++
                }
            }
            if (added > 0) {
                store.replaceTracks(targetPlaylistId, existing)
            }
            val message = when {
                tracks.size == 1 && added == 1 ->
                    Message(context.getString(R.string.notice_added_to_named_playlist, tracks.first().title, targetName))
                tracks.size == 1 && added == 0 ->
                    Message(context.getString(R.string.notice_already_in_playlist, tracks.first().title, targetName))
                added > 0 ->
                    Message(context.resources.getQuantityString(R.plurals.notice_added_count_to_playlist, added, added, targetName))
                else ->
                    Message(context.getString(R.string.notice_all_already_in_playlist, targetName))
            }
            _state.update { it.copy(message = message) }
        }
    }

    /**
     * Creates a new playlist and immediately adds [tracks] to it (B18).
     */
    fun createPlaylistAndAdd(name: String, tracks: List<TrackRef>) {
        if (tracks.isEmpty()) return
        scope.launch {
            val finalName = name.ifBlank { DEFAULT_PLAYLIST_NAME }
            val newId = store.createPlaylist(finalName)
            store.replaceTracks(newId, tracks)
            val updated = store.playlists()
            _state.update { it.copy(playlists = updated) }
            val message = if (tracks.size == 1) {
                Message(context.getString(R.string.notice_added_to_named_playlist, tracks.first().title, finalName))
            } else {
                Message(context.resources.getQuantityString(R.plurals.notice_added_count_to_playlist, tracks.size, tracks.size, finalName))
            }
            _state.update { it.copy(message = message) }
        }
    }

    // --- library ------------------------------------------------------------------------------

    /**
     * Grants a folder and adds everything playable in it to the playlist.
     *
     * It scans first and adds what the scan found, rather than adding every file whose name looked
     * promising. That is the difference A6 is about: the list you get is what the decoders accepted,
     * so nothing in it refuses when you press play.
     */
    fun addFolder(treeUri: Uri) {
        scope.launch {
            _state.update { it.copy(scanning = true) }
            MediaScanner.persistPermission(context, treeUri, isTree = true)
            val folder = GrantedFolder(
                uri = treeUri.toString(),
                displayName = MediaScanner.labelOf(treeUri),
            )
            store.rememberFolder(folder)
            _browse.update { it.copy(folders = store.grantedFolders()) }

            scanFolder(folder)
            scanJob?.join()

            _state.update { it.copy(scanning = false) }
            appendTracks(libraryIndex.tracksIn(folder.uri), ::describeAdded)
        }
    }

    fun addFiles(uris: List<Uri>) {
        scope.launch {
            val found = withContext(Dispatchers.IO) { MediaScanner.fromDocuments(context, uris) }
            appendTracks(found, ::describeAdded)
        }
    }

    private fun appendTracks(found: List<TrackRef>, describe: (added: Int, skipped: Int) -> Message?) {
        var added = 0
        var skipped = 0
        var firstAdded = 0
        _state.update { current ->
            // Rebuilding the queue rather than mutating it keeps the play history meaningful: the
            // indices it holds must keep pointing at the same tracks.
            val merged = current.queue.tracks.toMutableList()
            found.forEach { candidate ->
                if (merged.any { it.sameFileAs(candidate) }) {
                    skipped++
                } else {
                    if (added == 0) firstAdded = merged.size
                    merged += candidate
                    added++
                }
            }
            current.copy(
                queue = current.queue.withTracks(merged),
                scanning = false,
                dirty = current.dirty || added > 0,
                // Only replaces the current message when there is something to say; a null must
                // not silently clear a message the user has not read.
                message = describe(added, skipped) ?: current.message,
            )
        }
        resolveMetadataInBackground()

        // Adding appends to the end, so without this nothing visibly happens -- which matters more
        // now that the confirming message was deliberately removed.
        if (added > 0) _reveal.tryEmit(firstAdded)
    }

    /**
     * What to say after adding — now always something.
     *
     * **This used to stay silent on success**, on the reasoning that the rows appearing *is* the
     * confirmation and a notice repeating the screen is noise. That reasoning was written when the
     * snackbar covered the very rows it was reporting, and it does not survive contact: adding from
     * a local folder closes Browse and lands on a playlist that may not visibly change at all —
     * the new rows are at the end, and scrolling to them is not something a person registers as an
     * answer. The owner reported the silence as a fault, which settles it: he is the one who can
     * tell a confirmation from noise.
     *
     * The snackbar it once collided with is swipeable now (`SwipeableSnackbar`), so the objection
     * that produced the silence has been dealt with separately.
     */
    private fun describeAdded(added: Int, skipped: Int): Message? {
        val where = _state.value.activePlaylistName?.let { " to $it" }.orEmpty()
        return when {
            added == 0 && skipped == 0 -> Message("Nothing playable found there.")
            added == 0 -> Message("Already in this playlist.")
            skipped == 0 && added == 1 -> Message("Added 1 track$where.")
            skipped == 0 -> Message("Added $added tracks$where.")
            else -> Message("Added $added$where; $skipped already there.")
        }
    }

    /**
     * What [undoRemoval] would put back, as (position, track) pairs. Cleared once its notice is gone.
     *
     * A list rather than one pair, because a bulk delete is **one edit, not twenty**
     * (`docs/BACKLOG.md` A4). Undoing it has to restore the whole selection: putting back one row of
     * twenty is not an undo, it is a second surprise.
     */
    private var lastRemoval: List<TrackEditing.Removed> = emptyList()

    /**
     * Drops one track.
     *
     * Removing what is playing stops playback rather than jumping somewhere: silently starting a
     * different track because the user deleted this one is a surprise, and there is no reading of
     * "remove" that asks for it.
     *
     * No confirmation. Undo is the better answer for one row -- it costs nothing when the user meant
     * it, and a dialog on every delete is a toll paid by the people who did.
     */
    fun removeTrack(index: Int) = removeTracks(listOf(index))

    /**
     * Drops any number of tracks as one edit.
     *
     * Removing what is playing stops playback rather than jumping somewhere: silently starting a
     * different track because the user deleted this one is a surprise, and there is no reading of
     * "remove" that asks for it.
     *
     * No confirmation, for the same reason as before: undo costs nothing when the user meant it,
     * and a dialog on every delete is a toll paid by the people who did. What changed for a group
     * is that undo has to bring **all** of it back.
     */
    fun removeTracks(indices: List<Int>) {
        val currentState = _state.value
        val (kept, removed) = TrackEditing.remove(currentState.queue.tracks, indices)
        if (removed.isEmpty()) return
        val wasPlaying = removed.any { it.index == currentState.queue.currentIndex }

        if (wasPlaying) stopPlayback()
        lastRemoval = removed

        _state.update {
            it.copy(
                queue = it.queue.withTracks(kept),
                playing = if (wasPlaying) false else it.playing,
                metadata = if (wasPlaying) emptyMap() else it.metadata,
                positionSeconds = if (wasPlaying) 0.0 else it.positionSeconds,
                durationSeconds = if (wasPlaying) 0.0 else it.durationSeconds,
                dirty = true,
                message = Message(
                    text = if (removed.size == 1) {
                        "Removed ${removed.first().track.title}"
                    } else {
                        "Removed ${removed.size} tracks"
                    },
                    actionLabel = UNDO,
                ),
            )
        }
    }

    /**
     * Moves a track to another position.
     *
     * An edit like any other, so it waits for Save. History survives because [PlayQueue.withTracks]
     * remaps by track identity rather than by index — written for removal, and it covers this for
     * free.
     */
    fun moveTrack(from: Int, to: Int) {
        if (from == to) return
        _state.update { current ->
            val tracks = current.queue.tracks
            if (from !in tracks.indices || to !in tracks.indices) return@update current
            val reordered = tracks.toMutableList().apply { add(to, removeAt(from)) }
            current.copy(queue = current.queue.withTracks(reordered), dirty = true)
        }
    }

    /** Puts everything the last removal took back where it was. */
    fun undoRemoval() {
        val removed = lastRemoval
        if (removed.isEmpty()) return
        lastRemoval = emptyList()
        _state.update {
            it.copy(
                queue = it.queue.withTracks(TrackEditing.restore(it.queue.tracks, removed)),
                dirty = true,
                message = null,
            )
        }
    }

    fun dismissMessage() {
        lastRemoval = emptyList()
        _state.update { it.copy(message = null) }
    }

    // --- transport ----------------------------------------------------------------------------

    fun playAt(index: Int) = openAndPlay(_state.value.queue.startAt(index))

    fun next() {
        val now = _state.value
        // A tune inside the file first, but **only in "play all"**. The owner settled that: making
        // next mean something different depending on the *file* would be the transport behaving
        // differently for reasons the user did not choose, which is the complaint that shaped the
        // whole transport in the first place. Tied to a mode he set, it is predictable.
        if (now.playAllSubsongs && now.subsong + 1 < now.subsongCount) {
            selectSubsong(now.subsong + 1)
            return
        }
        if (now.transient != null) return randomNext()
        now.resultsQueue?.let { results ->
            if (results.hasNext) playFromResultsQueue(results.next())
            return
        }
        if (!now.queue.hasNext) return
        openAndPlay(now.queue.next())
    }

    fun previous() {
        val now = _state.value
        if (now.playAllSubsongs && now.subsong > 0) {
            selectSubsong(now.subsong - 1)
            return
        }
        if (now.transient != null) return randomPrevious()
        now.resultsQueue?.let { results ->
            if (results.hasPrevious) playFromResultsQueue(results.previous())
            return
        }
        if (!now.queue.hasPrevious) return
        openAndPlay(now.queue.previous())
    }

    fun seekTo(seconds: Double) {
        val open = track ?: return
        open.seekTo(seconds)
        // Shown immediately rather than waiting for the next poll: a slider that springs back to
        // where it was before catching up reads as a control that did not work.
        _state.update { it.copy(positionSeconds = seconds) }
    }

    /**
     * Plays a tune inside the current file.
     *
     * Always available, whatever the mode: the mode governs what *next* does and whether one tune
     * runs into the next, not whether the user may choose one by hand.
     */
    fun selectSubsong(index: Int) {
        val open = track ?: return
        val now = _state.value
        if (index < 0 || index >= now.subsongCount || index == now.subsong) return
        open.selectSubsong(index)
        _state.update {
            it.copy(
                subsong = index,
                positionSeconds = 0.0,
                // The length is per tune. Backends that know their own only know it after the
                // switch has been applied on the audio thread, so this is cleared rather than left
                // showing the previous tune's and the position poll puts the real one back --
                // except where the backend never knows, and then HVSC's list for this file does.
                durationSeconds = openSongLengths.getOrNull(index) ?: 0.0,
            )
        }
    }

    /**
     * Turns "play every tune in the file" on or off.
     *
     * Global and remembered, like shuffle and repeat, because it is about what plays next rather
     * than about one row.
     */
    fun toggleAllSubsongs() {
        _state.update { it.copy(playAllSubsongs = !it.playAllSubsongs) }
        scheduleSave()
    }

    fun togglePlayPause() {
        val open = track
        if (open == null) {
            // Nothing loaded -- which is the normal state after a restart, where the queue already
            // knows which track it was on. Resume that one, not the top of the list.
            val queue = _state.value.queue
            if (queue.tracks.isNotEmpty()) playAt(queue.currentIndex ?: 0)
            return
        }
        if (_state.value.playing) {
            open.stop()
            audioFocus.release()
            _state.update { it.copy(playing = false) }
        } else {
            // Asking first, and not starting if refused: a player that talks over a phone call is
            // worse than one that does nothing.
            if (!audioFocus.acquire()) {
                _state.update { it.copy(message = Message("Something else is using the audio.")) }
                return
            }
            val restarted = if (open.isFinished()) open.restart() else open.start()
            _state.update { it.copy(playing = restarted) }
        }
    }

    /**
     * Play or pause explicitly, rather than toggling.
     *
     * A media button says which it wants; toggling on a stale idea of the state is how a headphone
     * press ends up pausing something that was already paused.
     */
    fun togglePlayPauseTo(play: Boolean) {
        if (play == _state.value.playing) return
        togglePlayPause()
    }

    /** Stops playing without forgetting what was loaded. Used by the notification's stop action. */
    fun pause() {
        val open = track ?: return
        open.stop()
        audioFocus.release()
        _state.update { it.copy(playing = false) }
    }

    fun toggleShuffle() {
        _state.update { it.copy(queue = it.queue.withShuffle(!it.queue.shuffle)) }
        scheduleSave()
        // Both of these change what comes next, so anything read ahead is now the wrong track.
        prefetchUpcoming()
    }

    fun cycleRepeat() {
        _state.update { it.copy(queue = it.queue.withRepeat(it.queue.repeat.next())) }
        scheduleSave()
        prefetchUpcoming()
    }

    // --- internals ----------------------------------------------------------------------------

    private fun handleTrackEnded() {
        // A file with more tunes in it runs on into the next one, but only in "play all". Off, a
        // 256-subsong file behaves like any other track and the playlist keeps moving -- which is
        // exactly why the owner chose that as the default. The rule itself, including what
        // repeat-one means here, is in `SubsongAdvance` where it can be tested.
        val now = _state.value
        when (
            val next = SubsongAdvance.after(
                playAll = now.playAllSubsongs,
                subsong = now.subsong,
                subsongCount = now.subsongCount,
                repeatOne = now.queue.repeat == RepeatMode.ONE,
            )
        ) {
            is SubsongAdvance.Next.Subsong -> {
                selectSubsong(next.index)
                return
            }
            SubsongAdvance.Next.FileFinished -> Unit
        }

        _state.value.resultsQueue?.let { results ->
            val advanced = results.onTrackEnded()
            when {
                advanced == null -> {
                    track?.stop()
                    audioFocus.release()
                    _state.update { it.copy(playing = false, positionSeconds = it.durationSeconds) }
                }
                // Repeat-one hands back the identical queue.
                advanced == results -> {
                    val playing = track?.restart() ?: false
                    _state.update { it.copy(playing = playing, positionSeconds = 0.0) }
                }
                else -> playFromResultsQueue(advanced)
            }
            return
        }

        // A transient track is a Random pick -- `playTransient` is called from nowhere else, and a
        // search result sets `transient` to null on its way through `playFromResultsQueue` above.
        //
        // Random used to stop here, guarding against rolling on into the playlist: that would
        // answer a question nobody asked by pressing Random. **That guard still holds** and this is
        // not it. Going to the next random pick is the question they did ask, and stopping after
        // every tune made Random something you operate rather than something you listen to.
        if (_state.value.transient != null) {
            // Repeat-one is the one setting that means "keep playing this", and it says so on the
            // dock while Random is running. Skipping to another tune under it would be the app
            // contradicting its own button.
            if (_state.value.queue.repeat == RepeatMode.ONE) {
                val playing = track?.restart() ?: false
                _state.update { it.copy(playing = playing, positionSeconds = 0.0) }
            } else {
                randomNext()
            }
            return
        }

        val queue = _state.value.queue
        val advanced = queue.onTrackEnded()

        when {
            advanced == null -> {
                // End of the playlist with repeat off. Stop, but leave the track loaded so the
                // screen still says what was playing. Focus goes back: holding it while silent
                // would keep other apps ducked for no reason.
                track?.stop()
                audioFocus.release()
                _state.update { it.copy(playing = false, positionSeconds = it.durationSeconds) }
            }
            // Repeat-one hands back the identical queue: same track, from the top, without a new
            // history entry to step back through.
            advanced == queue -> {
                val playing = track?.restart() ?: false
                _state.update { it.copy(playing = playing, positionSeconds = 0.0) }
            }
            else -> openAndPlay(advanced)
        }
    }

    /** Plays something that is not in the playlist. */
    private fun playTransient(ref: TrackRef) {
        _state.update {
            it.copy(
                transient = ref,
                randomHasPrevious = randomCursor > 0,
                playing = false,
                positionSeconds = 0.0,
            )
        }
        load(ref)
    }

    private fun openAndPlay(queue: PlayQueue) {
        val ref = queue.current ?: return

        // The queue advances NOW, not inside the coroutine. Two quick presses of next both read the
        // old queue otherwise, and both advance to the same track.
        // Playing from the playlist proper leaves any detour behind.
        _state.update {
            it.copy(
                queue = queue,
                transient = null,
                resultsQueue = null,
                randomHasPrevious = false,
                playing = false,
                positionSeconds = 0.0,
            )
        }
        scheduleSave()
        load(ref)
    }

    private fun load(ref: TrackRef) {

        openJob?.cancel()
        openJob = scope.launch {
            track?.close()
            track = null

            // Already read while the previous track was playing, which is the whole point.
            // Removed rather than left: it is about to be the current track, and holding a second
            // copy of it helps nobody.
            val ready = prefetched.remove(ref.id)

            val bytes = ready ?: run {
                _state.update { it.copy(loadingTrack = true) }
                loadBytes(ref)
            }
            _state.update { it.copy(loadingTrack = false) }
            ensureActive()

            if (bytes == null) {
                _state.update { it.copy(message = Message("Could not read ${ref.title}")) }
                return@launch
            }

            val result = withContext(Dispatchers.IO) { NativeEngine.open(bytes, ref.fileNameOrTitle) }
            val opened = result.track
            if (opened == null) {
                // The reason, not just the verdict. "Not a format we can play" is wrong when a
                // backend claimed the file and then choked on it, which is exactly what sc68 does
                // with some SNDH files -- and the two are indistinguishable from outside.
                // The decoder's reason is true and often useless on its own. When the track came
                // from a catalogue we know something better: the archive files it under a format
                // name. Modland's `SidMon 1` directory holds 61 files with a `.sid` extension that
                // are Amiga executables, not C64 tunes -- so "no backend recognised it" was
                // accurate and told the owner nothing (`docs/STATUS.md` C9).
                val reason = result.error
                val format = catalogueFormatOf(ref)
                _state.update {
                    it.copy(
                        message = Message(
                            when {
                                format != null ->
                                    "$format is a format Protracktor cannot play yet."
                                reason.isBlank() -> "${ref.title}: no backend could open it"
                                else -> "${ref.title}: $reason"
                            }
                        )
                    )
                }
                return@launch
            }

            // Cancelled while the file was being read: throw away what was opened instead of
            // starting a stream nobody asked for and nobody will stop.
            if (!isActive) {
                opened.close()
                throw CancellationException()
            }

            if (!audioFocus.acquire()) {
                opened.close()
                _state.update { it.copy(message = Message("Something else is using the audio.")) }
                return@launch
            }

            track = opened
            val started = opened.start()
            val described = opened.describe()
            // A SID has no length in it, so the backend reports none and HVSC's database is asked
            // instead. Only when the backend has nothing: a format that knows its own length knows
            // it better than a lookup on a hash could.
            openSongLengths = songLengths.secondsFor(bytes).orEmpty()
            val duration = opened.durationSeconds().takeIf { it > 0.0 }
                ?: openSongLengths.firstOrNull()
                ?: 0.0
            _state.update {
                it.copy(
                    playing = started,
                    metadata = described,
                    subsong = 0,
                    subsongCount = opened.subsongCount().coerceAtLeast(1),
                    durationSeconds = duration,
                    positionSeconds = 0.0,
                    message = if (started) it.message else Message("Could not open the audio device"),
                )
            }
            // Recorded with the name the tune calls itself rather than the filename it arrived
            // under, which is why this waits until after the metadata has been read.
            recordPlayed(adoptTitleFrom(described, ref))

            prefetchUpcoming()
        }
    }

    /**
     * Takes the tune's real name and author from its metadata, once we have them.
     *
     * These formats carry a title inside, and it is usually better than the filename -- but there is
     * no way to know it without opening the file, and opening every file during a scan is exactly
     * the wait R9 exists to remove. So names improve as tracks are played, which costs nothing and
     * is the only affordable moment.
     *
     * The filename is kept either way, so identity does not shift under a playlist and the metadata
     * view can still say where a track came from.
     */
    private fun adoptTitleFrom(described: Map<String, String>, ref: TrackRef): TrackRef {
        val realTitle = described["title"]?.trim().orEmpty()
        // Backends disagree on which key carries it, so both are asked before giving up.
        val realAuthor = described["artist"]?.trim()?.ifBlank { null }
            ?: described["composer"]?.trim().orEmpty()

        val newTitle = realTitle.ifBlank { ref.title }
        val newAuthor = realAuthor.ifBlank { ref.author }
        val improved = ref.copy(title = newTitle, author = newAuthor, fileName = ref.fileNameOrTitle)
        if (newTitle == ref.title && newAuthor == ref.author) return improved

        _state.update { current ->
            val index = current.queue.tracks.indexOfFirst { it.id == ref.id }
            if (index < 0) return@update current
            val renamed = current.queue.tracks.toMutableList().apply {
                this[index] = this[index].copy(
                    title = newTitle,
                    author = newAuthor,
                    fileName = this[index].fileNameOrTitle,
                )
            }
            current.copy(queue = current.queue.withTracks(renamed))
        }
        // Written straight away rather than waiting for the user to press Save: this is not one of
        // their edits, it is the app learning something, and losing it would mean relearning it on
        // every launch.
        scheduleTrackWrite()
        return improved
    }

    /**
     * Fills in titles and authors for tracks nobody has played yet.
     *
     * These formats carry their real names inside, and until now a list of three hundred stayed a
     * list of filenames until each had been heard. This opens them in the background instead.
     *
     * **It runs only while nothing is loaded**, and that is a hard requirement rather than
     * politeness: sc68 keeps its 68000 emulator in global state, so opening a second instance while
     * one is playing would clobber the one you are listening to. Waiting for idle is also exactly
     * the lowest priority the owner asked for. The consequence — adding a folder mid-playback
     * resolves nothing until you stop — is real and accepted.
     */
    private fun resolveMetadataInBackground() {
        resolveJob?.cancel()
        resolveJob = scope.launch {
            // Only the ones that would learn something. A track whose title already differs from its
            // filename has been resolved before.
            val pending = _state.value.queue.tracks.filter {
                it.author.isBlank() && it.title == it.fileNameOrTitle
            }
            if (pending.isEmpty()) return@launch

            for (ref in pending) {
                ensureActive()
                // Anything the user asked for outranks this, indefinitely.
                while (track != null || _state.value.loadingTrack) delay(IDLE_CHECK_MS)
                delay(RESOLVE_GAP_MS)

                val startedAt = SystemClock.elapsedRealtime()
                val bytes = loadBytes(ref) ?: continue
                ensureActive()
                if (track != null) continue // something started while the file was being read

                // describe() and close() were on the caller's thread, which is the main one --
                // close() destroys a decoder, and for sc68 that is an emulator being torn down.
                // All of it belongs on the background thread, not just the open.
                val described = withContext(backgroundWork) {
                    val opened = NativeEngine.open(bytes, ref.fileNameOrTitle).track
                        ?: return@withContext null
                    val text = opened.describe()
                    opened.close()
                    text
                } ?: continue

                // Logged rather than guessed at. The owner reported twenty seconds of stutter for
                // twenty-two tracks and the first explanation -- a database write per track -- was
                // wrong, which a number here would have shown immediately.
                android.util.Log.d(
                    "Protracktor",
                    "resolved ${ref.fileNameOrTitle} in ${SystemClock.elapsedRealtime() - startedAt} ms",
                )
                adoptTitleFrom(described, ref)
            }
        }
    }

    /** Reads a track's bytes, from wherever it lives. */
    private suspend fun loadBytes(ref: TrackRef): ByteArray? =
        if (archiveCatalogueOf(ref.id) != null) {
            // "<catalogue>://<entry>" -- read out of the archive that catalogue shipped as, which is
            // already on disk. No network, which is why an archive catalogue is worth its download.
            remoteFiles.readFromArchive(ref.id.substringBefore("://"), ref.id.substringAfter("://"))
        } else if (ref.id.startsWith("http")) {
            remoteFiles.fetch(ref.id)
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .openInputStream(Uri.parse(ref.id))?.use { it.readBytes() }
                }.getOrNull()
            }
        }

    /**
     * Starts reading whatever comes next.
     *
     * Cancelled and restarted whenever the queue moves, because a read of a track the user has
     * already skipped past is a read competing with the one they are waiting for.
     */
    /**
     * Reads ahead whatever is coming, so pressing next costs nothing.
     *
     * In Random that is several tracks, because Random is where the wait was worst: every tune came
     * off the network and nothing had been decided early enough to fetch it in advance. Deciding
     * early is [fillRandomQueue]'s job; this only fetches what has been decided.
     */
    private fun prefetchUpcoming() {
        val wanted = if (_state.value.transient != null) {
            randomHistory.drop(randomCursor + 1).take(READ_AHEAD)
        } else {
            listOfNotNull(_state.value.queue.upcoming)
        }

        // The eviction rule, and it is exact rather than a budget: what is no longer coming is no
        // longer wanted. Pressing previous or switching playlists drops what was read for the path
        // not taken, at the moment it stops being the path.
        prefetched.keys.retainAll(wanted.mapTo(mutableSetOf()) { it.id })

        val missing = wanted.filter { it.id !in prefetched }
        if (missing.isEmpty()) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            // **Together, not one after another.** These fetches spend nearly all their time
            // waiting on a network, so reading three in turn makes the third arrive three round
            // trips late -- which is the wait this whole thing exists to remove. Written serially
            // first, and the owner noticed on a device before any measurement here did.
            missing.forEach { ref ->
                launch {
                    val bytes = loadBytes(ref) ?: return@launch
                    // A large file is not worth holding in memory to save a second; these formats
                    // are kilobytes and anything of this size is not one of them.
                    if (bytes.size > MAX_PREFETCH_BYTES) return@launch
                    // Back on the scope's dispatcher (main), so the budget check and the write
                    // cannot interleave with another fetch finishing. The second bound is on the
                    // total, because three files each under the per-file limit are not under it.
                    if (prefetched.values.sumOf { it.size } + bytes.size <= MAX_PREFETCH_TOTAL_BYTES) {
                        prefetched[ref.id] = bytes
                    }
                }
            }
        }
    }

    private fun stopPlayback() {
        prefetchJob?.cancel()
        prefetched.clear()
        track?.close()
        track = null
        audioFocus.release()
    }

    /** Releases the module. The process is going away; nothing owns native memory after this. */
    fun release() {
        resolveJob?.cancel()
        openJob?.cancel()
        stopPlayback()
    }

}
