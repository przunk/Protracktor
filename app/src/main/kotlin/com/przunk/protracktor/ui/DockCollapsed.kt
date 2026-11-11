// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
 * The grip on top of the dock in both its forms: tap it to change the form, drag it up to expand
 * or down to collapse. A button to TalkBack, named by what a press does.
 */
@Composable
internal fun DockGrip(collapsed: Boolean, onCollapsedChange: (Boolean) -> Unit) {
    val change by rememberUpdatedState(onCollapsedChange)
    val threshold = with(LocalDensity.current) { DockDrag.THRESHOLD.toPx() }
    val label = stringResource(if (collapsed) R.string.dock_expand else R.string.dock_collapse)
    Box(
        Modifier
            .fillMaxWidth()
            .height(GRIP_HEIGHT)
            .testTag("dock-grip")
            .clickable(onClickLabel = label, role = Role.Button) { change(!collapsed) }
            .semantics { contentDescription = label }
            .dockDrag(threshold) { intent ->
                when (intent) {
                    DockDrag.Intent.EXPAND -> change(false)
                    DockDrag.Intent.COLLAPSE -> change(true)
                    else -> Unit
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)))
    }
}

/**
 * The collapsed dock (redesign E2): the tune, its author and time; a seek bar drawn subtly but held
 * as easily as the full one; Play/Pause; Queue; and **+** at the right edge when the tune is not in
 * the playlist (C137's rule, the same column as on the full dock's card).
 *
 * Tap the tune or drag up to expand; swipe it sideways for the next or previous tune. The swipes
 * are TalkBack actions as well, so nothing is reachable only by a gesture.
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
    Column(Modifier.fillMaxWidth().testTag("dock-collapsed")) {
        SeekBar(
            positionSeconds = state.positionSeconds,
            durationSeconds = state.bar.seconds,
            enabled = state.seekable && loaded != null,
            onSeek = onSeek,
            subtle = true,
            label = stringResource(R.string.a11y_seek),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("dock-collapsed-seek"),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                Text(
                    loaded?.title ?: stringResource(R.string.dock_idle_title),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(
                        NowPlayingText.subtitle(state, loading = stringResource(R.string.dock_loading),
                            idle = stringResource(R.string.dock_idle_subtitle)),
                        if (loaded != null) "${formatTime(state.positionSeconds)} / ${formatBarTotal(state)}" else "",
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = loaded != null || state.transportQueue.tracks.isNotEmpty(),
                modifier = Modifier.size(52.dp).testTag("dock-collapsed-play"),
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
                )
            }
            LabelledAction(icon = PlayerIcons.Playlist, label = stringResource(R.string.queue_title),
                onClick = onQueue, slim = true, modifier = Modifier.testTag("queue-toggle"))
            // The right edge belongs to +, when there is something to add (C137).
            if (state.awayFromPlaylist) {
                IconButton(onClick = onKeep, modifier = Modifier.size(width = 48.dp, height = 56.dp).testTag("dock-keep")) {
                    Icon(PlayerIcons.Add, stringResource(R.string.a11y_keep_track))
                }
            }
        }
    }
}

/** The grip's band: the pill is small, the place to press is not. */
private val GRIP_HEIGHT = 20.dp
