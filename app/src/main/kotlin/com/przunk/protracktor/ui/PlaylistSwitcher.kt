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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.data.SavedPlaylist

/**
 * Choosing, creating, renaming and deleting playlists.
 *
 * The active one is marked by a filled dot as well as by colour, for the same reason the playing row
 * is: a mark only colour can carry is a mark some users cannot read (AGENTS.md §8).
 */
@Composable
fun PlaylistSwitcher(
    playlists: List<SavedPlaylist>,
    activeId: Long,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var naming by remember { mutableStateOf<NamingIntent?>(null) }
    var confirmingDelete by remember { mutableStateOf<SavedPlaylist?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.playlist_switch_title), style = MaterialTheme.typography.titleLarge)

        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
            items(playlists, key = { it.id }) { playlist ->
                val active = playlist.id == activeId
                ListItem(
                    headlineContent = {
                        Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (active) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Row {
                            if (active) {
                                IconButton(onClick = {
                                    naming = NamingIntent.Rename(playlist.name)
                                }) {
                                    Icon(
                                        PlayerIcons.Rename,
                                        stringResource(R.string.a11y_rename_playlist, playlist.name),
                                    )
                                }
                            }
                            IconButton(onClick = { confirmingDelete = playlist }) {
                                Icon(
                                    PlayerIcons.Remove,
                                    stringResource(R.string.a11y_delete_playlist, playlist.name),
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { onSelect(playlist.id) },
                )
            }
        }

        OutlinedButton(
            onClick = { naming = NamingIntent.Create },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.playlist_new)) }
    }

    naming?.let { intent ->
        NameDialog(
            initial = (intent as? NamingIntent.Rename)?.current.orEmpty(),
            title = stringResource(
                if (intent is NamingIntent.Create) R.string.playlist_new else R.string.playlist_rename
            ),
            onDismiss = { naming = null },
            onConfirm = { name ->
                if (intent is NamingIntent.Create) onCreate(name) else onRename(name)
                naming = null
            },
        )
    }

    // A dialog here and undo for a single track removal, deliberately not the same. Undoing a
    // deleted playlist from a snackbar that lives four seconds is not an escape route; asking first
    // is.
    confirmingDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text(stringResource(R.string.playlist_delete_title, playlist.name)) },
            text = { Text(stringResource(R.string.playlist_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(playlist.id)
                    confirmingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private sealed interface NamingIntent {
    data object Create : NamingIntent
    data class Rename(val current: String) : NamingIntent
}

@Composable
private fun NameDialog(
    initial: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name_label)) },
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
