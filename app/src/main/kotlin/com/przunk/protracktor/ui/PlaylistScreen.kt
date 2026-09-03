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
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
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
import com.przunk.protracktor.net.Catalogue
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
    listState: LazyListState,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onAddToOtherPlaylist: (TrackRef) -> Unit = {},
    onAddSelectedToPlaylist: (List<TrackRef>) -> Unit = {},
    onRemoveMany: (List<Int>) -> Unit = {},
    onBrowse: () -> Unit,
    onReturnToPlaylist: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Random and a search both play something that is not in this list, so the list goes behind
    // glass: visible, clearly not what you are listening to, and not touchable by accident. Cheaper
    // and more portable than a blur, which needs API 31 and this app runs from 29.
    if (state.awayFromPlaylist) {
        Box(modifier = modifier.fillMaxSize()) {
            PlaylistBody(state.queue.tracks, listState, null, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, contentPadding, enabled = false)
            AwayScrim(
                randomMode = state.randomMode,
                onReturnToPlaylist = onReturnToPlaylist,
                contentPadding = contentPadding,
            )
        }
        return
    }

    if (state.queue.tracks.isEmpty()) {
        EmptyPlaylist(onBrowse = onBrowse, contentPadding = contentPadding, modifier = modifier)
        return
    }

    PlaylistBody(
        tracks = state.queue.tracks,
        listState = listState,
        currentIndex = state.queue.currentIndex,
        onPlayAt = onPlayAt,
        onRemoveAt = onRemoveAt,
        onMove = onMove,
        onShowNeighbours = onShowNeighbours,
        onShareFile = onShareFile,
        onShareLink = onShareLink,
        onAddToOtherPlaylist = onAddToOtherPlaylist,
        onAddSelectedToPlaylist = onAddSelectedToPlaylist,
        onRemoveMany = onRemoveMany,
        contentPadding = contentPadding,
        enabled = true,
        modifier = modifier,
    )
}

