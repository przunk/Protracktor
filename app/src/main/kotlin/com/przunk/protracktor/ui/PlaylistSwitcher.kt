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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
 * Choosing and creating playlists, and acting on one of them.
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
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onImport: () -> Unit,
    onExport: (Long) -> Unit,
) {
    var naming by remember { mutableStateOf<NamingIntent?>(null) }
    var confirmingDelete by remember { mutableStateOf<SavedPlaylist?>(null) }
    var menuFor by remember { mutableStateOf<Long?>(null) }

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
                    // The size, where a dot used to say "this is the current one". The dot
                    // spent a slot on something the row can say by being highlighted, and left the
                    // question a list of bare names cannot answer: which of these has anything in
                    // it (`docs/BACKLOG.md` A21).
                    leadingContent = {
                        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "${playlist.trackCount}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                    // Everything you can do *to* this playlist, behind the same three dots a
                    // track row uses in Browse and in the playlist. Rename used to appear only on
                    // the active row and delete sat next to it unlabelled, while export was a
                    // button at the bottom that silently meant "the open one" -- three different
                    // shapes for three things that are all "act on one playlist".
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menuFor = playlist.id }) {
                                Icon(
                                    PlayerIcons.More,
                                    stringResource(R.string.a11y_playlist_menu, playlist.name),
                                )
                            }
                            DropdownMenu(
                                expanded = menuFor == playlist.id,
                                onDismissRequest = { menuFor = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playlist_rename)) },
                                    leadingIcon = { Icon(PlayerIcons.Rename, null) },
                                    onClick = {
                                        menuFor = null
                                        naming = NamingIntent.Rename(playlist.id, playlist.name)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.action_export_playlist))
                                    },
                                    leadingIcon = { Icon(PlayerIcons.Export, null) },
                                    onClick = {
                                        menuFor = null
                                        onExport(playlist.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    leadingIcon = { Icon(PlayerIcons.Remove, null) },
                                    onClick = {
                                        menuFor = null
                                        confirmingDelete = playlist
                                    },
                                )
                            }
                        }
                    },
                    // The whole row, the same way the playing track is marked in the playlist, so
                    // "which one am I in" is one convention rather than two.
                    colors = if (active) {
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier.clickable { onSelect(playlist.id) },
                )
            }
        }

        // The two ways to end up with a playlist that was not there before, side by side.
        // Import stays out of the row menu on purpose: it does not add to the playlist you opened
        // the menu on, it makes a new one (`docs/ARCHITECTURE.md` §16), and an "Import" sitting
        // among that row's own actions would promise exactly the thing it refuses to do.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { naming = NamingIntent.Create },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.playlist_new)) }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_import_playlist))
            }
        }
    }

    naming?.let { intent ->
        NameDialog(
            initial = (intent as? NamingIntent.Rename)?.current.orEmpty(),
            title = stringResource(
                if (intent is NamingIntent.Create) R.string.playlist_new else R.string.playlist_rename
            ),
            onDismiss = { naming = null },
            onConfirm = { name ->
                when (intent) {
                    is NamingIntent.Create -> onCreate(name)
                    is NamingIntent.Rename -> onRename(intent.id, name)
                }
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
            // The last one is emptied rather than removed, because one has to exist. The dialog
            // says which of the two is about to happen instead of promising the same thing twice.
            title = {
                Text(
                    stringResource(
                        if (playlists.size <= 1) R.string.playlist_clear_title
                        else R.string.playlist_delete_title,
                        playlist.name,
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (playlists.size <= 1) R.string.playlist_clear_body
                        else R.string.playlist_delete_body
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(playlist.id)
                    confirmingDelete = null
                }) {
                    Text(
                        stringResource(
                            if (playlists.size <= 1) R.string.action_empty else R.string.action_delete
                        )
                    )
                }
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
    data class Rename(val id: Long, val current: String) : NamingIntent
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
