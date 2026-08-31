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
import com.przunk.protracktor.data.SavedPlaylist
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
    val playlists: List<SavedPlaylist> = emptyList(),
    val activePlaylistId: Long = 0L,
    /** False until the stored state has been read. Saving before then would erase it. */
    val restored: Boolean = false,
    /** Shown to the user and cleared when acknowledged. Silence after a press is a defect. */
    val message: Message? = null,
) {
    val current: TrackRef? get() = queue.current

    val activePlaylistName: String?
        get() = playlists.firstOrNull { it.id == activePlaylistId }?.name
}

/**
 * What the Browse screen is looking at.
 *
 * Separate from [PlayerUiState] because it is a different lifetime: browsing comes and goes while
 * playback does not, and folding it in would mean every scan tick recomposing the player.
 */
data class BrowseState(
    val folders: List<GrantedFolder> = emptyList(),
    val openFolder: GrantedFolder? = null,
    val tracks: List<TrackRef> = emptyList(),
    val loading: Boolean = false,
)

/**
 * Everything about playback, owned once per process.
 *
 * It used to live in the ViewModel. That cannot survive the screen going away, and playback has to:
 * a foreground service and the UI must look at the *same* player, not two that agree by accident.
 * The service holds this, the ViewModel forwards to it, and there is one answer to "what is
 * playing" no matter who asks.
 */
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

    private val store = LibraryStore(context)

    /** Which playlist is being edited. Resolved during [restore]; there is only one so far. */
    private var playlistId: Long = 0L

    /** Debounces writes. Every transport press changes state; the disk does not need each one. */
    private var saveJob: Job? = null

    init {
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

    private suspend fun saveNow() {
        if (!_state.value.restored) return // never overwrite stored state with an empty start-up one
        val snapshot = _state.value
        store.replaceTracks(playlistId, snapshot.queue.tracks)
        store.savePlayerState(
            SavedPlayerState(
                activePlaylistId = playlistId,
                currentTrackId = snapshot.current?.id,
                shuffle = snapshot.queue.shuffle,
                repeat = snapshot.queue.repeat,
            )
        )
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
                    metadata = emptyMap(),
                    positionSeconds = 0.0,
                    durationSeconds = 0.0,
                )
            }
            scheduleSave()
        }
    }

    // --- browsing -----------------------------------------------------------------------------

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

    /** Adds a chosen set to the active playlist. Duplicates are ignored rather than doubled. */
    fun addToPlaylist(tracks: List<TrackRef>) {
        if (tracks.isEmpty()) return
        appendTracks(tracks, describeAdded(tracks.size))
    }

    private fun describeAdded(count: Int): Message = Message(
        if (count == 1) "Added 1 track." else "Added $count tracks."
    )

    // --- library ------------------------------------------------------------------------------

    fun addFolder(treeUri: Uri) {
        scope.launch {
            _state.update { it.copy(scanning = true) }
            MediaScanner.persistPermission(context, treeUri, isTree = true)
            store.rememberFolder(
                GrantedFolder(uri = treeUri.toString(), displayName = MediaScanner.labelOf(treeUri))
            )
            val found = MediaScanner.scanTree(context, treeUri)
            appendTracks(found, describeScan(found.size))
        }
    }

    fun addFiles(uris: List<Uri>) {
        scope.launch {
            val found = withContext(Dispatchers.IO) { MediaScanner.fromDocuments(context, uris) }
            appendTracks(found, describeScan(found.size))
        }
    }

    private fun appendTracks(found: List<TrackRef>, message: Message) {
        _state.update { current ->
            // Rebuilding the queue rather than mutating it keeps the play history meaningful: the
            // indices it holds must keep pointing at the same tracks.
            val merged = current.queue.tracks + found.filterNot { new ->
                current.queue.tracks.any { it.id == new.id }
            }
            current.copy(
                queue = current.queue.withTracks(merged),
                scanning = false,
                message = message,
            )
        }
        scheduleSave()
    }

    private fun describeScan(count: Int): Message = Message(
        when (count) {
            0 -> "Nothing playable found there."
            1 -> "Added 1 track."
            else -> "Added $count tracks."
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
                message = Message(text = "Removed ${removed.title}", actionLabel = UNDO),
            )
        }
        scheduleSave()
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
            it.copy(queue = it.queue.withTracks(restored), message = null)
        }
        scheduleSave()
    }

    fun dismissMessage() {
        lastRemoval = null
        _state.update { it.copy(message = null) }
    }

    // --- transport ----------------------------------------------------------------------------

    fun playAt(index: Int) = openAndPlay(_state.value.queue.startAt(index))

    fun next() {
        val queue = _state.value.queue
        if (!queue.hasNext) return
        openAndPlay(queue.next())
    }

    fun previous() {
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
            _state.update { it.copy(playing = false) }
        } else {
            val restarted = if (open.isFinished()) open.restart() else open.start()
            _state.update { it.copy(playing = restarted) }
        }
    }

    /** Stops playing without forgetting what was loaded. Used by the notification's stop action. */
    fun pause() {
        val open = track ?: return
        open.stop()
        _state.update { it.copy(playing = false) }
    }

    fun toggleShuffle() =
        _state.update { it.copy(queue = it.queue.withShuffle(!it.queue.shuffle)) }.also { scheduleSave() }

    fun cycleRepeat() =
        _state.update { it.copy(queue = it.queue.withRepeat(it.queue.repeat.next())) }.also { scheduleSave() }

    // --- internals ----------------------------------------------------------------------------

    private fun handleTrackEnded() {
        val queue = _state.value.queue
        val advanced = queue.onTrackEnded()

        when {
            advanced == null -> {
                // End of the playlist with repeat off. Stop, but leave the track loaded so the
                // screen still says what was playing.
                track?.stop()
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

    private fun openAndPlay(queue: PlayQueue) {
        val ref = queue.current ?: return

        // The queue advances NOW, not inside the coroutine. Two quick presses of next both read the
        // old queue otherwise, and both advance to the same track.
        _state.update { it.copy(queue = queue, playing = false, positionSeconds = 0.0) }
        scheduleSave()

        openJob?.cancel()
        openJob = scope.launch {
            track?.close()
            track = null

            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .openInputStream(Uri.parse(ref.id))?.use { it.readBytes() }
                }.getOrNull()
            }
            ensureActive()

            if (bytes == null) {
                _state.update { it.copy(message = Message("Could not read ${ref.title}")) }
                return@launch
            }

            val opened = withContext(Dispatchers.IO) { NativeEngine.open(bytes) }
            if (opened == null) {
                _state.update {
                    it.copy(message = Message("${ref.title} is not a format we can play yet"))
                }
                return@launch
            }

            // Cancelled while the file was being read: throw away what was opened instead of
            // starting a stream nobody asked for and nobody will stop.
            if (!isActive) {
                opened.close()
                throw CancellationException()
            }

            track = opened
            val started = opened.start()
            _state.update {
                it.copy(
                    playing = started,
                    metadata = opened.describe(),
                    durationSeconds = opened.durationSeconds(),
                    positionSeconds = 0.0,
                    message = if (started) it.message else Message("Could not open the audio device"),
                )
            }
        }
    }

    private fun stopPlayback() {
        track?.close()
        track = null
    }

    /** Releases the module. The process is going away; nothing owns native memory after this. */
    fun release() {
        openJob?.cancel()
        stopPlayback()
    }

}
