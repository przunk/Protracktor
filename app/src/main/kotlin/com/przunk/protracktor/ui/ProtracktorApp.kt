// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.przunk.protracktor.AppLanguage
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.R
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.PlaybackController
import com.przunk.protracktor.player.PlayerViewModel
import kotlinx.coroutines.launch

/**
 * The three-layer shell decided in `docs/OPEN_QUESTIONS.md` Q1.
 *
 * The playlist is the only destination. Now Playing expands upward from the dock over it, Browse
 * opens as a modal from the top bar, and back always descends one layer -- the only rule the user
 * has to learn. No side-swiping between pages (R7), and the dock never leaves (R3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtracktorApp(
    viewModel: PlayerViewModel = viewModel(),
    selectedLanguage: AppLanguage = AppLanguage.SYSTEM,
    onLanguageSelected: (AppLanguage) -> Unit = {},
    selectedTheme: AppTheme = AppTheme.SYSTEM,
    onThemeSelected: (AppTheme) -> Unit = {},
    dynamicColour: Boolean = true,
    onDynamicColourChanged: (Boolean) -> Unit = {},
    /**
     * A file another app asked us to open, or null.
     *
     * Passed in rather than read here, because the intent belongs to the activity and arrives twice
     * over: once in `onCreate` and again in `onNewIntent` when the app is already running.
     * [onExternalOpened] is what stops the same tune restarting on every recomposition.
     */
    externalOpen: android.net.Uri? = null,
    onExternalOpened: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val browse by viewModel.browse.collectAsStateWithLifecycle()
    var showNowPlaying by remember { mutableStateOf(false) }

    // Keyed on the URI so the same file opened twice in a row still plays, and a recomposition
    // caused by anything else does not restart it.
    LaunchedEffect(externalOpen) {
        val uri = externalOpen ?: return@LaunchedEffect
        viewModel.playExternal(uri)
        onExternalOpened()
    }

    // **Saved, not merely remembered.** Which full-screen destination is open is navigation state,
    // and it has to survive the activity being rebuilt. Changing the language or the theme calls
    // `recreate()` -- deliberately, so the window is built with the new one -- and with a plain
    // `remember` that dropped the user back on the playlist from inside Settings, which is where
    // they had just been changing the setting. Rotation lost the same thing, silently, and had
    // done all along.
    var showBrowse by rememberSaveable { mutableStateOf(false) }
    // Not saveable: a sheet asking a question should not survive a rotation as an unanswered one.
    var choosingRandomScope by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
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

    // A playlist file, not music. Any type: providers disagree about what an .m3u8 is, and a filter
    // that greys out the file the user is pointing at is worse than no filter.
    val playlistPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importPlaylist) }

    // Opening Browse from a button starts at the top; opening it from a jump does not, because the
    // jump has already aimed it. Tying the reset to the button rather than to the sheet being shown
    // is what keeps those two apart.
    val openBrowse = {
        viewModel.openDomain(BrowseDomain.ROOT)
        showBrowse = true
    }
    LaunchedEffect(Unit) { viewModel.showBrowse.collect { showBrowse = true } }

    // The chooser needs an activity; preparing what is shared needed a fetch. The controller does
    // the second and hands the first over here.
    // LocalContext inside an activity's content is that activity, so the chooser starts as a
    // normal child. `LocalActivity` would say this more plainly and arrived in activity-compose
    // 1.10; this project is on 1.9.3.
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_chooser)
    LaunchedEffect(Unit) {
        viewModel.share.collect { intent ->
            context.startActivity(Intent.createChooser(intent, shareTitle))
        }
    }

    val undoLabel = stringResource(R.string.action_undo)
    val choosePlaylistLabel = stringResource(R.string.a11y_choose_playlist)
    var pendingSwitch by remember { mutableStateOf<Long?>(null) }
    var pendingAddToPlaylist by remember { mutableStateOf<List<com.przunk.protracktor.player.TrackRef>?>(null) }
    // Hoisted so Now Playing can send the list to the playing track without owning the list.
    val playlistState = rememberLazyListState()

    // Newly added tracks land at the end of the list, out of sight. Going to them is the
    // confirmation that the removed snackbar used to be.
    LaunchedEffect(Unit) {
        viewModel.reveal.collect { index -> playlistState.animateScrollToItem(index) }
    }
    val scope = rememberCoroutineScope()
    state.message?.let { message ->
        LaunchedEffect(message.id) {
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = if (message.actionLabel == PlaybackController.UNDO) undoLabel else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemoval()
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(PlayerIcons.Back, stringResource(R.string.action_back))
                        }
                    } else if (showBrowse) {
                        IconButton(onClick = { if (!viewModel.browseBack()) showBrowse = false }) {
                            Icon(PlayerIcons.Back, stringResource(R.string.action_back))
                        }
                    }
                },
                title = {
                    if (showSettings) {
                        Text(stringResource(R.string.settings_title))
                    } else if (showBrowse) {
                        Text(stringResource(R.string.browse_title))
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // A chevron and a filled shape, because the owner could not tell the
                            // name was a button. It shares the left side with Browse: the two ways
                            // to choose what plays belong together, with enough air to remain two
                            // controls rather than one compound control.
                            Surface(
                                onClick = { showPlaylists = true },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    // Capped, so a long name ellipsises instead of shoving the
                                    // buttons across the bar. Without this the chip grew with the
                                    // name and everything to its right moved with it.
                                    .widthIn(max = PLAYLIST_PILL_MAX_WIDTH)
                                    // The same height as the buttons beside it, always. Its second
                                    // line only appears when the playlist has something in it, so
                                    // an empty one drew a pill half the height of its neighbours
                                    // and the bar changed shape as tracks came and went.
                                    .height(PLAYLIST_PILL_HEIGHT)
                                    .semantics { contentDescription = choosePlaylistLabel },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(start = 14.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = state.activePlaylistName
                                                ?: stringResource(R.string.playlist_default_name),
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (state.queue.tracks.isNotEmpty()) {
                                            Text(
                                                text = pluralStringResource(
                                                    R.plurals.track_count,
                                                    state.queue.tracks.size,
                                                    state.queue.tracks.size,
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = PlayerIcons.DropDown,
                                        contentDescription = null,
                                        modifier = Modifier.padding(start = 2.dp),
                                    )
                                }
                            }
                            // `LabelledAction` carries 3dp of its own on each side, so two of
                            // them sit 6dp apart. This makes the chip-to-button seam the same
                            // rather than the 12dp it was, which is why the gaps around Browse
                            // looked unlike the gaps between Save, Discard and Settings.
                            Spacer(Modifier.width(TOP_BAR_SEAM - LABELLED_ACTION_INSET))
                            LabelledAction(
                                icon = PlayerIcons.Cloud,
                                label = stringResource(R.string.action_browse),
                                onClick = openBrowse,
                                // The seam across the title/actions boundary, which the two slots
                                // do not otherwise share -- so it is the one gap on this bar that
                                // cannot be derived and had to be looked at.
                                modifier = Modifier.padding(
                                    end = TOP_BAR_SEAM - LABELLED_ACTION_INSET + TOP_BAR_SLOT_SEAM,
                                ),
                            )
                        }
                    }
                },
                actions = {
                    // The way out, as opposed to the way back. Back is a stack -- leave the
                    // selection, then up a level, then out -- and from four levels deep that is
                    // four presses even when it is behaving correctly. This is one, from anywhere.
                    // Labelled as well as drawn, because an icon alone does not say where it goes.
                    if (showBrowse) {
                        LabelledAction(
                            icon = PlayerIcons.Playlist,
                            label = stringResource(R.string.action_to_playlist),
                            onClick = { showBrowse = false },
                            modifier = Modifier.padding(end = TOP_BAR_EDGE),
                        )
                    }
                    if (!showBrowse && !showSettings) {
                        // Only while there is something to save. A permanently lit Save button
                        // teaches nothing about whether the list on screen is the list on disk.
                        if (state.dirty) {
                            LabelledAction(
                                icon = PlayerIcons.Discard,
                                label = stringResource(R.string.action_discard),
                                onClick = viewModel::discardChanges,
                            )
                            LabelledAction(
                                icon = PlayerIcons.Save,
                                label = stringResource(R.string.action_save),
                                onClick = viewModel::savePlaylist,
                            )
                        }
                        LabelledAction(
                            icon = PlayerIcons.Settings,
                            label = stringResource(R.string.settings_title),
                            onClick = { showSettings = true },
                            modifier = Modifier.padding(end = TOP_BAR_EDGE),
                        )
                    }
                },
            )
        },
        bottomBar = {
            PlayerDock(
                state = state,
                onSeek = viewModel::seekTo,
                onKeep = viewModel::keepTransient,
                onExpand = { showNowPlaying = true },
                onBrowse = openBrowse,
                onPlayPause = viewModel::togglePlayPause,
                onPrevious = viewModel::previous,
            onPreviousFile = viewModel::previousFile,
                onNext = viewModel::next,
            onNextFile = viewModel::nextFile,
                onShuffle = viewModel::toggleShuffle,
                onRepeat = viewModel::cycleRepeat,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> SwipeableSnackbar(data) } },
    ) { insets ->
        if (showSettings) {
            // The storage figures are read by `refreshCatalogues`, which until now only ran when
            // somebody opened the online catalogues. Settings can be reached without ever going
            // near Browse, and a storage section reporting zero because nobody asked would be
            // worse than one that is missing.
            LaunchedEffect(Unit) { viewModel.refreshCatalogues() }
            SettingsScreen(
                playAllSubsongs = state.playAllSubsongs,
                selectedLanguage = selectedLanguage,
                cacheBytes = browse.storageBytes.first,
                archiveBytes = browse.archiveBytes,
                databaseBytes = browse.databaseBytes,
                replayCount = browse.replayCount,
                replayBytes = browse.replayBytes,
                catalogues = browse.catalogues,
                songLengthCount = browse.songLengthCount,
                trackMetadataCount = browse.trackMetadataCount,
                favouriteCount = browse.favouritesListed,
                contentPadding = insets,
                selectedTheme = selectedTheme,
                dynamicColour = dynamicColour,
                onThemeSelected = onThemeSelected,
                onDynamicColourChanged = onDynamicColourChanged,
                onToggleAllSubsongs = viewModel::toggleAllSubsongs,
                onLanguageSelected = onLanguageSelected,
                onClearCache = viewModel::clearFetchedCache,
                onDeleteIndex = viewModel::deleteCatalogueIndex,
                onClearSongLengths = viewModel::clearSongLengths,
                onClearTrackMetadata = viewModel::clearTrackMetadata,
                onClearFavourites = viewModel::clearFavourites,
                onDeleteReplays = viewModel::deleteReplays,
            )
        } else if (showBrowse) {
            BrowseScreen(
                browse = browse,
                playlistName = state.activePlaylistName,
                contentPadding = insets,
                onOpenDomain = viewModel::openDomain,
                onPickFolder = { folderPicker.launch(null) },
                onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                onOpenFolder = viewModel::openFolder,
                onForgetFolder = viewModel::forgetFolder,
                onScanFolder = viewModel::scanFolder,
                onIndexCatalogue = viewModel::indexCatalogue,
                onDownloadSongLengths = viewModel::downloadSongLengths,
                onDownloadTrackMetadata = viewModel::downloadTrackMetadata,
                onDownloadFavourites = viewModel::downloadFavourites,
                onDownloadReplays = viewModel::downloadReplays,
                onOpenCatalogue = viewModel::openCatalogue,
                onOpenGroup = viewModel::openGroup,
                onRandom = { viewModel.playRandom(); showBrowse = false },
                onChooseRandomScope = { choosingRandomScope = true },
                onQueryChange = viewModel::setQuery,
                onScope = viewModel::setSearchScope,
                onTogglePlatform = viewModel::toggleSearchPlatform,
                onToggleCatalogue = viewModel::toggleSearchCatalogue,
                onSearch = viewModel::runSearch,
                // Playing from Browse never adds anything and never touches the playlist: whatever
                // is on screen becomes the queue for as long as you are looking at it.
                onClearHistory = viewModel::clearHistory,
                // Marks the row you are hearing. Browse plays through the results queue, so the
                // current track is the queue's, not the playlist's.
                playingId = state.current?.id,
                onShowNeighbours = viewModel::showNeighboursOf,
                onShareFile = viewModel::shareFile,
                onShareLink = viewModel::shareLink,
                onPlay = { index -> viewModel.playFromResults(browse.tracks, index) },
                onAdd = { tracks ->
                    viewModel.addToPlaylist(tracks)
                    showBrowse = false
                },
                onAddToOtherPlaylist = { tracks ->
                    pendingAddToPlaylist = tracks
                },
            )
        } else {
            PlaylistScreen(
                state = state,
                listState = playlistState,
                onPlayAt = viewModel::playAt,
                onRemoveAt = viewModel::removeTrack,
                onMove = viewModel::moveTrack,
                onShowNeighbours = viewModel::showNeighboursOf,
                onShareFile = viewModel::shareFile,
                onShareLink = viewModel::shareLink,
                onAddToOtherPlaylist = { track -> pendingAddToPlaylist = listOf(track) },
                onAddSelectedToPlaylist = { tracks -> pendingAddToPlaylist = tracks },
                onRemoveMany = viewModel::removeTracks,
                onBrowse = openBrowse,
                onReturnToPlaylist = viewModel::returnToPlaylist,
                contentPadding = insets,
            )
        }
    }

    // One level at a time, out of Browse and then out of the screen -- the rule the rest of the
    // navigation follows.
    if (showSettings) {
        BackHandler { showSettings = false }
    }

    if (showBrowse) {
        BackHandler { if (!viewModel.browseBack()) showBrowse = false }

        if (choosingRandomScope) {
            RandomScopeSheet(
                browse = browse,
                onDownloadFavourites = viewModel::downloadFavourites,
                onPick = {
                    viewModel.setRandomScope(it)
                    choosingRandomScope = false
                },
                onDismiss = { choosingRandomScope = false },
            )
        }
    }

    if (showNowPlaying) {
        // Back closes the overlay before it closes the app. Without this the user's escape from a
        // full-screen sheet is to hunt for a drag handle.
        BackHandler { showNowPlaying = false }
        ModalBottomSheet(
            onDismissRequest = { showNowPlaying = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            NowPlaying(
                state = state,
                onSeek = viewModel::seekTo,
                // Only when what is playing is actually in the list. During Random or a search there
                // is nothing to show, and a button that lands nowhere is worse than no button.
                onShowInPlaylist = state.queue.currentIndex
                    ?.takeIf { !state.awayFromPlaylist }
                    ?.let { index ->
                        {
                            showNowPlaying = false
                            scope.launch { playlistState.animateScrollToItem(index) }
                        }
                    },
                // The Random case is why this is here rather than only in the row menu: during
                // Random the playlist is behind glass and the row menu cannot be reached at all,
                // and a tune played at random is exactly the one you want to ask this about.
                onShowNeighbours = state.current
                    ?.takeIf { Catalogue.owning(it.id) != null }
                    ?.let { track ->
                        {
                            showNowPlaying = false
                            viewModel.showNeighboursOf(track)
                        }
                    },
                onSelectSubsong = viewModel::selectSubsong,
                onToggleAllSubsongs = viewModel::toggleAllSubsongs,
                onShareFile = state.current?.let { track -> { viewModel.shareFile(track) } },
                onShareLink = state.current
                    ?.takeIf { Catalogue.owning(it.id) != null }
                    ?.let { track -> { viewModel.shareLink(track) } },
                onAddToOtherPlaylist = state.current?.let { track ->
                    {
                        showNowPlaying = false
                        pendingAddToPlaylist = listOf(track)
                    }
                },
            )
        }
    }

    if (showPlaylists) {
        BackHandler { showPlaylists = false }
        PlaylistSheet(
            state = state,
            viewModel = viewModel,
            // Switching abandons unsaved edits, so ask before losing them rather than after.
            onSelect = { id ->
                if (state.dirty) pendingSwitch = id else viewModel.switchToPlaylist(id)
            },
            onImport = { playlistPicker.launch(arrayOf("*/*")) },
            onDismiss = { showPlaylists = false },
        )
    }

    pendingSwitch?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text(stringResource(R.string.playlist_unsaved_title)) },
            text = { Text(stringResource(R.string.playlist_unsaved_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.savePlaylist()
                    viewModel.switchToPlaylist(target)
                    pendingSwitch = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.switchToPlaylist(target)
                    pendingSwitch = null
                }) { Text(stringResource(R.string.action_discard)) }
            },
        )
    }

    pendingAddToPlaylist?.let { tracks ->
        AddToPlaylistDialog(
            tracks = tracks,
            playlists = state.playlists,
            activePlaylistId = state.activePlaylistId,
            onSelectPlaylist = { playlist ->
                viewModel.addToPlaylist(playlist.id, tracks)
                pendingAddToPlaylist = null
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylistAndAdd(name, tracks)
                pendingAddToPlaylist = null
            },
            onDismiss = { pendingAddToPlaylist = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSheet(
    state: com.przunk.protracktor.player.PlayerUiState,
    viewModel: PlayerViewModel,
    onSelect: (Long) -> Unit,
    onImport: () -> Unit,
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
                onSelect(id)
                onDismiss()
            },
            onCreate = viewModel::createPlaylist,
            onRename = viewModel::renamePlaylist,
            onDelete = viewModel::deletePlaylist,
            onImport = { onImport(); onDismiss() },
            onExport = { id -> viewModel.exportPlaylist(id); onDismiss() },
        )
    }
}

