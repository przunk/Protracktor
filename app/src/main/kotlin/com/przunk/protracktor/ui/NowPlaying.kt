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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.PlayerUiState

/**
 * What the main screen shows: **the file, not a visualiser** (R4).
 *
 * Format, tracker, author, channels, patterns, instruments, samples, subsongs — and the module
 * message, which is where the scene put its greetings and is half the reason anyone keeps these
 * files. A visualiser returns later as something the user switches on (`docs/WISHLIST.md`).
 */
@Composable
fun NowPlaying(
    state: PlayerUiState,
    onSeek: (Double) -> Unit,
    onShowInPlaylist: (() -> Unit)?,
    onShowNeighbours: (() -> Unit)?,
    onShareFile: (() -> Unit)?,
    onShareLink: (() -> Unit)?,
    onAddToOtherPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val track = state.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = track?.title ?: stringResource(R.string.dock_idle_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        SeekBar(
            positionSeconds = state.positionSeconds,
            durationSeconds = state.durationSeconds,
            enabled = state.seekable && track != null,
            onSeek = onSeek,
            label = stringResource(R.string.a11y_seek),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(state.positionSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTime(state.durationSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The filename and where it came from, which the title no longer shows once a tune's real
        // name has been read out of it.
        track?.let {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.field_file),
                    modifier = Modifier.width(120.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = listOf(it.subtitle, it.fileNameOrTitle).filter(String::isNotBlank).joinToString("/"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // One row of icons with their names underneath, rather than a stack of full-width text
        // buttons that grew by two in a single day (`docs/BACKLOG.md` A17). Each is absent rather
        // than disabled when it has nowhere to go -- a local file has no catalogue folder and no
        // address anyone else could open.
        // Equal columns sharing the width that is there, rather than a fixed cell size: five fixed
        // cells do not fit a phone, and the ones that wrapped read as missing rather than as a
        // second row. `weight` makes them a grid however many there are -- and the number does
        // vary, deliberately. "Show in playlist" is absent while what plays is not in the playlist,
        // and a local file has neither an author folder nor an address anyone else could open.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            onShowInPlaylist?.let {
                LabelledAction(PlayerIcons.Locate, stringResource(R.string.action_show_in_playlist), it, Modifier.weight(1f))
            }
            onShowNeighbours?.let {
                LabelledAction(PlayerIcons.Folder, stringResource(R.string.action_show_neighbours), it, Modifier.weight(1f))
            }
            onAddToOtherPlaylist?.let {
                LabelledAction(PlayerIcons.PlaylistAdd, stringResource(R.string.action_add_to_playlist), it, Modifier.weight(1f))
            }
            onShareFile?.let {
                LabelledAction(PlayerIcons.Share, stringResource(R.string.action_share_file), it, Modifier.weight(1f))
            }
            onShareLink?.let {
                LabelledAction(PlayerIcons.Link, stringResource(R.string.action_share_link), it, Modifier.weight(1f))
            }
        }

        val message = state.metadata["message"].orEmpty()
        val rows = FIELDS.mapNotNull { (key, label) ->
            state.metadata[key]?.takeIf { it.isNotBlank() && it != "0" }?.let { label to it }
        }

        if (rows.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            rows.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(label),
                        modifier = Modifier.width(120.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (message.isNotBlank()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.field_message),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Monospaced on purpose: these messages were written to a fixed-width tracker display
            // and their alignment is part of what they say.
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

private val FIELDS = listOf(
    "format" to R.string.field_format,
    "tracker" to R.string.field_tracker,
    "artist" to R.string.field_artist,
    "composer" to R.string.field_composer,
    "hardware" to R.string.field_hardware,
    "channels" to R.string.field_channels,
    "patterns" to R.string.field_patterns,
    "instruments" to R.string.field_instruments,
    "samples" to R.string.field_samples,
    "subsongs" to R.string.field_subsongs,
)
