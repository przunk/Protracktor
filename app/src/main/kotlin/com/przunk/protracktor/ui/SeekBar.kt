// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The seek control, used by both the dock and Now Playing.
 *
 * One component in two places so the two cannot drift apart. The owner asked to be able to move
 * through a track without opening anything first, and a bar that reads differently in each place
 * would be worse than not having it in the second.
 *
 * While the thumb is held it shows where the finger is rather than where playback is. Without that
 * the position poll two hundred milliseconds later drags the thumb out from under the user, which
 * reads as a control that refused.
 */
// The slot-based Slider overloads are still marked experimental. Taken knowingly: the default
// thumb and track are what make a progress line look unmovable, which is the fault being fixed.
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
    val range = durationSeconds.toFloat().coerceAtLeast(0.001f)
    val shown = (scrubbing ?: positionSeconds.toFloat()).coerceIn(0f, range)
    val interaction = remember { MutableInteractionSource() }

    // **"Taki haptic żebym czuł że go trzymam"** (owner, 2026-09-10). Three parts, and all three
    // are needed for that sentence to be true: a firm one when the thumb is taken hold of, a very
    // light one per notch while it moves, and the gesture's own end when it is let go.
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
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            // **The height is the touch target, not the look of the line.** The track's thickness
            // is set on the track itself, so this can be a proper 48dp finger without the bar
            // getting any fatter. It was 28dp, under Material's minimum, with a full-width button
            // immediately below it -- so a low miss did not do nothing, it opened Now Playing. The
            // owner met that in a car, which is where a small target costs the most.
            .then(if (compact) Modifier.height(COMPACT_TOUCH_HEIGHT) else Modifier)
            .then(label?.let { text -> Modifier.semantics { contentDescription = text } } ?: Modifier),
        thumb = {
            // A visible grab point, which is what the owner asked for: a progress line with nothing
            // to take hold of does not look like something you can move.
            //
            // **And nothing to take hold of when there is nothing to move.** SID and Atari ST
            // cannot seek — libsidplayfp is running a program and has no notion of a position at
            // all — and since HVSC started supplying SID durations, the bar shows a real length and
            // looked exactly like a bar you could drag. The owner tried, on 2026-09-04. A greyed
            // thumb reads as "not now"; no thumb reads as "this is progress", which is the truth.
            //
            // **Drawn here rather than by `SliderDefaults.Thumb`**, which grows while pressed. The
            // track is inset by the thumb's radius, so a thumb that changes size makes the line
            // itself widen at both ends the moment you touch it — which is what the owner saw. A
            // fixed circle keeps the bar still under the finger.
            //
            // **The same dot whether or not it can be dragged** (`docs/STATUS.md` C45). Drawing
            // nothing at all was the earlier answer to "this is progress, not a control", and the
            // owner met what it costs: with Material's disabled track colours the played part is
            // grey on a dark surface and there is no mark on it, so where the tune had got to could
            // not be read. A smaller dot was tried for one build and he sent it back -- the track is
            // inset by the thumb's radius, so a smaller circle sits higher and the bar shifts with
            // it. Same size, same place; the colours are what changed.
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
 * **The knob for the look of a bar you cannot drag** (owner, 2026-09-14: "daj kolor akcentowany
 * nieco ciemniej"). 0 is the playing colour exactly; 1 disappears into the background. Raise it to
 * push the bar further back, lower it to bring it forward.
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
