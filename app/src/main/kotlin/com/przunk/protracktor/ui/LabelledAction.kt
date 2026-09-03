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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One cell of the action grid. Every action is this wide, whatever its name. */
private val CELL = 84.dp

/**
 * An action drawn as an icon with its name underneath.
 *
 * The owner's shape, asked for twice: once for the way out of Browse (`docs/BACKLOG.md` A16) and
 * again for the actions on a track and the way in (`A17`, `A23`). An icon alone does not say where
 * it goes, and a word alone does not read as a control — the pair does both, and several of them
 * fit in one row where the same actions as full-width text buttons did not.
 *
 * Shared rather than repeated, because the version that existed was inline in the Browse header and
 * a second copy would have drifted from it by the third caller.
 */
@Composable
internal fun LabelledAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            // **A fixed width, not a ceiling.** Sized to the content, these came out ragged --
            // "Share" narrow, "More from this author" wide -- and a row of different-sized things
            // does not read as a set of equals. One width makes them a grid.
            .width(CELL)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        // The accent colour, because these are controls. Drawn in the ordinary content colour they
        // read as labels, which is what the owner noticed when the text buttons they replaced --
        // which were accented by default -- stopped being.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
