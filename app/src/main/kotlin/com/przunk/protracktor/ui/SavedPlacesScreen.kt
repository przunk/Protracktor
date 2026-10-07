// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.SavedPlaceAvailability
import com.przunk.protracktor.player.SavedPlaceKey
import com.przunk.protracktor.player.SavedPlacesState

@Composable
internal fun SavedPlaceHeader(
    key: SavedPlaceKey, state: SavedPlacesState, onSave: () -> Unit, onRemove: () -> Unit, onRetry: () -> Unit,
) {
    val saved = state.contains(key)
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(key.author.ifBlank { stringResource(R.string.browse_unknown) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(key.format, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (saved) Text(stringResource(R.string.saved_place_saved), style = MaterialTheme.typography.labelSmall)
            if (state.loading || state.writing) Text(stringResource(R.string.saved_places_loading), style = MaterialTheme.typography.labelSmall)
            if (state.failed) Text(stringResource(R.string.saved_place_failed), style = MaterialTheme.typography.bodySmall)
        }
        if (state.failed) LabelledAction(PlayerIcons.Refresh, stringResource(R.string.saved_places_retry), onRetry, slim = true)
        else LabelledAction(
            icon = if (saved) PlayerIcons.Remove else PlayerIcons.Save,
            label = stringResource(if (saved) R.string.saved_place_remove else R.string.saved_place_save),
            onClick = if (saved) onRemove else onSave,
            enabled = !state.loading && !state.writing,
            modifier = Modifier.width(116.dp), slim = true,
        )
    }
}

@Composable
internal fun SavedPlacesScreen(
    state: SavedPlacesState,
    onOpen: (SavedPlaceKey) -> Unit,
    onRemove: (SavedPlaceKey) -> Unit,
    onPrepare: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.saved_places_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            LabelledAction(PlayerIcons.Refresh, stringResource(R.string.saved_places_retry), onRetry,
                enabled = !state.loading && !state.writing, slim = true)
        }
        if (state.loading || state.writing || state.opening != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.saved_places_loading), Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelSmall)
        }
        if (state.failed) Text(stringResource(R.string.saved_place_failed), Modifier.padding(16.dp))
        if (!state.loading && !state.failed && state.rows.isEmpty()) {
            Text(stringResource(R.string.saved_places_empty), Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.rows, key = { row ->
                with(row.place.key) { "${catalogueId.length}:$catalogueId${format.length}:$format$author" }
            }) { row ->
                val place = row.place
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(place.key.author.ifBlank { stringResource(R.string.browse_unknown) }, style = MaterialTheme.typography.titleMedium)
                    Text("${place.catalogueName} › ${place.key.format}", style = MaterialTheme.typography.bodySmall)
                    val reason = when (row.availability) {
                        SavedPlaceAvailability.AVAILABLE -> null
                        SavedPlaceAvailability.INDEX_MISSING -> R.string.saved_place_index_missing
                        SavedPlaceAvailability.GROUP_MISSING -> R.string.saved_place_group_missing
                        SavedPlaceAvailability.CATALOGUE_UNAVAILABLE -> R.string.saved_place_source_missing
                    }
                    reason?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LabelledAction(PlayerIcons.Folder, stringResource(R.string.saved_place_open), { onOpen(place.key) },
                            enabled = reason == null && !state.writing && state.opening == null, modifier = Modifier.weight(1f), slim = true)
                        if (row.availability == SavedPlaceAvailability.INDEX_MISSING || row.availability == SavedPlaceAvailability.GROUP_MISSING) {
                            LabelledAction(PlayerIcons.Cloud, stringResource(R.string.saved_place_prepare), onPrepare,
                                modifier = Modifier.weight(1f), slim = true)
                        }
                        LabelledAction(PlayerIcons.Remove, stringResource(R.string.saved_place_remove), { onRemove(place.key) },
                            enabled = !state.writing && state.opening == null, modifier = Modifier.weight(1f), slim = true)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
