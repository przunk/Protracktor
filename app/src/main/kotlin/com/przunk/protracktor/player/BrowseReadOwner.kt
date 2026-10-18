// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/** A read belongs to one visit, not just to a route that can be left and entered again. */
internal class BrowseReadOwner private constructor(
    private val visit: Long,
    private val domain: BrowseDomain,
    private val catalogue: String?,
    private val format: String?,
    private val author: String?,
    private val folder: String?,
) {
    fun matches(state: BrowseState): Boolean = visit == state.navigationVisit &&
        domain == state.domain && catalogue == state.openCatalogue?.id &&
        format == state.openFormat && author == state.openAuthor && folder == state.openFolder?.uri

    fun publish(browse: MutableStateFlow<BrowseState>, change: (BrowseState) -> BrowseState) {
        browse.update { if (matches(it)) change(it) else it }
    }

    companion object {
        /** Observe an existing visit without retiring a read that may still complete there. */
        fun capture(state: BrowseState): BrowseReadOwner = BrowseReadOwner(
            state.navigationVisit, state.domain, state.openCatalogue?.id,
            state.openFormat, state.openAuthor, state.openFolder?.uri,
        )

        fun begin(browse: MutableStateFlow<BrowseState>, change: (BrowseState) -> BrowseState): BrowseReadOwner {
            val next = browse.updateAndGet { change(it).copy(navigationVisit = it.navigationVisit + 1) }
            return capture(next)
        }
    }
}