@Composable
private fun PlaylistBody(
    // **The track list, not the whole player state.** `positionSeconds` ticks every 200 ms while
    // anything plays, so taking `PlayerUiState` here recomposed every row five times a second --
    // which is what "as if the FPS were low" was. Browse takes `BrowseState`, which does not tick,
    // and stayed smooth at three hundred rows while this stuttered at twenty-two. That comparison
    // is what found it.
    tracks: List<TrackRef>,
    listState: LazyListState,
    currentIndex: Int?,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onAddToOtherPlaylist: (TrackRef) -> Unit,
    onAddSelectedToPlaylist: (List<TrackRef>) -> Unit,
    onRemoveMany: (List<Int>) -> Unit,
    contentPadding: PaddingValues,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
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
    val liveTracks by rememberUpdatedState(tracks)
    val move by rememberUpdatedState(onMove)
    val indexOfTrack = remember { { id: String -> liveTracks.indexOfFirst { it.id == id } } }
    val trackCount = remember { { liveTracks.size } }
    val moveTrack = remember { { from: Int, to: Int -> move(from, to) } }

    var following by remember { mutableStateOf(false) }
    // Selection lives here and dies with the screen, the same as in Browse: a tick that survives a
    // reload would act on a row the user never saw (`docs/ARCHITECTURE.md` §17).
    var selected by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(tracks) {
        selected = selected.intersect(tracks.mapTo(mutableSetOf()) { it.id })
    }
    val selecting = selected.isNotEmpty()
    // Takes back before the screen's own handler: leave the selection first, ticking nothing.
    BackHandler(enabled = selecting) { selected = emptySet() }
    val haptics = rememberHaptics()

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                index = index,
                track = track,
                playing = index == currentIndex,
                enabled = enabled,
                dragging = track.id == draggingId,
                dragOffset = if (track.id == draggingId) dragOffset else 0f,
                selecting = selecting,
                ticked = track.id in selected,
                onToggle = {
                    selected = if (track.id in selected) selected - track.id else selected + track.id
                },
                onStartSelecting = { selected = selected + track.id },
                onPlay = { onPlayAt(index) },
                onRemove = { onRemoveAt(index) },
                onInfo = { showingInfo = track },
                onAddToOtherPlaylist = { onAddToOtherPlaylist(track) },
                // Absent for a local file, which has no catalogue folder to open.
                onShowNeighbours = track.takeIf { Catalogue.owning(it.id) != null }
                    ?.let { { onShowNeighbours(it) } },
                onShareFile = { onShareFile(track) },
                // Absent for a local file, which has no address anyone else could open.
                onShareLink = track.takeIf { Catalogue.owning(it.id) != null }
                    ?.let { { onShareLink(it) } },
                dragHandleModifier = Modifier.dragToReorder(
                    trackId = track.id,
                    listState = listState,
                    indexOf = indexOfTrack,
                    trackCount = trackCount,
                    setDragging = { draggingId = it },
                    offset = { dragOffset },
                    setOffset = { dragOffset = it },
                    onMove = moveTrack,
                    haptics = haptics,
                ),
            )
        }
    }

        // What you can do with what you ticked. Two buttons for the owner's three actions: the
        // picker behind "Add to playlist..." offers an existing playlist *or* a new one, so
        // "make a new playlist from these" is in there rather than missing (`docs/BACKLOG.md` A4).
        if (selecting) {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // The dock's height goes *outside* the bar, not inside it. Putting the whole
                    // content padding within made the bar three rows tall and reaching a third of
                    // the way up the screen -- it was clearing the dock by growing rather than by
                    // sitting above it.
                    .padding(bottom = contentPadding.calculateBottomPadding()),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.browse_selected, selected.size, selected.size
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    // Icon with its name underneath, like every other action in the app.
                    LabelledAction(
                        icon = PlayerIcons.PlaylistAdd,
                        label = stringResource(R.string.action_add_to_playlist),
                        onClick = {
                            onAddSelectedToPlaylist(tracks.filter { it.id in selected })
                            selected = emptySet()
                        },
                    )
                    LabelledAction(
                        icon = PlayerIcons.Remove,
                        label = stringResource(R.string.action_delete),
                        onClick = {
                            onRemoveMany(
                                tracks.indices.filter { tracks[it].id in selected }
                            )
                            selected = emptySet()
                        },
                    )
                }
            }
        }

        // At the very edge, so the row's own drag handle and this one cannot be confused
        // (`docs/BACKLOG.md` A3). Absent while the list is behind glass: dragging a scrollbar for a
        // list you cannot touch would be a control that lies.
        if (enabled) {
            DraggableScrollbar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(contentPadding),
            )
        }
    }

    // Not while selecting: it floats over the bottom-right corner, which is where the actions are,
    // and following the playing track is not what you are doing when you are choosing rows.
    if (enabled && !selecting) {
        FollowTrackButton(
            listState = listState,
            currentIndex = currentIndex,
            contentPadding = contentPadding,
            following = following,
            onFollowingChange = { following = it },
        )
    }

    showingInfo?.let { track ->
        TrackInfoDialog(track = track, onDismiss = { showingInfo = null })
    }
}

/**
 * The follow-the-playing-track toggle.
 *
 * Off by default, because a list that scrolls itself while you are reading it is a feature people
 * turn off. Tapping it turns following on and hides the button, because it has nothing left to
 * offer; scrolling by hand turns following off and brings it back — the gesture that cancels it is
 * exactly the gesture that means "I want to look somewhere else".
 */
