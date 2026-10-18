// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.LearnedTitles
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.QueueSource
import com.przunk.protracktor.player.SessionSource
import com.przunk.protracktor.player.TrackRef

/** An inspection surface for the active source, never an editor of its collection. */
@Composable
fun QueueScreen(
    state: PlayerUiState,
    onPlay: (Int) -> Unit,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareAudio: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onSendToWeb: (List<TrackRef>) -> Unit,
    onAddToPlaylist: (TrackRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.activePlayback
    val tracks = active.order.tracks.map { LearnedTitles.display(it, state.learnedTitles) }
    Column(modifier.fillMaxHeight()) {
        Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        val source = (active.source as? QueueSource.Playlist)?.let { playlist ->
            playlist.copy(name = state.playlists.firstOrNull { it.id == playlist.id }?.name ?: playlist.name)
        } ?: active.source
        Text(queueSourceLabel(source), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        if (tracks.isEmpty()) Text(stringResource(if (state.loadingTrack) R.string.random_rolling else R.string.queue_empty), modifier = Modifier.padding(20.dp))
        else PlaylistBody(
            tracks = tracks, listState = rememberLazyListState(), currentIndex = active.order.currentIndex,
            onPlayAt = onPlay, onRemoveAt = {}, onMove = { _, _ -> },
            onShowNeighbours = onShowNeighbours, onShareFile = onShareFile, onShareAudio = onShareAudio,
            onShareLink = onShareLink, onSendToWeb = onSendToWeb,
            onAddToOtherPlaylist = onAddToPlaylist, onAddSelectedToPlaylist = {}, onRemoveMany = {},
            loadingCurrent = state.loadingTrack, contentPadding = PaddingValues(bottom = 24.dp),
            enabled = true, reorderable = false, readOnly = true, keyOf = { index, track -> "$index:${track.id}" },
        )
    }
}

@Composable
private fun queueSourceLabel(source: QueueSource): String = when (source) {
    is QueueSource.Playlist -> stringResource(R.string.queue_playlist, source.name)
    is QueueSource.Random -> stringResource(R.string.domain_random_title)
    QueueSource.External -> stringResource(R.string.queue_external)
    is QueueSource.Collection -> when (val origin = source.origin) {
        is SessionSource.Search -> stringResource(R.string.queue_search, origin.query)
        is SessionSource.Folder -> origin.path
        SessionSource.History -> stringResource(R.string.domain_history_title)
    }
}
