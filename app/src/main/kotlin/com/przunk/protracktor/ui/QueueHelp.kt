// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.przunk.protracktor.R

/** Keep gesture discovery available without making instructions a permanent part of the list. */
@Composable
internal fun QueueHeader(source: String, onClear: () -> Unit, onSource: (() -> Unit)? = null) {
    var help by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Reserve 80dp after the 32dp outer insets: large translated labels can consume the source.
        val wrapping = LocalDensity.current.fontScale > 1.25f
        val actionSpace = (maxWidth - 32.dp - 80.dp).coerceAtLeast(88.dp)
        // Clear has the longer caption in both languages; keep whole words at 200% text size.
        val clearWidth = if (wrapping) actionSpace * 0.54f else Dp.Infinity
        val helpWidth = if (wrapping) actionSpace * 0.46f else Dp.Infinity
        val actionSizing = if (wrapping) Modifier.fillMaxHeight() else Modifier
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically) {
            val sourceDescription = stringResource(R.string.queue_open_source, source)
            Row(Modifier.weight(1f).padding(end = 8.dp).fillMaxHeight().then(if (onSource != null)
                Modifier.testTag("queue-source").semantics { contentDescription = sourceDescription }
                    .clickable(role = Role.Button, onClick = onSource) else Modifier),
                verticalAlignment = Alignment.CenterVertically) {
                if (onSource != null) Icon(PlayerIcons.Back, null, Modifier.padding(end = 6.dp).size(20.dp).rotate(180f),
                    tint = MaterialTheme.colorScheme.primary)
                Text(source, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            LabelledAction(PlayerIcons.Remove, stringResource(R.string.queue_clear), onClear,
                modifier = Modifier.testTag("queue-clear").widthIn(max = clearWidth).then(actionSizing),
                slim = true, wrapLabel = wrapping)
            LabelledAction(PlayerIcons.Info, stringResource(R.string.queue_help), { help = true },
                modifier = Modifier.testTag("queue-help").widthIn(max = helpWidth).then(actionSizing),
                slim = true, wrapLabel = wrapping)
        }
    }
    if (help) AlertDialog(
        onDismissRequest = { help = false },
        icon = { Icon(PlayerIcons.Info, null) },
        title = { Text(stringResource(R.string.queue_help_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                QueueHelpLine(R.string.queue_help_left, R.string.queue_help_left_body)
                QueueHelpLine(R.string.queue_help_right, R.string.queue_help_right_body)
                QueueHelpLine(R.string.queue_help_hold, R.string.queue_help_hold_body)
                QueueHelpLine(R.string.queue_help_drag, R.string.queue_help_drag_body)
                QueueHelpLine(R.string.queue_clear, R.string.queue_clear_body)
            }
        },
        confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun QueueHelpLine(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}
