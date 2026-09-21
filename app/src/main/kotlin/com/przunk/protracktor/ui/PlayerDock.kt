// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.provider.Settings
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalContext
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
/**
 * A dock line that scrolls slowly left when it does not fit (A54), held for [DockMarquee.PAUSE_MS]
 * at the start of every pass. Without [scrolls] it is the plain line, cut with `…` as before.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.dockLine(scrolls: Boolean): Modifier =
    if (!scrolls) this else basicMarquee(
        iterations = Int.MAX_VALUE,
        initialDelayMillis = DockMarquee.PAUSE_MS,
        repeatDelayMillis = DockMarquee.PAUSE_MS,
        velocity = DockMarquee.VELOCITY_DP.dp,
    )

@Composable
fun PlayerDock(
    state: PlayerUiState,
    onSeek: (Double) -> Unit,
    onKeep: () -> Unit,
    onExpand: () -> Unit,
    onBrowse: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onPreviousFile: () -> Unit,
    onNext: () -> Unit,
    onNextFile: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val loaded = state.current
    // Read once per composition of the dock rather than watched: the setting changes rarely, and a
    // change reaches the dock the next time it is built -- the activity is recreated for less.
    val context = LocalContext.current
    val animatorScale = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    val expandLabel = stringResource(R.string.a11y_expand_player)
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // The surface still paints behind the gesture bar -- only the controls move up. Padding the
        // Surface instead would leave a strip of the wrong colour under the dock.
        Column(modifier = Modifier.navigationBarsPadding()) {
            // The same seek control as Now Playing, so a track can be moved through from the main
            // screen without opening anything first.
            //
            // Position on the left, length on the right, with the bar between them: the two
            // numbers then read as where this line starts and where it ends. Written as
            // "0:36 / 2:20" beside the title they are one string to decode, in a row that is
            // already crowded.
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
                    text = formatTotal(state.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Dead space between the seek bar and the button under it. The bar now claims a
            // proper 48dp of finger, and this is the margin for error beyond that: a low miss
            // lands on nothing instead of opening Now Playing, which is what it did in the car.
            Spacer(Modifier.height(6.dp))

            // **It has to look like a control.** This row opens the player, which is also how the
            // tunes inside a file are reached, and nothing else on it says so. Same remedy as the
            // playlist name in the top bar: a surface you can see, and a chevron.
            Surface(
                onClick = if (loaded != null) onExpand else onBrowse,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = expandLabel },
            ) {
            // **A height of its own, not its tallest child's** (`docs/STATUS.md` C54). The keep
            // button is an `IconButton` and carries Material's 48dp touch target; the chevron beside
            // it is a bare icon. Without a fixed height the card grows by four pixels wherever
            // keeping is offered, and the dock is visibly taller in Random than over the playlist.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DOCK_CARD_HEIGHT)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loaded?.title ?: stringResource(R.string.dock_idle_title),
                        // A title that does not fit scrolls rather than losing its end (A54); one
                        // that fits stands still, which the marquee decides by measuring.
                        modifier = Modifier.dockLine(DockMarquee.scrolls(animatorScale, isStatus = false)),
                        // A size up from titleSmall: this is read at a glance, in a car among
                        // other places, so the dock grows to fit rather than the text being
                        // squeezed to keep the dock's old height.
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // Said out loud, because on a network share this is seconds. A player that
                        // looks idle while it works gets pressed again.
                        // Author, then the machine and the year -- "AMIGA | 1992" -- because this
                        // is the line with room for it without making the dock taller. The author
                        // comes first and the whole line ellipsises, so a long name pushes out the
                        // decoration rather than the other way round.
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
                        modifier = Modifier.dockLine(
                            DockMarquee.scrolls(animatorScale, isStatus = state.loadingTrack)
                        ),
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
                // the player this row opens. Shown in **both** modes: gating it on "play all"
                // closes a door on itself, since you would have to already know a file held
                // fifteen tunes in order to switch to the mode that would tell you. "1 of 15" is
                // true either way.
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
                // **Dead while the dice is running, and drawn dead.** Shuffle reorders the
                // *playlist's* queue, and Random plays from neither the playlist nor its order, so
                // pressing it does nothing at all.
                //
                // Greyed rather than hidden. A transport that loses a button when the mode changes
                // is a transport whose other buttons move under the thumb, and this row is pressed
                // without looking.
                ToggleControl(
                    icon = PlayerIcons.Shuffle,
                    active = state.queue.shuffle,
                    description = stringResource(
                        if (state.queue.shuffle) R.string.a11y_shuffle_on else R.string.a11y_shuffle_off
                    ),
                    enabled = state.queue.tracks.isNotEmpty() && !state.randomMode,
                    onClick = onShuffle,
                )

                TransportButton(
                    icon = PlayerIcons.SkipPrevious,
                    description = stringResource(R.string.a11y_previous),
                    longDescription = stringResource(R.string.a11y_previous_file),
                    enabled = state.canGoPrevious,
                    onClick = onPrevious,
                    onLongClick = onPreviousFile,
                    longClickEnabled = state.canGoPreviousFile,
                )

                FilledIconButton(
                    onClick = { haptics.press(); onPlayPause() },
                    // A transient track counts: pressing Random with an empty playlist must still
                    // give you something you can pause.
                    enabled = state.loadingTrack || state.current != null || state.queue.tracks.isNotEmpty(),
                    modifier = Modifier.size(64.dp),
                ) {
                    // Three states, not two. A track being fetched is not "paused", and the button
                    // that would restart the same download is the one press nobody wants twice.
                    Icon(
                        imageVector = when {
                            state.loadingTrack -> PlayerIcons.Stop
                            state.playing -> PlayerIcons.Pause
                            else -> PlayerIcons.Play
                        },
                        modifier = Modifier.size(34.dp),
                        contentDescription = stringResource(
                            when {
                                state.loadingTrack -> R.string.a11y_stop_loading
                                state.playing -> R.string.a11y_pause
                                else -> R.string.a11y_play
                            }
                        ),
                    )
                }

                TransportButton(
                    icon = PlayerIcons.SkipNext,
                    description = stringResource(R.string.a11y_next),
                    longDescription = stringResource(R.string.a11y_next_file),
                    enabled = state.canGoNext,
                    onClick = onNext,
                    onLongClick = onNextFile,
                    longClickEnabled = state.canGoNextFile,
                )

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
                    // off → all → one → off: only the last step turns the light out.
                    activeAfter = state.queue.repeat != RepeatMode.ONE,
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
    /**
     * Whether the control will be lit **after** this press.
     *
     * Not `!active`, because repeat has three states and two of them are lit: off → all → one →
     * off. Guessing the opposite of the current state would tell the finger "you turned it off"
     * halfway through turning it up. The caller knows the cycle; this does not.
     */
    activeAfter: Boolean = !active,
) {
    val haptics = rememberHaptics()
    IconButton(
        onClick = { haptics.toggle(activeAfter); onClick() },
        enabled = enabled,
        modifier = Modifier
            .size(TRANSPORT_TARGET)
            .semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            // **The disabled tint has to be spelled out.** `IconButton` dims a disabled child
            // through `LocalContentColor`, and naming a tint here overrides exactly that -- so
            // without this `enabled = false` does nothing visible, and a dead button looks as alive
            // as the rest of the row.
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(TRANSPORT_GLYPH),
        )
    }
}

