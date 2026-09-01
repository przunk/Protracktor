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
import android.net.Uri
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.data.LibraryStore
import com.przunk.protracktor.data.SavedPlayerState
import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueStore
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.CatalogueTrack
import com.przunk.protracktor.data.SavedPlaylist
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val current: TrackRef? get() = transient ?: queue.current

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
    val canGoNext: Boolean get() = if (randomMode) true else queue.hasNext
    val canGoPrevious: Boolean get() = if (randomMode) randomHasPrevious else queue.hasPrevious

    val activePlaylistName: String?
        get() = playlists.firstOrNull { it.id == activePlaylistId }?.name

    /** True while Random is driving playback rather than the playlist. */
    val randomMode: Boolean get() = transient != null
}

/** Which part of Browse is on screen. Back moves one step towards [ROOT]. */
enum class BrowseDomain { ROOT, LOCAL, ONLINE, SEARCH }

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
        const val DEFAULT_PLAYLIST_NAME = "Playlist"

        /** Recognised by the UI, which turns it into the localised label on the snackbar action. */
        const val UNDO = "undo"

        /** Four megabytes. Comfortably above any tracker module and below anything worth holding. */
        private const val MAX_PREFETCH_BYTES = 4 * 1024 * 1024
    }

    // Main.immediate so a press and the state change it causes land in the same frame; the work
    // itself moves to IO where it belongs.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

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
    private var prefetched: Pair<String, ByteArray>? = null
    private var prefetchJob: Job? = null

    private val store = LibraryStore(context)
    private val catalogues = CatalogueStore(context)
    private val remoteFiles = RemoteFiles(context)

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
            _state.update { it.copy(dirty = false, message = Message("Playlist saved.")) }
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
        }
    }

    // --- browsing -----------------------------------------------------------------------------

    fun openDomain(domain: BrowseDomain) {
        _browse.update { it.copy(domain = domain, tracks = emptyList(), groups = emptyList()) }
        when (domain) {
            BrowseDomain.LOCAL -> refreshFolders()
            BrowseDomain.ONLINE, BrowseDomain.SEARCH -> refreshCatalogues()
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

    // --- online catalogues --------------------------------------------------------------------

    fun refreshCatalogues() {
        scope.launch {
            val summaries = catalogues.summaries()
            _browse.update { current ->
                current.copy(
                    catalogues = summaries,
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
            val entries = withContext(Dispatchers.Default) {
                catalogue.parseIndex(bytes) { name -> SupportedFormats.looksPlayable(name) }
            }
            catalogues.replaceIndex(catalogue, entries)
            _browse.update { it.copy(indexing = null, catalogues = catalogues.summaries()) }
            _state.update { it.copy(message = Message("Indexed ${entries.size} tracks from ${catalogue.displayName}.")) }
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
    fun playRandom() {
        scope.launch {
            val picked = catalogues.random()
            if (picked == null) {
                _state.update {
                    it.copy(message = Message("Nothing is indexed yet. Index a catalogue first."))
                }
                return@launch
            }
            val ref = toTrackRef(picked)
            // Anything ahead of the cursor is abandoned, the way it is in any other history: going
            // back and then picking something new discards the branch you left.
            while (randomHistory.lastIndex > randomCursor) randomHistory.removeAt(randomHistory.lastIndex)
            randomHistory += ref
            randomCursor = randomHistory.lastIndex
            playTransient(ref)
        }
    }

    private fun randomNext() {
        if (randomCursor < randomHistory.lastIndex) {
            randomCursor++
            playTransient(randomHistory[randomCursor])
        } else {
            playRandom()
        }
    }

    private fun randomPrevious() {
        if (randomCursor <= 0) return
        randomCursor--
        playTransient(randomHistory[randomCursor])
    }

    /**
     * Keeps what is playing, without leaving Random.
     *
     * The owner asked for these to be separate: adding a track you like should not end the sequence
     * you are listening through.
     */
    fun keepTransient() {
        val ref = _state.value.transient ?: return
        addToPlaylist(listOf(ref))
    }

    /** Leaves Random and goes back to the playlist. Playback stops; the playlist is where you were. */
    fun exitRandom() {
        if (_state.value.transient == null) return
        stopPlayback()
        randomHistory.clear()
        randomCursor = -1
        _state.update {
            it.copy(
                transient = null,
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
            subtitle = listOf(track.format, track.author).filter { it.isNotBlank() }.joinToString(" · "),
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

    private fun appendTracks(found: List<TrackRef>, describe: (added: Int, skipped: Int) -> Message) {
        var added = 0
        var skipped = 0
        _state.update { current ->
            // Rebuilding the queue rather than mutating it keeps the play history meaningful: the
            // indices it holds must keep pointing at the same tracks.
            val merged = current.queue.tracks.toMutableList()
            found.forEach { candidate ->
                if (merged.any { it.sameFileAs(candidate) }) skipped++ else { merged += candidate; added++ }
            }
            current.copy(
                queue = current.queue.withTracks(merged),
                scanning = false,
                dirty = current.dirty || added > 0,
                message = describe(added, skipped),
            )
        }
    }

    /**
     * What to say after adding.
     *
     * Duplicates skipped are reported rather than swallowed. Silently adding nothing looks exactly
     * like a button that did not work.
     */
    private fun describeAdded(added: Int, skipped: Int): Message = Message(
        when {
            added == 0 && skipped == 0 -> "Nothing playable found there."
            added == 0 -> "Already in this playlist."
            skipped == 0 -> if (added == 1) "Added 1 track." else "Added $added tracks."
            else -> "Added $added; $skipped already there."
        }
    )

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
        if (_state.value.transient != null) return randomNext()
        val queue = _state.value.queue
        if (!queue.hasNext) return
        openAndPlay(queue.next())
    }

    fun previous() {
        if (_state.value.transient != null) return randomPrevious()
        val queue = _state.value.queue
        if (!queue.hasPrevious) return
        openAndPlay(queue.previous())
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
        // A transient track ending stops playback. Rolling on into the playlist would be answering
        // a question the user did not ask by pressing Random.
        if (_state.value.transient != null) {
            track?.stop()
            audioFocus.release()
            _state.update { it.copy(playing = false, positionSeconds = it.durationSeconds) }
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
        _state.update {
            it.copy(queue = queue, transient = null, randomHasPrevious = false, playing = false, positionSeconds = 0.0)
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
            val ready = prefetched?.takeIf { it.first == ref.id }?.second
            prefetched = null

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

            val opened = withContext(Dispatchers.IO) { NativeEngine.open(bytes) }
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
            _state.update {
                it.copy(
                    playing = started,
                    metadata = described,
                    durationSeconds = opened.durationSeconds(),
                    positionSeconds = 0.0,
                    message = if (started) it.message else Message("Could not open the audio device"),
                )
            }
            adoptTitleFrom(described, ref)

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
    private fun adoptTitleFrom(described: Map<String, String>, ref: TrackRef) {
        val realTitle = described["title"]?.trim().orEmpty()
        // Backends disagree on which key carries it, so both are asked before giving up.
        val realAuthor = described["artist"]?.trim()?.ifBlank { null }
            ?: described["composer"]?.trim().orEmpty()

        val newTitle = realTitle.ifBlank { ref.title }
        val newAuthor = realAuthor.ifBlank { ref.author }
        if (newTitle == ref.title && newAuthor == ref.author) return

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
    }

    /** Reads a track's bytes, from wherever it lives. */
    private suspend fun loadBytes(ref: TrackRef): ByteArray? =
        if (ref.id.startsWith("http")) {
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
    private fun prefetchUpcoming() {
        val upcoming = _state.value.queue.upcoming ?: return
        if (prefetched?.first == upcoming.id) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            val bytes = loadBytes(upcoming) ?: return@launch
            // A large file is not worth holding in memory to save a second; these formats are
            // kilobytes and anything of this size is not one of them.
            if (bytes.size <= MAX_PREFETCH_BYTES) prefetched = upcoming.id to bytes
        }
    }

    private fun stopPlayback() {
        prefetchJob?.cancel()
        prefetched = null
        track?.close()
        track = null
        audioFocus.release()
    }

    /** Releases the module. The process is going away; nothing owns native memory after this. */
    fun release() {
        openJob?.cancel()
        stopPlayback()
    }

}
