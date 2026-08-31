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
import com.przunk.protracktor.engine.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val queue: PlayQueue = PlayQueue(tracks = emptyList()),
    val playing: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val scanning: Boolean = false,
    /** Shown to the user and cleared when acknowledged. Silence after a press is a defect. */
    val message: String? = null,
) {
    val current: TrackRef? get() = queue.current
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** The open module. Owned here because native memory is invisible to the garbage collector. */
    private var track: NativeEngine.Track? = null

    init {
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

    // --- library ------------------------------------------------------------------------------

    fun addFolder(treeUri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            MediaScanner.persistPermission(getApplication(), treeUri, isTree = true)
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

    private fun appendTracks(found: List<TrackRef>, message: String) {
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
    }

    private fun describeScan(count: Int): String = when (count) {
        0 -> "Nothing playable found there."
        1 -> "Added 1 track."
        else -> "Added $count tracks."
    }

    fun clearPlaylist() {
        stopPlayback()
        _state.update { PlayerUiState(message = "Playlist cleared.") }
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

    fun togglePlayPause() {
        val open = track
        if (open == null) {
            // Nothing loaded: start at the top rather than doing nothing quietly.
            if (_state.value.queue.tracks.isNotEmpty()) playAt(0)
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
        _state.update { it.copy(queue = it.queue.withShuffle(!it.queue.shuffle)) }

    fun cycleRepeat() =
        _state.update { it.copy(queue = it.queue.withRepeat(it.queue.repeat.next())) }

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
        viewModelScope.launch {
            track?.close()
            track = null
            _state.update { it.copy(queue = queue, playing = false, positionSeconds = 0.0) }

            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver
                        .openInputStream(Uri.parse(ref.id))?.use { it.readBytes() }
                }.getOrNull()
            }

            if (bytes == null) {
                _state.update { it.copy(message = "Could not read ${ref.title}") }
                return@launch
            }

            val opened = withContext(Dispatchers.IO) { NativeEngine.open(bytes) }
            if (opened == null) {
                _state.update { it.copy(message = "${ref.title} is not a format we can play yet") }
                return@launch
            }

            track = opened
            val started = opened.start()
            _state.update {
                it.copy(
                    playing = started,
                    metadata = opened.describe(),
                    durationSeconds = opened.durationSeconds(),
                    positionSeconds = 0.0,
                    message = if (started) null else "Could not open the audio device",
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
    }
}
