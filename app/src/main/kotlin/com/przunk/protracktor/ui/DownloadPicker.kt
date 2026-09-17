// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.Modland
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.DownloadKeys
import com.przunk.protracktor.player.DownloadPlan
import com.przunk.protracktor.player.DownloadSizes

/**
 * Which archives to fetch, and how far the fetching has got.
 *
 * **One sheet for both**, rather than a picker that hands off to a progress bar somewhere else. The
 * rows are the same rows either way: ticked before, spinning during, and already-held afterwards.
 * Somebody who opens this while a run is going sees exactly what it is doing.
 *
 * **What the boxes cost is on them**, and the button adds it up — this is somebody's mobile data,
 * and 49 MB is a different decision from 6 MB. `DownloadPlan` decides what a tick actually
 * downloads: the SID song lengths and the favourites list are not boxes of their own, they come
 * with Modland, because neither is any use without the index it describes.
 *
 * **Stop is a real answer.** A run is a minute or two of somebody else's connection, and leaving
 * the screen does not stop it. What already landed is kept — each step writes its own table when
 * it finishes — so stopping after Modland leaves Modland indexed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadPicker(
    browse: BrowseState,
    onDownload: (Set<String>) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val running = browse.indexing.containsKey(DownloadKeys.EVERYTHING)

    // Ticked to begin with: whatever this phone does not already hold. The commonest press is the
    // first one, on an install that holds nothing, and it should not start with a tour of the
    // checkboxes.
    var selected by remember(browse.catalogues, browse.trackMetadataCount) {
        mutableStateOf(DownloadPlan.choices().filter { !isHeld(it, browse) }.toSet())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.download_pick_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        DownloadPlan.choices().forEach { id ->
            val held = isHeld(id, browse)
            val busy = browse.indexing.containsKey(id) ||
                (id == Modland.id && browse.indexing.keys.any { it == DownloadKeys.SONG_LENGTHS || it == DownloadKeys.FAVOURITES })
            ListItem(
                headlineContent = { Text(labelFor(id)) },
                supportingContent = {
                    Text(
                        if (held) {
                            stringResource(R.string.download_pick_held)
                        } else {
                            stringResource(R.string.download_pick_size, megabytesFor(id))
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = {
                    Checkbox(
                        checked = id in selected,
                        // While it runs the boxes are a report, not a question: what is being
                        // fetched was decided when the button was pressed.
                        enabled = !running,
                        onCheckedChange = { ticked ->
                            selected = if (ticked) selected + id else selected - id
                        },
                    )
                },
                trailingContent = if (busy) {
                    { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        ) {
            if (running) {
                LabelledAction(
                    icon = PlayerIcons.Stop,
                    label = stringResource(R.string.action_stop_downloads),
                    onClick = onStop,
                )
            } else {
                LabelledAction(
                    icon = PlayerIcons.Download,
                    label = stringResource(
                        R.string.action_download_selected,
                        DownloadPlan.megabytesFor(selected),
                    ),
                    onClick = { onDownload(selected) },
                )
            }
        }
    }
}

/** Whether this phone already holds what [id] would fetch. */
private fun isHeld(id: String, browse: BrowseState): Boolean = when (id) {
    DownloadPlan.TRACK_METADATA -> browse.trackMetadataCount > 0
    else -> browse.catalogues.firstOrNull { it.id == id }?.let { it.indexed && !it.requiresIndex } ?: false
}

/** What a box costs on its own, the lengths and the favourites counted with Modland. */
private fun megabytesFor(id: String): Int = DownloadPlan.megabytesFor(setOf(id))

@Composable
private fun labelFor(id: String): String = when (id) {
    DownloadPlan.TRACK_METADATA -> stringResource(R.string.download_pick_metadata)
    Modland.id -> stringResource(R.string.download_pick_modland)
    else -> Catalogue.byId(id)?.displayName ?: id
}
