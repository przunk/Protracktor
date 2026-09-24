// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.przunk.protracktor.player.SessionSource
import com.przunk.protracktor.player.QueueLink
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
    onSendToWeb: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (TrackRef) -> Unit = {},
    onAddSelectedToPlaylist: (List<TrackRef>) -> Unit = {},
    onRemoveMany: (List<Int>) -> Unit = {},
    onBrowse: () -> Unit,
    /** Opens the download sheet. What the empty screen offers when there is nothing to browse. */
    onPickDownloads: () -> Unit,
    /** False on a phone that holds no index and no granted folder: Browse would lead nowhere. */
    canBrowse: Boolean,
    /**
     * False until the playlist and what this phone holds have both been read.
     *
     * The empty screen makes a claim either way -- "nothing here" or "nothing to browse" -- and
     * both are wrong while the answer is still being read off the disk.
     */
    stateKnown: Boolean,
    onReturnToPlaylist: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Back to where the session plays from -- Random, or the list in Browse (A61). */
    onReturnToSource: () -> Unit = {},
) {
    // Random and a search both play something that is not in this list, so the list goes behind
    // glass: visible, clearly not what you are listening to, and not touchable by accident. Cheaper
    // and more portable than a blur, which needs API 31 and this app runs from 29.
    // Random has a screen of its own now (`docs/PLAN_RANDOM.md`); the glass is for the two that
    // still have nothing to show — a file another app handed us, and a search result playing.
    // **Every session, Random included** (A61): Back from Random no longer ends it, so the playlist
    // is where it is left from as well, and the cover says what plays and leads back to it.
    if (state.awayFromPlaylist) {
        Box(modifier = modifier.fillMaxSize()) {
            PlaylistBody(
                state.queue.tracks, listState, null, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {},
                loadingCurrent = false, contentPadding = contentPadding, enabled = false,
            )
            AwayScrim(
                source = when {
                    state.externalMode -> Away.External
                    state.searchMode -> state.sessionSource?.let { Away.List(it) } ?: Away.List(SessionSource.Search(""))
                    else -> Away.Random
                },
                onReturnToPlaylist = onReturnToPlaylist,
                onReturnToSource = onReturnToSource,
                contentPadding = contentPadding,
            )
        }
        return
    }

    if (state.queue.tracks.isEmpty()) {
        if (stateKnown) {
            EmptyPlaylist(
                onBrowse = onBrowse,
                onPickDownloads = onPickDownloads,
                canBrowse = canBrowse,
                contentPadding = contentPadding,
                modifier = modifier,
            )
        } else {
            // **Say nothing rather than the wrong thing.** Until the playlist has been restored
            // and the catalogue summaries have been read, "nothing here yet" and "nothing to
            // browse" are both guesses, and the second one offers a 49 MB download to somebody who
            // already has the lot.
            Box(
                modifier = modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
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
        onSendToWeb = onSendToWeb,
        onAddToOtherPlaylist = onAddToOtherPlaylist,
        onAddSelectedToPlaylist = onAddSelectedToPlaylist,
        onRemoveMany = onRemoveMany,
        contentPadding = contentPadding,
        enabled = true,
        modifier = modifier,
        loadingCurrent = state.loadingTrack,
    )
}

@Composable
internal fun PlaylistBody(
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
    onSendToWeb: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (TrackRef) -> Unit,
    onAddSelectedToPlaylist: (List<TrackRef>) -> Unit,
    onRemoveMany: (List<Int>) -> Unit,
    /** Whether the row the queue points at is being fetched, so it can say so by breathing. */
    loadingCurrent: Boolean = false,
    contentPadding: PaddingValues,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Whether rows may be dragged into another order.
     *
     * False for the Random view, which shows a record rather than an arrangement. Shared with the
     * playlist rather than copied for it: two lists of tracks that drift apart in what a row offers
     * is exactly the defect `docs/STATUS.md` C23, C30 and C31 each were.
     */
    reorderable: Boolean = true,
    /**
     * What identifies a row to the list.
     *
     * **The track id everywhere but Random.** A playlist cannot hold the same file twice —
     * `appendTracks` drops a candidate that `sameFileAs` anything already there — so the id is
     * unique and reordering keeps a row's identity while it moves.
     *
     * The Random record has no such rule. The dice draws from a pool that may be small: forty
     * favourites cannot fill an evening without repeating, and the read-ahead falls back to
     * allowing one rather than stopping dead. Two rows with one key is not a cosmetic problem —
     * `LazyColumn` throws on it — so that view keys by position, which it can afford, having
     * nothing to reorder.
     */
    keyOf: (Int, TrackRef) -> Any = { _, track -> track.id },
    /**
     * What counts as the playing row *changing*, for [KeepRowInView]. The track's id by default,
     * so reordering or removing rows around it does not move the list; the Random view passes its
     * cursor, because its record can hold the same tune twice.
     */
    followKey: Any? = currentIndex?.let { tracks.getOrNull(it)?.id },
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
        itemsIndexed(tracks, key = keyOf) { index, track ->
            TrackRow(
                index = index,
                track = track,
                playing = index == currentIndex,
                loading = loadingCurrent && index == currentIndex,
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
                // Absent for a local file, which has no catalogue folder to open, and for a
                // live-search catalogue, which publishes no index and no author to open one by.
                onShowNeighbours = track.takeIf { Catalogue.owning(it.id)?.isOnlineOnly == false }
                    ?.let { { onShowNeighbours(it) } },
                onShareFile = { onShareFile(track) },
                // Absent for a local file, which has no address anyone else could open.
                onShareLink = track.takeIf { Catalogue.owning(it.id) != null }
                    ?.let { { onShareLink(it) } },
                // Absent where [QueueLink.pack] would refuse it: a local file, an MP3.
                onSendToWeb = track.takeIf { QueueLink.canSend(it) }?.let { { onSendToWeb(listOf(it)) } },
                dragHandleModifier = if (!reorderable) null else Modifier.dragToReorder(
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

        // What you can do with what you ticked. Two buttons for three actions: the
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
                        icon = PlayerIcons.Web,
                        label = stringResource(R.string.action_send_to_web),
                        onClick = {
                            onSendToWeb(tracks.filter { it.id in selected })
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

    // **No follow-track button**: a small FAB you switch on is switched off again by the first
    // drag. The list simply keeps the playing row on screen, one row at a time and only when it
    // would leave -- but not while ticking rows or dragging one, because a list that moves under a
    // working finger fights it.
    KeepRowInView(
        listState = listState,
        index = currentIndex,
        key = followKey,
        active = enabled && !selecting && draggingId == null,
    )

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
    loading: Boolean,
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
    onSendToWeb: (() -> Unit)?,
    /**
     * How the handle takes a drag, or **null where there is nothing to reorder**.
     *
     * The Random view is a record of what the dice gave, in the order it gave it. There is no
     * arrangement to express, so the handle is not drawn rather than drawn dead — a control that
     * answers nothing is worse than one that is not there (`docs/PLAN_RANDOM.md`).
     */
    dragHandleModifier: Modifier?,
) {
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }

    val label = SupportedFormats.labelFor(track.fileNameOrTitle)
    // In both modes, for the same reason the dock shows it in both: a count you only see once you
    // have switched to the mode tells you nothing you did not already know.
    val tunes = if (track.subsongs > 1) {
        pluralStringResource(R.plurals.subsongs_count, track.subsongs, track.subsongs)
    } else {
        ""
    }
    val subtitle = listOf(track.displayAuthor, label, tunes)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    ListItem(
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = subtitle.takeIf { it.isNotBlank() }?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            // The checkbox takes the ordinal's slot, which is already reserved and already this
            // size -- so entering selection moves nothing (`docs/ARCHITECTURE.md` §17). The
            // ordinal earns its place by saying where you are in three hundred rows; while
            // selecting, what matters is which rows are ticked.
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (selecting) {
                    Checkbox(
                        checked = ticked,
                        onCheckedChange = { on -> haptics.toggle(on); onToggle() },
                    )
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
                            onSendToWeb?.let { send ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_send_to_web)) },
                                    leadingIcon = { Icon(PlayerIcons.Web, contentDescription = null) },
                                    onClick = { menuOpen = false; send() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                leadingIcon = { Icon(PlayerIcons.Remove, contentDescription = null) },
                                onClick = { menuOpen = false; onRemove() },
                            )
                        }
                    }
                    // Delete is not out here, one thumb-width from the row you tap to play.
                    // Behind the menu it needs a deliberate second press.
                    dragHandleModifier?.let { handle ->
                        Icon(
                            imageVector = PlayerIcons.DragHandle,
                            contentDescription = stringResource(R.string.a11y_reorder, track.title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = handle.padding(horizontal = 8.dp),
                        )
                    }
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
            // **A row being fetched breathes.** It is already marked as the one
            // that was chosen; what a download has to add is "still working". Guarded like the drag
            // layer above, and for the same reason: at most one row is ever being fetched.
            .then(
                if (!loading) {
                    Modifier
                } else {
                    val breath = rememberInfiniteTransition(label = "fetching").animateFloat(
                        initialValue = 1f,
                        targetValue = 0.45f,
                        animationSpec = infiniteRepeatable(
                            // Half a cycle each way, so a breath is about a second: slow enough to
                            // read as working rather than as a blinking fault.
                            animation = tween(durationMillis = 550, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "breath",
                    )
                    Modifier.graphicsLayer { alpha = breath.value }
                }
            )
            .then(
                if (dragging) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                enabled = enabled,
                // **Choosing a tune is the firm one** — it is the press this whole screen exists
                // for. While selecting, the same tap is a tick in a box, so it
                // feels like the checkbox beside it rather than like starting a tune.
                onClick = {
                    if (selecting) {
                        haptics.toggle(!ticked)
                        onToggle()
                    } else {
                        haptics.press()
                        onPlay()
                    }
                },
                // The loose end `docs/BACKLOG.md` A8 left open: a long press with no answer feels
                // like a press that missed, and this one silently changes what every other tap on
                // the screen will do.
                onLongClick = {
                    if (!selecting) {
                        haptics.gestureEnd()
                        onStartSelecting()
                    }
                },
            ),
    )
}

/**
 * What we know about a track without opening it.
 *
 * Deliberately not the full metadata: that means reading the file, and it is a wishlist item of its
 * own. This says where the track came from and what it is, which is what "which one is this" needs.
 */

/**
 * The first screen of a fresh install, and the last one anybody should be stuck on.
 *
 * **What it offers depends on whether there is anything to browse.** With an index or a granted
 * folder, Browse is the way on. With neither, Browse leads to a list of archives that all say "no
 * index" — which is precisely what the first testers met, and why nobody found the music
 * (`docs/BACKLOG.md` A46). So on an empty phone the offer is the download sheet itself.
 */
@Composable
private fun EmptyPlaylist(
    onBrowse: () -> Unit,
    onPickDownloads: () -> Unit,
    canBrowse: Boolean,
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
                text = stringResource(
                    if (canBrowse) R.string.playlist_empty_body else R.string.playlist_empty_body_nothing_held
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            // The same pair the top bar's actions and the follow-track button use, rather than
            // the primary colour a bare `Button` defaults to. This is the same *offer* as Browse
            // up there, and two controls that do the same thing should not be told apart by
            // their colour.
            Button(
                onClick = if (canBrowse) onBrowse else onPickDownloads,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(
                    imageVector = if (canBrowse) PlayerIcons.Cloud else PlayerIcons.Download,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    stringResource(
                        if (canBrowse) R.string.action_browse else R.string.download_pick_open
                    )
                )
            }
        }
    }
}

/** What the cover is over: a file another app handed us, the dice, or a list in Browse (A61). */
private sealed interface Away {
    data object External : Away
    data object Random : Away
    data class List(val source: SessionSource) : Away
}

/**
 * The glass over the playlist while something else plays -- **saying truly what** (A61).
 *
 * It said "Playing from search" for a folder, for History and for a local folder too, which the
 * owner called a lie. Now it names the source and offers both ways out, each an icon with its name:
 * back to that source as it was, and back to the playlist -- the one that ends the session.
 *
 * One component for every detour, because from the playlist they are the same situation: what you
 * are hearing is not on this list. In the middle of the screen with labels rather than in a corner:
 * the playlist is covered, so there is room, and a mode you can enter but not obviously leave is a
 * trap.
 */
@Composable
private fun AwayScrim(
    source: Away,
    onReturnToPlaylist: () -> Unit,
    onReturnToSource: () -> Unit,
    contentPadding: PaddingValues,
) {
    val icon = when (source) {
        Away.External -> PlayerIcons.Folder
        Away.Random -> PlayerIcons.Dice
        is Away.List -> when (source.source) {
            is SessionSource.Search -> PlayerIcons.Search
            is SessionSource.Folder -> PlayerIcons.Folder
            SessionSource.History -> PlayerIcons.History
        }
    }
    val title = when (source) {
        Away.External -> stringResource(R.string.external_playing_title)
        Away.Random -> stringResource(R.string.random_playing_title)
        is Away.List -> when (val from = source.source) {
            is SessionSource.Search ->
                if (from.query.isBlank()) stringResource(R.string.search_playing_title)
                else stringResource(R.string.session_from_search, from.query)
            is SessionSource.Folder -> stringResource(R.string.session_from_folder, from.path)
            SessionSource.History -> stringResource(R.string.session_from_history)
        }
    }
    val body = when (source) {
        Away.External -> stringResource(R.string.external_playing_body)
        Away.Random -> stringResource(R.string.random_playing_body)
        is Away.List -> stringResource(R.string.session_body)
    }
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
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            // A file from another app has nowhere to go back to but here.
            if (source !is Away.External) {
                OutlinedButton(onClick = onReturnToSource) {
                    IconLabel(
                        icon,
                        stringResource(if (source is Away.Random) R.string.action_back_to_random else R.string.action_back_to_list),
                    )
                }
            }
            Button(onClick = onReturnToPlaylist) {
                IconLabel(PlayerIcons.Playlist, stringResource(R.string.random_back_to_playlist))
            }
        }
    }
}
