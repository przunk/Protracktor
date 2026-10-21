// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.przunk.protracktor.R

@Composable
internal fun EnqueueMenuItem(onEnqueue: (() -> Unit)?, onClose: () -> Unit) {
    if (onEnqueue == null) return
    DropdownMenuItem(
        text = { Text(stringResource(R.string.action_add_to_queue)) },
        leadingIcon = { Icon(PlayerIcons.QueueAdd, contentDescription = null) },
        onClick = { onClose(); onEnqueue() },
    )
}
