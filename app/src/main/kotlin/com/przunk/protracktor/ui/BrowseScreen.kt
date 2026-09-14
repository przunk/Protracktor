// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.przunk.protracktor.player.Platforms
import com.przunk.protracktor.player.RandomScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.DownloadKeys
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.QueueLink
import com.przunk.protracktor.player.SearchScope
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
    onDownloadTrackMetadata: () -> Unit,
    onDownloadFavourites: () -> Unit,
    onDownloadReplays: () -> Unit,
    onOpenCatalogue: (CatalogueSummary) -> Unit,
    onOpenGroup: (String) -> Unit,
    onRandom: () -> Unit,
    onChooseRandomScope: () -> Unit,
    onQueryChange: (String) -> Unit,
    onScope: (SearchScope) -> Unit,
    onToggleCatalogue: (String) -> Unit,
    onTogglePlatform: (String) -> Unit,
    onSearch: () -> Unit,
    onClearHistory: () -> Unit,
    playingId: String?,
    loadingId: String?,
    /**
     * Whose folder this is, while a Random session waits under it (`docs/BACKLOG.md` A41), or null.
     *
     * The heading it draws is the dice's own shape — icon, what this is, and under it which one —
     * because a digression is the same kind of state: something plays from somewhere that is not
     * the playlist, and there is a way back.
     */
    digressionAuthor: String? = null,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onSendToWeb: (List<TrackRef>) -> Unit,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (List<TrackRef>) -> Unit = {},
) {
    // One per Browse session. It dies when Browse closes, which is what makes a fresh entry start
    // at the top (`docs/STATUS.md` C6) while a descent and return does not.
    val scroll = rememberBrowseScroll()

    // **A step through the catalogue, felt** (owner, 2026-09-10). Keyed on where Browse is rather
    // than on the taps that got it there: descending, Back, "more from this author" and the jump a
    // search result makes all end up changing these five fields, and a call at each of those sites
    // would be a list that goes stale the first time a sixth route is added.
    //
    // `transition()` is the lightest thing in `Haptics`, on purpose — this fires on every level of
    // every descent, which is the most frequent haptic in the app by a wide margin.
    HapticOnChange(
        listOf(
            browse.domain,
            browse.openFolder,
            browse.openCatalogue?.id,
            browse.openFormat,
            browse.openAuthor,
        )
    ) { transition() }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        if (digressionAuthor != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                // The dice's own header height, so switching between the two screens does not move
                // the icon (owner, 2026-09-14).
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SESSION_HEADER_HEIGHT)
                    .padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = PlayerIcons.Detour,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.browsing_author),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = digressionAuthor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // **The banner that used to live here is gone** (owner, 2026-09-10): "jest to redundantne
        // i psuje UI (przeskakuje na czas istnienia paska)". It named the running downloads and
        // drew an indeterminate bar above the list, so starting one pushed the whole list down and
        // finishing it pulled the list back up -- while the row he had just tapped was already
        // saying the same thing with its own spinner.
        //
        // Both things it carried moved into the rows: `DownloadAction` says "indexing…" under its
        // spinner, and the replay row -- the one download that counts its files -- shows the count
        // in its own supporting line.

        when (browse.domain) {
            BrowseDomain.ROOT -> DomainChooser(
                browse = browse,
                onOpenDomain = onOpenDomain,
                onRandom = onRandom,
                onChooseRandomScope = onChooseRandomScope,
            )
            BrowseDomain.LOCAL -> LocalDomain(
                browse = browse,
                scroll = scroll,
                playlistName = playlistName,
                playingId = playingId,
                loadingId = loadingId,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
                onSendToWeb = onSendToWeb,
                onPickFolder = onPickFolder,
                onPickFiles = onPickFiles,
                onOpenFolder = onOpenFolder,
                onForgetFolder = onForgetFolder,
                onScanFolder = onScanFolder,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
            )
            BrowseDomain.ONLINE -> OnlineDomain(
                browse = browse,
                scroll = scroll,
                playlistName = playlistName,
                playingId = playingId,
                loadingId = loadingId,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
                onSendToWeb = onSendToWeb,
                onIndexCatalogue = onIndexCatalogue,
                onDownloadSongLengths = onDownloadSongLengths,
                onDownloadTrackMetadata = onDownloadTrackMetadata,
                onDownloadFavourites = onDownloadFavourites,
                onDownloadReplays = onDownloadReplays,
                onOpenCatalogue = onOpenCatalogue,
                onOpenGroup = onOpenGroup,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
            )
            BrowseDomain.HISTORY -> HistoryDomain(
                browse = browse,
                scroll = scroll,
                playlistName = playlistName,
                playingId = playingId,
                loadingId = loadingId,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
                onSendToWeb = onSendToWeb,
                onClearHistory = onClearHistory,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
            )
            BrowseDomain.SEARCH -> SearchDomain(
                browse = browse,
                scroll = scroll,
                playlistName = playlistName,
                playingId = playingId,
                loadingId = loadingId,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
                onSendToWeb = onSendToWeb,
                onQueryChange = onQueryChange,
                onScope = onScope,
                onToggleCatalogue = onToggleCatalogue,
                onTogglePlatform = onTogglePlatform,
                onSearch = onSearch,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
            )
        }
    }
}

