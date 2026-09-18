// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R

/**
 * The tunes inside one file.
 *
 * `docs/BACKLOG.md` A2. Without it a SAP holding fifteen tunes plays exactly one of them. Console
 * formats hold more: a GBS in the sample reported 99 subsongs and an HES 256.
 *
 * **It lives on Now Playing and nowhere else**: that screen *is* the track, so a strip there covers
 * nothing. It appears only when there is more than one tune, so a MOD's view is unchanged.
 *
 * The mode beside it is **global**, like shuffle and repeat: it decides
 * whether one tune runs into the next and whether the transport walks them. Manual selection here
 * works in either mode — the mode governs what happens on its own, not what the user may choose.
 */
@Composable
internal fun SubsongStrip(
    count: Int,
    current: Int,
    playAll: Boolean,
    onSelect: (Int) -> Unit,
    onTogglePlayAll: () -> Unit,
    trackKey: Any? = null,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return

    val haptics = rememberHaptics()
    val listState = rememberLazyListState()

    // Which tune the strip last moved for. Reset per file, because a new file's strip starts
    // wherever that file opened -- which for a KSS can be tune 47 of 256.
    var lastSeen by remember(trackKey, count) { mutableStateOf(-1) }

    // **Follows the music only while you are watching the music.**
    //
    // Following unconditionally costs too much: scroll out to tune 240 of 256 to see what is
    // there, and the moment the current tune ends the strip yanks itself back to tune 68, so
    // reading the far end of a long file while it plays is impossible. Worse, `bringIntoView`
    // scrolls the item to the *start* of the view, so even an ordinary advance drags the whole
    // strip and puts everything before the playing tune out of reach.
    //
    // The signal for "am I watching the music" is the playing chip itself: if the tune that just
    // finished was on screen, the user is looking at the playing area and following is what they
    // want. If it was not, they are reading somewhere else and must be left there.
    //
    // Opening a file is the exception and follows regardless — that is being taken somewhere, not
    // being kept somewhere, and it is the one moment the strip must move.
    LaunchedEffect(trackKey, current, count) {
        val opening = lastSeen < 0
        val watching = opening || listState.isVisible(lastSeen)
        lastSeen = current
        if (opening) listState.bringIntoView(current) else listState.keepInView(current, watching)
    }

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.subsongs_position, current + 1, count),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            LabelledAction(
                icon = PlayerIcons.Subsongs,
                label = stringResource(
                    if (playAll) R.string.subsongs_all else R.string.subsongs_first_only
                ),
                onClick = onTogglePlayAll,
                // A function button by shape, a toggle by meaning — and the label changes with it,
                // so the finger may as well be told which way it went.
                haptic = { toggle(!playAll) },
            )
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            items((0 until count).toList()) { index ->
                val selected = index == current
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            CircleShape,
                        )
                        .clickable {
                            // *"Delikatnie na subsong."* And nothing at all for the one already
                            // playing: pressing it changes nothing, and a buzz would say it had.
                            if (!selected) haptics.pick()
                            onSelect(index)
                        },
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
