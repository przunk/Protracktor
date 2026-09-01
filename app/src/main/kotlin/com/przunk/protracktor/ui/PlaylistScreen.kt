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
package com.przunk.protracktor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.przunk.protracktor.R
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.SupportedFormats
import com.przunk.protracktor.player.TrackRef

/**
 * The one real destination (`docs/OPEN_QUESTIONS.md` Q1): the active playlist.
 *
 * Not the whole library. Shuffle and repeat operate inside this list (R6), so this is also the
 * thing whose contents the user needs to see.
 */
@Composable
fun PlaylistScreen(
    state: PlayerUiState,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onBrowse: () -> Unit,
    onExitRandom: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Random plays something that is not in this list, so the list is put behind glass: visible,
    // clearly not what you are listening to, and not touchable by accident. Cheaper and more
    // portable than a blur, which needs API 31 and this app runs from 29.
    if (state.randomMode) {
        Box(modifier = modifier.fillMaxSize()) {
            PlaylistBody(state, null, {}, {}, { _, _ -> }, contentPadding, enabled = false)
            RandomScrim(onExitRandom = onExitRandom, contentPadding = contentPadding)
        }
        return
    }

    if (state.queue.tracks.isEmpty()) {
        EmptyPlaylist(onBrowse = onBrowse, contentPadding = contentPadding, modifier = modifier)
        return
    }

    PlaylistBody(
        state = state,
        currentIndex = state.queue.currentIndex,
        onPlayAt = onPlayAt,
        onRemoveAt = onRemoveAt,
        onMove = onMove,
        contentPadding = contentPadding,
        enabled = true,
        modifier = modifier,
    )
}

@Composable
private fun PlaylistBody(
    state: PlayerUiState,
    currentIndex: Int?,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    contentPadding: PaddingValues,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Identified by track id, not by index. The index of the row being dragged changes the moment it
    // moves, which restarted the gesture and dropped the drag after every single step -- and left
    // the offset applied to whichever row had inherited that index, which is what made rows overlap.
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var showingInfo by remember { mutableStateOf<TrackRef?>(null) }

    // The pointer handler is created once and never restarted, so anything it reads must be read
    // through a holder that is kept current. Capturing the track list directly is what broke the
    // drag after two rows: the handler kept answering with the position the row had when the
    // gesture began, so the third step was computed from a stale origin and threw the row back.
    val tracks by rememberUpdatedState(state.queue.tracks)
    val move by rememberUpdatedState(onMove)
    val indexOfTrack = remember { { id: String -> tracks.indexOfFirst { it.id == id } } }
    val trackCount = remember { { tracks.size } }
    val moveTrack = remember { { from: Int, to: Int -> move(from, to) } }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        itemsIndexed(state.queue.tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                index = index,
                track = track,
                playing = index == currentIndex,
                enabled = enabled,
                dragging = track.id == draggingId,
                dragOffset = if (track.id == draggingId) dragOffset else 0f,
                onPlay = { onPlayAt(index) },
                onRemove = { onRemoveAt(index) },
                onInfo = { showingInfo = track },
                dragHandleModifier = Modifier.dragToReorder(
                    trackId = track.id,
                    listState = listState,
                    indexOf = indexOfTrack,
                    trackCount = trackCount,
                    setDragging = { draggingId = it },
                    offset = { dragOffset },
                    setOffset = { dragOffset = it },
                    onMove = moveTrack,
                ),
            )
        }
    }

    showingInfo?.let { track ->
        TrackInfoDialog(track = track, onDismiss = { showingInfo = null })
    }
}

/**
 * Drag to reorder, from the handle only.
 *
 * On the handle rather than the whole row, and without a long press: a dedicated grip is what says
 * "this moves", and gesturing on the row itself would fight the list's own scrolling. The row moves
 * as you drag rather than at the end, so the list you are looking at is the list you will get.
 *
 * **Keyed on the track id, never on its index.** `pointerInput` restarts when its key changes, and
 * the index of the row being dragged changes the instant it moves — so an index key cancelled the
 * gesture after every single step, and left the drag offset attached to whichever row had inherited
 * that number.
 *
 * **And nothing it reads may be captured.** The handler is created once and then never re-created,
 * which is the point — but it means a captured list is frozen at the moment the gesture started.
 * That was the second bug: after two rows the origin it was measuring from no longer existed, and
 * the row snapped back mid-drag. Everything comes in as a function that reads current state.
 */
