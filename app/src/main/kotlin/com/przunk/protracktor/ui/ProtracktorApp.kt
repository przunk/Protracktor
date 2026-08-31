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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.przunk.protracktor.R
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

    LaunchedEffect(showBrowse) { if (showBrowse) viewModel.refreshFolders() }

    state.message?.let { message ->
        LaunchedEffect(message.id) {
            snackbarHostState.showSnackbar(message.text)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.clickable { showPlaylists = true }) {
                        Text(state.activePlaylistName ?: stringResource(R.string.playlist_default_name))
                        if (state.queue.tracks.isNotEmpty()) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.track_count,
                                    state.queue.tracks.size,
                                    state.queue.tracks.size,
                                ),
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showBrowse = true }) {
                        Text(stringResource(R.string.action_browse))
                    }
                },
            )
        },
        bottomBar = {
            PlayerDock(
                state = state,
                onExpand = { showNowPlaying = true },
                onBrowse = { showBrowse = true },
                onPlayPause = viewModel::togglePlayPause,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onShuffle = viewModel::toggleShuffle,
                onRepeat = viewModel::cycleRepeat,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        PlaylistScreen(
            state = state,
            onPlayAt = viewModel::playAt,
            onRemoveAt = viewModel::removeTrack,
            onBrowse = { showBrowse = true },
            contentPadding = insets,
        )
    }

    if (showNowPlaying) {
        // Back closes the overlay before it closes the app. Without this the user's escape from a
        // full-screen sheet is to hunt for a drag handle.
        BackHandler { showNowPlaying = false }
        ModalBottomSheet(
            onDismissRequest = { showNowPlaying = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            NowPlaying(state = state, onSeek = viewModel::seekTo)
        }
    }

    if (showPlaylists) {
        BackHandler { showPlaylists = false }
        PlaylistSheet(state = state, viewModel = viewModel, onDismiss = { showPlaylists = false })
    }

    if (showBrowse) {
        // Back leaves the open folder first and the sheet second: one layer at a time, which is the
        // rule the rest of the navigation follows.
        BackHandler { if (browse.openFolder != null) viewModel.closeFolder() else showBrowse = false }
        ModalBottomSheet(
            onDismissRequest = { showBrowse = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BrowseScreen(
                browse = browse,
                playlistName = state.activePlaylistName,
                onPickFolder = { folderPicker.launch(null) },
                onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                onOpenFolder = viewModel::openFolder,
                onForgetFolder = viewModel::forgetFolder,
                onCloseFolder = viewModel::closeFolder,
                onAdd = { tracks ->
                    viewModel.addToPlaylist(tracks)
                    showBrowse = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSheet(
    state: com.przunk.protracktor.player.PlayerUiState,
    viewModel: PlayerViewModel,
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
                viewModel.switchToPlaylist(id)
                onDismiss()
            },
            onCreate = viewModel::createPlaylist,
            onRename = viewModel::renameActivePlaylist,
            onDelete = viewModel::deletePlaylist,
        )
    }
}
