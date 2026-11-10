// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.data.SavedPlaylist
import com.przunk.protracktor.player.TrackRef
import com.przunk.protracktor.ui.LabelledAction
import com.przunk.protracktor.ui.PlayerIcons

/**
 * *Add music* (B62): tunes from the saved playlists, History, or a search over both and the
 * catalogues on the phone, each with **Add**. A tune added is prepared by [AlarmMusicQueue], one at a
 * time; its row says where it has got to, in words. [picks] is the queue as it stands.
 */
@Composable
internal fun AlarmMusicPicker(
    sources: AlarmMusicSources,
    picks: List<AlarmPick>,
    onAdd: (TrackRef) -> Unit,
    onBack: () -> Unit,
    /** A playlist already open, for a preview of its tunes. */
    initialPlaylist: SavedPlaylist? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var playlist by remember { mutableStateOf(initialPlaylist) }
    // One step back at a time: out of a playlist, then out of the picker.
    BackHandler { if (playlist != null && query.isBlank()) playlist = null else onBack() }

    // Asked again a moment after typing stops, so one search answers a word, not each letter.
    val found by produceState<List<AlarmCandidate>?>(null, query) {
        value = null
        if (query.isBlank()) return@produceState
        kotlinx.coroutines.delay(SEARCH_PAUSE_MS)
        value = runCatching { sources.search(query) }.getOrDefault(emptyList())
    }
    val playlists by produceState<List<SavedPlaylist>?>(null) { value = runCatching { sources.playlists() }.getOrDefault(emptyList()) }
    val history by produceState<List<AlarmCandidate>?>(null) { value = runCatching { sources.history() }.getOrDefault(emptyList()) }
    val opened by produceState<List<AlarmCandidate>?>(null, playlist) {
        value = null
        playlist?.let { value = runCatching { sources.tracksIn(it.id) }.getOrDefault(emptyList()) }
    }

    Surface(Modifier.fillMaxSize().testTag("alarm-music-picker")) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PickProgress(picks, onBack) }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
                AlarmHeader(stringResource(R.string.alarm_add_music), onBack, help = stringResource(R.string.alarm_picker_help)) {}
                OutlinedTextField(query, { query = it.take(120) },
                    placeholder = { Text(stringResource(R.string.alarm_picker_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(PlayerIcons.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) {
                        Icon(PlayerIcons.Close, stringResource(R.string.playlist_search_clear))
                    } },
                    singleLine = true, shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("alarm-picker-search"))
                if (query.isBlank()) TabRow(tab, Modifier.padding(top = 8.dp)) {
                    listOf(R.string.alarm_picker_playlists to PlayerIcons.PlaylistAdd, R.string.alarm_picker_history to PlayerIcons.History)
                        .forEachIndexed { i, (label, icon) ->
                            Tab(tab == i, { tab = i; playlist = null }, text = { Text(stringResource(label)) }, icon = { Icon(icon, null) })
                        }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        query.isNotBlank() -> candidates(found, R.string.alarm_picker_no_results, picks, onAdd)
                        tab == 1 -> candidates(history, R.string.alarm_picker_no_history, picks, onAdd)
                        playlist != null -> {
                            item(key = "all") {
                                LabelledAction(PlayerIcons.Back, stringResource(R.string.alarm_picker_all_playlists), { playlist = null }, slim = true)
                            }
                            item(key = "name") {
                                Text(playlist!!.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            candidates(opened, R.string.alarm_picker_no_results, picks, onAdd)
                        }
                        else -> {
                            val shown = playlists
                            when {
                                shown == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                                shown.isEmpty() -> item { Empty(R.string.alarm_picker_no_playlists) }
                                else -> items(shown, key = { "playlist:${it.id}" }) { list ->
                                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth().clickable { playlist = list }) {
                                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Icon(PlayerIcons.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary)
                                            Column(Modifier.weight(1f)) {
                                                Text(list.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(pluralStringResource(R.plurals.track_count, list.trackCount, list.trackCount),
                                                    style = MaterialTheme.typography.bodySmall)
                                            }
                                            Icon(PlayerIcons.ChevronRight, null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.candidates(
    list: List<AlarmCandidate>?, empty: Int, picks: List<AlarmPick>, onAdd: (TrackRef) -> Unit,
) {
    when {
        list == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        list.isEmpty() -> item { Empty(empty) }
        else -> items(list, key = { it.ref.id }) { candidate ->
            CandidateRow(candidate, picks.firstOrNull { it.ref.id == candidate.ref.id }?.state) { onAdd(candidate.ref) }
        }
    }
}

@Composable private fun Empty(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
}

/** A tune with its Add, or with where its preparation has got to. */
@Composable private fun CandidateRow(candidate: AlarmCandidate, state: AlarmPickState?, onAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("alarm-candidate")) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(PlayerIcons.AudioFile, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(candidate.ref.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (candidate.detail.isNotBlank()) Text(candidate.detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state is AlarmPickState.Failed) Text(stringResource(state.reason), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Box(Modifier.widthIn(min = 88.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    null -> LabelledAction(PlayerIcons.Add, stringResource(R.string.alarm_pick_add), onAdd, slim = true)
                    is AlarmPickState.Failed -> LabelledAction(PlayerIcons.Refresh, stringResource(R.string.alarm_pick_retry), onAdd, slim = true)
                    AlarmPickState.Waiting -> PickStatus(stringResource(R.string.alarm_pick_waiting)) { Icon(PlayerIcons.Clock, null) }
                    AlarmPickState.Preparing -> PickStatus(stringResource(R.string.alarm_pick_preparing)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    is AlarmPickState.Added -> PickStatus(stringResource(R.string.alarm_pick_added)) {
                        Icon(PlayerIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable private fun PickStatus(text: String, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 6.dp)) {
        icon()
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** How the picks are going, and the way back, once there are any. */
@Composable private fun PickProgress(picks: List<AlarmPick>, onDone: () -> Unit) {
    if (picks.isEmpty()) return
    val added = picks.count { it.state is AlarmPickState.Added }
    val pending = picks.count { it.state == AlarmPickState.Waiting || it.state == AlarmPickState.Preparing }
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.alarm_picker_progress, added, pending), Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium)
            LabelledAction(PlayerIcons.Check, stringResource(R.string.alarm_picker_done), onDone, slim = true)
        }
    }
}

/** Long enough to be between words, short enough not to read as waiting. */
private const val SEARCH_PAUSE_MS = 300L
