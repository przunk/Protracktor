// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.net

/** Preserve the parallel AMP trial's data without enabling its catalogue in QUEUE. */
internal object CatalogueRetention {
    val ids = setOf("amp")
    const val visibility = " AND catalogue_id <> 'amp'"
    fun blocksReference(id: String): Boolean = ids.any { id.startsWith("$it://") }
}
