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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.TrackRef

/**
 * Browse: pick a folder, then pick what goes in the playlist.
 *
 * The whole point of the screen is that adding a folder is not the same as adding everything in it.
 * Two hundred files found and three of them wanted is the normal case with these archives.
 *
 * Granted folders are remembered, so the second visit does not start at a file picker.
 */
@Composable
fun BrowseScreen(
    browse: BrowseState,
    playlistName: String?,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenFolder: (GrantedFolder) -> Unit,
    onForgetFolder: (String) -> Unit,
    onCloseFolder: () -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
) {
    if (browse.openFolder == null) {
        FolderList(
            folders = browse.folders,
            onPickFolder = onPickFolder,
            onPickFiles = onPickFiles,
            onOpenFolder = onOpenFolder,
            onForgetFolder = onForgetFolder,
        )
    } else {
        FolderContents(
            browse = browse,
            playlistName = playlistName,
            onCloseFolder = onCloseFolder,
            onAdd = onAdd,
        )
    }
}

@Composable
private fun FolderList(
    folders: List<GrantedFolder>,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenFolder: (GrantedFolder) -> Unit,
    onForgetFolder: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.browse_title), style = MaterialTheme.typography.titleLarge)

        if (folders.isEmpty()) {
            Text(
                text = stringResource(R.string.browse_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(folders, key = { it.uri }) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            IconButton(onClick = { onForgetFolder(folder.uri) }) {
                                Icon(
                                    PlayerIcons.Remove,
                                    stringResource(R.string.a11y_forget_folder, folder.displayName),
                                )
                            }
                        },
                        modifier = Modifier.clickable { onOpenFolder(folder) },
                    )
                }
            }
            HorizontalDivider()
        }

        OutlinedButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_add_folder))
        }
        OutlinedButton(onClick = onPickFiles, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_add_files))
        }
    }
}

@Composable
private fun FolderContents(
    browse: BrowseState,
    playlistName: String?,
    onCloseFolder: () -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
) {
    // Selection is the screen's own business and dies with it. Nothing else needs to know which
    // rows were ticked, and holding it in the controller would mean remembering to clear it.
    var selected by remember(browse.openFolder?.uri) { mutableStateOf(emptySet<String>()) }

    // Anything that vanished on a rescan must not stay silently selected and get added later.
    LaunchedEffect(browse.tracks) {
        selected = selected.intersect(browse.tracks.map { it.id }.toSet())
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCloseFolder) { Text(stringResource(R.string.action_back)) }
            Text(
                text = browse.openFolder?.displayName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (browse.tracks.isNotEmpty()) {
                TextButton(
                    onClick = {
                        selected = if (selected.size == browse.tracks.size) {
                            emptySet()
                        } else {
                            browse.tracks.map { it.id }.toSet()
                        }
                    }
                ) {
                    Text(
                        stringResource(
                            if (selected.size == browse.tracks.size) {
                                R.string.browse_select_none
                            } else {
                                R.string.browse_select_all
                            }
                        )
                    )
                }
            }
        }

        when {
            browse.loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(48.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            browse.tracks.isEmpty() -> Text(
                text = stringResource(R.string.browse_nothing_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )

            else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(browse.tracks, key = { it.id }) { track ->
                    val ticked = track.id in selected
                    ListItem(
                        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = if (track.subtitle.isNotBlank()) {
                            { Text(track.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        } else null,
                        leadingContent = {
                            Checkbox(checked = ticked, onCheckedChange = null)
                        },
                        modifier = Modifier.clickable {
                            selected = if (ticked) selected - track.id else selected + track.id
                        },
                    )
                }
            }
        }

        if (selected.isNotEmpty()) {
            Button(
                onClick = { onAdd(browse.tracks.filter { it.id in selected }) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    pluralStringResource(R.plurals.browse_add_selected, selected.size, selected.size) +
                        (playlistName?.let { " → $it" } ?: "")
                )
            }
        }
    }
}
