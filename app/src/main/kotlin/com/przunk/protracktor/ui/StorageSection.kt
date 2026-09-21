// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.net.CacheBudget
import com.przunk.protracktor.net.Catalogue

/**
 * What the app is holding on this phone, and how to make it stop.
 *
 * **Everything listed here is a copy.** That is the rule the section is built on, not a property it
 * happens to have: a downloaded index, a fetched tune, an archive, a table of song lengths — each
 * came from somewhere that still has it, and each is re-fetched by a button already on this screen.
 * The user's own things are deliberately absent. Playlists, granted folders and history are not
 * storage to be reclaimed, and a screen that mixes the two teaches people to be afraid of it.
 *
 * **It lives in Settings**, drawn by `SettingsScreen`, and is one composable so that it can be
 * moved in one piece if the navigation changes again (`docs/OPEN_QUESTIONS.md` Q1,
 * `docs/BACKLOG.md` A13).
 */
@Composable
fun StorageSection(
    cacheBytes: Long,
    archiveBytes: Map<String, Long>,
    databaseBytes: Long,
    replayCount: Int,
    playerCount: Int,
    replayBytes: Long,
    catalogues: List<CatalogueSummary>,
    songLengthCount: Int,
    trackMetadataCount: Int,
    songDbLengthCount: Int,
    onClearCache: () -> Unit,
    onDeleteIndex: (String) -> Unit,
    onDeleteSongMetadata: () -> Unit,
    onDeleteReplayRoutines: () -> Unit,
) {
    var confirming by remember { mutableStateOf<Confirmation?>(null) }

    val stored = catalogues.filter {
        !it.isOnlineOnly && (it.trackCount > 0 || (archiveBytes[it.id] ?: 0L) > 0L)
    }
    val songMetadata = songLengthCount + trackMetadataCount + songDbLengthCount
    val replayRoutines = replayCount + playerCount
    if (cacheBytes <= 0 && stored.isEmpty() && databaseBytes <= 0 && songMetadata <= 0 && replayRoutines <= 0) return

    HorizontalDivider()
    Text(
        text = stringResource(R.string.storage_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )

    // The fetched tunes. No confirmation: it refills itself as music plays, so the cost of a
    // mistaken tap is a slower next play and nothing else. Everything below takes a real download
    // to get back and asks first.
    if (cacheBytes > 0) {
        StorageRow(
            title = stringResource(R.string.storage_cache),
            detail = stringResource(
                R.string.storage_cache_detail,
                cacheBytes / (1024 * 1024),
                CacheBudget.DEFAULT_CEILING_BYTES / (1024 * 1024),
            ),
            onDelete = onClearCache,
        )
    }

    // One row per catalogue, because for an archive catalogue the downloaded zip **is** the index
    // -- ASMA publishes a single file that is stored whole and parsed in place. Listing them
    // separately would offer two deletes for one thing, and taking either would leave the other
    // describing a catalogue that no longer works.
    catalogues.filter { !it.isOnlineOnly }.forEach { catalogue ->
        val archived = archiveBytes[catalogue.id] ?: 0L
        if (catalogue.trackCount <= 0 && archived <= 0L) return@forEach
        val counted = pluralStringResource(
            R.plurals.track_count, catalogue.trackCount, catalogue.trackCount
        )
        StorageRow(
            title = stringResource(R.string.storage_index, catalogue.displayName),
            detail = if (archived > 0L) {
                stringResource(R.string.storage_index_and_archive, counted, archived / (1024 * 1024))
            } else {
                counted
            },
            onDelete = {
                confirming = Confirmation(
                    title = catalogue.displayName,
                    body = R.string.storage_confirm_index,
                    act = { onDeleteIndex(catalogue.id) },
                )
            },
        )
    }

    // **One row per button** (decided 2026-09-21): what one press in Browse fetched, one press
    // here deletes. The three databases of facts about files are one row, as they are one
    // download; the two sets of replay routines likewise. Modland's favourites are not listed at
    // all -- they go with Modland's index, above.
    if (songMetadata > 0) {
        val name = stringResource(R.string.song_metadata_title)
        val tunes = songLengthCount + songDbLengthCount
        StorageRow(
            title = name,
            detail = pluralStringResource(R.plurals.song_metadata_count, tunes, tunes),
            onDelete = {
                confirming = Confirmation(
                    title = name,
                    body = R.string.storage_confirm_song_metadata,
                    act = onDeleteSongMetadata,
                )
            },
        )
    }

    if (replayRoutines > 0) {
        val name = stringResource(R.string.replay_routines_title)
        StorageRow(
            title = name,
            detail = pluralStringResource(R.plurals.replay_routines_count, replayRoutines, replayRoutines),
            onDelete = {
                confirming = Confirmation(
                    title = name,
                    body = R.string.storage_confirm_replay_routines,
                    act = onDeleteReplayRoutines,
                )
            },
        )
    }

    // The database, and no delete beside it — the one thing here that is **not** a copy. It holds
    // the playlists, the history and the granted folders alongside every index that is rows rather
    // than a file, which is why Modland has no size of its own above: its rows share this file's
    // pages, indexes and free list with the user's own work, and splitting the number between them
    // would be a guess presented as a measurement.
    if (databaseBytes > 0) {
        Text(
            text = stringResource(R.string.storage_database, databaseBytes / (1024 * 1024)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    confirming?.let { intent ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.storage_confirm_title, intent.title)) },
            text = { Text(stringResource(intent.body)) },
            confirmButton = {
                TextButton(onClick = {
                    intent.act()
                    confirming = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private data class Confirmation(val title: String, val body: Int, val act: () -> Unit)

@Composable
private fun StorageRow(title: String, detail: String, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(PlayerIcons.Remove, stringResource(R.string.a11y_delete_storage, title))
            }
        },
    )
}
