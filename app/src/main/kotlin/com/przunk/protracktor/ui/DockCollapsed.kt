// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.NowPlayingText
import kotlin.math.abs

/**
 * What a drag on the dock asks for (redesign E2), decided when the finger lifts: mostly up expands,
 * mostly down collapses, mostly sideways moves a tune. Pure, so the JVM holds the thresholds.
 */
internal object DockDrag {
    enum class Intent { EXPAND, COLLAPSE, NEXT, PREVIOUS, NONE }

    fun intent(total: Offset, threshold: Float): Intent = when {
        abs(total.y) >= abs(total.x) && total.y <= -threshold -> Intent.EXPAND
        abs(total.y) >= abs(total.x) && total.y >= threshold -> Intent.COLLAPSE
        abs(total.x) > abs(total.y) && total.x <= -threshold -> Intent.NEXT
        abs(total.x) > abs(total.y) && total.x >= threshold -> Intent.PREVIOUS
        else -> Intent.NONE
    }

    /** How far a finger goes before a drag means something. */
    val THRESHOLD = 40.dp
}

/** Reads a drag over the area and acts on it when the finger lifts (see [DockDrag]). */
internal fun Modifier.dockDrag(threshold: Float, act: (DockDrag.Intent) -> Unit): Modifier = pointerInput(threshold) {
    var total = Offset.Zero
    detectDragGestures(
        onDragStart = { total = Offset.Zero },
        onDragEnd = { act(DockDrag.intent(total, threshold)); total = Offset.Zero },
        onDragCancel = { total = Offset.Zero },
    ) { change, amount -> change.consume(); total += amount }
}

/**
 * The collapsed dock (redesign E2, shaped after my sketch of 2026-11-11): the seek bar along its top,
 * drawn thin but held in a band easy to take; under it the tune and author, scrolling when they do
 * not fit as on the full dock; **+** when the tune is not in the playlist; a smaller Play/Pause;
 * Queue as its icon.
 *
 * Tap the tune or drag the dock up to expand; swipe the tune sideways for the next or previous
 * tune. The swipes are TalkBack actions as well, so nothing is reachable only by a gesture.
 */
@Composable
internal fun CollapsedDock(
    state: PlayerUiState,
    onSeek: (Double) -> Unit,
    onKeep: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    onExpand: () -> Unit,
) {
    val loaded = state.current
    val threshold = with(LocalDensity.current) { DockDrag.THRESHOLD.toPx() }
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val expand by rememberUpdatedState(onExpand)
    val nextLabel = stringResource(R.string.a11y_next)
    val previousLabel = stringResource(R.string.a11y_previous)
    val expandLabel = stringResource(R.string.dock_expand)
    val context = androidx.compose.ui.platform.LocalContext.current
    val animatorScale = androidx.compose.runtime.remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    Column(Modifier.fillMaxWidth().testTag("dock-collapsed")) {
        // Along the top, the line near the edge; the band around it is what is pressed.
        Box(Modifier.fillMaxWidth().height(SEEK_BAND), contentAlignment = Alignment.Center) {
            SeekBar(
                positionSeconds = state.positionSeconds,
                durationSeconds = state.bar.seconds,
                enabled = state.seekable && loaded != null,
                onSeek = onSeek,
                subtle = true,
                label = stringResource(R.string.a11y_seek),
                modifier = Modifier.requiredHeight(SEEK_BAND).padding(horizontal = 4.dp).testTag("dock-collapsed-seek"),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .testTag("dock-collapsed-tune")
                    .clickable(onClickLabel = expandLabel, role = Role.Button) { expand() }
                    .dockDrag(threshold) { intent ->
                        when (intent) {
                            DockDrag.Intent.EXPAND -> expand()
                            DockDrag.Intent.NEXT -> next()
                            DockDrag.Intent.PREVIOUS -> previous()
                            else -> Unit
                        }
                    }
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(nextLabel) { next(); true },
                            CustomAccessibilityAction(previousLabel) { previous(); true },
                        )
                    }
                    .padding(vertical = 4.dp),
            ) {
                // As on the full dock: a line that does not fit scrolls rather than losing its end.
                Text(
                    loaded?.title ?: stringResource(R.string.dock_idle_title),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    modifier = Modifier.dockLine(DockMarquee.scrolls(animatorScale, isStatus = false)),
                )
                Text(
                    NowPlayingText.subtitle(state, loading = stringResource(R.string.dock_loading),
                        idle = stringResource(R.string.dock_idle_subtitle)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.dockLine(DockMarquee.scrolls(animatorScale, isStatus = state.loadingTrack)),
                )
            }
            if (state.awayFromPlaylist) {
                IconButton(onClick = onKeep, modifier = Modifier.size(44.dp).testTag("dock-keep")) {
                    Icon(PlayerIcons.Add, stringResource(R.string.a11y_keep_track))
                }
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = loaded != null || state.transportQueue.tracks.isNotEmpty(),
                modifier = Modifier.size(44.dp).testTag("dock-collapsed-play"),
            ) {
                Icon(
                    when {
                        state.loadingTrack -> PlayerIcons.Stop
                        state.playing -> PlayerIcons.Pause
                        else -> PlayerIcons.Play
                    },
                    stringResource(when {
                        state.loadingTrack -> R.string.a11y_stop_loading
                        state.playing -> R.string.a11y_pause
                        else -> R.string.a11y_play
                    }),
                    Modifier.size(22.dp),
                )
            }
            // Its icon alone here, by my choice for this form (2026-11-11); named for TalkBack.
            IconButton(onClick = onQueue, modifier = Modifier.size(44.dp).testTag("queue-toggle")) {
                Icon(PlayerIcons.Playlist, stringResource(R.string.queue_title))
            }
        }
    }
}

/** The collapsed seek bar's band: the line is thin, the place to press is not. */
private val SEEK_BAND = 32.dp
