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
    val ownsPlayback: Boolean get() = !player.value.manualCurrent &&
        player.value.activePlayback.belongsTo(player.value.activePlaylistId)

    data class WriteSnapshot(val playlistId: Long, val tracks: List<TrackRef>)

    /** Persist the published selected draft, never an independently playing source. */
    fun writeSnapshot(expectedPlaylistId: Long): WriteSnapshot? {
        val current = player.value
        if (!current.restored || current.activePlaylistId != expectedPlaylistId) return null
        return WriteSnapshot(current.activePlaylistId, current.queue.tracks.toList())
    }

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
                playback = current.playback ?: ActiveQueue(QueueSource.Playlist(playlistId, playlists.firstOrNull { it.id == playlistId }?.name.orEmpty()), queue),
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
            shuffle = snapshot.transportQueue.shuffle,
            repeat = snapshot.transportQueue.repeat,
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

    fun markSaved(saved: WriteSnapshot) {
        // Transport changes are not playlist edits; only identity and persisted contents matter.
        player.update {
            if (it.activePlaylistId == saved.playlistId && it.queue.tracks == saved.tracks) {
                it.copy(dirty = false)
            } else it
        }
    }

    fun discard(playlistId: Long, stored: List<TrackRef>) {
        player.update {
            if (it.activePlaylistId == playlistId) {
                it.withEditorQueue(it.queue.withTracks(stored)).copy(dirty = false, message = message(Notice.CHANGES_DISCARDED))
            } else it
        }
    }

    suspend fun emptyPlaylist(read: suspend () -> List<SavedPlaylist>) {
        player.update {
            val owns = !it.manualCurrent && it.activePlayback.belongsTo(it.activePlaylistId)
            it.withEditorQueue(PlayQueue(emptyList(), shuffle = it.queue.shuffle, repeat = it.queue.repeat)).copy(
                schedule = if (owns) it.schedule.resetTraversal() else it.schedule,
                playlists = read(),
                playing = if (owns) false else it.playing,
                dirty = false,
                metadata = if (owns) emptyMap() else it.metadata,
                positionSeconds = if (owns) 0.0 else it.positionSeconds,
                durationSeconds = if (owns) 0.0 else it.durationSeconds,
                message = message(Notice.PLAYLIST_EMPTIED),
            )
        }
    }

    fun switchPlaylist(id: Long, tracks: List<TrackRef>, playlists: List<SavedPlaylist>) {
        player.update {
            val active = it.activePlayback
            val next = if (active.belongsTo(id)) active.order.withTracks(tracks)
                else PlayQueue(tracks = tracks, shuffle = it.transportQueue.shuffle, repeat = it.transportQueue.repeat)
            it.copy(
                playback = if (active.belongsTo(id)) active.withOrder(next) else active,
                queue = next,
                playlists = playlists,
                activePlaylistId = id,
                dirty = false,
            )
        }
    }
}
