// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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

    // **Cards, as the alarms have them** (2026-11-14): one rounded, contrasting card per playlist
    // with little air between them, the open one drawn stronger and saying so in words, and the two
    // ways to a new playlist as small labelled actions beside the title rather than outlined buttons
    // under the list. The sheet grows with the list instead of stopping at a fixed height.
    val maxListHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.62f).dp
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.playlist_switch_title), style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val newLabel = stringResource(R.string.playlist_new_short)
            LabelledAction(PlayerIcons.Add, newLabel, { naming = NamingIntent.Create }, slim = true,
                modifier = Modifier.width(slimActionWidth(newLabel)).testTag("playlist-new"))
            val importLabel = stringResource(R.string.action_import_playlist)
            LabelledAction(PlayerIcons.Download, importLabel, onImport, slim = true,
                modifier = Modifier.width(slimActionWidth(importLabel)).testTag("playlist-import"))
        }

        LazyColumn(
            modifier = Modifier.heightIn(max = maxListHeight),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                val active = playlist.id == activeId
                Card(
                    onClick = { onSelect(playlist.id) },
                    shape = RoundedCornerShape(20.dp),
                    colors = if (active) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) else CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("playlist-card-${playlist.id}")
                        .semantics { selected = active },
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // The cover: the playlist's sign, a tick on the open one, so it is told
                        // apart by shape as well as by the card's colour (AGENTS.md §8).
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (active) PlayerIcons.Check else PlayerIcons.Playlist, contentDescription = null,
                                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // How much is in it, in words: a bare number read as a row number.
                            val count = pluralStringResource(R.plurals.track_count, playlist.trackCount, playlist.trackCount)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(PlayerIcons.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp),
                                    tint = LocalContentColor.current.copy(alpha = 0.8f))
                                Text(if (active) stringResource(R.string.playlist_open_with_count, count) else count,
                                    style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                    color = LocalContentColor.current.copy(alpha = 0.8f))
                            }
                        }
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
                    }
                }
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
            // Held, not tapped (2026-11-12): the dialog says what goes, the hold makes the yes
            // deliberate.
            confirmButton = {
                HoldToConfirm(
                    icon = PlayerIcons.Remove,
                    label = stringResource(if (playlists.size <= 1) R.string.action_empty else R.string.action_delete),
                    onConfirm = {
                        onDelete(playlist.id)
                        confirmingDelete = null
                    },
                    askFirst = false,
                    slim = true,
                    modifier = Modifier.testTag("confirm-hold"),
                )
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
                // A playlist name is a title, so the keyboard offers a capital for the first letter
                // of each word and the autocorrect the rest of the phone uses. **It is the
                // keyboard's suggestion, not a rule** -- somebody who wants "d-bug #197" in lower
                // case types it and keeps it, which forcing the first character would not allow.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (name.isNotBlank()) onConfirm(name.trim()) },
                ),
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
