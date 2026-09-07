// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.Platforms
import com.przunk.protracktor.player.SearchScope

/**
 * The three things a search can be narrowed to, and the row of chips the chosen one opens.
 *
 * **Two shapes, not three.** Before anything is chosen the tiles are large and share the width, so
 * the choice is the screen. Once a scope is set they shrink to a row of icons with the active one
 * lit, and the chips appear underneath. That is what keeps the filter quiet: at rest there are no
 * chips at all, and once there are, the tiles cost one line.
 *
 * It also removes a control. An earlier sketch replaced the tiles with the chip list and needed a
 * small back arrow to get out; keeping the icons means switching from platforms to catalogues is one
 * tap rather than two, and there is nothing to go back from — which is why the system back button
 * can keep the single meaning it has in `PlaybackController.browseBack`.
 */
private val TILE_HEIGHT = 88.dp
private val ICON_ROW_HEIGHT = 44.dp

@Composable
internal fun SearchScopePanel(
    browse: BrowseState,
    onScope: (SearchScope) -> Unit,
    onToggleCatalogue: (String) -> Unit,
    onTogglePlatform: (String) -> Unit,
) {
    val scope = browse.searchScope
    val compact = scope != SearchScope.Everywhere

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScopeTile(
            icon = PlayerIcons.Folder,
            label = stringResource(R.string.scope_local),
            selected = scope is SearchScope.Local,
            compact = compact,
            modifier = Modifier.weight(1f),
            // Tapping the lit tile puts the scope back to everywhere, which is the same thing back
            // does. A toggle that only ever narrows is a trap the user has to leave the screen to
            // escape.
            onClick = { onScope(if (scope is SearchScope.Local) SearchScope.Everywhere else SearchScope.Local) },
        )
        ScopeTile(
            icon = PlayerIcons.Cloud,
            label = stringResource(R.string.scope_online),
            selected = scope is SearchScope.Online,
            compact = compact,
            modifier = Modifier.weight(1f),
            onClick = { onScope(if (scope is SearchScope.Online) SearchScope.Everywhere else SearchScope.Online()) },
        )
        ScopeTile(
            icon = PlayerIcons.Joystick,
            label = stringResource(R.string.scope_platform),
            selected = scope is SearchScope.ByPlatform,
            compact = compact,
            modifier = Modifier.weight(1f),
            onClick = {
                onScope(
                    if (scope is SearchScope.ByPlatform) SearchScope.Everywhere
                    else SearchScope.ByPlatform()
                )
            },
        )
    }

    when (scope) {
        is SearchScope.Online -> CatalogueChips(browse, scope, onToggleCatalogue)
        is SearchScope.ByPlatform -> PlatformChips(browse, scope, onTogglePlatform)
        else -> Unit
    }
}

@Composable
private fun ScopeTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(if (compact) ICON_ROW_HEIGHT else TILE_HEIGHT),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = if (compact) label else null, modifier = Modifier.size(24.dp))
            // The label is dropped in the compact row, not shrunk. Three names at eight point are
            // three things nobody reads; the icons carry it, and the field's own label says which
            // one is active in words anyway.
            if (!compact) {
                // `minLines = 2` is what keeps the three icons on one line. The group is centred
                // vertically, so a tile whose label wraps is taller in its text and pushes its icon
                // up relative to its neighbours -- "Online catalogues" wraps and "By platform" does
                // not. Reserving both lines everywhere makes the three tiles the same shape, and
                // then centring puts the icons at the same height by construction rather than by
                // a hand-tuned padding that would break in the other language.
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    minLines = 2,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * One row that scrolls sideways, for both chip lists.
 *
 * This was a wrapping grid first, on the argument that a horizontal row hides its own contents. The
 * owner ran it and the argument lost: thirteen platforms wrap to four rows, and with the field and
 * the tiles above them **two results were left visible**. A filter that costs you the answer is
 * worse than one you have to drag.
 *
 * The order is fixed — biggest share of the archive first — so the chips that matter are the ones
 * you reach without dragging, and nothing moves as an index grows.
 */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun CatalogueChips(
    browse: BrowseState,
    scope: SearchScope.Online,
    onToggle: (String) -> Unit,
) {
    val indexed = browse.catalogues.filter { it.indexed }
    if (indexed.isEmpty()) {
        Text(
            text = stringResource(R.string.search_no_catalogues),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        return
    }
    ChipRow {
        indexed.forEach { catalogue ->
            FilterChip(
                selected = catalogue.id in scope.catalogueIds,
                onClick = { onToggle(catalogue.id) },
                label = { Text(catalogue.displayName) },
            )
        }
    }
}

/**
 * One chip per platform, wrapped rather than scrolled sideways.
 *
 * A platform with nothing indexed is drawn **disabled**, rather than left out. Leaving it out would
 * say the archive is smaller than it is; showing it says which machines are waiting on a decoder,
 * which is the truth and doubles as a roadmap. The order puts them last on their own, because it is
 * by share of the archive and the unplayable machines are the small ones.
 */
@Composable
private fun PlatformChips(
    browse: BrowseState,
    scope: SearchScope.ByPlatform,
    onToggle: (String) -> Unit,
) {
    ChipRow {
        Platforms.all.forEach { platform ->
            val held = browse.platformCounts[platform.id] ?: 0
            FilterChip(
                selected = platform.id in scope.platformIds,
                enabled = held > 0,
                onClick = { onToggle(platform.id) },
                label = { Text(platform.name) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

/**
 * What the search field's label says the search covers.
 *
 * The label rather than a placeholder, because a placeholder disappears the moment you start typing
 * — which is the moment the scope matters most. It also replaces the word "Search": the magnifier
 * in the field already says what the field is, so one text does both jobs.
 */
@Composable
internal fun scopeLabel(browse: BrowseState): String = when (val scope = browse.searchScope) {
    SearchScope.Everywhere -> stringResource(R.string.scope_label_everywhere)
    SearchScope.Local -> stringResource(R.string.scope_label_local)
    is SearchScope.Online -> {
        val names = browse.catalogues.filter { it.id in scope.catalogueIds }.map { it.displayName }
        if (names.isEmpty()) stringResource(R.string.scope_label_online)
        else stringResource(R.string.scope_label_online_some, joined(names))
    }
    is SearchScope.ByPlatform -> {
        val names = Platforms.all.filter { it.id in scope.platformIds }.map { it.name }
        if (names.isEmpty()) stringResource(R.string.scope_label_all_platforms) else joined(names)
    }
}

/** Two names, then a count. `SearchScope.parts` holds the rule so it can be tested. */
@Composable
private fun joined(names: List<String>): String {
    val parts = SearchScope.parts(names)
    val shown = parts.shown.joinToString(", ")
    return if (parts.hidden == 0) shown
    else stringResource(R.string.scope_label_more, shown, parts.hidden)
}
