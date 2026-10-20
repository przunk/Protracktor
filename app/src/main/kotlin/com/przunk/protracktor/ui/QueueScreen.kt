// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.przunk.protracktor.player.PlaybackRow
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.QueueSource
import com.przunk.protracktor.player.RepeatMode
import com.przunk.protracktor.player.SessionSource
import com.przunk.protracktor.player.TrackRef

/** A projection of source continuation and one-shot inserts, not the saved playlist's editor. */
@Composable
fun QueueScreen(
    state: PlayerUiState,
    onPlay: (Long) -> Unit,
    onEnqueue: (TrackRef, Boolean) -> Unit,
    onPlayPending: (Long) -> Unit,
    onRemovePending: (Long) -> Unit,
    onRemoveContext: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.activePlayback
    val rows = remember(active, state.schedule) { state.schedule.rows(active) }
    val playingIndex = rows.indexOfFirst { it.playing }
    val listState = key(state.schedule.generation) {
        rememberLazyListState(initialFirstVisibleItemIndex = playingIndex.coerceAtLeast(0))
    }
    var info by remember { mutableStateOf<TrackRef?>(null) }
    info?.let { TrackInfoDialog(it, { info = null }, it.id in state.listenedIds) }
    Column(modifier.fillMaxSize()) {
        val source = (active.source as? QueueSource.Playlist)?.let { playlist ->
            playlist.copy(name = state.playlists.firstOrNull { it.id == playlist.id }?.name ?: playlist.name)
        } ?: active.source
        Text(queueSourceLabel(source), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Text(stringResource(R.string.queue_swipe_hint), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        if (state.schedule.pending.isNotEmpty() && active.order.repeat == RepeatMode.ONE)
            Text(stringResource(R.string.queue_repeat_one_wait), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        if (rows.isEmpty()) Text(stringResource(R.string.queue_pending_empty), Modifier.padding(20.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(rows, key = { row -> if (row is PlaybackRow.Source) "${state.schedule.generation}:${row.key}" else row.key }) { row ->
                val manual = row as? PlaybackRow.Manual
                val track = LearnedTitles.display(row.track, state.learnedTitles)
                val fromHistory = manual?.request?.fromHistory ?: active.fromHistory
                QueueSwipeRow(onAdd = { onEnqueue(track, fromHistory) },
                    onRemove = { when (row) {
                        is PlaybackRow.Manual -> onRemovePending(row.request.id)
                        is PlaybackRow.Source -> onRemoveContext(row.id)
                    } }, modifier = Modifier.testTag("playback-row-${row.key}")) {
                    ListItem(
                        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (row.playing) FontWeight.Bold else FontWeight.Normal) },
                        supportingContent = { Text(listOf(track.displayAuthor, LearnedTitles.sourceLine(track))
                            .filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Column {
                            if (row.playing) Icon(PlayerIcons.Play, stringResource(R.string.a11y_now_playing_row), Modifier.size(24.dp))
                            if (manual != null) Icon(PlayerIcons.QueueAdd, stringResource(R.string.queue_manual_current),
                                Modifier.size(if (row.playing) 16.dp else 24.dp))
                            else if (!row.playing) Icon(PlayerIcons.Playlist, null, Modifier.size(24.dp))
                        } },
                        trailingContent = { IconButton(onClick = { info = track }, modifier = Modifier.testTag("queue-info-${row.key}")) {
                            Icon(PlayerIcons.Info, stringResource(R.string.queue_info_request, track.title))
                        } },
                        colors = ListItemDefaults.colors(containerColor = if (row.playing) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface),
                        modifier = Modifier.padding(start = if (manual != null) 16.dp else 0.dp).clickable {
                            when (row) {
                                is PlaybackRow.Manual -> onPlayPending(row.request.id)
                                is PlaybackRow.Source -> onPlay(row.id)
                            }
                        },
                    )
                }
            }
        }
    }
    KeepRowInView(listState, playingIndex.takeIf { it >= 0 }, rows.getOrNull(playingIndex)?.key)
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
