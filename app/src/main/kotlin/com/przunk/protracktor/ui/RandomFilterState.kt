// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.RandomFilterAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class RandomFilterState(
    val availability: RandomFilterAvailability?,
    val loading: Boolean,
    val failed: Boolean,
    val retry: () -> Unit,
)

private data class FilterInputs(
    val catalogues: List<CatalogueSummary>,
    val platforms: Map<String, Int>,
    val favourites: Int,
    val listed: Int,
    val downloads: Set<String>,
    val deleting: Set<String>,
    val preparing: Boolean,
) {
    val blocked: Boolean get() = preparing || deleting.isNotEmpty()
}

/** Lives beside the Random screen, not inside the disposable filter sheet. */
@Composable
internal fun rememberRandomFilterState(
    active: Boolean,
    browse: BrowseState,
    preparingDatabase: Boolean,
    loadAvailability: suspend () -> RandomFilterAvailability,
): RandomFilterState {
    var availability by remember { mutableStateOf<RandomFilterAvailability?>(null) }
    var acceptedInputs by remember { mutableStateOf<FilterInputs?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    val readAvailability by rememberUpdatedState(loadAvailability)
    val inputs = FilterInputs(
        browse.catalogues, browse.platformCounts, browse.favouriteCount, browse.favouritesListed,
        browse.indexing.keys.toSet(), browse.deleting, preparingDatabase,
    )

    // Progress percentages and the sheet's visibility do not change the catalogue's contents.
    LaunchedEffect(active, inputs, retry) {
        failed = false
        if (inputs.blocked || (!active && acceptedInputs != inputs)) {
            availability = null
            acceptedInputs = null
        }
        loading = active && inputs.blocked
        if (!active || inputs.blocked) return@LaunchedEffect
        loading = true
        try {
            val result = readAvailability()
            currentCoroutineContext().ensureActive()
            availability = result
            acceptedInputs = inputs
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Retain a previously verified snapshot; failed reads are not zero counts.
            failed = true
        }
        loading = false
    }

    return RandomFilterState(
        availability = availability.takeUnless { inputs.blocked },
        loading = loading || inputs.blocked,
        failed = failed && !inputs.blocked,
        retry = { retry++ },
    )
}
