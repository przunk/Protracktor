// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R

/**
 * How many tunes wait in the queue, on Queue's button (B66, 2026-11-14). Counted are the tunes
 * queued by hand -- what Queue holds of its own; what follows from the playlist or Random is that
 * source, said where the source is. Nothing waiting shows nothing.
 */
internal object QueueCount {
    /** The badge's text: none for an empty queue, and a short form past two digits. */
    fun badge(waiting: Int): String? = when {
        waiting <= 0 -> null
        waiting > 99 -> "99+"
        else -> waiting.toString()
    }
}

/**
 * [content] with the count over its top end corner, and the same count said to TalkBack as the
 * button's state ("3 waiting"), so it is not a picture only. [content] is handed the modifier that
 * carries it, for the button itself: TalkBack reads the button's own node, not the box around it.
 */
@Composable
internal fun QueueCountBadge(waiting: Int, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    val badge = QueueCount.badge(waiting)
    val said = if (badge == null) null else pluralStringResource(R.plurals.queue_waiting, waiting, waiting)
    Box(modifier) {
        content(if (said == null) Modifier else Modifier.semantics { stateDescription = said })
        if (badge != null) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).testTag("queue-count"),
            ) { Text(badge, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
