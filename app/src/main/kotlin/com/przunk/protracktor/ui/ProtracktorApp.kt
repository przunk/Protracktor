// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.PlayerViewModel
import com.przunk.protracktor.player.QueueLink
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
        // A link to the page at its permanent address is a queue or a tune to hear, not a file (A40).
        if (com.przunk.protracktor.player.QueueLink.isPageLink(uri.toString())) viewModel.openLink(uri.toString())
        else viewModel.playExternal(uri)
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
    // Opened from Browse and drawn here, so a run of downloads survives Browse closing under it.
    var choosingDownloads by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    /**
     * The Random view: a full-screen destination like Browse and Settings.
     *
     * **Not a mode of the playlist screen.** It shows a different list, with different actions, and
     * leaving it ends the session — which is a destination, not a state the playlist can be in
     * (`docs/PLAN_RANDOM.md`).
     */
    var showRandom by rememberSaveable { mutableStateOf(false) }
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
    // **While a list plays, Browse opens on it** (A61): the same words, the same folder, the playing
    // row marked. Only with nothing playing from Browse does it start at the top -- a new search from
    // the uncovered playlist starts fresh, which is what the reset was for.
    val openBrowse = {
        if (!viewModel.returnToSession()) viewModel.openDomain(BrowseDomain.ROOT)
        showBrowse = true
    }
    // **Out of Browse, and back to whatever sent us there** (`docs/BACKLOG.md` A41). `browseBack`
    // answers false when there is no level left to climb -- which for "More from this author" is
    // straight away, since a jump is one step rather than a descent. That is where a digression
    // ends: the dice is waiting, so Back returns to its record rather than to the playlist.
    val leaveBrowse = {
        if (!viewModel.browseBack()) {
            when {
                state.diceWaiting -> {
                    viewModel.resumeDice()
                    showRandom = true
                    showBrowse = false
                }
                // A search waiting under the folder: back to its results, still in Browse.
                state.searchWaiting -> viewModel.resumeSearch()
                else -> showBrowse = false
            }
        }
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

    // The scanner is a full-screen destination like Browse and Settings, and back leaves it -- the
    // one rule this shell has (`docs/OPEN_QUESTIONS.md` Q1).
    var scanning by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.scan.collect { scanning = true } }

    // **Where you are, felt when it becomes somewhere else.**
    //
    // Keyed on the destination rather than called from the buttons, because there are a dozen ways
    // to change it — three buttons, two Back handlers, a link arriving, a scan finishing, a random
    // tune starting — and a call at each is a list that goes one short the moment somebody adds a
    // route. The destination is the fact; the taps are only ways of reaching it.
    //
    // The scanner gets the firmer `press()`: opening a camera takes a visible moment to warm up,
    // and it is the one destination that asks for a permission, so an answer to the tap is worth
    // more there than anywhere else. Everything else gets the lightest effect there is.
    HapticOnChange(
        when {
            scanning -> "scanner"
            showSettings -> "settings"
            showBrowse -> "browse"
            showRandom -> "random"
            else -> "player"
        }
    ) { if (scanning) press() else transition() }

    val undoLabel = stringResource(R.string.action_undo)
    val choosePlaylistLabel = stringResource(R.string.a11y_choose_playlist)
    var pendingSwitch by remember { mutableStateOf<Long?>(null) }
    var pendingAddToPlaylist by remember { mutableStateOf<List<com.przunk.protracktor.player.TrackRef>?>(null) }
    // Hoisted so Now Playing can send the list to the playing track without owning the list.
    val playlistState = rememberLazyListState()
    // Its own scroll position. Sharing the playlist's would put the Random view wherever the
    // playlist was left, in a list of a different length.
    val randomState = rememberLazyListState()

    // Newly added tracks land at the end of the list, out of sight. Going to them is half the
    // confirmation that anything happened; the notice is the other half.
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
            Column {
                // The playlist screen's own header, because it is the one that can hold more than fits:
                // the chip and five actions come to more than a phone is wide once Save and Discard
                // appear, and a top bar cannot wrap (`docs/STATUS.md` C48).
                if (!showSettings && !showBrowse && !showRandom) {
                    PlaylistTopBar(
                        state = state,
                        paired = browse.pairedBrowser,
                        onChoosePlaylist = { showPlaylists = true },
                        onBrowse = openBrowse,
                        onDiscard = viewModel::discardChanges,
                        onSave = viewModel::savePlaylist,
                        onSendToBrowser = viewModel::sendQueueToBrowser,
                        onRescan = viewModel::rescan,
                        onSettings = { showSettings = true },
                    )
                } else TopAppBar(
                    navigationIcon = {
                        if (showSettings) {
                            IconButton(onClick = { showSettings = false }) {
                                Icon(PlayerIcons.Back, stringResource(R.string.action_back))
                            }
                        } else if (showBrowse) {
                            IconButton(onClick = leaveBrowse) {
                                Icon(PlayerIcons.Back, stringResource(R.string.action_back))
                            }
                        } else if (showRandom) {
                            // The same arrow Browse has: the two screens sit side by side, so one of
                            // them offering no way out of its bar would leave their headings out of
                            // line as well. **Back leaves, and the dice plays on** (A61): the playlist
                            // it leaves to is covered, and the cover leads back here.
                            IconButton(onClick = { showRandom = false }) {
                                Icon(PlayerIcons.Back, stringResource(R.string.action_back))
                            }
                        }
                    },
                    title = {
                        if (showSettings) {
                            Text(stringResource(R.string.settings_title))
                        } else if (showBrowse) {
                            // The screen names itself; whose folder a digression is in is said by the
                            // header under the bar, in the shape the dice's own heading has.
                            Text(stringResource(R.string.browse_title))
                        } else if (showRandom) {
                            // The screen says "Playing at random" over its own list, so the bar stays
                            // out of its way. The playlist chip in particular would be offering to
                            // switch a playlist that nothing is playing from.
                            Text(stringResource(R.string.domain_random_title))
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
                                // **Out, not back.** During a digression both screens count
                                // themselves showing, so without this condition the button is drawn
                                // twice. There is one: Back returns to whatever sent you here — the
                                // dice — and this leaves for the playlist whatever is waiting.
                                onClick = {
                                    viewModel.returnToPlaylist()
                                    showRandom = false
                                    showBrowse = false
                                },
                                haptic = null,
                                slim = true,
                                modifier = Modifier.padding(end = TOP_BAR_ACTION_EDGE),
                            )
                        }
                        // **Leaving ends the session**, which is what it has always done -- the record
                        // goes, the tunes stay in the history, and the playlist is exactly where it was
                        // left because nothing ever wrote to it.
                        if (showRandom && !showBrowse) {
                            LabelledAction(
                                icon = PlayerIcons.Playlist,
                                label = stringResource(R.string.action_to_playlist),
                                onClick = { viewModel.returnToPlaylist(); showRandom = false },
                                haptic = null,
                                // **The same pill as Filter, one row below it.** See
                                // `TOP_BAR_ACTION_EDGE`.
                                slim = true,
                                modifier = Modifier.padding(end = TOP_BAR_ACTION_EDGE),
                            )
                        }
                    },
                )
                // Under whichever bar is showing: the lists wait on the database on every screen (A60).
                if (state.preparingDatabase) PreparingDatabase()
            }
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
        if (scanning) {
            ScannerScreen(
                contentPadding = insets,
                onScanned = { endpoint ->
                    scanning = false
                    viewModel.pairWith(endpoint)
                },
                // The link keeps a home, and this is the right one: somebody who cannot scan is
                // looking at the scanner (`docs/PLAN_HANDOFF.md` §3).
                onSendLink = {
                    scanning = false
                    viewModel.sendQueueAsLink()
                },
                onCancel = { scanning = false },
            )
        } else if (showSettings) {
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
                playerCount = browse.playerCount,
                replayBytes = browse.replayBytes,
                catalogues = browse.catalogues,
                // From the collected state, not from the parameter: a successful pairing
                // rewrites this address, and a value captured where the screen is built would
                // show the one from before the scan.
                webPlayer = browse.webPlayer,
                onWebPlayerChanged = viewModel::setWebPlayer,
                songLengthCount = browse.songLengthCount,
                trackMetadataCount = browse.trackMetadataCount,
                songDbLengthCount = browse.songDbLengthCount,
                contentPadding = insets,
                selectedTheme = selectedTheme,
                dynamicColour = dynamicColour,
                onThemeSelected = onThemeSelected,
                onDynamicColourChanged = onDynamicColourChanged,
                onToggleAllSubsongs = viewModel::toggleAllSubsongs,
                fallbackLengthSeconds = state.fallbackLengthSeconds,
                onFallbackLengthChanged = viewModel::setFallbackLength,
                cacheAhead = state.cacheAhead,
                onCacheAheadSelected = viewModel::setCacheAhead,
                shareAudioMinutes = state.shareAudioMinutes,
                onShareAudioMinutesSelected = viewModel::setShareAudioMinutes,
                onLanguageSelected = onLanguageSelected,
                onClearCache = viewModel::clearFetchedCache,
                onDeleteIndex = viewModel::deleteCatalogueIndex,
                onDeleteSongMetadata = viewModel::deleteSongMetadata,
                onDeleteReplayRoutines = viewModel::deleteReplayRoutines,
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
                onDownloadSongMetadata = viewModel::downloadSongMetadata,
                onDownloadReplayRoutines = viewModel::downloadReplayRoutines,
                onOpenCatalogue = viewModel::openCatalogue,
                onOpenGroup = viewModel::openGroup,
                // **Open, not just play.** Starting a tune and dropping back onto a playlist
                // behind glass says nothing about what the dice did; this opens the record it is
                // about to fill, and rolls once so there is no second press between here and
                // music.
                onRandom = {
                    viewModel.openRandom()
                    showBrowse = false
                    showRandom = true
                },
                onChooseRandomScope = { choosingRandomScope = true },
                onQueryChange = viewModel::setQuery,
                onScope = viewModel::setSearchScope,
                onTogglePlatform = viewModel::toggleSearchPlatform,
                onToggleCatalogue = viewModel::toggleSearchCatalogue,
                onSearch = viewModel::runSearch,
                // Playing from Browse never adds anything and never touches the playlist: whatever
                // is on screen becomes the queue for as long as you are looking at it.
                onClearHistory = viewModel::clearHistory,
                onHistoryPage = viewModel::showHistoryPage,
                // Marks the row you are hearing. Browse plays through the results queue, so the
                // current track is the queue's, not the playlist's.
                playingId = state.current?.id,
                // What is being fetched, so its row says so by breathing (`docs/WISHLIST.md` B32).
                loadingId = state.current?.id?.takeIf { state.loadingTrack },
                // Whose folder, while the dice waits under it — from the moment the jump lands,
                // not only once something here is playing.
                digressionAuthor = browse.openAuthor
                    ?.takeIf { browse.arrivedByJump && (state.randomMode || state.diceWaiting || state.searchWaiting) },
                onShowNeighbours = viewModel::showNeighboursOf,
                onShareFile = viewModel::shareFile,
                onShareAudio = viewModel::shareAsAudio,
                onShareLink = viewModel::shareLink,
                onSendToWeb = viewModel::sendToWeb,
                onPlay = { index -> viewModel.playFromResults(browse.tracks, index) },
                // Stays in Browse (`docs/STATUS.md` C46). Closing it on an add would take away
                // the list the tracks were picked from and leave the playlist behind the scrim,
                // since a search result is what is playing. The notice says what was added.
                onAdd = { tracks -> viewModel.addToPlaylist(tracks) },
                onAddToOtherPlaylist = { tracks ->
                    pendingAddToPlaylist = tracks
                },
            )
        } else if (showRandom) {
            RandomScreen(
                state = state,
                browse = browse,
                listState = randomState,
                onPlayAt = viewModel::playRandomAt,
                onRemoveAt = viewModel::removeRandomAt,
                onFilter = { choosingRandomScope = true },
                onShowNeighbours = viewModel::showNeighboursOf,
                onShareFile = viewModel::shareFile,
                onShareAudio = viewModel::shareAsAudio,
                onShareLink = viewModel::shareLink,
                onSendToWeb = viewModel::sendToWeb,
                onAddToOtherPlaylist = { track -> pendingAddToPlaylist = listOf(track) },
                onAddSelectedToPlaylist = { tracks -> pendingAddToPlaylist = tracks },
                contentPadding = insets,
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
                onShareAudio = viewModel::shareAsAudio,
                onShareLink = viewModel::shareLink,
                onSendToWeb = viewModel::sendToWeb,
                onAddToOtherPlaylist = { track -> pendingAddToPlaylist = listOf(track) },
                onAddSelectedToPlaylist = { tracks -> pendingAddToPlaylist = tracks },
                onRemoveMany = viewModel::removeTracks,
                onBrowse = openBrowse,
                onPickDownloads = { choosingDownloads = true },
                // Nothing indexed and no folder granted means Browse opens on a list of
                // archives that all say "no index" -- a way in that leads nowhere.
                canBrowse = browse.hasSomethingToBrowse,
                // Both halves: the playlist comes from one read and what is held from
                // another, and the empty screen must not speak before either has landed.
                stateKnown = state.restored && browse.knowsWhatIsHeld,
                onReturnToPlaylist = viewModel::returnToPlaylist,
                contentPadding = insets,
                // Back to the source: the dice's screen, or the list in Browse as it was (A61).
                onReturnToSource = {
                    if (state.randomMode && !state.searchMode) showRandom = true
                    else if (viewModel.returnToSession()) showBrowse = true
                },
            )
        }
    }

    // One level at a time, out of Browse and then out of the screen -- the rule the rest of the
    // navigation follows.
    if (showSettings) {
        BackHandler { showSettings = false }
    }

    if (scanning) {
        BackHandler { scanning = false }
    }

    if (showBrowse) {
        BackHandler(onBack = leaveBrowse)
    }

    // **Back out of Random leaves it playing** (A61, the owner's variant (A)): Back means look
    // elsewhere, and only the Playlist button ends a session. The playlist it leaves to is covered,
    // and the cover leads back here.
    if (showRandom) {
        BackHandler { showRandom = false }

        // **A file arriving from another app takes the screen.** It replaces what is playing, so
        // leaving the Random view up would show a record of a session that has been ended
        // underneath it -- rows that play nothing and a heading that is no longer true.
        LaunchedEffect(state.externalMode) { if (state.externalMode) showRandom = false }

        // **And so does a tune played from a list** (`docs/STATUS.md` C49). "More from this
        // author", or a search result, moves playback to that list: the dice is no longer the
        // source, `next` walks the list, and this screen showed a record of picks that had nothing
        // to do with what was playing. It closes, and the playlist behind says what is playing.
        LaunchedEffect(state.searchMode) { if (state.searchMode) showRandom = false }
    }

    // **Here rather than inside Browse.** The Random view's Filter button opens the same sheet,
    // and a sheet that exists only under one destination cannot be reached from another
    // (`docs/PLAN_RANDOM.md`).
    if (choosingDownloads) {
        DownloadPicker(
            browse = browse,
            onDownload = viewModel::downloadSelected,
            onStop = viewModel::cancelDownloads,
            onDismiss = { choosingDownloads = false },
        )
    }

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
                    ?.takeIf { Catalogue.owning(it.id)?.isOnlineOnly == false }
                    ?.let { track ->
                        {
                            showNowPlaying = false
                            viewModel.showNeighboursOf(track)
                        }
                    },
                onSelectSubsong = viewModel::selectSubsong,
                onToggleAllSubsongs = viewModel::toggleAllSubsongs,
                onShareFile = state.current?.let { track -> { viewModel.shareFile(track) } },
                onShareAudio = state.current?.let { track -> { viewModel.shareAsAudio(track) } },
                onShareLink = state.current
                    ?.takeIf { Catalogue.owning(it.id) != null }
                    ?.let { track -> { viewModel.shareLink(track) } },
                onSendToWeb = state.current
                    ?.takeIf { QueueLink.canSend(it) }
                    ?.let { track -> { viewModel.sendToWeb(listOf(track)) } },
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
 * The playlist screen's top bar: the chip, and the actions, wrapping when they must.
 *
 * **A row that cannot wrap has to take the space from something**, and what it took was the
 * playlist's name: with Save and Discard showing, the chip and five pills come to about 450dp on a
 * screen some 360dp wide, so "Favorites" became "Favo…" (`docs/STATUS.md` C48). A `TopAppBar` is one
 * fixed-height row and cannot answer that, which is why this is not one. The actions flow onto a
 * second line instead, and only while there is something to save.
 */
