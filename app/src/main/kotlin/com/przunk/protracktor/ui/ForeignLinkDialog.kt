// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.przunk.protracktor.R

/**
 * The question before a shared link's tunes from unknown servers are fetched (RV-B001): how many,
 * and which servers, because the server is what the person can judge. Cancel fetches nothing.
 */
@Composable
fun ForeignLinkDialog(addresses: List<String>, onPlay: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.link_foreign_title)) },
        text = {
            Text(
                pluralStringResource(R.plurals.link_foreign_body, addresses.size, addresses.size, ForeignHosts.of(addresses)),
                modifier = Modifier.testTag("foreign-link-body"),
            )
        },
        confirmButton = { TextButton(onClick = onPlay) { Text(stringResource(R.string.link_foreign_play)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The servers named, each once, the first three of them; an address with no host is shown whole. */
internal object ForeignHosts {
    private const val SHOWN = 3

    fun of(addresses: List<String>): String {
        val hosts = addresses.map { address ->
            runCatching { java.net.URI(address).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: address
        }.distinct()
        return hosts.take(SHOWN).joinToString(", ") + if (hosts.size > SHOWN) ", …" else ""
    }
}
