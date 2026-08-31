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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.RepeatMode

/**
 * The player bar, docked to the bottom of every screen (R3).
 *
 * With nothing loaded it does not disappear: the transport is visibly disabled and the identity row
 * becomes the button that opens Browse. A strip that is permanently dead and explains nothing
 * teaches the user something untrue (AGENTS.md §7).
 */
@Composable
fun PlayerDock(
    state: PlayerUiState,
    onExpand: () -> Unit,
    onBrowse: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = state.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // The surface still paints behind the gesture bar -- only the controls move up. Padding the
        // Surface instead would leave a strip of the wrong colour under the dock.
        Column(modifier = Modifier.navigationBarsPadding()) {
            val fraction = when {
                state.durationSeconds <= 0.0 -> 0f
                else -> (state.positionSeconds / state.durationSeconds).coerceIn(0.0, 1.0).toFloat()
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = if (loaded != null) onExpand else onBrowse)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loaded?.title ?: stringResource(R.string.dock_idle_title),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = loaded?.let { it.subtitle.ifBlank { formatOf(state) } }
                            ?: stringResource(R.string.dock_idle_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${formatTime(state.positionSeconds)} / ${formatTime(state.durationSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleControl(
                    icon = PlayerIcons.Shuffle,
                    active = state.queue.shuffle,
                    description = stringResource(
                        if (state.queue.shuffle) R.string.a11y_shuffle_on else R.string.a11y_shuffle_off
                    ),
                    enabled = state.queue.tracks.isNotEmpty(),
                    onClick = onShuffle,
                )

                IconButton(onClick = onPrevious, enabled = state.queue.hasPrevious) {
                    Icon(PlayerIcons.SkipPrevious, stringResource(R.string.a11y_previous))
                }

                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = state.queue.tracks.isNotEmpty(),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = if (state.playing) PlayerIcons.Pause else PlayerIcons.Play,
                        contentDescription = stringResource(
                            if (state.playing) R.string.a11y_pause else R.string.a11y_play
                        ),
                    )
                }

                IconButton(onClick = onNext, enabled = state.queue.hasNext) {
                    Icon(PlayerIcons.SkipNext, stringResource(R.string.a11y_next))
                }

                // Shape carries the mode, not just tint: repeat-one is a different glyph from
                // repeat-all, so the state survives being read without colour (AGENTS.md §8).
                ToggleControl(
                    icon = if (state.queue.repeat == RepeatMode.ONE) PlayerIcons.RepeatOne else PlayerIcons.Repeat,
                    active = state.queue.repeat != RepeatMode.OFF,
                    description = stringResource(
                        when (state.queue.repeat) {
                            RepeatMode.OFF -> R.string.a11y_repeat_off
                            RepeatMode.PLAYLIST -> R.string.a11y_repeat_playlist
                            RepeatMode.ONE -> R.string.a11y_repeat_one
                        }
                    ),
                    enabled = state.queue.tracks.isNotEmpty(),
                    onClick = onRepeat,
                )
            }
        }
    }
}

@Composable
private fun ToggleControl(
    icon: ImageVector,
    active: Boolean,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics {
        contentDescription = description
    }) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatOf(state: PlayerUiState): String = state.metadata["format"].orEmpty()

internal fun formatTime(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0) return "--:--"
    val total = seconds.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
