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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.data.SavedPlaylist
import com.przunk.protracktor.player.TrackRef

/**
 * Dialog for selecting a destination playlist to add [tracks] to (B18).
 *
 * Allows picking an existing playlist or creating a new playlist immediately.
 */
@Composable
fun AddToPlaylistDialog(
    tracks: List<TrackRef>,
    playlists: List<SavedPlaylist>,
    activePlaylistId: Long?,
    onSelectPlaylist: (SavedPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creatingNew by remember { mutableStateOf(false) }

    if (creatingNew) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creatingNew = false },
            title = { Text(stringResource(R.string.playlist_new)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name_label)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onCreatePlaylist(name.trim())
                        creatingNew = false
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { creatingNew = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
        return
    }

    val title = if (tracks.size == 1) {
        stringResource(R.string.dialog_add_to_playlist_title)
    } else {
        pluralStringResource(R.plurals.dialog_add_tracks_to_playlist_title, tracks.size, tracks.size)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (playlists.isNotEmpty()) {
                    // The current playlist first. Since this dialogue became the *only* way to add
                    // from a menu (`docs/BACKLOG.md` A17), adding to the one you are already in
                    // must stay two unthinking taps rather than a hunt.
                    val ordered = playlists.sortedByDescending { it.id == activePlaylistId }
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(ordered, key = { it.id }) { playlist ->
                            val isActive = playlist.id == activePlaylistId
                            ListItem(
                                headlineContent = {
                                    Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(
                                        text = listOfNotNull(
                                            if (isActive) {
                                                stringResource(R.string.playlist_active_indicator)
                                            } else {
                                                null
                                            },
                                            pluralStringResource(
                                                R.plurals.track_count,
                                                playlist.trackCount,
                                                playlist.trackCount,
                                            ),
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                leadingContent = {
                                    Icon(PlayerIcons.Playlist, contentDescription = null)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPlaylist(playlist) },
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { creatingNew = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(PlayerIcons.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.playlist_new))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