@Composable
private fun DomainChooser(
    browse: BrowseState,
    onOpenDomain: (BrowseDomain) -> Unit,
    onRandom: () -> Unit,
    onChooseRandomScope: () -> Unit,
) {
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
            // The scope lives in the title and the subtitle, the way the search field's label
            // carries what a search covers. A dice that quietly remembered a setting would have
            // stopped being a dice; saying it out loud is what lets it remember one at all.
            val scopeName = when (val scope = browse.randomScope) {
                is RandomScope.Everything -> null
                is RandomScope.Favourites -> stringResource(R.string.random_scope_favourites)
                is RandomScope.OnPlatform -> Platforms.byId(scope.platformId)?.name
            }
            DomainRow(
                icon = PlayerIcons.Dice,
                title = scopeName?.let {
                    stringResource(R.string.domain_random_title_scoped, it)
                } ?: stringResource(R.string.domain_random_title),
                subtitle = stringResource(
                    if (scopeName != null) R.string.domain_random_body_scoped
                    else R.string.domain_random_body
                ),
                onClick = onRandom,
                onLongClick = onChooseRandomScope,
                longClickLabel = stringResource(R.string.a11y_choose_random_scope),
            )
        }
        item {
            DomainRow(
                icon = PlayerIcons.History,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DomainRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    val haptics = rememberHaptics()
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val rememberedClick = remember { { currentClick() } }
    val rememberedLongClick: () -> Unit = remember {
        {
            haptics.gestureEnd()
            currentLongClick?.invoke()
        }
    }
    // **A row of its own rather than a `ListItem`, because these rows must not change size.** The
    // Random row's words depend on what the dice is set to, and a `ListItem` grows with them: with
    // "everything" the second line wrapped, the row got taller, the icon sat above centre and every
    // row under it moved a few pixels (owner, 2026-09-14). A fixed height tall enough for two lines
    // makes the list stand still whatever the scope says.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(DOMAIN_ROW_HEIGHT)
            // `combinedClickable` only where a row has a second action -- `clickable` elsewhere, so
            // a row with nothing to hold does not advertise a long press to TalkBack that does
            // nothing.
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(onClick = rememberedClick)
                } else {
                    // Remembered handlers, for the reason `PlayerDock.TransportButton` sets out at
                    // length: a fresh lambda restarts the gesture detector, and a detector
                    // restarted under a finger that is still down starts timing another long press.
                    Modifier.combinedClickable(
                        onClick = rememberedClick,
                        onLongClickLabel = longClickLabel,
                        onLongClick = rememberedLongClick,
                    )
                }
            )
            .padding(horizontal = 16.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * How tall the rows at the root of Browse stand, whatever their words.
 *
 * Room for a title and two lines under it: the Random row says what the dice is set to, and that
 * sentence is a line longer for some scopes than for others.
 */
private val DOMAIN_ROW_HEIGHT = 78.dp

/**
 * Where the dice picks from.
 *
 * The **same chips as the search filter**, down to the rule that a platform with nothing indexed is
 * drawn disabled — one vocabulary for "which machine", not two. `Platforms` and the counts were
 * built for the search and are reused whole.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RandomScopeSheet(
    browse: BrowseState,
    onPick: (RandomScope) -> Unit,
    onDownloadFavourites: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.random_scope_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = browse.randomScope is RandomScope.Everything,
                onClick = { onPick(RandomScope.Everything) },
                label = { Text(stringResource(R.string.random_scope_everything)) },
            )
            // Beside "Everything" rather than among the platforms, because it is not one: it cuts
            // across every machine in the list. Disabled with a reason the sheet can show, since a
            // chip that is simply dead is the complaint the platform chips already earned.
            FilterChip(
                selected = browse.randomScope is RandomScope.Favourites,
                enabled = browse.favouriteCount > 0,
                onClick = { onPick(RandomScope.Favourites) },
                label = { Text(stringResource(R.string.random_scope_favourites)) },
            )
            Platforms.all.forEach { platform ->
                val held = browse.platformCounts[platform.id] ?: 0
                FilterChip(
                    selected = (browse.randomScope as? RandomScope.OnPlatform)?.platformId == platform.id,
                    enabled = held > 0,
                    onClick = { onPick(RandomScope.OnPlatform(platform.id)) },
                    label = { Text(platform.name) },
                )
            }
        }

        // **A disabled chip has to say why, and here it can also fix it.** The owner met a dead
        // platform chip once already and asked for the reason to be shown; a dead Favourites chip
        // is worse, because the thing it needs is a 142 KB download the app can start from this
        // sheet. Zero has two causes, and they take different advice -- the list was never
        // downloaded, or it was and Modland is not indexed, in which case offering a download
        // again would send somebody round a loop.
        if (browse.favouriteCount == 0) {
            val downloading = browse.indexing.containsKey(DownloadKeys.FAVOURITES)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (browse.favouritesListed > 0) R.string.random_scope_favourites_unindexed
                        else R.string.random_scope_favourites_missing
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (browse.favouritesListed == 0) {
                    TextButton(onClick = onDownloadFavourites, enabled = !downloading) {
                        Text(
                            stringResource(
                                if (downloading) R.string.random_scope_favourites_downloading
                                else R.string.random_scope_favourites_download
                            )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// --- local ------------------------------------------------------------------------------------

@Composable
private fun LocalDomain(
    browse: BrowseState,
    scroll: BrowseScroll,
    playlistName: String?,
    playingId: String?,
    loadingId: String?,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onSendToWeb: (List<TrackRef>) -> Unit,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenFolder: (com.przunk.protracktor.data.GrantedFolder) -> Unit,
    onForgetFolder: (String) -> Unit,
    onScanFolder: (com.przunk.protracktor.data.GrantedFolder) -> Unit,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
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
                loadingId = loadingId,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
                onSendToWeb = onSendToWeb,
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
    loadingId: String?,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onSendToWeb: (List<TrackRef>) -> Unit,
    onIndexCatalogue: (String) -> Unit,
    onDownloadSongLengths: () -> Unit,
    onDownloadTrackMetadata: () -> Unit,
    onDownloadFavourites: () -> Unit,
    onDownloadReplays: () -> Unit,
    onOpenCatalogue: (CatalogueSummary) -> Unit,
    onOpenGroup: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (List<TrackRef>) -> Unit,
) {
    val haptics = rememberHaptics()
    when {
        browse.openAuthor != null -> Selectable(
            browse = browse,
            scroll = scroll,
            playlistName = playlistName,
            playingId = playingId,
            loadingId = loadingId,
            onPlay = onPlay,
            onAdd = onAdd,
            onAddToOtherPlaylist = onAddToOtherPlaylist,
            onShowNeighbours = onShowNeighbours,
            onShareFile = onShareFile,
            onShareLink = onShareLink,
            onSendToWeb = onSendToWeb,
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
                        Column {
                            // What it holds, and what that costs. The size used to appear only
                            // in the storage section and, oddly, in the notice after deleting it --
                            // so the one moment you were told how much a catalogue weighed was the
                            // moment you no longer had it. It belongs where the decision is made.
                            val archived = browse.archiveBytes[catalogue.id] ?: 0L
                            Text(
                                if (catalogue.isOnlineOnly) {
                                    stringResource(R.string.catalogue_online_search)
                                } else if (catalogue.indexed) {
                                    val counted = pluralStringResource(
                                        R.plurals.track_count, catalogue.trackCount, catalogue.trackCount
                                    )
                                    if (archived > 0L) {
                                        stringResource(
                                            R.string.catalogue_count_and_size,
                                            counted,
                                            archived / (1024 * 1024),
                                        )
                                    } else {
                                        counted
                                    }
                                } else {
                                    stringResource(R.string.catalogue_not_indexed)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                // The sentence already carries the meaning; error colour reinforces
                                // the reduced functionality without making colour the only signal.
                                color = if (catalogue.requiresIndex) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            // An index keeps only the formats a decoder could play when it was
                            // built, so one built by an older set is missing whatever arrived
                            // since -- and looks empty rather than out of date. The owner lost
                            // 60,572 C64 tunes to exactly this and nothing said why.
                            if (catalogue.isStale(browse.backends)) {
                                Text(
                                    text = stringResource(R.string.catalogue_stale),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(PlayerIcons.Cloud, contentDescription = null) },
                    trailingContent = if (catalogue.isOnlineOnly) {
                        null
                    } else {
                        { DownloadAction(
                            downloading = browse.indexing.containsKey(catalogue.id),
                            description = stringResource(
                                R.string.a11y_index_catalogue, catalogue.displayName,
                            ),
                            onClick = { onIndexCatalogue(catalogue.id) },
                        ) }
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
                        DownloadAction(
                            downloading = browse.indexing.containsKey(DownloadKeys.SONG_LENGTHS),
                            description = stringResource(R.string.a11y_download_song_lengths),
                            onClick = onDownloadSongLengths,
                        )
                    },
                )
            }
            // Beside the song lengths and for the same reason: nothing in it plays, it answers a
            // question the *file* cannot. A plain `.mod` has nowhere to record a year.
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.track_metadata_title)) },
                    supportingContent = {
                        Text(
                            if (browse.trackMetadataCount > 0) {
                                pluralStringResource(
                                    R.plurals.track_metadata_count,
                                    browse.trackMetadataCount,
                                    browse.trackMetadataCount,
                                )
                            } else {
                                stringResource(R.string.track_metadata_none)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = { Icon(PlayerIcons.History, contentDescription = null) },
                    trailingContent = {
                        DownloadAction(
                            downloading = browse.indexing.containsKey(DownloadKeys.TRACK_METADATA),
                            description = stringResource(R.string.a11y_download_track_metadata),
                            onClick = onDownloadTrackMetadata,
                        )
                    },
                )
            }
            // The third of the same kind, and the one that changes what plays rather than what is
            // shown: it is what the dice draws from when Random is scoped to the favourites. The
            // count is the playable one -- listed and indexed -- because that is the number the
            // dice obeys, and because it is zero in both of the states that leave the chip dead.
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.favourites_title)) },
                    supportingContent = {
                        Text(
                            if (browse.favouriteCount > 0) {
                                pluralStringResource(
                                    R.plurals.favourites_count,
                                    browse.favouriteCount,
                                    browse.favouriteCount,
                                )
                            } else {
                                stringResource(R.string.favourites_none)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = { Icon(PlayerIcons.Dice, contentDescription = null) },
                    trailingContent = {
                        DownloadAction(
                            downloading = browse.indexing.containsKey(DownloadKeys.FAVOURITES),
                            description = stringResource(R.string.a11y_download_favourites),
                            onClick = onDownloadFavourites,
                        )
                    },
                )
            }
            // Not a catalogue either, and offered rather than shipped. sc68 needs a small 68000
            // routine for each tune and the app carries exactly one of the ninety-nine -- sc68's
            // own. The rest are other people's code of unestablished status, so the device fetches
            // them from sc68 instead of us handing them out (`docs/LICENSES.md`).
            item {
                if (browse.replayCount == 0) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.replays_title)) },
                        supportingContent = {
                            // **The one download that counts its files**, so while it runs this
                            // line carries the count rather than the invitation. It used to be in
                            // the banner at the top of the screen, which is gone; nothing else on
                            // this row could say how far ninety-eight small files had got.
                            Text(
                                browse.indexing[DownloadKeys.REPLAYS]
                                    ?: stringResource(R.string.replays_none),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = { Icon(PlayerIcons.Download, contentDescription = null) },
                        // Ninety-eight small files, so this one is worth a spinner more than any of
                        // them. The whole row is the button here rather than an arrow at the end.
                        trailingContent = if (browse.indexing.containsKey(DownloadKeys.REPLAYS)) {
                            { CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                        modifier = if (browse.indexing.containsKey(DownloadKeys.REPLAYS)) {
                            Modifier
                        } else {
                            // The same act as an arrow, only the whole row is the button.
                            Modifier.clickable { haptics.press(); onDownloadReplays() }
                        },
                    )
                }
            }

            // The storage section lived here from 2026-09-04 until the settings screen existed,
            // which was always the plan and was said so at the time. Browse is for finding music;
            // what the app is keeping on the phone is not that.
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
    loadingId: String?,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onSendToWeb: (List<TrackRef>) -> Unit,
    onQueryChange: (String) -> Unit,
    onScope: (SearchScope) -> Unit,
    onToggleCatalogue: (String) -> Unit,
    onTogglePlatform: (String) -> Unit,
    onSearch: () -> Unit,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (List<TrackRef>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = browse.query,
            onValueChange = onQueryChange,
            singleLine = true,
            // One line, always. "Online: ASMA (Atari 8-bit), The Mod Archive" wrapped and made the
            // field taller, so adding a third catalogue -- which shortens the text to
            // "Modland, ASMA (Atari 8-bit) +1" -- made the whole screen jump back up. The label is
            // a statement of scope, not a place to read catalogue names in full; the chips
            // underneath show which are lit.
            label = {
                Text(scopeLabel(browse), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(PlayerIcons.Search, stringResource(R.string.domain_search_title))
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SearchScopePanel(
            browse = browse,
            onScope = onScope,
            onToggleCatalogue = onToggleCatalogue,
            onTogglePlatform = onTogglePlatform,
        )

        if (browse.loading) {
            Loading()
        } else {
            Selectable(
                browse = browse,
                scroll = scroll,
                playlistName = playlistName,
                playingId = playingId,
                loadingId = loadingId,
                onPlay = onPlay,
                onAdd = onAdd,
                onAddToOtherPlaylist = onAddToOtherPlaylist,
                onShowNeighbours = onShowNeighbours,
                onShareFile = onShareFile,
                onShareLink = onShareLink,
                onSendToWeb = onSendToWeb,
            )
        }
    }
}

// --- shared -----------------------------------------------------------------------------------

/**
 * Waiting, said near the top rather than in the middle.
 *
 * **The middle of the window is not the middle of what you can see.** The app is edge-to-edge, so
 * the keyboard is drawn *over* the content rather than shrinking it — and a spinner centred in the
 * full height sits underneath it for the whole of a search, which is the one place you most want to
 * know something is happening. The owner reported exactly that on 2026-09-04.
 *
 * `imePadding` alone would have fixed the search case and left the spinner wherever the remaining
 * space happened to centre. Near the top is better for every caller: it is where the results will
 * appear, so the spinner marks the place rather than moving out of it.
 */
@Composable
private fun Loading() {
    Box(
        modifier = Modifier.fillMaxWidth().imePadding().padding(top = 32.dp, bottom = 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
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
    loadingId: String?,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onSendToWeb: (List<TrackRef>) -> Unit,
    onClearHistory: () -> Unit,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
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
            loadingId = loadingId,
            onPlay = onPlay,
            onAdd = onAdd,
            onAddToOtherPlaylist = onAddToOtherPlaylist,
            onShowNeighbours = onShowNeighbours,
            onShareFile = onShareFile,
            onShareLink = onShareLink,
            onSendToWeb = onSendToWeb,
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
    loadingId: String?,
    onPlay: (Int) -> Unit,
    onAdd: (List<TrackRef>) -> Unit,
    onAddToOtherPlaylist: (List<TrackRef>) -> Unit,
    onShowNeighbours: (TrackRef) -> Unit,
    onShareFile: (TrackRef) -> Unit,
    onShareLink: (TrackRef) -> Unit,
    onSendToWeb: (List<TrackRef>) -> Unit,
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
            // An empty list is two different states and only one of them is a disappointment.
            // Nothing searched yet reads as ordinary text; nothing *found* borrows the colour the
            // stale-index warnings use, because it is the same kind of news.
            browse.tracks.isEmpty() -> {
                val searchedAndEmpty = browse.domain != BrowseDomain.SEARCH || browse.searched
                Text(
                    text = stringResource(
                        when {
                            browse.domain != BrowseDomain.SEARCH -> R.string.browse_nothing_found
                            // Said before "nothing found", because it is the reason there is
                            // nothing rather than a result.
                            browse.liveSearchNeededQuery -> R.string.search_live_needs_query
                            browse.searched -> R.string.search_nothing_found
                            else -> R.string.search_not_yet
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (searchedAndEmpty) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> {
                // Said above the list rather than at its end, because the end is 2,000 rows away and
                // the point of the line is to stop the scrolling, not to reward it.
                if (browse.searchMatches > browse.tracks.size) {
                    Text(
                        text = stringResource(
                            R.string.browse_search_capped,
                            browse.tracks.size,
                            browse.searchMatches,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
                val key = browse.levelKey()
                val listState = scroll.stateFor(key)
                RestorePosition(scroll, key, listState, browse.tracks.map { it.id }, browse.loading)
                Box(modifier = Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(browse.tracks, key = { _, track -> track.id }) { index, track ->
                    BrowseTrackRow(
                        track = track,
                        index = index,
                        ticked = track.id in selected,
                        selecting = selecting,
                        playing = track.id == playingId,
                        loading = track.id == loadingId,
                        onPlay = { onPlay(index) },
                        onToggle = {
                            selected = if (track.id in selected) selected - track.id
                            else selected + track.id
                        },
                        onStartSelecting = { selected = selected + track.id },
                        onAddToOtherPlaylist = { onAddToOtherPlaylist(listOf(track)) },
                        onInfo = { showingInfo = track },
                        // Not for a live-search catalogue: it publishes no index, and its result
                        // rows carry no author, so there is nowhere for this to go.
                        onShowNeighbours = track.takeIf { Catalogue.owning(it.id)?.isOnlineOnly == false }
                            ?.let { { onShowNeighbours(it) } },
                        onShareFile = { onShareFile(track) },
                        onShareLink = track.takeIf { Catalogue.owning(it.id) != null }
                            ?.let { { onShareLink(it) } },
                        // Absent where [QueueLink.pack] would refuse it: a local file, an MP3.
                        onSendToWeb = track.takeIf { QueueLink.canSend(it) }?.let { { onSendToWeb(listOf(it)) } },
                    )
                }
            }

                DraggableScrollbar(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )

                // The playing row kept on screen as next and previous move it, the same as the playlist
                // and the Random record. These are the lists you scroll a long way down while
                // something plays -- a folder, a search, an author's other tunes -- which is why the
                // follow-track button was added here too, and why its replacement is here as well.
                // **Arriving from the tune itself** (owner, 2026-09-14). "More from this author" is
                // a jump made *from* something playing, so the folder opens with that tune on
                // screen rather than at the top of eighty rows. Only on a jump: walking into a
                // folder is not a request to be taken anywhere (`ListScrolling`'s own rule).
                LaunchedEffect(browse.openAuthor, browse.tracks.size) {
                    if (!browse.arrivedByJump) return@LaunchedEffect
                    val at = browse.tracks.indexOfFirst { it.id == playingId }
                    if (at >= 0) listState.revealRow(at)
                }

                KeepRowInView(
                    listState = listState,
                    index = browse.tracks.indexOfFirst { it.id == playingId }.takeIf { it >= 0 },
                    key = playingId,
                    active = !selecting,
                )
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
                        contentDescription = stringResource(R.string.action_add_to_playlist),
                    )
                }
                IconButton(
                    onClick = {
                        val toSend = browse.tracks.filter { it.id in selected }
                        selected = emptySet()
                        onSendToWeb(toSend)
                    },
                ) {
                    Icon(
                        imageVector = PlayerIcons.Web,
                        contentDescription = stringResource(R.string.action_send_to_web),
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
    loading: Boolean,
    onPlay: () -> Unit,
    onToggle: () -> Unit,
    onStartSelecting: () -> Unit,
    onAddToOtherPlaylist: () -> Unit,
    onInfo: () -> Unit,
    onShowNeighbours: (() -> Unit)?,
    onShareFile: () -> Unit,
    onShareLink: (() -> Unit)?,
    onSendToWeb: (() -> Unit)?,
) {
    val haptics = rememberHaptics()
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
                if (selecting) {
                    Checkbox(
                        checked = ticked,
                        onCheckedChange = { on -> haptics.toggle(on); onToggle() },
                    )
                }
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
                            text = { Text(stringResource(R.string.action_add_to_playlist)) },
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
                        onSendToWeb?.let { send ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_send_to_web)) },
                                leadingIcon = { Icon(PlayerIcons.Web, contentDescription = null) },
                                onClick = { menuOpen = false; send() },
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
            // **Fetching says so by breathing** (`docs/WISHLIST.md` B32), here as in the playlist:
            // these are the lists a tune is most often started from. Allocated only for the row
            // being fetched, so three hundred others still scroll without a layer each.
            .then(
                if (!loading) {
                    Modifier
                } else {
                    val breath = rememberInfiniteTransition(label = "fetching").animateFloat(
                        initialValue = 1f,
                        targetValue = 0.45f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 550, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "breath",
                    )
                    Modifier.graphicsLayer { alpha = breath.value }
                }
            )
            // `combinedClickable` uses the platform long-press timeout, and a gesture that turns
            // into a scroll is claimed by the list before it ever becomes a long press. Both matter:
            // the owner's complaint about another player is a long press firing at a twentieth of a
            // second mid-scroll, after which back throws him out of the list entirely.
            .combinedClickable(
                // **Choosing a tune is the firm one** (owner, 2026-09-10) — it is the press this
                // whole screen exists for. While selecting, the same tap is a tick in a box, so it
                // feels like the checkbox beside it rather than like starting a tune.
                onClick = {
                    if (selecting) {
                        haptics.toggle(!ticked)
                        onToggle()
                    } else {
                        haptics.press()
                        onPlay()
                    }
                },
                // The loose end `docs/BACKLOG.md` A8 left open: a long press with no answer feels
                // like a press that missed, and this one silently changes what every other tap on
                // the screen will do.
                onLongClick = {
                    if (!selecting) {
                        haptics.gestureEnd()
                        onStartSelecting()
                    }
                },
            ),
    )
}

/**
 * The arrow that starts a download, and the spinner-with-a-word it becomes while one is running.
 *
 * **In the row rather than only in the banner.** Several of these can run at once — they always
 * could, being independent coroutines — but the screen only ever showed the most recent one, so
 * tapping a second arrow looked like it had cancelled the first (owner, 2026-09-09). A row that
 * shows its own state cannot lie about it, and the same spinner is what says "this one is already
 * going" when a second tap would otherwise do nothing visible.
 *
 * **A bare spinner is a shape, not a sentence** (owner, 2026-09-10), so it now carries the word
 * under it. Both states sit in a box of one fixed size, centred, which is the whole point: the
 * banner this replaced changed the layout when it appeared and again when it left, and a caption
 * that made the row grow would be the same mistake one level down. The box is as wide as the
 * longest of the two languages needs — Polish "indeksowanie…" is half again the English — and as
 * tall as the icon already was, so the spinner and its word fit inside what the arrow occupied.
 */
@Composable
private fun DownloadAction(downloading: Boolean, description: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier.width(84.dp).height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (downloading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.browse_indexing_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            // **The one button in this app whose result is a spinner.** Everything it starts is
            // minutes of work over the network, and until the first byte arrives the screen has
            // nothing to show but the spinner it swapped in. The buzz is the receipt.
            IconButton(onClick = { haptics.press(); onClick() }) {
                Icon(PlayerIcons.Download, description)
            }
        }
    }
}
