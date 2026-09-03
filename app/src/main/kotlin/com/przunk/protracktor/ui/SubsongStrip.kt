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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R

/**
 * The tunes inside one file.
 *
 * `docs/BACKLOG.md` A2. A SAP holding fifteen tunes played exactly one of them until now, and the
 * owner's own example — Tactic — is one of those. Console formats are worse: a GBS in the sample
 * reported 99 and an HES 256.
 *
 * **It lives on the expanded player and nowhere else**, which is the owner's constraint (c): that
 * screen *is* the track, so a strip there covers nothing. It appears only when there is more than
 * one tune, so a MOD's view is exactly what it was.
 *
 * The mode beside it is constraint (b) and it is **global**, like shuffle and repeat: it decides
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
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return

    val listState = rememberLazyListState()
    // Follows what is playing. With 256 of them the current one is otherwise off screen for good.
    LaunchedEffect(current) { listState.bringIntoView(current) }

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
                        .clickable { onSelect(index) },
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
