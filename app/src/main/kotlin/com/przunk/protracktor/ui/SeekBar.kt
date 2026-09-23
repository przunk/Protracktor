// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.PlayerUiState

/**
 * The seek control, used by both the dock and Now Playing.
 *
 * One component in two places so the two cannot drift apart: a track can be moved through without
 * opening anything first, and a bar that read differently in each place would be worse than not
 * having it in the dock at all.
 *
 * While the thumb is held it shows where the finger is rather than where playback is. Without that
 * the position poll two hundred milliseconds later drags the thumb out from under the user, which
 * reads as a control that refused.
 */
// The slot-based Slider overloads are still marked experimental. Taken knowingly: the default
// thumb and track are what make a progress line look unmovable, which is the fault being fixed.
/**
 * The elapsed time beside a seek bar, or, while a seek takes long enough to be seen waiting, a
 * small spinner in its place (Q11, the owner's choice 2026-09-22): where the eye already is, beside
 * the bar just dragged. The same width either way, so the bar does not move.
 */
@Composable
fun ElapsedTime(state: PlayerUiState) {
    val seeking = stringResource(R.string.a11y_seeking)
    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.widthIn(min = barLabelWidth())) {
        // Holds the width of a time, so the spinner does not pull the bar sideways.
        Text(
            text = formatTime(state.positionSeconds),
            style = barLabelStyle(),
            maxLines = 1,
            softWrap = false,
            color = if (state.seekSlow) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.seekSlow) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(12.dp).semantics { contentDescription = seeking },
            )
        }
    }
}

/**
 * The total at the end of the seek bar, in the same room as [ElapsedTime] and set against the bar's
 * end, so the digits stay where they are and a `~` appears in space already kept for it (A58).
 */
@Composable
fun BarTotal(state: PlayerUiState) {
    Text(
        text = formatBarTotal(state),
        style = barLabelStyle(),
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.End,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(min = barLabelWidth()),
    )
}

/** Tabular figures: `1:11` and `8:08` are the same width, so the times do not jitter as they count. */
@Composable
private fun barLabelStyle(): TextStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")

/**
 * The room [BAR_LABEL_TEMPLATE] takes in the labels' own style, measured rather than written down
 * as a number of dp, so it follows the font and the user's text size.
 */
