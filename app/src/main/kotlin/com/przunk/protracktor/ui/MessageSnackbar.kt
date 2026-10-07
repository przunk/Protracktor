// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.przunk.protracktor.player.Message
import com.przunk.protracktor.player.PlaybackController

/** The action belongs to the displayed notice, not whichever removal happened most recently. */
@Composable
internal fun MessageSnackbar(
    message: Message?, host: SnackbarHostState, undoLabel: String,
    onUndoTrackRemoval: () -> Unit, onDismiss: (Long) -> Unit,
) {
    message?.let {
        LaunchedEffect(it.id) {
            val result = host.showSnackbar(
                message = it.text,
                actionLabel = if (it.actionLabel == PlaybackController.UNDO) undoLabel else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                it.action?.invoke() ?: onUndoTrackRemoval()
            }
            onDismiss(it.id)
        }
    }
}
