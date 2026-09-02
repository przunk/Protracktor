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

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.przunk.protracktor.R
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.PlaybackController
import com.przunk.protracktor.player.PlayerViewModel

/**
 * The three-layer shell decided in `docs/OPEN_QUESTIONS.md` Q1.
 *
 * The playlist is the only destination. Now Playing expands upward from the dock over it, Browse
 * opens as a modal from the top bar, and back always descends one layer -- the only rule the user
 * has to learn. No side-swiping between pages (R7), and the dock never leaves (R3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtracktorApp(viewModel: PlayerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val browse by viewModel.browse.collectAsStateWithLifecycle()
    var showNowPlaying by remember { mutableStateOf(false) }
    var showBrowse by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // The foreground service runs with or without this; only its notification is suppressed. Asked
    // once, at the top, rather than in the middle of the user pressing play.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        LaunchedEffect(Unit) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) viewModel.rememberFolder(uri) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) { viewModel.addFiles(uris); showBrowse = false } }

    // Opening Browse from a button starts at the top; opening it from a jump does not, because the
    // jump has already aimed it. Tying the reset to the button rather than to the sheet being shown
    // is what keeps those two apart.
    val openBrowse = {
        viewModel.openDomain(BrowseDomain.ROOT)
        showBrowse = true
    }
    LaunchedEffect(Unit) { viewModel.showBrowse.collect { showBrowse = true } }

    // The chooser needs an activity; preparing what is shared needed a fetch. The controller does
    // the second and hands the first over here.
    // LocalContext inside an activity's content is that activity, so the chooser starts as a
    // normal child. `LocalActivity` would say this more plainly and arrived in activity-compose
    // 1.10; this project is on 1.9.3.
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_chooser)
    LaunchedEffect(Unit) {
        viewModel.share.collect { intent ->
            context.startActivity(Intent.createChooser(intent, shareTitle))
        }
    }

    val undoLabel = stringResource(R.string.action_undo)
    val choosePlaylistLabel = stringResource(R.string.a11y_choose_playlist)
    var pendingSwitch by remember { mutableStateOf<Long?>(null) }
    var pendingAddToPlaylist by remember { mutableStateOf<List<com.przunk.protracktor.player.TrackRef>?>(null) }
    // Hoisted so the expanded player can send the list to the playing track without owning the list.
    val playlistState = rememberLazyListState()

    // Newly added tracks land at the end of the list, out of sight. Going to them is the
    // confirmation that the removed snackbar used to be.
    LaunchedEffect(Unit) {
        viewModel.reveal.collect { index -> playlistState.animateScrollToItem(index) }
    }
    val scope = rememberCoroutineScope()
    state.message?.let { message ->
        LaunchedEffect(message.id) {
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = if (message.actionLabel == PlaybackController.UNDO) undoLabel else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemoval()
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBrowse) {
                        IconButton(onClick = { if (!viewModel.browseBack()) showBrowse = false }) {
                            Icon(PlayerIcons.Back, stringResource(R.string.action_back))
                        }
                    }
                },
                title = {
                    if (showBrowse) {
                        Text(stringResource(R.string.browse_title))
                    } else {
                        // A chevron and a filled shape, because the owner could not tell the name
                        // was a button. A control that only looks like a label is a control nobody
                        // presses.
                        Surface(
                            onClick = { showPlaylists = true },
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.semantics { contentDescription = choosePlaylistLabel },
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = state.activePlaylistName
                                            ?: stringResource(R.string.playlist_default_name),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                    )
                                    if (state.queue.tracks.isNotEmpty()) {
                                        Text(
                                            text = pluralStringResource(
                                                R.plurals.track_count,
                                                state.queue.tracks.size,
                                                state.queue.tracks.size,
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = PlayerIcons.DropDown,
                                    contentDescription = null,
                                    modifier = Modifier.padding(start = 2.dp),
                                )
                            }
                        }
                    }
                },
                actions = {
                    // The way out, as opposed to the way back. Back is a stack -- leave the
                    // selection, then up a level, then out -- and from four levels deep that is
                    // four presses even when it is behaving correctly. This is one, from anywhere.
                    // Labelled as well as drawn, because an icon alone does not say where it goes.
                    if (showBrowse) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { showBrowse = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = PlayerIcons.Playlist,
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.action_to_playlist),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    if (!showBrowse) {
                        // Only while there is something to save. A permanently lit Save button
                        // teaches nothing about whether the list on screen is the list on disk.
                        if (state.dirty) {
                            IconButton(onClick = viewModel::discardChanges) {
                                Icon(PlayerIcons.Discard, stringResource(R.string.a11y_discard_changes))
                            }
                            FilledIconButton(onClick = viewModel::savePlaylist) {
                                Icon(PlayerIcons.Save, stringResource(R.string.a11y_save_playlist))
                            }
                        }
                        TextButton(onClick = openBrowse) {
                            Text(stringResource(R.string.action_browse))
                        }
                    }
                },
            )
        },
        bottomBar = {
            PlayerDock(
                state = state,
                onSeek = viewModel::seekTo,
                onKeep = viewModel::keepTransient,
                onExpand = { showNowPlaying = true },
                onBrowse = openBrowse,
                onPlayPause = viewModel::togglePlayPause,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onShuffle = viewModel::toggleShuffle,
                onRepeat = viewModel::cycleRepeat,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> SwipeableSnackbar(data) } },
    ) { insets ->
        if (showBrowse) {
            BrowseScreen(
                browse = browse,
                playlistName = state.activePlaylistName,
                contentPadding = insets,
                onOpenDomain = viewModel::openDomain,
                onPickFolder = { folderPicker.launch(null) },
                onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                onOpenFolder = viewModel::openFolder,
                onForgetFolder = viewModel::forgetFolder,
                onScanFolder = viewModel::scanFolder,
                onIndexCatalogue = viewModel::indexCatalogue,
                onDownloadSongLengths = viewModel::downloadSongLengths,
                onOpenCatalogue = viewModel::openCatalogue,
                onOpenGroup = viewModel::openGroup,
                onRandom = { viewModel.playRandom(); showBrowse = false },
                onQueryChange = viewModel::setQuery,
                onToggleLocal = viewModel::toggleSearchLocal,
                onToggleOnline = viewModel::toggleSearchOnline,
                onToggleCatalogue = viewModel::toggleSearchCatalogue,
                onSearch = viewModel::runSearch,
                // Playing from Browse never adds anything and never touches the playlist: whatever
                // is on screen becomes the queue for as long as you are looking at it.
                onClearHistory = viewModel::clearHistory,
                // Marks the row you are hearing. Browse plays through the results queue, so the
                // current track is the queue's, not the playlist's.
                playingId = state.current?.id,
                onShowNeighbours = viewModel::showNeighboursOf,
                onShareFile = viewModel::shareFile,
                onShareLink = viewModel::shareLink,
                onPlay = { index -> viewModel.playFromResults(browse.tracks, index) },
                onAdd = { tracks ->
                    viewModel.addToPlaylist(tracks)
                    showBrowse = false
                },
                onAddToOtherPlaylist = { tracks ->
                    pendingAddToPlaylist = tracks
                },
            )
        } else {
            PlaylistScreen(
                state = state,
                listState = playlistState,
                onPlayAt = viewModel::playAt,
                onRemoveAt = viewModel::removeTrack,
                onMove = viewModel::moveTrack,
                onShowNeighbours = viewModel::showNeighboursOf,
                onShareFile = viewModel::shareFile,
                onShareLink = viewModel::shareLink,
                onAddToOtherPlaylist = { track -> pendingAddToPlaylist = listOf(track) },
                onBrowse = openBrowse,
                onReturnToPlaylist = viewModel::returnToPlaylist,
                contentPadding = insets,
            )
        }
    }

    // One level at a time, out of Browse and then out of the screen -- the rule the rest of the
    // navigation follows.
    if (showBrowse) {
        BackHandler { if (!viewModel.browseBack()) showBrowse = false }
    }

    if (showNowPlaying) {
        // Back closes the overlay before it closes the app. Without this the user's escape from a
        // full-screen sheet is to hunt for a drag handle.
        BackHandler { showNowPlaying = false }
        ModalBottomSheet(
            onDismissRequest = { showNowPlaying = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            NowPlaying(
                state = state,
                onSeek = viewModel::seekTo,
                // Only when what is playing is actually in the list. During Random or a search there
                // is nothing to show, and a button that lands nowhere is worse than no button.
                onShowInPlaylist = state.queue.currentIndex
                    ?.takeIf { !state.awayFromPlaylist }
                    ?.let { index ->
                        {
                            showNowPlaying = false
                            scope.launch { playlistState.animateScrollToItem(index) }
                        }
                    },
                // The Random case is why this is here rather than only in the row menu: during
                // Random the playlist is behind glass and the row menu cannot be reached at all,
                // and a tune played at random is exactly the one you want to ask this about.
                onShowNeighbours = state.current
                    ?.takeIf { Catalogue.owning(it.id) != null }
                    ?.let { track ->
                        {
                            showNowPlaying = false
                            viewModel.showNeighboursOf(track)
                        }
                    },
                onShareFile = state.current?.let { track -> { viewModel.shareFile(track) } },
                onShareLink = state.current
                    ?.takeIf { Catalogue.owning(it.id) != null }
                    ?.let { track -> { viewModel.shareLink(track) } },
                onAddToOtherPlaylist = state.current?.let { track ->
                    {
                        showNowPlaying = false
                        pendingAddToPlaylist = listOf(track)
                    }
                },
            )
        }
    }

    if (showPlaylists) {
        BackHandler { showPlaylists = false }
        PlaylistSheet(
            state = state,
            viewModel = viewModel,
            // Switching abandons unsaved edits, so ask before losing them rather than after.
            onSelect = { id ->
                if (state.dirty) pendingSwitch = id else viewModel.switchToPlaylist(id)
            },
            onDismiss = { showPlaylists = false },
        )
    }

    pendingSwitch?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text(stringResource(R.string.playlist_unsaved_title)) },
            text = { Text(stringResource(R.string.playlist_unsaved_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.savePlaylist()
                    viewModel.switchToPlaylist(target)
                    pendingSwitch = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.switchToPlaylist(target)
                    pendingSwitch = null
                }) { Text(stringResource(R.string.action_discard)) }
            },
        )
    }

    pendingAddToPlaylist?.let { tracks ->
        AddToPlaylistDialog(
            tracks = tracks,
            playlists = state.playlists,
            activePlaylistId = state.activePlaylistId,
            onSelectPlaylist = { playlist ->
                viewModel.addToPlaylist(playlist.id, tracks)
                pendingAddToPlaylist = null
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylistAndAdd(name, tracks)
                pendingAddToPlaylist = null
            },
            onDismiss = { pendingAddToPlaylist = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSheet(
    state: com.przunk.protracktor.player.PlayerUiState,
    viewModel: PlayerViewModel,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PlaylistSwitcher(
            playlists = state.playlists,
            activeId = state.activePlaylistId,
            onSelect = { id ->
                onSelect(id)
                onDismiss()
            },
            onCreate = viewModel::createPlaylist,
            onRename = viewModel::renameActivePlaylist,
            onDelete = viewModel::deletePlaylist,
        )
    }
}
