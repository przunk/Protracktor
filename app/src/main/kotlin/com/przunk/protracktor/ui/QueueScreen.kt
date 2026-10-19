// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.LearnedTitles
import com.przunk.protracktor.player.NowPlayingText
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.QueueSource
import com.przunk.protracktor.player.RepeatMode
import com.przunk.protracktor.player.SessionSource
import com.przunk.protracktor.player.TrackRef

/** Inspect the source context or edit deliberate pending requests, never the source collection. */
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
    onEnqueue: ((TrackRef) -> Unit)? = null,
    onPlayPending: (Long) -> Unit = {},
    onRemovePending: (Long) -> Unit = {},
) {
    val active = state.activePlayback
    val tracks = active.order.tracks.map { LearnedTitles.display(it, state.learnedTitles) }
    val contextList = rememberLazyListState(initialFirstVisibleItemIndex = active.order.currentIndex ?: 0)
    val pendingList = rememberLazyListState()
    var tab by remember { mutableIntStateOf(0) }
    var showingInfo by remember { mutableStateOf<TrackRef?>(null) }
    showingInfo?.let { TrackInfoDialog(it, { showingInfo = null }) }
    Column(modifier.fillMaxHeight()) {
        Text(stringResource(R.string.queue_panel_title), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        state.current?.let { current ->
            Text(stringResource(R.string.queue_current_track, current.title), fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp).testTag("queue-current-track"))
            Text(NowPlayingText.subtitle(state, stringResource(R.string.dock_loading), stringResource(R.string.dock_idle_subtitle)),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
            if (state.manualCurrent) Text(stringResource(R.string.queue_manual_current),
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (state.schedule.pending.isNotEmpty() && state.transportQueue.repeat == RepeatMode.ONE) {
            Text(stringResource(R.string.queue_repeat_one_wait), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.testTag("playback-context-tab"),
                icon = { Icon(PlayerIcons.Playlist, null) }, text = { Text(stringResource(R.string.queue_context)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.testTag("playback-pending-tab"),
                icon = { Icon(PlayerIcons.QueueAdd, null) }, text = { Text(stringResource(R.string.queue_title)) })
        }
        Box(Modifier.weight(1f)) {
            if (tab == 1) PendingQueueScreen(state.schedule.pending, state.learnedTitles, pendingList,
                onPlayPending, onRemovePending, { showingInfo = it })
            else Column {
                val source = (active.source as? QueueSource.Playlist)?.let { playlist ->
                    playlist.copy(name = state.playlists.firstOrNull { it.id == playlist.id }?.name ?: playlist.name)
                } ?: active.source
                Text(if (tracks.isEmpty() && source !is QueueSource.Random) stringResource(R.string.queue_no_context)
                    else queueSourceLabel(source), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                if (state.manualCurrent) active.current?.let { anchor ->
                    Text(stringResource(R.string.queue_context_anchor, anchor.title),
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 20.dp))
                }
                if (tracks.isEmpty()) Text(stringResource(if (state.loadingTrack && !state.manualCurrent) R.string.random_rolling else R.string.queue_empty),
                    modifier = Modifier.padding(20.dp))
                else PlaylistBody(
                    tracks = tracks, listState = contextList,
                    currentIndex = active.order.currentIndex.takeUnless { state.manualCurrent },
                    contextIndex = active.order.currentIndex.takeIf { state.manualCurrent },
                    onPlayAt = onPlay, onRemoveAt = {}, onMove = { _, _ -> },
                    onShowNeighbours = onShowNeighbours, onShareFile = onShareFile, onShareAudio = onShareAudio,
                    onShareLink = onShareLink, onSendToWeb = onSendToWeb,
                    onAddToOtherPlaylist = onAddToPlaylist, onAddSelectedToPlaylist = {}, onRemoveMany = {},
                    onEnqueue = onEnqueue,
                    loadingCurrent = state.loadingTrack && !state.manualCurrent, contentPadding = PaddingValues(bottom = 24.dp),
                    enabled = true, reorderable = false, readOnly = true, keyOf = { index, track -> "$index:${track.id}" },
                )
            }
        }
    }
}

@Composable
private fun queueSourceLabel(source: QueueSource): String = when (source) {
    QueueSource.None -> stringResource(R.string.queue_no_context)
    is QueueSource.Playlist -> stringResource(R.string.queue_playlist, source.name)
    is QueueSource.Random -> stringResource(R.string.domain_random_title)
    QueueSource.External -> stringResource(R.string.queue_external)
    is QueueSource.Collection -> when (val origin = source.origin) {
        is SessionSource.Search -> stringResource(R.string.queue_search, origin.query)
        is SessionSource.Folder -> origin.path
        SessionSource.History -> stringResource(R.string.domain_history_title)
    }
}
