// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R

/** Null opts out (playlist); false reserves the same lower slot as true to prevent row jumps. */
@Composable
internal fun TrackLeadingStatus(playedBefore: Boolean?, primary: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .then(if (playedBefore == null) Modifier.fillMaxSize() else Modifier.height(24.dp)),
            contentAlignment = Alignment.Center,
        ) { primary() }
        if (playedBefore == true) {
            Icon(
                PlayerIcons.History,
                contentDescription = stringResource(R.string.played_before),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).size(12.dp),
            )
        }
    }
}
