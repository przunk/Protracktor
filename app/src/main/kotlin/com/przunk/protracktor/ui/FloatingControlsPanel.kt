// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.FloatingControlsState
import com.przunk.protracktor.player.FloatingPanelDrag
import com.przunk.protracktor.player.PlaylistHeart

@Composable
internal fun FloatingControlsPanel(
    state: FloatingControlsState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onHeart: (PlaylistHeart.Request) -> Unit,
    onHide: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onMoveEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enlarged = LocalDensity.current.fontScale > 1.25f
    val keepDescription = when {
        state.heart == null -> stringResource(R.string.floating_keep_unavailable)
        state.heart.saving -> stringResource(R.string.notification_playlist_saving, state.heart.playlistName)
        state.heart.member -> stringResource(R.string.notification_playlist_member, state.heart.playlistName)
        else -> stringResource(R.string.notification_playlist_add, state.heart.playlistName)
    }
    val actions = listOf(
        FloatingAction(PlayerIcons.SkipPrevious, stringResource(R.string.floating_previous), state.previous,
            "floating-previous", onPrevious),
        FloatingAction(
            when { state.loading -> PlayerIcons.Stop; state.playing -> PlayerIcons.Pause; else -> PlayerIcons.Play },
            stringResource(when { state.loading -> R.string.floating_stop; state.playing -> R.string.a11y_pause; else -> R.string.a11y_play }),
            state.hasTrack || state.loading, "floating-play", onPlayPause),
        FloatingAction(PlayerIcons.SkipNext, stringResource(R.string.floating_next), state.next, "floating-next", onNext),
        FloatingAction(
            ImageVector.vectorResource(if (state.heart?.member == true)
                R.drawable.ic_playlist_heart_filled else R.drawable.ic_playlist_heart_outline),
            stringResource(when {
                state.loading -> R.string.floating_loading
                state.heart == null -> R.string.floating_no_playlist
                state.heart.saving -> R.string.floating_saving
                else -> R.string.floating_keep
            }),
            state.heart != null && !state.heart.saving, "floating-heart",
            { state.heart?.let { onHeart(it.request) } }, keepDescription),
    )
    val captionHeight = with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
    val gridHeight = maxOf(76.dp, captionHeight + 38.dp)
    // Only the background is translucent; labels stay legible over a busy map.
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        secondaryContainer = Color.White.copy(alpha = .08f),
        onSecondaryContainer = Color.White,
    )) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xDC101820),
            contentColor = Color.White,
            shadowElevation = 4.dp,
            modifier = modifier.testTag("floating-panel"),
        ) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (enlarged) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FloatingDragHandle(onMove, onMoveEnd, Modifier.weight(1f))
                        FloatingHide(onHide, Modifier.width(96.dp))
                    }
                }
                if (enlarged) {
                    actions.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth()) {
                            pair.forEach { FloatingActionButton(it, Modifier.weight(1f).height(gridHeight)) }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(48.dp)) {
                            FloatingDragHandle(onMove, onMoveEnd, Modifier.fillMaxWidth())
                            FloatingHide(onHide, Modifier.fillMaxWidth())
                        }
                        Row(Modifier.weight(1f)) {
                            actions.forEach { FloatingActionButton(it, Modifier.weight(1f).heightIn(min = 64.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private data class FloatingAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val tag: String,
    val onClick: () -> Unit,
    val description: String? = null,
)

@Composable
private fun FloatingActionButton(action: FloatingAction, modifier: Modifier) {
    LabelledAction(action.icon, action.label, action.onClick,
        modifier.testTag(action.tag).semantics { action.description?.let { stateDescription = it } },
        enabled = action.enabled, fillLabelWidth = true)
}

@Composable
private fun FloatingHide(onHide: () -> Unit, modifier: Modifier) {
    LabelledAction(PlayerIcons.Close, stringResource(R.string.floating_hide_short), onHide,
        modifier.testTag("floating-hide"), slim = true, fillLabelWidth = true)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FloatingDragHandle(onMove: (Int, Int) -> Unit, onMoveEnd: () -> Unit, modifier: Modifier) {
    val haptics = rememberHaptics()
    val move by rememberUpdatedState(onMove)
    val endMove by rememberUpdatedState(onMoveEnd)
    val slop = ViewConfiguration.get(LocalContext.current).scaledTouchSlop.toFloat()
    val drag = remember(slop) { FloatingPanelDrag(slop) }
    val step = with(LocalDensity.current) { 32.dp.roundToPx() }
    val directions = listOf(
        Triple(stringResource(R.string.floating_move_left), -step, 0),
        Triple(stringResource(R.string.floating_move_right), step, 0),
        Triple(stringResource(R.string.floating_move_up), 0, -step),
        Triple(stringResource(R.string.floating_move_down), 0, step),
    )
    val description = stringResource(R.string.floating_drag_hint)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.heightIn(min = 48.dp).testTag("floating-drag")
            .semantics(mergeDescendants = true) {
                contentDescription = description
                customActions = directions.map { (label, dx, dy) ->
                    CustomAccessibilityAction(label) { move(dx, dy); endMove(); true }
                }
            }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> drag.begin(event.rawX, event.rawY)
                    MotionEvent.ACTION_MOVE -> {
                        val wasMoving = drag.moving
                        drag.move(event.rawX, event.rawY)?.let { amount ->
                            if (!wasMoving) haptics.gestureStart()
                            move(amount.x, amount.y)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_POINTER_DOWN -> if (drag.end()) {
                        endMove()
                        haptics.gestureEnd()
                    }
                }
                true
            },
    ) {
        Icon(PlayerIcons.DragHandle, contentDescription = null, Modifier.size(24.dp))
        Text(stringResource(R.string.floating_move), style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
