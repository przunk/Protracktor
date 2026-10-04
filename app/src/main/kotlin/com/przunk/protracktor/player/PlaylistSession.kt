// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.SavedPlayerState
import com.przunk.protracktor.data.SavedPlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    /** Which playlist is being edited. Resolved during [restore]; there is only one so far. */
    var playlistId: Long = 0L
        private set

    /** Debounces writes. Every transport press changes state; the disk does not need each one. */
    private var saveJob: Job? = null
    private var trackWriteJob: Job? = null

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
     * Writes the track list, shortly.
     *
     * **Debounced, and it has to be.** `replaceTracks` deletes every row of the playlist and
     * reinserts it — two inserts per track — and background metadata resolution identifies a
     * track every 120 ms. Undebounced, a playlist of three hundred means some six hundred inserts
     * eight times a second, into the same database the list is being read from, for as long as the
     * resolution runs, and the list stutters for as long as it does.
     *
     * The state still updates per track, so titles appear as they are learned. It is only the disk
     * that waits.
     */
    fun scheduleTrackWrite() {
        trackWriteJob?.cancel()
        trackWriteJob = scope.launch {
            delay(TRACK_WRITE_DEBOUNCE_MS)
            store.replaceTracks(playlistId, state.tracks)
            refreshPlaylists()
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
    private suspend fun saveNow() {
        val saved = state.savedState(playlistId) ?: return
        store.savePlayerState(saved)
    }

    /** Commits the edited list. The diskette button. */
    fun savePlaylist() {
        scope.launch {
            val tracks = state.tracks
            store.replaceTracks(playlistId, tracks)
            state.markSaved()
        }
    }

    /** Throws the edits away and goes back to what is on disk. */
    fun discardChanges() {
        scope.launch {
            val stored = store.tracksIn(playlistId)
            state.discard(stored)
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
        scope.launch {
            if (store.playlists().size <= 1) {
                store.replaceTracks(id, emptyList())
                store.renamePlaylist(id, defaultPlaylistName())
                stopPlayback()
                state.emptyPlaylist(store::playlists)
                return@launch
            }
            store.deletePlaylist(id)
            val remaining = store.playlists()
            state.showPlaylists(remaining)
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
            state.switchPlaylist(id, tracks, store::playlists)
            scheduleSave()
            resolveMetadataInBackground()
        }
    }
}
