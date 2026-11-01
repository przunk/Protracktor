// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.net

/** Retained references stay blocked until their catalogue is available in this build. */
internal object CatalogueRetention {
    val ids = setOf("amp")
    fun blocksReference(id: String): Boolean = ids.any {
        id.startsWith("$it://") && Catalogue.byId(it) == null
    }
}
