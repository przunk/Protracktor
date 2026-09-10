// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * Moving around Browse, as state transitions and nothing else.
 *
 * Extracted so it can be tested. `PlaybackController` takes a `Context` and owns a database, so it
 * cannot be built on the JVM, and there is no emulator in this workshop -- which is how
 * `docs/STATUS.md` C6 survived: a rule nobody could write a test for. `PlayQueue` and `SchemaSql`
 * were pulled out of their neighbours for the same reason and this follows them.
 */
object BrowseNavigation {

    /**
     * Entering a domain from the top.
     *
     * **Everything about where you were goes.** C6 was exactly this not happening: `openCatalogue`,
     * `openFormat` and `openAuthor` survived, `tracks` and `groups` did not, and the Online screen
     * then rendered the deepest level still set with an emptied list underneath it -- a folder
     * view with nothing in it, or nothing at all.
     *
     * Note what this does **not** do: it says nothing about scroll position. Browse remembers that
     * per level for the life of a session (`docs/BACKLOG.md` A20), and the two rules are opposites
     * that must both hold -- descend and return keeps your place, leaving and re-entering does not.
     */
    fun enteringDomain(current: BrowseState, domain: BrowseDomain): BrowseState = current.copy(
        domain = domain,
        openCatalogue = null,
        openFormat = null,
        openAuthor = null,
        openFolder = null,
        groups = emptyList(),
        tracks = emptyList(),
        history = emptyList(),
        folderUnscanned = false,
        folderStale = false,
        // A jump is a place you were put, not one you walked to. Choosing a domain by hand ends it.
        arrivedByJump = false,
    )
}
