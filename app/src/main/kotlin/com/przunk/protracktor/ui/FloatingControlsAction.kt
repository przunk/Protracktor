// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.przunk.protracktor.R
import com.przunk.protracktor.player.FloatingControls

internal data class FloatingControlsToggle(val onToggle: () -> Unit, val label: String)

@Composable
internal fun rememberFloatingControlsAction(available: Boolean, enabled: Boolean): FloatingControlsToggle? {
    val action = rememberFloatingControlsPermission(available, enabled)
    return action.takeIf { enabled }
}

/** Keep the result receiver registered so disabling the preference also retires a pending grant. */
@Composable
internal fun rememberFloatingControlsPermission(
    available: Boolean,
    enabled: Boolean,
    request: (Context, Boolean) -> Unit = FloatingControls::request,
): FloatingControlsToggle {
    val context = LocalContext.current
    val visible by FloatingControls.visible.collectAsState()
    val currentVisible by rememberUpdatedState(visible)
    val currentAvailable by rememberUpdatedState(available)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentRequest by rememberUpdatedState(request)
    var explain by rememberSaveable { mutableStateOf(false) }
    var waiting by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<Int?>(null) }
    val permission = com.przunk.protracktor.permissions.rememberAccessRequestCoordinator { _, result ->
        if (waiting) {
            waiting = false
            when {
                !currentEnabled -> Unit
                result == com.przunk.protracktor.permissions.AccessRequestResult.UNAVAILABLE -> problem = R.string.floating_settings_unavailable
                !com.przunk.protracktor.permissions.SystemAccess.allowed(context, com.przunk.protracktor.permissions.AppAccess.OVERLAY) -> problem = R.string.floating_permission_denied
                !currentAvailable -> problem = R.string.floating_no_track
                else -> currentRequest(context, true)
            }
        }
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            waiting = false
            explain = false
            problem = null
        }
    }
    val toggle = remember {
        {
            when {
                !currentEnabled -> Unit
                waiting -> Unit
                currentVisible -> currentRequest(context, false)
                !currentAvailable -> problem = R.string.floating_no_track
                com.przunk.protracktor.permissions.SystemAccess.allowed(context, com.przunk.protracktor.permissions.AppAccess.OVERLAY) -> currentRequest(context, true)
                else -> explain = true
            }
        }
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(stringResource(R.string.floating_show)) },
            text = { Text(stringResource(R.string.floating_permission_explanation)) },
            confirmButton = { TextButton(onClick = {
                explain = false
                waiting = true
                permission.request(com.przunk.protracktor.permissions.AppAccess.OVERLAY)
            }) { Text(stringResource(R.string.floating_open_settings)) } },
            dismissButton = { TextButton(onClick = { explain = false }) {
                Text(stringResource(R.string.action_cancel))
            } },
        )
    }
    problem?.let { message ->
        AlertDialog(
            onDismissRequest = { problem = null },
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = { problem = null }) {
                Text(stringResource(R.string.action_close))
            } },
        )
    }
    return FloatingControlsToggle(toggle,
        stringResource(if (visible) R.string.floating_hide else R.string.floating_show))
}
