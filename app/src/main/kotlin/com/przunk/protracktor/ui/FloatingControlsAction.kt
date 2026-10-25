// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.przunk.protracktor.R
import com.przunk.protracktor.player.FloatingControls

@Composable
internal fun FloatingControlsAction(available: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val visible by FloatingControls.visible.collectAsState()
    val currentAvailable by rememberUpdatedState(available)
    var explain by rememberSaveable { mutableStateOf(false) }
    var waiting by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<Int?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (waiting) {
            waiting = false
            when {
                !Settings.canDrawOverlays(context) -> problem = R.string.floating_permission_denied
                !currentAvailable -> problem = R.string.floating_no_track
                else -> FloatingControls.request(context, show = true)
            }
        }
    }
    LabelledAction(
        PlayerIcons.FloatingControls,
        stringResource(if (visible) R.string.floating_hide else R.string.floating_show),
        onClick = {
            when {
                visible -> FloatingControls.request(context, show = false)
                Settings.canDrawOverlays(context) -> FloatingControls.request(context, show = true)
                else -> explain = true
            }
        },
        modifier = modifier,
        enabled = (available || visible) && !waiting,
    )
    if (!available && !visible) Text(stringResource(R.string.floating_no_track))
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(stringResource(R.string.floating_show)) },
            text = { Text(stringResource(R.string.floating_permission_explanation)) },
            confirmButton = { TextButton(onClick = {
                explain = false
                waiting = true
                try {
                    permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")))
                } catch (failure: ActivityNotFoundException) {
                    waiting = false
                    problem = R.string.floating_settings_unavailable
                } catch (failure: SecurityException) {
                    waiting = false
                    problem = R.string.floating_settings_unavailable
                }
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
}
