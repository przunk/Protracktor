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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.PlayerUiState

/**
 * The one real destination (`docs/OPEN_QUESTIONS.md` Q1): the active playlist.
 *
 * Not the whole library. Shuffle and repeat operate inside this list (R6), so this is also the
 * thing whose contents the user needs to see.
 */
@Composable
fun PlaylistScreen(
    state: PlayerUiState,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onBrowse: () -> Unit,
    onExitRandom: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Random plays something that is not in this list, so the list is put behind glass: visible,
    // clearly not what you are listening to, and not touchable by accident. Cheaper and more
    // portable than a blur, which needs API 31 and this app runs from 29.
    if (state.randomMode) {
        Box(modifier = modifier.fillMaxSize()) {
            PlaylistBody(state, currentIndex = null, onPlayAt = {}, onRemoveAt = {}, contentPadding = contentPadding, enabled = false)
            RandomScrim(onExitRandom = onExitRandom, contentPadding = contentPadding)
        }
        return
    }
    if (state.queue.tracks.isEmpty() && !state.randomMode) {
        EmptyPlaylist(onBrowse = onBrowse, contentPadding = contentPadding, modifier = modifier)
        return
    }

    // Nothing in the list is playing while Random is on, so nothing in the list is marked.
    PlaylistBody(
        state = state,
        currentIndex = state.queue.currentIndex,
        onPlayAt = onPlayAt,
        onRemoveAt = onRemoveAt,
        contentPadding = contentPadding,
        enabled = true,
        modifier = modifier,
    )
}

@Composable
private fun PlaylistBody(
    state: PlayerUiState,
    currentIndex: Int?,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    contentPadding: PaddingValues,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        itemsIndexed(state.queue.tracks, key = { _, track -> track.id }) { index, track ->
            val isCurrent = index == currentIndex
            ListItem(
                headlineContent = {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                // The author when the tune carries one, otherwise the folder it came from --
                // this music is filed by author far more often than it is tagged with one.
                supportingContent = track.displayAuthor.takeIf { it.isNotBlank() }?.let { author ->
                    { Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                },
                leadingContent = {
                    // The playing row is marked by a shape as well as a colour, so which row it is
                    // survives being read without colour (AGENTS.md §8).
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isCurrent) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                trailingContent = if (!enabled) null else {
                    {
                    IconButton(onClick = { onRemoveAt(index) }) {
                        Icon(
                            imageVector = PlayerIcons.Remove,
                            contentDescription = stringResource(R.string.a11y_remove_track, track.title),
                        )
                    }
                    }
                },
                colors = if (isCurrent) {
                    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    ListItemDefaults.colors()
                },
                modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onPlayAt(index) },
            )
        }
    }
}

@Composable
private fun EmptyPlaylist(
    onBrowse: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.playlist_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.playlist_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onBrowse) { Text(stringResource(R.string.action_browse)) }
        }
    }
}

/**
 * The glass over the playlist during Random, and the way out of it.
 *
 * The way out is in the middle of the screen with a label rather than tucked into a corner: the
 * playlist is already covered, so there is room, and a mode you can enter but cannot obviously
 * leave is a trap.
 */
@Composable
private fun RandomScrim(onExitRandom: () -> Unit, contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            // Swallows taps so a row underneath cannot be pressed through the glass.
            .clickable(enabled = true, onClick = {})
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = PlayerIcons.Dice,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.random_playing_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.random_playing_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onExitRandom) {
                Text(stringResource(R.string.random_back_to_playlist))
            }
        }
    }
}