private fun Modifier.dragToReorder(
    trackId: String,
    listState: LazyListState,
    indexOf: (String) -> Int,
    trackCount: () -> Int,
    setDragging: (String?) -> Unit,
    offset: () -> Float,
    setOffset: (Float) -> Unit,
    onMove: (Int, Int) -> Unit,
): Modifier = pointerInput(trackId) {
    detectDragGestures(
        onDragStart = { setDragging(trackId); setOffset(0f) },
        onDragEnd = { setDragging(null); setOffset(0f) },
        onDragCancel = { setDragging(null); setOffset(0f) },
        onDrag = { change, delta ->
            change.consume()
            setOffset(offset() + delta.y)

            // Looked up every time rather than captured: this row's position is exactly the thing
            // that changes while the gesture runs.
            val from = indexOf(trackId)
            if (from < 0) return@detectDragGestures

            // Measured rather than assumed: a row's height depends on whether it has a second line,
            // and a hard-coded guess drifts by one position after a few moves.
            val height = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == from }?.size?.takeIf { it > 0 }
                ?: return@detectDragGestures

            val steps = (offset() / height).toInt()
            if (steps == 0) return@detectDragGestures

            val target = (from + steps).coerceIn(0, trackCount() - 1)
            if (target != from) {
                onMove(from, target)
                // The row has moved under the finger, so the accumulated offset that caused the
                // move is spent. What remains is the part of the drag past it.
                setOffset(offset() - (target - from) * height)
            }
        },
    )
}

@Composable
private fun TrackRow(
    index: Int,
    track: TrackRef,
    playing: Boolean,
    enabled: Boolean,
    dragging: Boolean,
    dragOffset: Float,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onInfo: () -> Unit,
    dragHandleModifier: Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    val label = SupportedFormats.labelFor(track.fileNameOrTitle)
    val subtitle = listOf(track.displayAuthor, label).filter { it.isNotBlank() }.joinToString(" · ")

    ListItem(
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = subtitle.takeIf { it.isNotBlank() }?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (playing) {
                    // A triangle rather than the dot this used to be: a triangle says what the row
                    // IS, where a dot only says which one.
                    Icon(
                        imageVector = PlayerIcons.Play,
                        contentDescription = stringResource(R.string.a11y_now_playing_row),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    // The number stays. With three hundred tracks it is the only thing on the row
                    // that says where in the list you are.
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        trailingContent = if (!enabled) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(PlayerIcons.More, stringResource(R.string.a11y_track_actions, track.title))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_info)) },
                                leadingIcon = { Icon(PlayerIcons.Info, contentDescription = null) },
                                onClick = { menuOpen = false; onInfo() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                leadingIcon = { Icon(PlayerIcons.Remove, contentDescription = null) },
                                onClick = { menuOpen = false; onRemove() },
                            )
                        }
                    }
                    // Delete used to sit here, one thumb-width from the row you tap to play. Behind
                    // the menu it needs a deliberate second press.
                    Icon(
                        imageVector = PlayerIcons.DragHandle,
                        contentDescription = stringResource(R.string.a11y_reorder, track.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = dragHandleModifier.padding(horizontal = 8.dp),
                    )
                }
            }
        },
        colors = if (playing) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .clickable(enabled = enabled, onClick = onPlay),
    )
}

/**
 * What we know about a track without opening it.
 *
 * Deliberately not the full metadata: that means reading the file, and it is a wishlist item of its
 * own. This says where the track came from and what it is, which is what "which one is this" needs.
 */
@Composable
private fun TrackInfoDialog(track: TrackRef, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine(R.string.field_artist, track.displayAuthor)
                InfoLine(R.string.field_format, SupportedFormats.labelFor(track.fileNameOrTitle))
                InfoLine(R.string.field_file, track.fileNameOrTitle)
                InfoLine(R.string.info_location, track.subtitle)
                InfoLine(
                    R.string.info_size,
                    if (track.sizeBytes > 0) "${track.sizeBytes / 1024} kB" else "",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun InfoLine(label: Int, value: String) {
    if (value.isBlank()) return
    Column {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyPlaylist(
    onBrowse: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.playlist_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.playlist_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onBrowse) { Text(stringResource(R.string.action_browse)) }
        }
    }
}

/**
 * The glass over the playlist during Random, and the way out of it.
 *
 * The way out is in the middle of the screen with a label rather than tucked into a corner: the
 * playlist is already covered, so there is room, and a mode you can enter but cannot obviously
 * leave is a trap.
 */
@Composable
private fun RandomScrim(onExitRandom: () -> Unit, contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            // Swallows taps so a row underneath cannot be pressed through the glass.
            .clickable(enabled = true, onClick = {})
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = PlayerIcons.Dice,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.random_playing_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.random_playing_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onExitRandom) {
                Text(stringResource(R.string.random_back_to_playlist))
            }
        }
    }
}