@Composable
private fun FollowTrackButton(
    listState: LazyListState,
    currentIndex: Int?,
    contentPadding: PaddingValues,
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
) {
    // A real drag from the user, not our own scrolling. isScrollInProgress cannot tell those apart,
    // and mistaking one for the other would switch following off the instant it was switched on.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) onFollowingChange(false)
        }
    }

    LaunchedEffect(following, currentIndex) {
        if (following && currentIndex != null) listState.bringIntoView(currentIndex)
    }

    if (following || currentIndex == null) return

    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(12.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // Small and icon-only. The label made it discoverable and made it cover the list, and the
        // owner has now seen it -- so the trade goes the other way. Its content description still
        // says what it does, which is where discoverability belongs once you know the button exists.
        SmallFloatingActionButton(
            onClick = { onFollowingChange(true) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        ) {
            Icon(
                imageVector = PlayerIcons.Locate,
                contentDescription = stringResource(R.string.action_follow_track),
            )
        }
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
    haptics: Haptics,
): Modifier = pointerInput(trackId) {
    detectDragGestures(
        onDragStart = { setDragging(trackId); setOffset(0f); haptics.gestureStart() },
        onDragEnd = { setDragging(null); setOffset(0f); haptics.gestureEnd() },
        onDragCancel = { setDragging(null); setOffset(0f); haptics.gestureEnd() },
        onDrag = { change, delta ->
            change.consume()
            setOffset(offset() + delta.y)

            // Looked up every time rather than captured: this row's position is exactly the thing
            // that changes while the gesture runs.
            val from = indexOf(trackId)
            if (from < 0) return@detectDragGestures

            val height = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == from }?.size?.takeIf { it > 0 }
                ?: return@detectDragGestures

            val steps = (offset() / height).toInt()
            if (steps == 0) return@detectDragGestures

            val target = (from + steps).coerceIn(0, trackCount() - 1)
            if (target != from) {
                onMove(from, target)
                haptics.tick()
                setOffset(offset() - (target - from) * height)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    index: Int,
    track: TrackRef,
    playing: Boolean,
    enabled: Boolean,
    dragging: Boolean,
    dragOffset: Float,
    selecting: Boolean,
    ticked: Boolean,
    onToggle: () -> Unit,
    onStartSelecting: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onInfo: () -> Unit,
    onAddToOtherPlaylist: () -> Unit,
    onShowNeighbours: (() -> Unit)?,
    onShareFile: () -> Unit,
    onShareLink: (() -> Unit)?,
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
            // The checkbox takes the ordinal's slot, which is already reserved and already this
            // size -- so entering selection moves nothing (`docs/ARCHITECTURE.md` §17). The owner
            // defended the ordinal for telling him where he is in three hundred rows; while
            // selecting, what matters is which rows are ticked.
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (selecting) {
                    Checkbox(checked = ticked, onCheckedChange = { onToggle() })
                } else if (playing) {
                    Icon(
                        imageVector = PlayerIcons.Play,
                        contentDescription = stringResource(R.string.a11y_now_playing_row),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        trailingContent = if (!enabled || selecting) null else {
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
                                text = { Text(stringResource(R.string.action_add_to_playlist)) },
                                leadingIcon = { Icon(PlayerIcons.PlaylistAdd, contentDescription = null) },
                                onClick = { menuOpen = false; onAddToOtherPlaylist() },
                            )
                            onShowNeighbours?.let { show ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_show_neighbours)) },
                                    leadingIcon = { Icon(PlayerIcons.Folder, contentDescription = null) },
                                    onClick = { menuOpen = false; show() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share_file)) },
                                leadingIcon = { Icon(PlayerIcons.Share, contentDescription = null) },
                                onClick = { menuOpen = false; onShareFile() },
                            )
                            onShareLink?.let { share ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_share_link)) },
                                    leadingIcon = { Icon(PlayerIcons.Link, contentDescription = null) },
                                    onClick = { menuOpen = false; share() },
                                )
                            }
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
            // **Only while this row is being dragged.** `graphicsLayer` allocates a render node,
            // and applied unconditionally that is one per row -- allocated and thrown away again
            // for every row a fling brings past. Browse's rows have no such modifier, which is why
            // three hundred of them scroll smoothly while twenty-two of these did not. At most one
            // row is ever dragged, so at most one layer is ever needed.
            .then(
                if (dragging) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                enabled = enabled,
                onClick = { if (selecting) onToggle() else onPlay() },
                onLongClick = { if (!selecting) onStartSelecting() },
            ),
    )
}

/**
 * What we know about a track without opening it.
 *
 * Deliberately not the full metadata: that means reading the file, and it is a wishlist item of its
 * own. This says where the track came from and what it is, which is what "which one is this" needs.
 */

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
 * The glass over the playlist while something else is playing, and the way out of it.
 *
 * One component for both detours — Random and a search — because they are the same situation from
 * the playlist's point of view: what you are hearing is not on this list. Only the wording differs.
 *
 * The way out is in the middle of the screen with a label rather than tucked into a corner: the
 * playlist is already covered, so there is room, and a mode you can enter but cannot obviously
 * leave is a trap.
 */
@Composable
private fun AwayScrim(
    randomMode: Boolean,
    onReturnToPlaylist: () -> Unit,
    contentPadding: PaddingValues,
) {
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
                imageVector = if (randomMode) PlayerIcons.Dice else PlayerIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(
                    if (randomMode) R.string.random_playing_title else R.string.search_playing_title
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (randomMode) R.string.random_playing_body else R.string.search_playing_body
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onReturnToPlaylist) {
                Text(stringResource(R.string.random_back_to_playlist))
            }
        }
    }
}