/**
 * How big a transport control is, and how big its glyph.
 *
 * These are pressed without looking, in a car among other places. Material's 48dp is the minimum
 * that counts as reachable sitting still and looking at it; this is a size up from that, and the
 * glyph grows with the target so the button does not become a large area of nothing around a small
 * mark.
 */
private val TRANSPORT_TARGET = 56.dp
private val TRANSPORT_GLYPH = 30.dp

private fun formatOf(state: PlayerUiState): String = state.metadata["format"].orEmpty()

internal fun formatTime(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0) return "--:--"
    val total = seconds.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * A tune's length, or dashes when nothing knows it.
 *
 * **Zero is not a length**, and "0:00" printed where a total belongs says the tune is over before
 * it starts. Formats with nowhere to record a length are ordinary here -- an NSF, an AY, a SID with
 * no HVSC entry -- so this is the common case rather than the odd one (`docs/STATUS.md` C67, C68).
 *
 * Deliberately not the same as [formatTime]: an *elapsed* zero is a real zero and reads correctly
 * on the left of the bar.
 */
internal fun formatTotal(seconds: Double): String =
    if (seconds > 0.0) formatTime(seconds) else "--:--"

/**
 * Skip forward or back: a press moves by tune, a hold moves by file.
 *
 * **Why a hold and not another button.** `aleste 2.kss` holds 256 tunes, so leaving it with the
 * ordinary next is 256 presses; but a fourth transport control would be on screen always, for a
 * thing wanted rarely, in a row that is pressed without looking. The hold costs nothing when it
 * is not used.
 *
 * `IconButton` has no long press, so this is the same shape built on `combinedClickable`: the
 * material target size, a circular ripple, and the disabled tint M3 uses. `onLongClickLabel` is not
 * decoration — it is what makes the second action reachable by TalkBack, which cannot discover a
 * gesture the screen does not mention.
 *
 * **Haptics on the hold**, because nothing else answers it. A press changes the title; a hold that
 * did nothing visible for a moment would read as a press that missed (`AGENTS.md` §8).
 */
/**
 * How tall the now-playing card stands, whatever is in it.
 *
 * Room for two lines of text and for the 48dp touch target a keep button brings with it, so the dock
 * is the same height over every screen.
 */
private val DOCK_CARD_HEIGHT = 60.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    longDescription: String,
    enabled: Boolean,
    longClickEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = rememberHaptics()

    // **The handlers are remembered, and that is the whole of the fix.**
    //
    // `combinedClickable` installs a gesture detector keyed on the lambdas it is given. Hand it a
    // fresh lambda on every recomposition and the detector is torn down and started again -- and a
    // detector that starts while a finger is already down begins timing a *new* long press. Skipping
    // a file recomposes this row, because the title above it changes, so one continuous hold fires
    // once every half second: two files skipped, or four if the hold is a little longer. It looks
    // like a race in the player and is not one.
    //
    // `rememberUpdatedState` keeps the identity fixed while letting the body see the current
    // callbacks.
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val currentLongEnabled by rememberUpdatedState(longClickEnabled)
    // Inside the remembered lambda, not wrapped around it: `combinedClickable` keys its gesture
    // detector on this identity, and the paragraph above is what happens when that identity moves.
    // `haptics` is itself remembered on the View, so this stays one stable lambda.
    val click = remember { { haptics.press(); currentClick() } }
    val longClick = remember {
        {
            if (currentLongEnabled) {
                haptics.gestureEnd()
                currentLongClick()
            }
        }
    }

    Box(
        modifier = Modifier
            .size(TRANSPORT_TARGET)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onLongClickLabel = longDescription,
                onLongClick = longClick,
                onClick = click,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(TRANSPORT_GLYPH),
            tint = if (enabled) {
                LocalContentColor.current
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}
