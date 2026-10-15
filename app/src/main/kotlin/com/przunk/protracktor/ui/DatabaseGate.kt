// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.data.DatabaseAccessState

/** The ViewModel and external-open effect must not exist until compatibility is known. */
@Composable
internal fun DatabaseGate(
    check: suspend () -> DatabaseAccessState,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    var state by remember { mutableStateOf<DatabaseAccessState?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val currentCheck by rememberUpdatedState(check)
    LaunchedEffect(attempt) { state = currentCheck() }
    val shown = state
    if (shown == DatabaseAccessState.Ready) {
        content()
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (shown == null) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.database_checking))
                } else {
                    Icon(PlayerIcons.Database, contentDescription = null)
                    Text(stringResource(R.string.database_unavailable_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text(shown.problemText(LocalContext.current), modifier = Modifier.widthIn(max = 560.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LabelledAction(PlayerIcons.Refresh, stringResource(R.string.database_retry),
                            onClick = { state = null; attempt++ })
                        LabelledAction(PlayerIcons.Close, stringResource(R.string.action_close), onClick = onClose)
                    }
                }
            }
        }
    }
}

/** The service and startup screen explain the same refusal. */
internal fun DatabaseAccessState.problemText(context: Context): String = when (this) {
    is DatabaseAccessState.Newer -> context.getString(R.string.database_newer_detail, found, supported)
    DatabaseAccessState.Unreadable -> context.getString(R.string.database_unreadable_detail)
    DatabaseAccessState.Ready -> error("A compatible database has no refusal message")
}