@Composable
private fun PlaylistTopBar(
    state: PlayerUiState,
    paired: Boolean,
    onChoosePlaylist: () -> Unit,
    onBrowse: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onSendToBrowser: () -> Unit,
    onRescan: () -> Unit,
    onSettings: () -> Unit,
) {
    val choosePlaylistLabel = stringResource(R.string.a11y_choose_playlist)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            verticalArrangement = Arrangement.spacedBy(TOP_BAR_SEAM),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = TOP_BAR_EDGE, vertical = TOP_BAR_SEAM),
        ) {
            // The name across the whole width, so it is never the thing that gives way, and the
            // count beside it rather than under it, which is what lets this row be short.
            Surface(
                onClick = onChoosePlaylist,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    // A floor, not a fixed height: at a large system font two lines need more than
                    // any number written here, and a fixed one would cut the second.
                    .defaultMinSize(minHeight = PLAYLIST_CHIP_HEIGHT)
                    .semantics { contentDescription = choosePlaylistLabel },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    // **The text takes every pixel the chevron does not**, so the chevron is at the
                    // right edge whatever the name is. Giving the name a weight it need not fill
                    // left the slack unclaimed and the chevron floating in the middle of the bar.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.activePlaylistName ?: stringResource(R.string.playlist_default_name),
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
                                maxLines = 1,
                            )
                        }
                    }
                    Icon(imageVector = PlayerIcons.DropDown, contentDescription = null)
                }
            }

            // **Sides, not a queue.** Browse is where you go for more music and it stays at the left
            // edge; Settings is the app's own and stays at the right. Save and Discard appear beside
            // them when there is something to save, growing into the gap in the middle so nothing
            // that was already on the bar moves (`docs/STATUS.md` C48).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LabelledAction(
                    icon = PlayerIcons.Cloud,
                    label = stringResource(R.string.action_browse),
                    onClick = onBrowse,
                    // Arriving at Browse buzzes; pressing the way in as well would be two for one.
                    haptic = null,
                    slim = true,
                )
                Spacer(Modifier.weight(1f))
                // Only while there is something to save. A permanently lit Save teaches nothing
                // about whether the list on screen is the list on disk.
                if (state.dirty) {
                    LabelledAction(
                        icon = PlayerIcons.Discard,
                        label = stringResource(R.string.action_discard),
                        onClick = onDiscard,
                        slim = true,
                    )
                    LabelledAction(
                        icon = PlayerIcons.Save,
                        label = stringResource(R.string.action_save),
                        onClick = onSave,
                        slim = true,
                    )
                }
                // It acts on the whole playlist, so it sits with the app's own actions rather than
                // in a row menu. Only while there is a list to send.
                if (state.queue.tracks.isNotEmpty()) {
                    LabelledAction(
                        // **The icon says which of the two things a press will do.** With nobody
                        // paired it opens the camera, so it is a code; paired, it sends, so it is a
                        // link.
                        icon = if (paired) PlayerIcons.Link else PlayerIcons.QrCode,
                        label = stringResource(R.string.action_send_to_browser),
                        onClick = onSendToBrowser,
                        onLongClick = onRescan,
                        longClickLabel = stringResource(R.string.action_pair_again),
                        slim = true,
                    )
                }
                LabelledAction(
                    icon = PlayerIcons.Settings,
                    label = stringResource(R.string.settings_title),
                    onClick = onSettings,
                    haptic = null,
                    slim = true,
                )
            }
        }
    }
}

