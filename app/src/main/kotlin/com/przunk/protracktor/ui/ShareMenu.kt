// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.przunk.protracktor.R

/**
 * The ways to share one tune. Null is a way this tune has not got -- a file on the phone has no
 * address to send.
 */
internal class ShareActions(
    val file: (() -> Unit)?,
    val audio: (() -> Unit)?,
    val link: (() -> Unit)?,
    val protracktor: (() -> Unit)?,
) {
    val any: Boolean get() = file != null || audio != null || link != null || protracktor != null
}

/**
 * **The same four, in the same order, wherever a tune can be shared** (the owner, 2026-09-25): a
 * row's menu and Now Playing each had their own list, and they had already drifted -- one offered
 * Share with Protracktor and the other did not. One list, drawn here, so they cannot drift again.
 *
 * In a row's menu it is what the menu turns into after Share, with [onBack] leading back to the
 * rest; Android has no nested menus, and this keeps the choice where the finger already is. [onDone]
 * closes the menu before the action runs.
 */
@Composable
internal fun ShareMenuItems(actions: ShareActions, onBack: (() -> Unit)?, onDone: () -> Unit) {
    onBack?.let { back ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_back)) },
            leadingIcon = { Icon(PlayerIcons.Back, contentDescription = null) },
            onClick = back,
        )
        HorizontalDivider()
    }
    listOfNotNull(
        actions.file?.let { Triple(PlayerIcons.Share, R.string.action_share_file, it) },
        actions.audio?.let { Triple(PlayerIcons.AudioFile, R.string.action_share_audio, it) },
        actions.link?.let { Triple(PlayerIcons.Link, R.string.action_share_link, it) },
        actions.protracktor?.let { Triple(PlayerIcons.Web, R.string.action_send_to_web, it) },
    ).forEach { (icon, label, action) ->
        DropdownMenuItem(
            text = { Text(stringResource(label)) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            onClick = { onDone(); action() },
        )
    }
}

/** A row menu's way into [ShareMenuItems]: one Share where there used to be four. */
@Composable
internal fun ShareMenuEntry(actions: ShareActions, onOpen: () -> Unit) {
    if (!actions.any) return
    DropdownMenuItem(
        text = { Text(stringResource(R.string.action_share)) },
        leadingIcon = { Icon(PlayerIcons.Share, contentDescription = null) },
        onClick = onOpen,
    )
}