@Composable
private fun barLabelWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = barLabelStyle()
    val density = LocalDensity.current
    return remember(style, density) {
        with(density) { measurer.measure(BAR_LABEL_TEMPLATE, style, maxLines = 1, softWrap = false).size.width.toDp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekBar(
    positionSeconds: Double,
    durationSeconds: Double,
    enabled: Boolean,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    label: String? = null,
) {
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    // **A tune of unknown length has no fraction to draw.** The floor below keeps the slider from
    // dividing by zero, and with a real duration it is invisible -- but with none it made the
    // range a thousandth of a second, so the thumb sat at the far end from the first tick while
    // the elapsed time counted up beside it (`docs/STATUS.md` C68). An empty bar is the honest
    // picture: something is playing, and how far through it is unknown.
    val measured = durationSeconds > 0.0
    val range = durationSeconds.toFloat().coerceAtLeast(0.001f)
    val shown = if (measured) (scrubbing ?: positionSeconds.toFloat()).coerceIn(0f, range) else 0f
    val interaction = remember { MutableInteractionSource() }

    // **The thumb is felt, not just seen.** Three parts, and all three are needed for that: a
    // firm one when the thumb is taken hold of, a very light one per notch while it moves, and the
    // gesture's own end when it is let go.
    //
    // The notches are a fixed count across the bar rather than a fixed number of seconds, so a
    // ninety-second chiptune and a twenty-minute SID feel the same under the thumb. Without them
    // this would fire on every pixel of movement — a continuous rattle, not a texture.
    val haptics = rememberHaptics()
    var notch by remember { mutableIntStateOf(-1) }

    Slider(
        value = shown,
        onValueChange = { value ->
            val step = (value / range * SEEK_NOTCHES).toInt()
            if (scrubbing == null) {
                haptics.grab()
                // Adopted rather than compared, so taking hold is one buzz and not two.
                notch = step
            } else if (step != notch) {
                notch = step
                haptics.scrub()
            }
            scrubbing = value
        },
        onValueChangeFinished = {
            scrubbing?.let { onSeek(it.toDouble()) }
            scrubbing = null
            notch = -1
            haptics.gestureEnd()
        },
        valueRange = 0f..range,
        // Nothing to drag towards: a position is asked for as a fraction of a length, and there is
        // no length. The dot goes with it, for the same reason it goes on a backend that cannot
        // seek (`docs/STATUS.md` C45).
        enabled = enabled && measured,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            // **The height is the touch target, not the look of the line.** The track's thickness
            // is set on the track itself, so this can be a proper 48dp finger without the bar
            // getting any fatter. At 28dp it is under Material's minimum, with a full-width
            // button immediately below it -- so a low miss does not do nothing, it opens Now
            // Playing.
            .then(if (compact) Modifier.height(COMPACT_TOUCH_HEIGHT) else Modifier)
            .then(label?.let { text -> Modifier.semantics { contentDescription = text } } ?: Modifier),
        thumb = {
            // A visible grab point: a progress line with nothing to take hold of does not look
            // like something you can move.
            //
            // **And nothing to take hold of when there is nothing to move.** A backend that cannot
            // seek still shows a real length where a database supplies one, so the bar would
            // otherwise look exactly like a bar you could drag. A greyed thumb reads as "not now".
            // (SID and Atari ST were the reason once; since 2026-09-22 they seek by running their
            // machines to the place, Q11.)
            //
            // **Drawn here rather than by `SliderDefaults.Thumb`**, which grows while pressed. The
            // track is inset by the thumb's radius, so a thumb that changes size makes the line
            // itself widen at both ends the moment you touch it. A fixed circle keeps the bar
            // still under the finger.
            //
            // **The same dot whether or not it can be dragged** (`docs/STATUS.md` C45). Drawing
            // nothing costs too much: with Material's disabled track colours the played part is
            // grey on a dark surface and carries no mark, so where the tune has got to cannot be
            // read. Nor a smaller dot -- the track is inset by the thumb's radius, so a smaller
            // circle sits higher and the bar shifts with it. Same size, same place; only the
            // colours change.
            val size = if (compact) 14.dp else 20.dp
            // Darker where it cannot be dragged, and darker by *mixing with the surface* rather
            // than by going see-through: a translucent dot takes the colour of whatever is behind
            // it, which here is the track it sits on, and then reads as a hole in the bar.
            // [DIMMED] is the one number to turn.
            val dot = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surface, DIMMED)
            }
            Box(modifier = Modifier.size(size).background(dot, CircleShape))
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                enabled = enabled,
                modifier = Modifier.height(if (compact) 4.dp else 8.dp),
                drawStopIndicator = null,
            )
        },
        colors = SliderDefaults.colors(
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            // **The same two, dimmed rather than greyed.** Material's disabled defaults are
            // `onSurface` at a third, which on this dark surface is a line you cannot see -- and a
            // tune that cannot be seeked is still a tune whose progress is worth reading (C45).
            disabledActiveTrackColor = lerp(
                MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surface, DIMMED,
            ),
            disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

/**
 * How far the dot and the played line are taken towards the surface where the tune cannot be seeked.
 *
 * **The knob for the look of a bar you cannot drag.** 0 is the playing colour exactly; 1
 * disappears into the background. Raise it to push the bar further back, lower it to bring it
 * forward.
 */
private const val DIMMED = 0.4f

/**
 * How much finger the dock's seek bar answers to.
 *
 * Material asks for 48dp and the bar was 28dp, which is fine on a desk and not in a car. The line
 * still draws 4dp thick — this is the target around it, not the thing you see.
 */
private val COMPACT_TOUCH_HEIGHT = 48.dp

/**
 * How many notches the thumb passes from one end of the bar to the other.
 *
 * Forty is a thumb's width of travel per notch on a phone-sized bar — often enough to feel like a
 * surface rather than a series of events, rare enough that a slow drag does not become a rattle.
 */
private const val SEEK_NOTCHES = 40
