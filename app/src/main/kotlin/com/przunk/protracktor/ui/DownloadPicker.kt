// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    // **The sheet closes itself when the run ends.** Left open it re-reads what this phone now
    // holds, finds everything held, unticks every box and offers "Download 0 MB" -- an active
    // button for nothing, over a job that is finished. The snackbar underneath says how it went.
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        if (wasRunning && !running) onDismiss()
        wasRunning = running
    }

    // Ticked to begin with: whatever this phone does not already hold. The commonest press is the
    // first one, on an install that holds nothing, and it should not start with a tour of the
    // checkboxes.
    var selected by remember(
        browse.catalogues, browse.songLengthCount, browse.trackMetadataCount, browse.songDbLengthCount,
    ) {
        mutableStateOf(DownloadPlan.choices().filter { !isHeld(it, browse) }.toSet())
    }

    // **Opened whole, not half.** A Material bottom sheet stops at half height when its content
    // is tall enough, and the one thing anybody came here to press is the last thing in it -- so
    // half a sheet is a sheet with its button cut in two. `skipPartiallyExpanded` removes the
    // state that does that; there is nothing behind this sheet worth seeing past it.
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Text(
            text = stringResource(R.string.download_pick_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        // **The rows scroll; the button does not.** At the largest text sizes four rows and a
        // heading are taller than the sheet, and the button has to stay reachable -- the same
        // lesson as `docs/STATUS.md` C62, one level up. `fill = false` so a short list still wraps
        // its content rather than stretching to the ceiling.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            DownloadPlan.choices().forEach { id ->
                val held = isHeld(id, browse)
                val busy = browse.indexing.containsKey(id) ||
                    (id == Modland.id && browse.indexing.containsKey(DownloadKeys.FAVOURITES)) ||
                    (id == DownloadPlan.SONG_METADATA && browse.indexing.keys.any {
                        it == DownloadKeys.SONG_LENGTHS || it == DownloadKeys.TRACK_METADATA
                    })
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
                    // **A slot of one size, whether or not it is spinning.** Rows that change height
                    // as each step starts and finishes move the sheet under the finger -- and the
                    // button with it, which is what the owner saw walking down the list.
                    trailingContent = {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // **One width for both**, and a wide one. Download and Stop are the same control in two
        // states, and a button that changes size when it changes meaning moves under the thumb
        // that is about to press it. Half the sheet, centred, with room below it: this is the last
        // thing on the screen and the only thing to press.
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        ) {
            if (running) {
                LabelledAction(
                    icon = PlayerIcons.Stop,
                    label = stringResource(R.string.action_stop_downloads),
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(BUTTON_SHARE),
                )
            } else {
                LabelledAction(
                    icon = PlayerIcons.Download,
                    label = stringResource(
                        R.string.action_download_selected,
                        DownloadPlan.megabytesFor(selected),
                    ),
                    onClick = { onDownload(selected) },
                    // Nothing ticked is nothing to fetch. Drawn dead rather than hidden, so the
                    // sheet does not change shape as the boxes are ticked.
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(BUTTON_SHARE),
                )
            }
        }
    }
}

/** How much of the sheet's width the one button takes. */
private const val BUTTON_SHARE = 0.55f

/** Whether this phone already holds what [id] would fetch. */
private fun isHeld(id: String, browse: BrowseState): Boolean = when (id) {
    DownloadPlan.SONG_METADATA -> browse.songMetadataComplete
    else -> browse.catalogues.firstOrNull { it.id == id }?.let { it.indexed && !it.requiresIndex } ?: false
}

/** What a box costs on its own: the favourites counted with Modland, the three databases together. */
private fun megabytesFor(id: String): Int = DownloadPlan.megabytesFor(setOf(id))

@Composable
private fun labelFor(id: String): String = when (id) {
    DownloadPlan.SONG_METADATA -> stringResource(R.string.download_pick_metadata)
    Modland.id -> stringResource(R.string.download_pick_modland)
    else -> Catalogue.byId(id)?.displayName ?: id
}
