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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.przunk.protracktor.R
import com.przunk.protracktor.player.SupportedFormats
import com.przunk.protracktor.player.TrackRef

/**
 * What we know about one track.
 *
 * Shared rather than duplicated: the same dialog is reached from a playlist row and from a Browse,
 * search or history row, and a track's identity does not change depending on which list you found
 * it in.
 */
@Composable
internal fun TrackInfoDialog(track: TrackRef, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine(R.string.field_artist, track.displayAuthor)
                InfoLine(R.string.field_format, SupportedFormats.labelFor(track.fileNameOrTitle))
                InfoLine(R.string.field_file, track.fileNameOrTitle)
                InfoLine(R.string.info_location, track.subtitle)
                InfoLine(
                    R.string.info_size,
                    if (track.sizeBytes > 0) "${track.sizeBytes / 1024} kB" else "",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun InfoLine(label: Int, value: String) {
    if (value.isBlank()) return
    Column {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
