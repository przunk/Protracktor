// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * A notice with a unique id so identical consecutive texts can appear separately.
 * [actionLabel] carries an optional action token; the UI localizes the Undo token.
 */
data class Message(
    val text: String,
    val actionLabel: String? = null,
    val id: Long = nextMessageId(),
    val action: (() -> Unit)? = null,
)

private var messageCounter = 0L

private fun nextMessageId(): Long = ++messageCounter
