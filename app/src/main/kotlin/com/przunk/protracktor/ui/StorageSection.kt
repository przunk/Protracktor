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
 * **It lives in Browse rather than in a settings screen**, because granted folders are managed from
 * Browse already and this is the same question — what does this app have of mine. Building it here
 * needs no new navigation, which matters: the app has no top-level overflow menu and adding one to
 * reach a single screen is a navigation decision the owner has reserved (`docs/OPEN_QUESTIONS.md`
 * Q1, `docs/BACKLOG.md` A13). When A13 is designed this section moves in one piece.
 */
@Composable
fun StorageSection(
    cacheBytes: Long,
    archiveBytes: Map<String, Long>,
    databaseBytes: Long,
    catalogues: List<CatalogueSummary>,
    songLengthCount: Int,
    onClearCache: () -> Unit,
    onDeleteIndex: (String) -> Unit,
    onClearSongLengths: () -> Unit,
) {
    var confirming by remember { mutableStateOf<Confirmation?>(null) }

    val stored = catalogues.filter {
        !it.isOnlineOnly && (it.trackCount > 0 || (archiveBytes[it.id] ?: 0L) > 0L)
    }
    if (cacheBytes <= 0 && stored.isEmpty() && songLengthCount <= 0 && databaseBytes <= 0) return

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
    // separately offered two deletes for one thing, and taking either left the other describing a
    // catalogue that no longer worked (round 6 review R4).
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

    if (songLengthCount > 0) {
        // Resolved outside the click, because a lambda is not a composable scope.
        val songLengthsName = stringResource(R.string.song_lengths_title)
        StorageRow(
            title = songLengthsName,
            detail = pluralStringResource(
                R.plurals.song_lengths_count, songLengthCount, songLengthCount
            ),
            onDelete = {
                confirming = Confirmation(
                    title = songLengthsName,
                    body = R.string.storage_confirm_song_lengths,
                    act = onClearSongLengths,
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