/**
 * The playlist chip matches the buttons beside it rather than its own contents.
 *
 * `LabelledAction` is 52dp at its smallest, and the chip's second line — the track count — is
 * absent on an empty playlist. Without a fixed height the top bar visibly changed shape as a
 * playlist filled and emptied, which the owner spotted.
 */
private val PLAYLIST_PILL_HEIGHT = 52.dp

/**
 * How wide the playlist chip may get before its name starts ellipsising.
 *
 * A cap rather than a fixed width: a short name should not be padded out to a slab. The reason it
 * needs one at all is that a long name pushed every button on the bar to the right, so where Browse
 * sat depended on what the playlist was called.
 */
private val PLAYLIST_PILL_MAX_WIDTH = 220.dp

/** The gap between any two controls on the top bar. */
private val TOP_BAR_SEAM = 6.dp

/** The inset between the last control and the edge of the screen. Not a seam; a margin. */
private val TOP_BAR_EDGE = 8.dp

/**
 * What the title/actions boundary swallows, added back.
 *
 * **Measured on a device, not computed.** Every other gap on this bar is arithmetic — two known
 * paddings either side of a known spacer — but this one crosses between two slots the top bar lays
 * out itself, and how much they leave between them is not ours to know. The owner looked at it and
 * said it was a pixel short, which is the only instrument there is for this.
 */
private val TOP_BAR_SLOT_SEAM = 1.dp

/**
 * What `LabelledAction` already puts on each of its own sides.
 *
 * Subtracted wherever a seam is built by hand, so the bar's gaps are equal whether the two things
 * either side are both buttons, or a button and the playlist chip. Getting this wrong is what made
 * the space around Browse look unlike the space between Discard, Save and Settings.
 */
private val LABELLED_ACTION_INSET = 3.dp
