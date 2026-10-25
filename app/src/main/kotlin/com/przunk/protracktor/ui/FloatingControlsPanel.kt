// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.FloatingControlsState
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
    onPreviousFile: () -> Unit,
    onNextFile: () -> Unit,
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
            "floating-previous", onPrevious, onLongClick = { if (state.previousFile) onPreviousFile() },
            longLabel = stringResource(R.string.a11y_previous_file)),
        FloatingAction(
            when { state.loading -> PlayerIcons.Stop; state.playing -> PlayerIcons.Pause; else -> PlayerIcons.Play },
            stringResource(when { state.loading -> R.string.floating_stop; state.playing -> R.string.a11y_pause; else -> R.string.a11y_play }),
            state.hasTrack || state.loading, "floating-play", onPlayPause),
        FloatingAction(PlayerIcons.SkipNext, stringResource(R.string.floating_next), state.next,
            "floating-next", onNext, onLongClick = { if (state.nextFile) onNextFile() },
            longLabel = stringResource(R.string.a11y_next_file)),
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
    val step = with(LocalDensity.current) { 32.dp.roundToPx() }
    val directions = listOf(
        Triple(stringResource(R.string.floating_move_left), -step, 0),
        Triple(stringResource(R.string.floating_move_right), step, 0),
        Triple(stringResource(R.string.floating_move_up), 0, -step),
        Triple(stringResource(R.string.floating_move_down), 0, step),
    )
    val hideLabel = stringResource(R.string.floating_hide)
    val dragHint = stringResource(R.string.floating_drag_hint)
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
            modifier = modifier.testTag("floating-panel").semantics {
                contentDescription = dragHint
                customActions = directions.map { (label, dx, dy) ->
                    CustomAccessibilityAction(label) { onMove(dx, dy); onMoveEnd(); true }
                } + CustomAccessibilityAction(hideLabel) { onHide(); true }
            },
        ) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (enlarged) {
                    actions.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth()) {
                            pair.forEach { FloatingActionButton(it, Modifier.weight(1f).height(gridHeight)) }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        actions.forEach { FloatingActionButton(it, Modifier.weight(1f).heightIn(min = 64.dp)) }
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
    val onLongClick: (() -> Unit)? = null,
    val longLabel: String? = null,
)

@Composable
private fun FloatingActionButton(action: FloatingAction, modifier: Modifier) {
    LabelledAction(action.icon, action.label, action.onClick,
        modifier.testTag(action.tag).semantics { action.description?.let { stateDescription = it } },
        enabled = action.enabled, fillLabelWidth = true,
        onLongClick = action.onLongClick, longClickLabel = action.longLabel)
}
