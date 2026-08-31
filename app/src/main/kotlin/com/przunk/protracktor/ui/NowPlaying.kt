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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

        // While the thumb is held, the slider shows where the finger is rather than where playback
        // is. Without that the poll two hundred milliseconds later drags the thumb back out from
        // under the user, which reads as a control that refused.
        var scrubbing by remember { mutableStateOf<Float?>(null) }
        val seekable = state.durationSeconds > 0.0 && track != null
        val shown = scrubbing ?: state.positionSeconds.toFloat()

        Slider(
            value = shown.coerceIn(0f, state.durationSeconds.toFloat().coerceAtLeast(0f)),
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                scrubbing?.let { onSeek(it.toDouble()) }
                scrubbing = null
            },
            valueRange = 0f..state.durationSeconds.toFloat().coerceAtLeast(0.001f),
            enabled = seekable,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(shown.toDouble()),
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
    "channels" to R.string.field_channels,
    "patterns" to R.string.field_patterns,
    "instruments" to R.string.field_instruments,
    "samples" to R.string.field_samples,
    "subsongs" to R.string.field_subsongs,
)
