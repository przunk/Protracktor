// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.przunk.protracktor.R
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.player.QueueLink
import com.przunk.protracktor.player.TrackRef

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
internal fun ShareMenuItems(actions: ShareActions, onBack: (() -> Unit)?, onDone: () -> Unit, many: Boolean = false) {
    onBack?.let { back ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_back)) },
            leadingIcon = { Icon(PlayerIcons.Back, contentDescription = null) },
            onClick = back,
        )
        HorizontalDivider()
    }
    listOfNotNull(
        actions.file?.let { Triple(PlayerIcons.Share, if (many) R.string.action_share_files else R.string.action_share_file, it) },
        actions.audio?.let { Triple(PlayerIcons.AudioFile, R.string.action_share_audio, it) },
        actions.link?.let { Triple(PlayerIcons.Link, if (many) R.string.action_share_links else R.string.action_share_link, it) },
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

/**
 * **Share for a selection** (B59, 2026-11-09): the same list as one tune's, for all the ticked ones
 * -- the files in one share, the links one per line, or a Share with Protracktor link carrying the
 * list. *Share as audio* only for a single tune: rendering many takes a while each. An option none
 * of the selection can use is absent, as in the one-tune menu.
 */
@Composable
internal fun SelectionShareAction(
    tracks: List<TrackRef>,
    onShareFiles: (List<TrackRef>) -> Unit,
    onShareAudio: (TrackRef) -> Unit,
    onShareLinks: (List<TrackRef>) -> Unit,
    onSendToWeb: (List<TrackRef>) -> Unit,
    onShared: () -> Unit,
    slim: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val actions = ShareActions(
        file = { onShareFiles(tracks) },
        audio = tracks.singleOrNull()?.let { one -> { onShareAudio(one) } },
        link = if (tracks.any { Catalogue.owning(it.id) != null }) ({ onShareLinks(tracks) }) else null,
        protracktor = if (tracks.any(QueueLink::canSend)) ({ onSendToWeb(tracks) }) else null,
    )
    Box(modifier) {
        LabelledAction(
            icon = PlayerIcons.Share,
            label = stringResource(R.string.action_share),
            slim = slim,
            onClick = { open = true },
            // Filling only a share it was given (a weighted column); a slim pill keeps its own width,
            // or it would take the whole row from its neighbours.
            modifier = if (slim) Modifier else Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ShareMenuItems(actions, onBack = null, onDone = { open = false; onShared() }, many = tracks.size > 1)
        }
    }
}
