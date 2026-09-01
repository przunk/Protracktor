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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.TrackRef

/**
 * Browse: a full screen, one component, four domains.
 *
 * The owner asked for the choice of *where to look* to come first — local disk, the online
 * archives, a random pick, or a search — rather than for "add a folder" to be the only thing on
 * offer. Everything below that is the same list-and-tick machinery whatever the source, which is
 * why one component covers all four.
 */
@Composable
fun BrowseScreen(
    browse: BrowseState,
    playlistName: String?,
    contentPadding: PaddingValues,
    onOpenDomain: (BrowseDomain) -> Unit,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenFolder: (com.przunk.protracktor.data.GrantedFolder) -> Unit,
    onForgetFolder: (String) -> Unit,
    onIndexCatalogue: (String) -> Unit,
    onOpenCatalogue: (CatalogueSummary) -> Unit,
    onOpenGroup: (String) -> Unit,
    onRandom: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleLocal: () -> Unit,
    onToggleCatalogue: (String) -> Unit,
    onSearch: () -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        if (browse.indexing != null) {
            // An index download is minutes of work on a slow connection. Saying which catalogue and
            // showing movement is the difference between "working" and "hung".
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = stringResource(R.string.browse_indexing, browse.indexing),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }

        when (browse.domain) {
            BrowseDomain.ROOT -> DomainChooser(onOpenDomain = onOpenDomain, onRandom = onRandom)
            BrowseDomain.LOCAL -> LocalDomain(
                browse = browse,
                playlistName = playlistName,
                onPickFolder = onPickFolder,
                onPickFiles = onPickFiles,
                onOpenFolder = onOpenFolder,
                onForgetFolder = onForgetFolder,
                onAdd = onAdd,
            )
            BrowseDomain.ONLINE -> OnlineDomain(
                browse = browse,
                playlistName = playlistName,
                onIndexCatalogue = onIndexCatalogue,
                onOpenCatalogue = onOpenCatalogue,
                onOpenGroup = onOpenGroup,
                onAdd = onAdd,
            )
            BrowseDomain.SEARCH -> SearchDomain(
                browse = browse,
                playlistName = playlistName,
                onQueryChange = onQueryChange,
                onToggleLocal = onToggleLocal,
                onToggleCatalogue = onToggleCatalogue,
                onSearch = onSearch,
                onAdd = onAdd,
            )
        }
    }
}

@Composable
private fun DomainChooser(onOpenDomain: (BrowseDomain) -> Unit, onRandom: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            DomainRow(
                icon = PlayerIcons.Folder,
                title = stringResource(R.string.domain_local_title),
                subtitle = stringResource(R.string.domain_local_body),
                onClick = { onOpenDomain(BrowseDomain.LOCAL) },
            )
        }
        item {
            DomainRow(
                icon = PlayerIcons.Cloud,
                title = stringResource(R.string.domain_online_title),
                subtitle = stringResource(R.string.domain_online_body),
                onClick = { onOpenDomain(BrowseDomain.ONLINE) },
            )
        }
        item {
            DomainRow(
                icon = PlayerIcons.Dice,
                title = stringResource(R.string.domain_random_title),
                subtitle = stringResource(R.string.domain_random_body),
                onClick = onRandom,
            )
        }
        item {
            DomainRow(
                icon = PlayerIcons.Search,
                title = stringResource(R.string.domain_search_title),
                subtitle = stringResource(R.string.domain_search_body),
                onClick = { onOpenDomain(BrowseDomain.SEARCH) },
            )
        }
    }
}

@Composable
private fun DomainRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// --- local ------------------------------------------------------------------------------------

@Composable
private fun LocalDomain(
    browse: BrowseState,
    playlistName: String?,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenFolder: (com.przunk.protracktor.data.GrantedFolder) -> Unit,
    onForgetFolder: (String) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
) {
    if (browse.openFolder != null) {
        Selectable(browse = browse, playlistName = playlistName, onAdd = onAdd)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onPickFolder, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_add_folder))
            }
            OutlinedButton(onClick = onPickFiles, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_add_files))
            }
        }

        if (browse.folders.isEmpty()) {
            Text(
                text = stringResource(R.string.browse_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(browse.folders, key = { it.uri }) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(PlayerIcons.Folder, contentDescription = null) },
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
        }
    }
}

// --- online -----------------------------------------------------------------------------------

