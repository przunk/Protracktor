// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R

/**
 * The navigation bar under the dock (redesign E1): **Playlist · Browse · Search · Random · More**.
 *
 * Small icons with small labels -- the project's icon-and-label rule, and five icons alone would be
 * guessing -- with the current tab marked by a pill behind its icon and by its semantics, so the
 * mark is not colour alone. It replaces the top row's Browse, WEB and Settings and the *Playlist*
 * way back each place had.
 */
@Composable
internal fun ShellNavigationBar(selected: ShellTab, onSelect: (ShellTab) -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(NAV_HEIGHT).testTag("shell-navigation"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShellTab.entries.forEach { tab ->
                NavItem(tab, tab == selected, Modifier.weight(1f)) { onSelect(tab) }
            }
        }
    }
}

@Composable
private fun NavItem(tab: ShellTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (icon, label) = tabLook(tab)
    Column(
        modifier
            .fillMaxHeight()
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag("nav-${tab.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 3.dp),
        ) {
            Icon(icon, null, Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun tabLook(tab: ShellTab): Pair<ImageVector, String> = when (tab) {
    ShellTab.PLAYLIST -> PlayerIcons.Playlist to stringResource(R.string.action_to_playlist)
    ShellTab.BROWSE -> PlayerIcons.Cloud to stringResource(R.string.action_browse)
    ShellTab.SEARCH -> PlayerIcons.Search to stringResource(R.string.nav_search)
    ShellTab.RANDOM -> PlayerIcons.Dice to stringResource(R.string.nav_random)
    ShellTab.MORE -> PlayerIcons.More to stringResource(R.string.nav_more)
}

/** What More offers: the places used less often, each an icon with its name. */
internal class MoreActions(
    val onHistory: () -> Unit,
    val onAlarms: (() -> Unit)?,
    val onSavedPlaces: () -> Unit,
    val webIcon: ImageVector,
    val onWeb: () -> Unit,
    val onWebHold: () -> Unit,
    val onSettings: () -> Unit,
)

/**
 * More, as a sheet over the navigation bar: History, Alarms, Saved places, WEB (send the playlist,
 * or pair; hold to pair again, as the old top-row button did) and Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreSheet(actions: MoreActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("more-sheet")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.nav_more), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val done: (() -> Unit) -> () -> Unit = { action -> { onDismiss(); action() } }
            val items = listOfNotNull<@Composable (Modifier) -> Unit>(
                { m -> LabelledAction(PlayerIcons.History, stringResource(R.string.domain_history_title), done(actions.onHistory), m) },
                actions.onAlarms?.let { alarms -> { m -> LabelledAction(PlayerIcons.Alarm, stringResource(R.string.alarm_title), done(alarms), m) } },
                { m -> LabelledAction(PlayerIcons.Bookmark, stringResource(R.string.saved_places_title), done(actions.onSavedPlaces), m) },
                { m -> LabelledAction(actions.webIcon, stringResource(R.string.action_send_to_browser), done(actions.onWeb), m,
                    onLongClick = done(actions.onWebHold), longClickLabel = stringResource(R.string.action_pair_again)) },
                { m -> LabelledAction(PlayerIcons.Settings, stringResource(R.string.settings_title), done(actions.onSettings), m, haptic = null) },
            )
            items.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { it(Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Short enough to sit under the dock without taking back the room the top row gave. */
private val NAV_HEIGHT = 56.dp

/**
 * How many lists have rows selected right now (redesign E3). While any has, the navigation bar
 * steps aside, so the selection bar and the list keep the room it took.
 */
@androidx.compose.runtime.Stable
internal class SelectionPresence {
    var count by androidx.compose.runtime.mutableIntStateOf(0)
}

internal val LocalSelectionPresence = androidx.compose.runtime.staticCompositionLocalOf<SelectionPresence?> { null }

/** A list says whether it is selecting; the shell counts it while it does. */
@Composable
internal fun ReportSelecting(selecting: Boolean) {
    val presence = LocalSelectionPresence.current ?: return
    androidx.compose.runtime.DisposableEffect(selecting, presence) {
        if (selecting) presence.count++
        onDispose { if (selecting) presence.count-- }
    }
}
