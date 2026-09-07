// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.przunk.protracktor.player.Platforms
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
    onSeek: (Double) -> Unit,
    onKeep: () -> Unit,
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
    val expandLabel = stringResource(R.string.a11y_expand_player)
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // The surface still paints behind the gesture bar -- only the controls move up. Padding the
        // Surface instead would leave a strip of the wrong colour under the dock.
        Column(modifier = Modifier.navigationBarsPadding()) {
            // The same seek control as Now Playing. The owner asked to be able to move
            // through a track from the main screen without opening anything first; a progress line
            // you cannot grab was the fault.
            // Position on the left, length on the right, with the bar between them: the two
            // numbers then read as where this line starts and where it ends. Written as
            // "0:36 / 2:20" beside the title they were one string to decode, in a row that was
            // getting crowded -- the owner's observation, and his layout.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Text(
                    text = formatTime(state.positionSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SeekBar(
                    positionSeconds = state.positionSeconds,
                    durationSeconds = state.durationSeconds,
                    enabled = state.seekable && loaded != null,
                    onSeek = onSeek,
                    compact = true,
                    label = stringResource(R.string.a11y_seek),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    text = formatTime(state.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Dead space between the seek bar and the button under it. The bar now claims a
            // proper 48dp of finger, and this is the margin for error beyond that: a low miss
            // lands on nothing instead of opening Now Playing, which is what it did in the car.
            Spacer(Modifier.height(6.dp))

            // **It has to look like a control.** This row opens the player, and nothing said so --
            // the owner pointed that out, and it matters more now that the tunes inside a file are
            // reached through it. Same remedy as the playlist name in the top bar, which had the
            // same problem: a surface you can see, and a chevron.
            Surface(
                onClick = if (loaded != null) onExpand else onBrowse,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = expandLabel },
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loaded?.title ?: stringResource(R.string.dock_idle_title),
                        // A size up from titleSmall. The owner reads this in a car, where a glance
                        // is all there is; the dock grows to fit rather than the text being
                        // squeezed to keep the dock's old height.
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // Said out loud, because on a network share this is seconds. A player that
                        // looks idle while it works gets pressed again.
                        // Author, then the machine and the year -- the owner asked for
                        // "AMIGA | 1992" while driving, and this is the line that had room for it
                        // without making the dock taller. The author comes first and the whole
                        // line ellipsises, so a long name pushes out the decoration rather than
                        // the other way round.
                        //
                        // With no author the platform *replaces* the format rather than joining
                        // it: "MOD · Amiga" says one thing twice.
                        text = when {
                            state.loadingTrack -> stringResource(R.string.dock_loading)
                            loaded != null -> {
                                val machine = Platforms.forFileName(loaded.fileName)?.name
                                val year = state.releaseYear
                                val lead = loaded.displayAuthor.ifBlank {
                                    machine ?: formatOf(state)
                                }
                                listOfNotNull(
                                    lead,
                                    machine.takeIf { loaded.displayAuthor.isNotBlank() },
                                    year.takeIf { it.isNotBlank() },
                                ).joinToString(" \u00b7 ")
                            }
                            else -> stringResource(R.string.dock_idle_subtitle)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // For anything playing that is not the playlist: a random pick, a search result, a
                // track tapped while browsing. Keeping it is a decision made after hearing it,
                // which is the only order that makes sense for something you did not choose.
                if (state.awayFromPlaylist) {
                    IconButton(onClick = onKeep) {
                        Icon(PlayerIcons.Add, stringResource(R.string.a11y_keep_track))
                    }
                }
                // Says there is more inside without adding a control: the tunes are chosen in
                // the player this row opens. Shown in **both** modes -- gating it on "play all" was
                // the first design and it closed a door on itself, since you then had to already
                // know a file held fifteen tunes in order to switch to the mode that would tell
                // you. The owner spotted that; "1 of 15" is true either way.
                if (state.subsongCount > 1) {
                    Text(
                        text = stringResource(
                            R.string.subsongs_position, state.subsong + 1, state.subsongCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Icon(
                    imageVector = PlayerIcons.Expand,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
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

                IconButton(
                    onClick = onPrevious,
                    enabled = state.canGoPrevious,
                    modifier = Modifier.size(TRANSPORT_TARGET),
                ) {
                    Icon(
                        PlayerIcons.SkipPrevious,
                        stringResource(R.string.a11y_previous),
                        modifier = Modifier.size(TRANSPORT_GLYPH),
                    )
                }

                FilledIconButton(
                    onClick = onPlayPause,
                    // A transient track counts: pressing Random with an empty playlist must still
                    // give you something you can pause.
                    enabled = state.current != null || state.queue.tracks.isNotEmpty(),
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        imageVector = if (state.playing) PlayerIcons.Pause else PlayerIcons.Play,
                        modifier = Modifier.size(34.dp),
                        contentDescription = stringResource(
                            if (state.playing) R.string.a11y_pause else R.string.a11y_play
                        ),
                    )
                }

                IconButton(
                    onClick = onNext,
                    enabled = state.canGoNext,
                    modifier = Modifier.size(TRANSPORT_TARGET),
                ) {
                    Icon(
                        PlayerIcons.SkipNext,
                        stringResource(R.string.a11y_next),
                        modifier = Modifier.size(TRANSPORT_GLYPH),
                    )
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
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(TRANSPORT_TARGET)
            .semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(TRANSPORT_GLYPH),
        )
    }
}

/**
 * How big a transport control is, and how big its glyph.
 *
 * The owner's reason is the whole specification: *"when I am driving it is hard to hit them."*
 * Material's 48dp is the minimum that counts as reachable sitting still and looking at it; this is
 * a size up from that, and the glyph grows with the target so the button does not become a large
 * area of nothing around a small mark.
 */
private val TRANSPORT_TARGET = 56.dp
private val TRANSPORT_GLYPH = 30.dp

private fun formatOf(state: PlayerUiState): String = state.metadata["format"].orEmpty()

internal fun formatTime(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0) return "--:--"
    val total = seconds.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