@Composable
private fun OnlineDomain(
    browse: BrowseState,
    playlistName: String?,
    onIndexCatalogue: (String) -> Unit,
    onOpenCatalogue: (CatalogueSummary) -> Unit,
    onOpenGroup: (String) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
) {
    when {
        browse.openAuthor != null -> Selectable(browse = browse, playlistName = playlistName, onAdd = onAdd)

        browse.openCatalogue != null -> {
            if (browse.loading) {
                Loading()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(browse.groups, key = { it.name }) { group ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    group.name.ifBlank { stringResource(R.string.browse_unknown) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                Text("${group.count}", style = MaterialTheme.typography.labelMedium)
                            },
                            modifier = Modifier.clickable { onOpenGroup(group.name) },
                        )
                    }
                }
            }
        }

        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(browse.catalogues, key = { it.id }) { catalogue ->
                ListItem(
                    headlineContent = { Text(catalogue.displayName) },
                    supportingContent = {
                        Text(
                            if (catalogue.indexed) {
                                pluralStringResource(
                                    R.plurals.track_count, catalogue.trackCount, catalogue.trackCount
                                )
                            } else {
                                stringResource(R.string.catalogue_not_indexed)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = { Icon(PlayerIcons.Cloud, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { onIndexCatalogue(catalogue.id) }) {
                            Icon(
                                PlayerIcons.Download,
                                stringResource(R.string.a11y_index_catalogue, catalogue.displayName),
                            )
                        }
                    },
                    // Only openable once there is an index. Tapping an empty catalogue and landing
                    // on an empty list would teach nothing about why.
                    modifier = if (catalogue.indexed) {
                        Modifier.clickable { onOpenCatalogue(catalogue) }
                    } else {
                        Modifier
                    },
                )
            }
            item {
                Text(
                    text = stringResource(R.string.catalogue_more_coming),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

// --- search -----------------------------------------------------------------------------------

// FlowRow is still experimental. Taken knowingly: the scope chips have to wrap, and a Row
// that clips them would hide catalogues the user is trying to include.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchDomain(
    browse: BrowseState,
    playlistName: String?,
    onQueryChange: (String) -> Unit,
    onToggleLocal: () -> Unit,
    onToggleCatalogue: (String) -> Unit,
    onSearch: () -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = browse.query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { Text(stringResource(R.string.search_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(PlayerIcons.Search, stringResource(R.string.domain_search_title))
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = browse.searchLocal,
                onClick = onToggleLocal,
                label = { Text(stringResource(R.string.search_scope_local)) },
            )
            browse.catalogues.filter { it.indexed }.forEach { catalogue ->
                FilterChip(
                    selected = catalogue.id in browse.searchCatalogues,
                    onClick = { onToggleCatalogue(catalogue.id) },
                    label = { Text(catalogue.displayName) },
                )
            }
        }
        Text(
            // Nothing ticked among the catalogues means all of them, which is a rule the user
            // cannot guess from looking at unticked chips.
            text = stringResource(R.string.search_scope_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (browse.loading) Loading() else Selectable(browse, playlistName, onAdd)
    }
}

// --- shared -----------------------------------------------------------------------------------

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * The list-and-tick part, shared by every domain.
 *
 * Selection is this composable's own business and dies with it. Holding it in the controller would
 * mean remembering to clear it, and a stale tick that survives a rescan adds a file nobody chose.
 */
@Composable
private fun Selectable(
    browse: BrowseState,
    playlistName: String?,
    onAdd: (List<TrackRef>) -> Unit,
) {
    var selected by remember(browse.openFolder?.uri, browse.openAuthor, browse.query) {
        mutableStateOf(emptySet<String>())
    }
    LaunchedEffect(browse.tracks) {
        selected = selected.intersect(browse.tracks.map { it.id }.toSet())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (browse.tracks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.track_count, browse.tracks.size, browse.tracks.size
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
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
                            if (selected.size == browse.tracks.size) R.string.browse_select_none
                            else R.string.browse_select_all
                        )
                    )
                }
            }
        }

        when {
            browse.loading -> Loading()
            browse.tracks.isEmpty() -> Text(
                text = stringResource(R.string.browse_nothing_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(browse.tracks, key = { it.id }) { track ->
                    val ticked = track.id in selected
                    ListItem(
                        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = if (track.subtitle.isNotBlank()) {
                            { Text(track.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        } else null,
                        leadingContent = { Checkbox(checked = ticked, onCheckedChange = null) },
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
