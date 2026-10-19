// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.LearnedTitles
import com.przunk.protracktor.player.ManualRequest
import com.przunk.protracktor.player.TrackRef

/** Only pending occurrences are editable; current playback is deliberately outside this list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PendingQueueScreen(
    requests: List<ManualRequest>, learnedTitles: Map<String, String>, listState: LazyListState,
    onPlay: (Long) -> Unit, onRemove: (Long) -> Unit, onInfo: (TrackRef) -> Unit,
) {
    if (requests.isEmpty()) {
        Text(stringResource(R.string.queue_pending_empty), modifier = Modifier.padding(20.dp))
        return
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        itemsIndexed(requests, key = { _, request -> request.id }) { index, request ->
            val track = LearnedTitles.display(request.track, learnedTitles)
            val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) { onRemove(request.id); true } else false
            })
            SwipeToDismissBox(
                state = dismiss, enableDismissFromStartToEnd = false,
                modifier = Modifier.testTag("manual-request-${request.id}"),
                backgroundContent = {
                    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconLabel(PlayerIcons.Remove, stringResource(R.string.queue_remove))
                    }
                },
            ) {
                ListItem(
                    headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(LearnedTitles.sourceLine(track).ifBlank { track.fileNameOrTitle },
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { Text("${index + 1}", style = MaterialTheme.typography.labelSmall) },
                    trailingContent = {
                        Row {
                            val playDescription = stringResource(R.string.queue_play_request, track.title)
                            LabelledAction(PlayerIcons.Play, stringResource(R.string.queue_play), { onPlay(request.id) },
                                slim = true, modifier = Modifier.testTag("play-request-${request.id}")
                                    .semantics { contentDescription = playDescription })
                            val removeDescription = stringResource(R.string.queue_remove_request, track.title)
                            LabelledAction(PlayerIcons.Remove, stringResource(R.string.queue_remove), { onRemove(request.id) },
                                slim = true, modifier = Modifier.testTag("remove-request-${request.id}")
                                    .semantics { contentDescription = removeDescription })
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { onInfo(track) },
                )
            }
        }
    }
}
