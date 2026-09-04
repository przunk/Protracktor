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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
 * The seek control, used by both the dock and the expanded player.
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
            .then(if (compact) Modifier.height(28.dp) else Modifier)
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
            if (enabled) {
                SliderDefaults.Thumb(
                    interactionSource = interaction,
                    thumbSize = if (compact) DpSize(14.dp, 14.dp) else DpSize(20.dp, 20.dp),
                    enabled = true,
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
