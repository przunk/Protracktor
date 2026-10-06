// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.SavedPlayerState
import com.przunk.protracktor.data.SavedPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Applies session transitions to the existing flows; it keeps no independent queue or UI state. */
internal class PlaylistSessionState(
    private val player: MutableStateFlow<PlayerUiState>,
    private val browse: MutableStateFlow<BrowseState>,
    private val message: (Notice) -> Message,
) {
    enum class Notice { CHANGES_DISCARDED, PLAYLIST_EMPTIED }

    val tracks: List<TrackRef> get() = player.value.queue.tracks

    fun restore(
        saved: SavedPlayerState?,
        playlists: List<SavedPlaylist>,
        tracks: List<TrackRef>,
        playlistId: Long,
    ) {
        player.update { current ->
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
                fallbackLengthSeconds =
                    FallbackLength.fromStored(saved?.fallbackLengthSeconds ?: 0),
                restored = true,
            )
        }
        // The dice's scope, kept between runs now that the Random view shows what is set.
        browse.update { it.copy(randomScope = storedRandomScope(saved?.randomScope)) }
    }

    fun savedState(playlistId: Long): SavedPlayerState? {
        if (!player.value.restored) return null // never overwrite stored state with an empty start-up one
        val snapshot = player.value
        return SavedPlayerState(
            activePlaylistId = playlistId,
            // The queue's track, never a transient one: a random pick is not where the user
            // was, and restoring into it would be restoring somewhere they never chose to be.
            currentTrackId = snapshot.queue.current?.id,
            shuffle = snapshot.queue.shuffle,
            repeat = snapshot.queue.repeat,
            playAllSubsongs = snapshot.playAllSubsongs,
            fallbackLengthSeconds = snapshot.fallbackLengthSeconds,
            randomScope = browse.value.randomScope.stored(),
        )
    }

    fun showPlaylists(known: List<SavedPlaylist>) {
        player.update { it.copy(playlists = known) }
    }

    suspend fun reloadPlaylists(read: suspend () -> List<SavedPlaylist>) {
        player.update { it.copy(playlists = read()) }
    }

    fun markSaved() {
        // Saved silently. The Save button disappearing is the confirmation, and it is on
        // screen already.
        player.update { it.copy(dirty = false) }
    }

    fun discard(stored: List<TrackRef>) {
        player.update {
            it.copy(queue = it.queue.withTracks(stored), dirty = false, message = message(Notice.CHANGES_DISCARDED))
        }
    }

    suspend fun emptyPlaylist(read: suspend () -> List<SavedPlaylist>) {
        player.update {
            it.copy(
                queue = PlayQueue(tracks = emptyList(), shuffle = it.queue.shuffle, repeat = it.queue.repeat),
                playlists = read(),
                playing = false,
                dirty = false,
                metadata = emptyMap(),
                positionSeconds = 0.0,
                durationSeconds = 0.0,
                message = message(Notice.PLAYLIST_EMPTIED),
            )
        }
    }

    suspend fun switchPlaylist(id: Long, tracks: List<TrackRef>, read: suspend () -> List<SavedPlaylist>) {
        player.update {
            it.copy(
                queue = PlayQueue(tracks = tracks, shuffle = it.queue.shuffle, repeat = it.queue.repeat),
                playlists = read(),
                activePlaylistId = id,
                playing = false,
                dirty = false,
                metadata = emptyMap(),
                positionSeconds = 0.0,
                durationSeconds = 0.0,
            )
        }
    }
}
