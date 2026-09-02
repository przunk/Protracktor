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
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.data.LibraryStore
import com.przunk.protracktor.data.SavedPlayerState
import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueStore
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.CatalogueTrack
import com.przunk.protracktor.data.SavedPlaylist
import com.przunk.protracktor.data.HistoryStore
import com.przunk.protracktor.data.SongLengthStore
import com.przunk.protracktor.data.SongLengths
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.RemoteFiles
import com.przunk.protracktor.engine.NativeEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    /** How many SID tunes HVSC has given us a length for. Zero until the database is downloaded. */
    val songLengthCount: Int = 0,

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

    /** The background metadata pass. Cancelled and restarted whenever the track list changes. */
    private var resolveJob: Job? = null

    private val store = LibraryStore(context)
    private val catalogues = CatalogueStore(context)
    private val remoteFiles = RemoteFiles(context)
    private val songLengths = SongLengthStore(context)
    private val history = HistoryStore(context)

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

    init {
        // Before anything can be opened: sc68 reads its replay binaries from a path, and an asset
        // inside an APK does not have one.
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
                } else {
                    _state.update { it.copy(positionSeconds = open.positionSeconds()) }
                }
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
                    restored = true,
                )
            }
            resolveMetadataInBackground()
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

    fun renameActivePlaylist(name: String) {
        if (name.isBlank()) return
        scope.launch {
            store.renamePlaylist(playlistId, name)
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
        _browse.update { it.copy(domain = domain, tracks = emptyList(), groups = emptyList()) }
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
        }
    }

    fun forgetFolder(uri: String) {
        scope.launch {
            store.forgetFolder(uri)
            _browse.update { it.copy(folders = store.grantedFolders(), openFolder = null, tracks = emptyList()) }
        }
    }

    fun openFolder(folder: GrantedFolder) {
        scope.launch {
            _browse.update { it.copy(openFolder = folder, loading = true, tracks = emptyList()) }
            val found = MediaScanner.scanTree(context, Uri.parse(folder.uri))
            _browse.update { it.copy(tracks = found, loading = false) }
        }
    }

    fun closeFolder() = _browse.update { it.copy(openFolder = null, tracks = emptyList()) }

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
                subtitle = entry.subtitle,
                sizeBytes = entry.sizeBytes,
                fileName = entry.fileName,
                author = entry.author,
            )
        }
        // Also the `tracks` list, because that is what the add-to-playlist machinery reads and
        // there is no reason history should be the one list you cannot add from.
        _browse.update { it.copy(history = played, tracks = played, loading = false) }
    }

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
            _browse.update { current ->
                current.copy(
                    catalogues = summaries,
                    songLengthCount = lengths,
                    // Everything indexed is searched until the user says otherwise. Starting with
                    // none ticked would make the first search return nothing and look broken.
                    searchCatalogues = current.searchCatalogues.ifEmpty {
                        summaries.filter { it.indexed }.map { it.id }.toSet()
                    },
                )
            }
        }
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
                catalogue.parseIndex(bytes) { name -> SupportedFormats.looksPlayable(name) }
            }
            catalogues.replaceIndex(catalogue, entries)
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
                it.copy(openCatalogue = summary, openFormat = null, openAuthor = null, loading = true, tracks = emptyList())
            }
            val formats = catalogues.formats(summary.id)
            _browse.update { it.copy(groups = formats, loading = false) }
        }
    }

    fun openFormat(format: String) {
        val catalogueId = _browse.value.openCatalogue?.id ?: return
        scope.launch {
            _browse.update { it.copy(openFormat = format, openAuthor = null, loading = true, tracks = emptyList()) }
            val authors = catalogues.authors(catalogueId, format)
            _browse.update { it.copy(groups = authors, loading = false) }
        }
    }

    fun openAuthor(author: String) {
        val current = _browse.value
        val catalogueId = current.openCatalogue?.id ?: return
        val format = current.openFormat ?: return
        scope.launch {
            _browse.update { it.copy(openAuthor = author, loading = true, tracks = emptyList()) }
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
        addToPlaylist(listOf(ref))
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
            val fromOnline = if (current.searchOnline && current.searchCatalogues.isNotEmpty()) {
                catalogues.search(current.query, current.searchCatalogues).map(::toTrackRef)
            } else {
                emptyList()
            }

            _browse.update { it.copy(tracks = fromLocal + fromOnline, loading = false) }
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

    // --- library ---    // --- library ------------------------------------------------------------------------------

    fun addFolder(treeUri: Uri) {
        scope.launch {
            _state.update { it.copy(scanning = true) }
            MediaScanner.persistPermission(context, treeUri, isTree = true)
            store.rememberFolder(
                GrantedFolder(uri = treeUri.toString(), displayName = MediaScanner.labelOf(treeUri))
            )
            val found = MediaScanner.scanTree(context, treeUri)
            appendTracks(found, ::describeAdded)
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
     * What to say after adding — usually nothing.
     *
     * A notice that repeats what the screen already shows is noise, and this one covered the very
     * rows it was reporting. The tracks appearing **is** the confirmation.
     *
     * It still speaks when the screen does not tell the story: nothing was added, or some were
     * silently skipped as duplicates. Both look identical to a button that did not work.
     */
    private fun describeAdded(added: Int, skipped: Int): Message? = when {
        added == 0 && skipped == 0 -> Message("Nothing playable found there.")
        added == 0 -> Message("Already in this playlist.")
        skipped == 0 -> null
        else -> Message("Added $added; $skipped already there.")
    }

    /** What [undoRemoval] would put back. Cleared once its notice is gone. */
    private var lastRemoval: Pair<Int, TrackRef>? = null

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
    fun removeTrack(index: Int) {
        val currentState = _state.value
        val removed = currentState.queue.tracks.getOrNull(index) ?: return
        val wasPlaying = currentState.queue.currentIndex == index

        if (wasPlaying) stopPlayback()
        lastRemoval = index to removed

        _state.update {
            it.copy(
                queue = it.queue.withTracks(it.queue.tracks.filterIndexed { i, _ -> i != index }),
                playing = if (wasPlaying) false else it.playing,
                metadata = if (wasPlaying) emptyMap() else it.metadata,
                positionSeconds = if (wasPlaying) 0.0 else it.positionSeconds,
                durationSeconds = if (wasPlaying) 0.0 else it.durationSeconds,
                dirty = true,
                message = Message(text = "Removed ${removed.title}", actionLabel = UNDO),
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

    /** Puts the last removed track back where it was. */
    fun undoRemoval() {
        val (index, track) = lastRemoval ?: return
        lastRemoval = null
        _state.update {
            val restored = it.queue.tracks.toMutableList().apply {
                // The list may have changed since; clamp rather than throw.
                add(index.coerceIn(0, size), track)
            }
            it.copy(queue = it.queue.withTracks(restored), dirty = true, message = null)
        }
    }

    fun dismissMessage() {
        lastRemoval = null
        _state.update { it.copy(message = null) }
    }

    // --- transport ----------------------------------------------------------------------------

    fun playAt(index: Int) = openAndPlay(_state.value.queue.startAt(index))

    fun next() {
        val now = _state.value
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

            val opened = withContext(Dispatchers.IO) { NativeEngine.open(bytes, ref.fileNameOrTitle) }
            if (opened == null) {
                // The reason, not just the verdict. "Not a format we can play" is wrong when a
                // backend claimed the file and then choked on it, which is exactly what sc68 does
                // with some SNDH files -- and the two are indistinguishable from outside.
                val reason = NativeEngine.lastOpenError()
                _state.update {
                    it.copy(
                        message = Message(
                            if (reason.isBlank()) "${ref.title}: no backend could open it"
                            else "${ref.title}: $reason"
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
            val duration = opened.durationSeconds().takeIf { it > 0.0 }
                ?: songLengths.secondsFor(bytes)?.firstOrNull()
                ?: 0.0
            _state.update {
                it.copy(
                    playing = started,
                    metadata = described,
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
        scope.launch { store.replaceTracks(playlistId, _state.value.queue.tracks) }
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

                val bytes = loadBytes(ref) ?: continue
                ensureActive()
                if (track != null) continue // something started while the file was being read

                val opened = withContext(Dispatchers.IO) {
                    NativeEngine.open(bytes, ref.fileNameOrTitle)
                } ?: continue
                val described = opened.describe()
                opened.close()
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
