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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
    var showNowPlaying by remember { mutableStateOf(false) }
    var showBrowse by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) { viewModel.addFolder(uri); showBrowse = false } }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) { viewModel.addFiles(uris); showBrowse = false } }

    state.message?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.playlist_default_name))
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
            NowPlaying(state = state)
        }
    }

    if (showBrowse) {
        BackHandler { showBrowse = false }
        ModalBottomSheet(
            onDismissRequest = { showBrowse = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BrowseSheet(
                scanning = state.scanning,
                hasTracks = state.queue.tracks.isNotEmpty(),
                onPickFolder = { folderPicker.launch(null) },
                onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                onClear = { viewModel.clearPlaylist(); showBrowse = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseSheet(
    scanning: Boolean,
    hasTracks: Boolean,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.browse_title),
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.browse_body),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onPickFolder,
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (scanning) R.string.action_scanning else R.string.action_add_folder
                )
            )
        }
        OutlinedButton(
            onClick = onPickFiles,
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_add_files)) }

        if (hasTracks) {
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_clear_playlist))
            }
        }
    }
}
