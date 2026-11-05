// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

/** Catalogue availability belongs to application composition, never to durable storage. */
internal fun interface AlarmSourcePolicy {
    fun allows(catalogueId: String?): Boolean
    companion object {
        val current = AlarmSourcePolicy { catalogue ->
            when (catalogue) {
                "amp" -> com.przunk.protracktor.net.Amp.access.enabled
                "unexotica" -> com.przunk.protracktor.net.UnExoticA.ENABLED
                else -> true
            }
        }
    }
}
