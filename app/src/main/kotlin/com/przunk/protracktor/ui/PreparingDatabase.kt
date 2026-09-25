// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R

/**
 * "Preparing the database", under the top bar while it happens (`docs/BACKLOG.md` A60).
 *
 * The owner asked for it in these words, with a database icon and a clock. **A strip, not a
 * dialog**: what plays goes on playing and Settings still opens; it is the lists that wait, and the
 * strip sits above them saying why. Announced to a screen reader once, politely.
 */
@Composable
internal fun PreparingDatabase(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Box {
                    Icon(PlayerIcons.Database, contentDescription = null, modifier = Modifier.size(24.dp))
                    // The clock as a badge on the drives, on a disc of the strip's colour so it
                    // reads as one sign rather than two icons touching.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 5.dp, y = 5.dp)
                            .size(15.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(PlayerIcons.Clock, contentDescription = null, modifier = Modifier.size(13.dp))
                    }
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(stringResource(R.string.preparing_database), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.preparing_database_detail),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
