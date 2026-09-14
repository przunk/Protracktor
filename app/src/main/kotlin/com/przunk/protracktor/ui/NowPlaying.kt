// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.engine.DescribeBlock
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
    onSelectSubsong: (Int) -> Unit = {},
    onToggleAllSubsongs: () -> Unit = {},
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

        // Under the seek bar and above the actions: it is about what is playing, so it belongs
        // with the transport rather than with the things you can do to the file.
        SubsongStrip(
            count = state.subsongCount,
            current = state.subsong,
            playAll = state.playAllSubsongs,
            onSelect = onSelectSubsong,
            onTogglePlayAll = onToggleAllSubsongs,
            // So the strip knows a *different file* is playing rather than inferring it from the
            // count, which two unrelated files can easily share.
            trackKey = track?.id,
        )

        // One row of icons with their names underneath, rather than a stack of full-width text
        // buttons that grew by two in a single day (`docs/BACKLOG.md` A17). Each is absent rather
        // than disabled when it has nowhere to go -- a local file has no catalogue folder and no
        // address anyone else could open.
        // Equal columns sharing the width that is there, rather than a fixed cell size: five fixed
        // cells do not fit a phone, and the ones that wrapped read as missing rather than as a
        // second row. `weight` makes them a grid however many there are -- and the number does
        // vary, deliberately. "Show in playlist" is absent while what plays is not in the playlist,
        // and a local file has neither an author folder nor an address anyone else could open.
        // **As tall as the tallest label needs** (owner, 2026-09-14): asking for the minimum
        // intrinsic height lets every pill fill it, so a name that wraps to two lines is not cut and
        // the row stays a grid of equals.
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            onShowInPlaylist?.let {
                LabelledAction(PlayerIcons.Locate, stringResource(R.string.action_show_in_playlist), it,
                    Modifier.weight(1f), stretch = true)
            }
            onShowNeighbours?.let {
                LabelledAction(PlayerIcons.Folder, stringResource(R.string.action_show_neighbours), it,
                    Modifier.weight(1f), stretch = true)
            }
            onAddToOtherPlaylist?.let {
                LabelledAction(PlayerIcons.PlaylistAdd, stringResource(R.string.action_add_to_playlist), it,
                    Modifier.weight(1f), stretch = true)
            }
            onShareFile?.let {
                LabelledAction(PlayerIcons.Share, stringResource(R.string.action_share_file), it,
                    Modifier.weight(1f), stretch = true)
            }
            onShareLink?.let {
                LabelledAction(PlayerIcons.Link, stringResource(R.string.action_share_link), it,
                    Modifier.weight(1f), stretch = true)
            }
        }

        val message = state.metadata["message"].orEmpty()
        // The year is derived rather than looked up, because no two backends record it under the
        // same name and one of them does not record a year at all -- see `ReleaseYear`. It goes
        // first: of everything in this list it is the one fact about the tune rather than about
        // the file.
        val rows = listOfNotNull(
            state.releaseYear.takeIf { it.isNotBlank() }?.let { R.string.field_year to it }
        ) + FIELDS.mapNotNull { (key, label) ->
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

        // Where the scene wrote when the format had nowhere else -- a MOD's sample names are its only
        // text (`docs/PLAN_INSTRUMENT_NAMES.md`). Folded, and only where there is something to read;
        // one list where the two say the same.
        val instruments = DescribeBlock.names(state.metadata["instrument_names"])
        val samples = DescribeBlock.names(state.metadata["sample_names"])
        if (instruments.isNotEmpty()) {
            NameList(stringResource(R.string.field_instrument_names), instruments, track?.id)
        }
        if (samples.isNotEmpty() && samples != instruments) {
            NameList(stringResource(R.string.field_sample_names), samples, track?.id)
        }
    }
}

/**
 * One list of names, folded until tapped: numbered as a tracker numbers them, monospaced, never
 * wrapped -- a line too wide scrolls sideways in its own box, so the text an author laid out keeps
 * its shape. Folded again for the next tune, which is what keying it by the track does.
 */
@Composable
private fun NameList(label: String, names: List<String>, trackKey: String?) {
    var open by rememberSaveable(trackKey, label) { mutableStateOf(false) }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(vertical = 4.dp),
    ) {
        Icon(
            imageVector = PlayerIcons.Expand,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(if (open) 0f else 180f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label (${names.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        val digits = names.size.toString().length.coerceAtLeast(2)
        Text(
            text = names.mapIndexed { i, name -> "${(i + 1).toString().padStart(digits, '0')} $name" }
                .joinToString("\n"),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

private val FIELDS = listOf(
    "format" to R.string.field_format,
    "tracker" to R.string.field_tracker,
    "artist" to R.string.field_artist,
    // Filled by the songdb lookup where the file itself is silent, which for a plain `.mod` is
    // always: the format has nowhere to record either.
    "album" to R.string.field_album,
    "publisher" to R.string.field_publisher,
    "composer" to R.string.field_composer,
    "hardware" to R.string.field_hardware,
    "channels" to R.string.field_channels,
    "patterns" to R.string.field_patterns,
    "instruments" to R.string.field_instruments,
    "samples" to R.string.field_samples,
    "subsongs" to R.string.field_subsongs,
)
