/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.ui

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R

/**
 * The follow-the-playing-track toggle.
 *
 * Off by default, because a list that scrolls itself while you are reading it is a feature people
 * turn off.
 *
 * **Shared by every list of tracks**, which is what the owner asked for: the playlist had it, and
 * Browse, search, history and "more from this author" did not — and those are exactly the lists you
 * scroll a long way down while something is playing. One component, so the gesture that cancels it
 * and the moment it reappears cannot drift apart between screens. Tapping it turns following on and hides the button, because it has nothing left to
 * offer; scrolling by hand turns following off and brings it back — the gesture that cancels it is
 * exactly the gesture that means "I want to look somewhere else".
 */
@Composable
internal fun FollowTrackButton(
    listState: LazyListState,
    currentIndex: Int?,
    contentPadding: PaddingValues,
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
) {
    // A real drag from the user, not our own scrolling. isScrollInProgress cannot tell those apart,
    // and mistaking one for the other would switch following off the instant it was switched on.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) onFollowingChange(false)
        }
    }

    LaunchedEffect(following, currentIndex) {
        if (following && currentIndex != null) listState.bringIntoView(currentIndex)
    }

    if (following || currentIndex == null) return

    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(12.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // Small and icon-only. The label made it discoverable and made it cover the list, and the
        // owner has now seen it -- so the trade goes the other way. Its content description still
        // says what it does, which is where discoverability belongs once you know the button exists.
        SmallFloatingActionButton(
            onClick = { onFollowingChange(true) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        ) {
            Icon(
                imageVector = PlayerIcons.Locate,
                contentDescription = stringResource(R.string.action_follow_track),
            )
        }
    }
}
