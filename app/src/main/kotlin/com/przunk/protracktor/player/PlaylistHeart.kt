// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal data class PlaylistHeart(
    val request: Request,
    val track: TrackRef,
    val playlistName: String,
    val member: Boolean,
    val saving: Boolean,
) {
    data class Request(val trackId: String, val playlistId: Long)

    companion object {
        fun of(state: PlayerUiState): PlaylistHeart? {
            val track = state.current ?: return null
            val name = state.activePlaylistName ?: return null
            if (!state.restored || state.activePlaylistId <= 0 || state.loadingTrack) return null
            return PlaylistHeart(Request(track.id, state.activePlaylistId), track, name,
                state.queue.tracks.any { it.sameFileAs(track) }, state.notificationPlaylistSaving)
        }
    }
}

/** Notification saves intentionally commit earlier edits as well as this addition (B48). */
internal class PlaylistHeartSave(
    private val player: MutableStateFlow<PlayerUiState>,
    private val save: ((Boolean) -> Unit) -> Unit,
    private val resolveMetadata: () -> Unit,
    private val notice: (Outcome, PlaylistHeart?) -> Unit,
) {
    enum class Outcome { ADDED_AND_SAVED, ALREADY_AND_SAVED, STALE, FAILED, SAVING }

    fun add(request: PlaylistHeart.Request?) {
        var accepted: PlaylistHeart? = null
        player.update { state ->
            val heart = PlaylistHeart.of(state)
            if (request == null || heart == null || heart.request != request || heart.saving) {
                accepted = null
                state
            } else {
                accepted = heart
                state.withEditorQueue(if (heart.member) state.queue
                    else state.queue.withTracks(state.queue.tracks + heart.track)).copy(
                    dirty = state.dirty || !heart.member,
                    notificationPlaylistSaving = true,
                )
            }
        }
        val heart = accepted
        if (heart == null) {
            notice(if (player.value.notificationPlaylistSaving) Outcome.SAVING else Outcome.STALE, null)
            return
        }
        // Save captures its playlist snapshot synchronously, before metadata or navigation changes.
        save { written ->
            player.update { it.copy(notificationPlaylistSaving = false) }
            notice(when {
                !written -> Outcome.FAILED
                heart.member -> Outcome.ALREADY_AND_SAVED
                else -> Outcome.ADDED_AND_SAVED
            }, heart)
        }
        if (!heart.member) resolveMetadata()
    }
}
