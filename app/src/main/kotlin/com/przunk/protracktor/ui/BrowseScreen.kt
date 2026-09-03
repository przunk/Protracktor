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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemDefaults
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
import com.przunk.protracktor.net.CacheBudget
import com.przunk.protracktor.net.Catalogue
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
    onScanFolder: (com.przunk.protracktor.data.GrantedFolder) -> Unit,
    onIndexCatalogue: (String) -> Unit,
    onDownloadSongLengths: () -> Unit,
    onOpenCatalogue: (CatalogueSummary) -> Unit,
    onOpenGroup: (String) -> Unit,
    onRandom: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleLocal: () -> Unit,
    onToggleOnline: () -> Unit,
    onToggleCatalogue: (String) -> Unit,
    onSearch: () -> Unit,
    onClearHistory: () -> Unit,
    playingId: String?,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
    onAddStayingHere: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (List<TrackRef>) -> Unit = {},
) {
    // One per Browse session. It dies when Browse closes, which is what makes a fresh entry start
    // at the top (`docs/STATUS.md` C6) while a descent and return does not.
    val scroll = rememberBrowseScroll()

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
                scroll = scroll,
                playlistName = playlistName,
                playingId = playingId,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
                onPickFolder = onPickFolder,
                onPickFiles = onPickFiles,
                onOpenFolder = onOpenFolder,
                onForgetFolder = onForgetFolder,
                onScanFolder = onScanFolder,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddStayingHere = onAddStayingHere,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
            )
            BrowseDomain.ONLINE -> OnlineDomain(
                browse = browse,
                scroll = scroll,
                playlistName = playlistName,
                playingId = playingId,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
                onIndexCatalogue = onIndexCatalogue,
                onDownloadSongLengths = onDownloadSongLengths,
                onOpenCatalogue = onOpenCatalogue,
                onOpenGroup = onOpenGroup,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddStayingHere = onAddStayingHere,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
            )
            BrowseDomain.HISTORY -> HistoryDomain(
                browse = browse,
                scroll = scroll,
                playlistName = playlistName,
                playingId = playingId,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
                onClearHistory = onClearHistory,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddStayingHere = onAddStayingHere,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
            )
            BrowseDomain.SEARCH -> SearchDomain(
                browse = browse,
                scroll = scroll,
                playlistName = playlistName,
                playingId = playingId,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
                onQueryChange = onQueryChange,
                onToggleLocal = onToggleLocal,
                onToggleOnline = onToggleOnline,
                onToggleCatalogue = onToggleCatalogue,
                onSearch = onSearch,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddStayingHere = onAddStayingHere,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
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
                icon = PlayerIcons.Info,
                title = stringResource(R.string.domain_history_title),
                subtitle = stringResource(R.string.domain_history_body),
                onClick = { onOpenDomain(BrowseDomain.HISTORY) },
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
    scroll: BrowseScroll,
    playlistName: String?,
    playingId: String?,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenFolder: (com.przunk.protracktor.data.GrantedFolder) -> Unit,
    onForgetFolder: (String) -> Unit,
    onScanFolder: (com.przunk.protracktor.data.GrantedFolder) -> Unit,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
    onAddStayingHere: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (List<TrackRef>) -> Unit,
) {
    val folder = browse.openFolder
    if (folder != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            // A scan reads every file in the tree, so it says how far it has got. On a network
            // share this is minutes, and a spinner with no number is indistinguishable from a hang.
            browse.scanProgress?.let { (done, total) ->
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = if (total > 0) {
                            stringResource(R.string.scan_progress, done, total)
                        } else {
                            stringResource(R.string.scan_listing)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }

            if (browse.scanProgress == null && (browse.folderUnscanned || browse.folderStale)) {
                // Two different sentences, because they are two different situations: never looked,
                // versus looked with decoders this build no longer has.
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(
                            if (browse.folderUnscanned) R.string.folder_unscanned
                            else R.string.folder_stale
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { onScanFolder(folder) },
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) { Text(stringResource(R.string.action_scan_folder)) }
                }
            }

            Selectable(
                browse = browse,
                scroll = scroll,
                playlistName = playlistName,
                playingId = playingId,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddStayingHere = onAddStayingHere,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
            )
        }
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
                        modifier = Modifier.clickable {
                            scroll.descendingFrom(browse.levelKey(), folder.uri)
                            onOpenFolder(folder)
                        },
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
    scroll: BrowseScroll,
    playlistName: String?,
    playingId: String?,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onIndexCatalogue: (String) -> Unit,
    onDownloadSongLengths: () -> Unit,
    onOpenCatalogue: (CatalogueSummary) -> Unit,
    onOpenGroup: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
    onAddStayingHere: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (List<TrackRef>) -> Unit,
) {
    when {
        browse.openAuthor != null -> Selectable(
            browse = browse,
            scroll = scroll,
            playlistName = playlistName,
            playingId = playingId,
            onPlay = onPlay,
            onAdd = onAdd,
            onAddStayingHere = onAddStayingHere,
            onAddToOtherPlaylist = onAddToOtherPlaylist,
            onShowNeighbours = onShowNeighbours,
            onShareFile = onShareFile,
            onShareLink = onShareLink,
        )

        browse.openCatalogue != null -> {
            if (browse.loading) {
                Loading()
            } else {
                val key = browse.levelKey()
                val listState = scroll.stateFor(key)
                RestorePosition(scroll, key, listState, browse.groups.map { it.name }, browse.loading)

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                            modifier = Modifier.clickable {
                                scroll.descendingFrom(key, group.name)
                                onOpenGroup(group.name)
                            },
                        )
                    }
                }
            }
        }

        else -> {
        val key = browse.levelKey()
        val listState = scroll.stateFor(key)
        RestorePosition(scroll, key, listState, browse.catalogues.map { it.id }, browse.loading)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(browse.catalogues, key = { it.id }) { catalogue ->
                ListItem(
                    headlineContent = { Text(catalogue.displayName) },
                    supportingContent = {
                        Text(
                            if (catalogue.isOnlineOnly) {
                                stringResource(R.string.catalogue_online_search)
                            } else if (catalogue.indexed) {
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
                    trailingContent = if (catalogue.isOnlineOnly) {
                        null
                    } else {
                        {
                            IconButton(onClick = { onIndexCatalogue(catalogue.id) }) {
                                Icon(
                                    PlayerIcons.Download,
                                    stringResource(R.string.a11y_index_catalogue, catalogue.displayName),
                                )
                            }
                        }
                    },
                    // Only openable once there is an index. Tapping an empty catalogue and landing
                    // on an empty list would teach nothing about why.
                    modifier = if (catalogue.indexed && !catalogue.isOnlineOnly) {
                        Modifier.clickable { onOpenCatalogue(catalogue) }
                    } else {
                        Modifier
                    },
                )
            }
            // Not a catalogue: nothing in it can be played. It answers "how long is this SID"
            // about tunes that came from anywhere at all, which is why it sits under the list
            // rather than in it.
            item {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.song_lengths_title)) },
                    supportingContent = {
                        Text(
                            if (browse.songLengthCount > 0) {
                                pluralStringResource(
                                    R.plurals.song_lengths_count,
                                    browse.songLengthCount,
                                    browse.songLengthCount,
                                )
                            } else {
                                stringResource(R.string.song_lengths_none)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = { Icon(PlayerIcons.Info, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = onDownloadSongLengths) {
                            Icon(
                                PlayerIcons.Download,
                                stringResource(R.string.a11y_download_song_lengths),
                            )
                        }
                    },
                )
            }
            // What the app is holding, said out loud. It cannot be deleted from here yet
            // (`docs/BACKLOG.md` A13), but an app that takes disk quietly is worse than one that
            // takes the same disk and says so.
            item {
                val (cache, permanent) = browse.storageBytes
                if (cache > 0 || permanent > 0) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(
                            R.string.storage_summary,
                            cache / (1024 * 1024),
                            CacheBudget.DEFAULT_CEILING_BYTES / (1024 * 1024),
                            permanent / (1024 * 1024),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
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
}

// --- search -----------------------------------------------------------------------------------

// FlowRow is still experimental. Taken knowingly: the scope chips have to wrap, and a Row
// that clips them would hide catalogues the user is trying to include.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchDomain(
    browse: BrowseState,
    scroll: BrowseScroll,
    playlistName: String?,
    playingId: String?,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleLocal: () -> Unit,
    onToggleOnline: () -> Unit,
    onToggleCatalogue: (String) -> Unit,
    onSearch: () -> Unit,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
    onAddStayingHere: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (List<TrackRef>) -> Unit,
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

        // Two levels: which side to search, then which catalogues within the online side. The
        // earlier version treated "no catalogue ticked" as "all of them", which made the filter look
        // broken -- unticking Modland searched Modland anyway.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = browse.searchLocal,
                onClick = onToggleLocal,
                label = { Text(stringResource(R.string.search_scope_local)) },
            )
            FilterChip(
                selected = browse.searchOnline,
                onClick = onToggleOnline,
                label = { Text(stringResource(R.string.search_scope_online)) },
            )
        }

        val indexed = browse.catalogues.filter { it.indexed }
        if (browse.searchOnline && indexed.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                indexed.forEach { catalogue ->
                    FilterChip(
                        selected = catalogue.id in browse.searchCatalogues,
                        onClick = { onToggleCatalogue(catalogue.id) },
                        label = { Text(catalogue.displayName) },
                    )
                }
            }
        }
        if (browse.searchOnline && indexed.isEmpty()) {
            Text(
                text = stringResource(R.string.search_no_catalogues),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (browse.loading) {
            Loading()
        } else {
            Selectable(
                browse = browse,
                scroll = scroll,
                playlistName = playlistName,
                playingId = playingId,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddStayingHere = onAddStayingHere,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
            )
        }
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
// --- history ----------------------------------------------------------------------------------

/**
 * What has been played.
 *
 * Deliberately the same list component as everywhere else, so a tune found here can be played or
 * ticked into a playlist exactly as it can when found anywhere else. The only thing history adds is
 * the way out of it.
 */
@Composable
private fun HistoryDomain(
    browse: BrowseState,
    scroll: BrowseScroll,
    playlistName: String?,
    playingId: String?,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onClearHistory: () -> Unit,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
    onAddStayingHere: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (List<TrackRef>) -> Unit,
) {
    if (browse.loading) {
        Loading()
        return
    }
    if (browse.history.isEmpty()) {
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onClearHistory) {
                Text(stringResource(R.string.action_clear_history))
            }
        }
        Selectable(
            browse = browse,
            scroll = scroll,
            playlistName = playlistName,
            playingId = playingId,
            onPlay = onPlay,
            onAdd = onAdd,
            onAddStayingHere = onAddStayingHere,
            onAddToOtherPlaylist = onAddToOtherPlaylist,
            onShowNeighbours = onShowNeighbours,
            onShareFile = onShareFile,
            onShareLink = onShareLink,
        )
    }
}

/**
 * The track list, shared by every domain.
 *
 * Two modes, and `docs/ARCHITECTURE.md` §17 is why they are these two. **Normally a tap plays** --
 * the app used to select on tap, which almost nothing does, and the owner said so. **A long press
 * starts selecting**, a checkbox appears where nothing was, and further taps tick rows. Back leaves
 * the selection with nothing ticked.
 *
 * Selection is this composable's own business and dies with it. Holding it in the controller would
 * mean remembering to clear it, and a stale tick that survives a rescan adds a file nobody chose.
 */
/**
 * Puts the row you came out of back on screen, once the list it lives in has arrived.
 *
 * Identity first, and there is no index fallback on purpose: an index is only "where I was" while
 * the list is unchanged, and the case this exists for is precisely the one where it changed. When
 * the row is gone, the level's own saved offset is already correct enough, and jumping somewhere
 * arbitrary because a number still parses would be worse than leaving it alone.
 */
@Composable
private fun RestorePosition(
    scroll: BrowseScroll,
    key: String,
    listState: LazyListState,
    rowKeys: List<String>,
    loading: Boolean,
) {
    LaunchedEffect(key, rowKeys, loading) {
        when (val what = scroll.restoreFor(key, rowKeys, loading)) {
            is Restore.ScrollTo -> {
                listState.bringIntoView(what.index)
                scroll.returned(key)
            }
            Restore.Forget -> scroll.returned(key)
            Restore.Wait, Restore.Nothing -> Unit
        }
    }
}

@Composable
private fun Selectable(
    browse: BrowseState,
    scroll: BrowseScroll,
    playlistName: String?,
    playingId: String?,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
    onAddStayingHere: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (List<TrackRef>) -> Unit,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
) {
    var selected by remember(browse.openFolder?.uri, browse.openAuthor, browse.query) {
        mutableStateOf(emptySet<String>())
    }
    var showingInfo by remember { mutableStateOf<TrackRef?>(null) }
    LaunchedEffect(browse.tracks) {
        selected = selected.intersect(browse.tracks.map { it.id }.toSet())
    }
    val selecting = selected.isNotEmpty()

    // Takes back before the level-and-exit handler outside, because the innermost enabled handler
    // wins. That is the stack the owner asked for: leave the selection, then go up a level, then
    // out to the playlist -- one step each.
    BackHandler(enabled = selecting) { selected = emptySet() }

    showingInfo?.let { track ->
        TrackInfoDialog(track = track, onDismiss = { showingInfo = null })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (browse.tracks.isNotEmpty()) {
            // A fixed height, because the select-all button only exists while selecting and a
            // header that grows when it appears shifts the whole list under the finger that just
            // long-pressed. Same reason as the row below.
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (selecting) {
                        pluralStringResource(R.plurals.browse_selected, selected.size, selected.size)
                    } else {
                        pluralStringResource(
                            R.plurals.track_count, browse.tracks.size, browse.tracks.size
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                // Only while selecting. Before that there is nothing to select all of, and the
                // button was advertising a mode the user had not entered.
                if (selecting) {
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
        }

        when {
            browse.loading -> Loading()
            browse.tracks.isEmpty() -> Text(
                text = stringResource(R.string.browse_nothing_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            else -> {
                val key = browse.levelKey()
                val listState = scroll.stateFor(key)
                RestorePosition(scroll, key, listState, browse.tracks.map { it.id }, browse.loading)
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(browse.tracks, key = { _, track -> track.id }) { index, track ->
                    BrowseTrackRow(
                        track = track,
                        index = index,
                        ticked = track.id in selected,
                        selecting = selecting,
                        playing = track.id == playingId,
                        onPlay = { onPlay(index) },
                        onToggle = {
                            selected = if (track.id in selected) selected - track.id
                            else selected + track.id
                        },
                        onStartSelecting = { selected = selected + track.id },
                        // Not `onAdd`: that one closes Browse, which is right for the bulk button
                        // -- you have finished choosing -- and wrong for a menu item on one row,
                        // where the point is to keep browsing (`docs/STATUS.md` C7).
                        onAdd = { onAddStayingHere(listOf(track)) },
                        onAddToOtherPlaylist = { onAddToOtherPlaylist(listOf(track)) },
                        onInfo = { showingInfo = track },
                        onShowNeighbours = track.takeIf { Catalogue.owning(it.id) != null }
                            ?.let { { onShowNeighbours(it) } },
                        onShareFile = { onShareFile(track) },
                        onShareLink = track.takeIf { Catalogue.owning(it.id) != null }
                            ?.let { { onShareLink(it) } },
                    )
                }
            }
            }
        }

        if (selecting) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        onAdd(browse.tracks.filter { it.id in selected })
                        selected = emptySet()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        pluralStringResource(R.plurals.browse_add_selected, selected.size, selected.size) +
                            (playlistName?.let { " \u2192 $it" } ?: "")
                    )
                }
                IconButton(
                    onClick = {
                        val toAdd = browse.tracks.filter { it.id in selected }
                        selected = emptySet()
                        onAddToOtherPlaylist(toAdd)
                    },
                ) {
                    Icon(
                        imageVector = PlayerIcons.PlaylistAdd,
                        contentDescription = stringResource(R.string.action_add_to_other_playlist),
                    )
                }
            }
        }
    }
}

/**
 * One row of a track list outside the playlist.
 *
 * The same anatomy as a playlist row minus the drag handle, which is the only thing that is
 * genuinely different: a playlist has an order that belongs to the user and these do not.
 *
 * No ordinal. In the playlist the number answers "where am I in three hundred rows of *my* list";
 * here it would only say which row of somebody else's archive this is. The space it would have
 * taken is the checkbox's, so the row does not change width when selection begins.
 */
/** Wide enough for a checkbox, and reserved whether or not one is showing. */
private val CHECKBOX_SLOT = 40.dp

/** What every track row is at least, in both modes, so entering selection moves nothing. */
private val ROW_HEIGHT = 72.dp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BrowseTrackRow(
    track: TrackRef,
    index: Int,
    ticked: Boolean,
    selecting: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onToggle: () -> Unit,
    onStartSelecting: () -> Unit,
    onAdd: () -> Unit,
    onAddToOtherPlaylist: () -> Unit,
    onInfo: () -> Unit,
    onShowNeighbours: (() -> Unit)?,
    onShareFile: () -> Unit,
    onShareLink: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = track.subtitle.takeIf { it.isNotBlank() }?.let { where ->
            // The full source rather than just the author: in a search result the question is
            // "which one is this", and two tunes with one name are told apart by where they live.
            { Text(where, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        // The slot is always here, empty or not. Letting it appear along with the checkbox made
        // every row grow the moment selection started, so the list jumped by more than a row --
        // under the very finger that had just long-pressed one.
        leadingContent = {
            Box(modifier = Modifier.size(CHECKBOX_SLOT), contentAlignment = Alignment.Center) {
                if (selecting) Checkbox(checked = ticked, onCheckedChange = { onToggle() })
            }
        },
        // Nothing here while selecting: a menu on a row you are ticking is a second meaning for a
        // press that already has one.
        trailingContent = if (selecting) {
            null
        } else {
            {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(PlayerIcons.More, stringResource(R.string.a11y_track_menu, track.title))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_track)) },
                            leadingIcon = { Icon(PlayerIcons.Add, contentDescription = null) },
                            onClick = { menuOpen = false; onAdd() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_to_other_playlist)) },
                            leadingIcon = { Icon(PlayerIcons.PlaylistAdd, contentDescription = null) },
                            onClick = { menuOpen = false; onAddToOtherPlaylist() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_info)) },
                            leadingIcon = { Icon(PlayerIcons.Info, contentDescription = null) },
                            onClick = { menuOpen = false; onInfo() },
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
            // A floor rather than a fixed height: rows with no source line are shorter than rows
            // with one, and a list whose rows change height when a checkbox arrives is the defect
            // this is here to prevent.
            .heightIn(min = ROW_HEIGHT)
            // `combinedClickable` uses the platform long-press timeout, and a gesture that turns
            // into a scroll is claimed by the list before it ever becomes a long press. Both matter:
            // the owner's complaint about another player is a long press firing at a twentieth of a
            // second mid-scroll, after which back throws him out of the list entirely.
            .combinedClickable(
                onClick = { if (selecting) onToggle() else onPlay() },
                onLongClick = { if (!selecting) onStartSelecting() },
            ),
    )
}