/**
 * The name's own row: the name, and under it what the playlist holds.
 *
 * Two lines, which the full width affords — the row the actions have to themselves is what stops
 * them taking the name's space (`docs/STATUS.md` C48).
 */
private val PLAYLIST_CHIP_HEIGHT = 56.dp




/** The gap between any two controls on the top bar. */
private val TOP_BAR_SEAM = 6.dp

/** The inset between the last control and the edge of the screen. Not a seam; a margin. */
private val TOP_BAR_EDGE = 8.dp

/**
 * Where an action in the app bar puts its **visible** right edge, so it lines up with the action in
 * the header underneath.
 *
 * Filter, in the header, and the playlist action in the bar above it have to match. Three separate
 * things make them differ:
 *
 * - **Height.** The bar's action was a full pill, 72dp, which a 64dp `TopAppBar` then clipped;
 *   Filter is slim at 46dp. Both are slim now.
 * - **Width.** A full pill's floor is 48dp and a slim one's is `SLIM_MIN_WIDTH` = 56dp, and neither
 *   label needs more than that — so as full pills they were 51dp and 56dp, and as slim ones they
 *   are both exactly 56dp.
 * - **The right edge**, which is what this constant is for.
 *
 * Three paddings stack up before a pill's background starts: this one, `LabelledAction`'s own
 * [ACTION_SEAM], and `TopAppBar`'s internal 4dp. The heading below adds [SESSION_HEADER_EDGE] and
 * the same seam — so the seam cancels, and what is left is the heading's edge less what the bar
 * already adds. **Derived rather than written down**, so that moving the heading moves this too.
 *
 * Measured rather than assumed: before this, the two pills stood 32px and 40px from the edge of an
 * 864px screen at 2.1x, which is 15dp and 19dp — and 19dp is what `SESSION_HEADER_EDGE +
 * ACTION_SEAM` comes to.
 *
 * `TOP_BAR_EDGE` stays 8dp for the playlist bar, whose actions that bar lays out itself rather than
 * `TopAppBar`, so they never had the extra 4dp to account for.
 */
private val TOP_APP_BAR_ACTION_PADDING = 4.dp
private val TOP_BAR_ACTION_EDGE = SESSION_HEADER_EDGE - TOP_APP_BAR_ACTION_PADDING

/**
 * What the title/actions boundary swallows, added back.
 *
 * **Measured on a device, not computed.** Every other gap on this bar is arithmetic — two known
 * paddings either side of a known spacer — but this one crosses between two slots the top bar lays
 * out itself, and how much they leave between them is not ours to know. A pixel short is visible
 * on a screen and in no formula here, which is the only instrument there is for this.
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
