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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    Slider(
        value = shown,
        onValueChange = { scrubbing = it },
        onValueChangeFinished = {
            scrubbing?.let { onSeek(it.toDouble()) }
            scrubbing = null
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
            if (enabled) {
                val size = if (compact) 14.dp else 20.dp
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            } else {
                Box(Modifier.size(0.dp))
            }
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
        ),
    )
}

/**
 * How much finger the dock's seek bar answers to.
 *
 * Material asks for 48dp and the bar was 28dp, which is fine on a desk and not in a car. The line
 * still draws 4dp thick — this is the target around it, not the thing you see.
 */
private val COMPACT_TOUCH_HEIGHT = 48.dp
