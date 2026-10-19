// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.SavedPlayerState
import com.przunk.protracktor.data.SavedPlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns playlist-session persistence and its jobs, not the decoder or the working queue. */
internal class PlaylistSession(
    private val scope: CoroutineScope,
    private val store: Store,
    private val state: PlaylistSessionState,
    private val defaultPlaylistName: () -> String,
    private val stopPlayback: () -> Unit,
    private val resolveMetadataInBackground: () -> Unit,
) {
    companion object {
        private const val SAVE_DEBOUNCE_MS = 400L

        /**
         * How long the track list waits before being written.
         *
         * Longer than the controller's `RESOLVE_GAP_MS` by an order of magnitude, so a run of
         * resolutions collapses into one write rather than one write each.
         */
        private const val TRACK_WRITE_DEBOUNCE_MS = 1500L
    }

    interface Store {
        suspend fun loadPlayerState(): SavedPlayerState?
        suspend fun savePlayerState(saved: SavedPlayerState)
        suspend fun playlists(): List<SavedPlaylist>
        suspend fun defaultPlaylistId(name: String): Long
        suspend fun tracksIn(id: Long): List<TrackRef>
        suspend fun replaceTracks(id: Long, tracks: List<TrackRef>)
        suspend fun createPlaylist(name: String): Long
        suspend fun renamePlaylist(id: Long, name: String)
        suspend fun deletePlaylist(id: Long)
    }

    /** Published persistence target; a switch changes it only when destination data is ready. */
    var playlistId: Long = 0L
        private set

    /** Debounces writes. Every transport press changes state; the disk does not need each one. */
    private var saveJob: Job? = null
    private var trackWriteJob: Job? = null
    private var trackWriteTarget: Long? = null
    private val trackWrites = Mutex()
    private val settingsWrites = Mutex()
    private var activationGeneration = 0L
    private var activating = false
    private data class TrackWrite(val snapshot: PlaylistSessionState.WriteSnapshot, val revision: Long)
    private val writeRevisions = mutableMapOf<Long, Long>()
    private val deletedPlaylists = mutableSetOf<Long>()

    private fun captureWrite(): TrackWrite? {
        if (activating) return null
        val snapshot = state.writeSnapshot(playlistId) ?: return null
        if (snapshot.playlistId in deletedPlaylists) return null
        return TrackWrite(snapshot, writeRevisions[snapshot.playlistId] ?: 0L)
    }

    fun restore() {
        scope.launch {
            val saved = store.loadPlayerState()
            // Read once and used twice. `defaultPlaylistId` can create one, so the list is read
            // again only when it did; asking the database the same question twice at launch is a
            // query nobody needs on the path A48 is about.
            val known = store.playlists()
            // The stored active playlist, unless it has since been deleted.
            val stored = known.firstOrNull { it.id == saved?.activePlaylistId }?.id
            playlistId = stored ?: store.defaultPlaylistId(defaultPlaylistName())
            val playlists = if (stored != null) known else store.playlists()
            val tracks = store.tracksIn(playlistId)

            state.restore(saved, playlists, tracks, playlistId)
            resolveMetadataInBackground()
        }
    }

    /**
     * Debounces full playlist rewrites while metadata arrives; UI updates remain immediate.
     * Capture ownership and contents together before navigation can change either of them.
     */
    fun scheduleTrackWrite() {
        val request = captureWrite() ?: return
        trackWriteJob?.cancel()
        trackWriteTarget = request.snapshot.playlistId
        trackWriteJob = scope.launch {
            delay(TRACK_WRITE_DEBOUNCE_MS)
            if (writeTracks(request)) refreshPlaylists()
        }
    }

    private fun cancelPendingTrackWrite(id: Long) {
        if (trackWriteTarget == id) trackWriteJob?.cancel()
    }

    private fun invalidateTrackWrites(id: Long) {
        writeRevisions[id] = (writeRevisions[id] ?: 0L) + 1L
        cancelPendingTrackWrite(id)
    }

    private fun isCurrent(request: TrackWrite): Boolean =
        request.snapshot.playlistId !in deletedPlaylists &&
            request.revision == (writeRevisions[request.snapshot.playlistId] ?: 0L)

    private suspend fun writeTracks(request: TrackWrite, markSaved: Boolean = false): Boolean =
        // A newer explicit save must not finish first and then be overwritten by an older one.
        trackWrites.withLock {
            if (!isCurrent(request)) return@withLock false
            val snapshot = request.snapshot
            store.replaceTracks(snapshot.playlistId, snapshot.tracks)
            if (markSaved && isCurrent(request)) state.markSaved(snapshot)
            true
        }

    /** Automatic additions persist immediately without changing the user's editing state. */
    fun writeTracks() {
        val request = captureWrite() ?: return
        cancelPendingTrackWrite(request.snapshot.playlistId)
        scope.launch {
            if (writeTracks(request)) refreshPlaylists()
        }
    }

    /**
     * The switcher counts what each playlist holds, so every write that changes a count says so.
     *
     * `docs/STATUS.md` C44: making, renaming and deleting a playlist already re-read the list;
     * the writes that change only its **contents** did not, so "Add to playlist…" wrote the track
     * and left the number on screen as it was until the playlist was opened.
     */
    suspend fun refreshPlaylists() {
        val known = store.playlists()
        state.showPlaylists(known)
    }

    /**
     * Writes the current state, shortly.
     *
     * Debounced because every press of shuffle, repeat, next and previous changes something worth
     * keeping, and none of them is worth a disk write on its own.
     */
    fun scheduleSave() {
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
    private suspend fun saveNow(allowed: () -> Boolean = { true }) = settingsWrites.withLock {
        if (!allowed()) return@withLock
        val saved = state.savedState(playlistId) ?: return@withLock
        store.savePlayerState(saved)
    }

    /** Commits the edited list. The diskette button. */
    fun savePlaylist(onComplete: ((Boolean) -> Unit)? = null) {
        val request = captureWrite()
        if (request == null) {
            onComplete?.invoke(false)
            return
        }
        cancelPendingTrackWrite(request.snapshot.playlistId)
        scope.launch {
            if (onComplete == null) {
                writeTracks(request, markSaved = true)
            } else {
                val written = try {
                    writeTracks(request, markSaved = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                onComplete(written)
            }
        }
    }

    /** Throws the edits away and goes back to what is on disk. */
    fun discardChanges() {
        val request = captureWrite() ?: return
        val id = request.snapshot.playlistId
        invalidateTrackWrites(id)
        scope.launch {
            trackWrites.withLock {
                val stored = store.tracksIn(id)
                state.discard(id, stored)
                // Also retire requests captured while the old queue was visible during the read.
                invalidateTrackWrites(id)
            }
        }
    }

    // --- playlists ----------------------------------------------------------------------------

    fun createPlaylist(name: String) {
        scope.launch {
            val id = store.createPlaylist(name.ifBlank { defaultPlaylistName() })
            state.reloadPlaylists(store::playlists)
            switchToPlaylist(id)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        scope.launch {
            store.renamePlaylist(id, name)
            state.reloadPlaylists(store::playlists)
        }
    }

    /**
     * Deletes a playlist — or empties it, when it is the only one there is. The tracks stay in the
     * library; only the list goes.
     *
     * **The last playlist is not refused, it is reset.** Refusing it means the user presses Delete,
     * confirms, and nothing happens — and the notice explaining why loses a race with the sheet the
     * button lives in, so not even the explanation arrives.
     *
     * Somebody deleting their only playlist wants it gone, and the closest thing to gone that can
     * exist is empty and called what a new one would be called. There is nothing to undo that the
     * confirmation did not already ask about.
     */
    fun deletePlaylist(id: Long) {
        invalidateTrackWrites(id)
        scope.launch {
            if (store.playlists().size <= 1) {
                trackWrites.withLock {
                    store.replaceTracks(id, emptyList())
                    store.renamePlaylist(id, defaultPlaylistName())
                    if (state.ownsPlayback) stopPlayback()
                    state.emptyPlaylist(store::playlists)
                    invalidateTrackWrites(id)
                }
                return@launch
            }
            trackWrites.withLock {
                store.deletePlaylist(id)
                deletedPlaylists += id
            }
            val remaining = store.playlists()
            state.showPlaylists(remaining)
            if (id == playlistId) switchToPlaylist(remaining.first().id)
        }
    }

    /**
     * Switches the active playlist and abandons edits after the UI has confirmed their loss.
     * Browsing another playlist never transfers playback ownership.
     */
    fun switchToPlaylist(id: Long) {
        val generation = ++activationGeneration
        activating = true
        fun current() = generation == activationGeneration
        scope.launch {
            try {
                saveNow(::current)
                if (!current()) return@launch
                val tracks = store.tracksIn(id)
                if (!current()) return@launch
                val playlists = store.playlists()
                if (!current()) return@launch
                // No suspension between assigning ownership and publishing its queue.
                playlistId = id
                activating = false
                state.switchPlaylist(id, tracks, playlists)
                if (!current()) return@launch
                scheduleSave()
                resolveMetadataInBackground()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Superseded failures belong to the abandoned request, not the new selection.
                if (current()) throw failure
            } finally {
                if (current()) activating = false
            }
        }
    }
}
