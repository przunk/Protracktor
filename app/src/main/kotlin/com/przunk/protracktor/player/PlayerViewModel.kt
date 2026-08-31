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

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.data.LibraryStore
import com.przunk.protracktor.data.SavedPlayerState
import com.przunk.protracktor.engine.NativeEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    /** False until the stored state has been read. Saving before then would erase it. */
    val restored: Boolean = false,
    /** Shown to the user and cleared when acknowledged. Silence after a press is a defect. */
    val message: Message? = null,
) {
    val current: TrackRef? get() = queue.current
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

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

    private val store = LibraryStore(application)

    /** Which playlist is being edited. Resolved during [restore]; there is only one so far. */
    private var playlistId: Long = 0L

    /** Debounces writes. Every transport press changes state; the disk does not need each one. */
    private var saveJob: Job? = null

    init {
        restore()
        // One loop drives both the progress bar and end-of-track handling. The native side flags
        // completion rather than calling back, so something has to look; since the progress bar
        // needs a tick anyway, this is that tick.
        viewModelScope.launch {
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
        viewModelScope.launch {
            playlistId = store.defaultPlaylistId(DEFAULT_PLAYLIST_NAME)
            val tracks = store.tracksIn(playlistId)
            val saved = store.loadPlayerState()

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

                current.copy(queue = queue, restored = true)
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
        saveJob = viewModelScope.launch {
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

    // --- library ------------------------------------------------------------------------------

    fun addFolder(treeUri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            MediaScanner.persistPermission(getApplication(), treeUri, isTree = true)
            store.rememberFolder(
                GrantedFolder(uri = treeUri.toString(), displayName = MediaScanner.labelOf(treeUri))
            )
            val found = MediaScanner.scanTree(getApplication(), treeUri)
            appendTracks(found, describeScan(found.size))
        }
    }

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { MediaScanner.fromDocuments(getApplication(), uris) }
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

    /**
     * Drops one track.
     *
     * Removing what is playing stops playback rather than jumping somewhere: silently starting a
     * different track because the user deleted this one is a surprise, and there is no reading of
     * "remove" that asks for it.
     */
    fun removeTrack(index: Int) {
        val currentState = _state.value
        val removed = currentState.queue.tracks.getOrNull(index) ?: return
        val wasPlaying = currentState.queue.currentIndex == index

        if (wasPlaying) stopPlayback()

        _state.update {
            it.copy(
                queue = it.queue.withTracks(it.queue.tracks.filterIndexed { i, _ -> i != index }),
                playing = if (wasPlaying) false else it.playing,
                metadata = if (wasPlaying) emptyMap() else it.metadata,
                positionSeconds = if (wasPlaying) 0.0 else it.positionSeconds,
                durationSeconds = if (wasPlaying) 0.0 else it.durationSeconds,
                message = Message("Removed ${removed.title}"),
            )
        }
        scheduleSave()
    }

    fun clearPlaylist() {
        stopPlayback()
        _state.update { PlayerUiState(message = Message("Playlist cleared."), restored = true) }
        scheduleSave()
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

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
        openJob = viewModelScope.launch {
            track?.close()
            track = null

            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver
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

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }

    private companion object {
        // Fast enough that a progress bar does not visibly step, slow enough to be free.
        const val POLL_INTERVAL_MS = 200L
        const val SAVE_DEBOUNCE_MS = 400L
        const val DEFAULT_PLAYLIST_NAME = "Playlist"
    }
}
