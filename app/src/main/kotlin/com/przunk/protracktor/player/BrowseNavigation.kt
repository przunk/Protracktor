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
     * Scroll positions remain UI-owned: returning to a catalogue level retains its place (A20).
     * An explicit History entry starts a new scroll visit; paging does not (C107).
     */
    fun enteringDomain(current: BrowseState, domain: BrowseDomain): BrowseState = current.copy(
        domain = domain,
        navigationVisit = current.navigationVisit + 1,
        loading = false,
        openCatalogue = null,
        openFormat = null,
        openAuthor = null,
        openFolder = null,
        groups = emptyList(),
        tracks = emptyList(),
        historyTotal = 0,
        historyPage = 0,
        historyVisit = if (domain == BrowseDomain.HISTORY) current.historyVisit + 1 else current.historyVisit,
        historyQuery = if (domain == BrowseDomain.HISTORY) "" else current.historyQuery,
        historyReadFailed = false,
        folderUnscanned = false,
        folderStale = false,
        // A jump is a place you were put, not one you walked to. Choosing a domain by hand ends it.
        arrivedByJump = false,
        returnToSavedPlaces = false,
        // **The words go with the results they found.** This already empties `tracks`, and a query
        // left behind greets the next visit with the last search written in the box and nothing
        // under it -- which reads as a search that returned nothing.
        //
        // Only on entering a domain by hand. Walking back out of a folder into results does not
        // come through here, so a search survives being walked away from and returned to.
        query = "",
    )

    /**
     * What a jump to an author's folder should return to, or null when there is nothing to return to.
     *
     * **A search is a place, like the dice** (the owner, 2026-09-24: "to ma działać identycznie").
     * "More from this author" from a list of results used to throw the results away: the folder
     * opened, and Back left Browse. Now the search -- its words, its scope and what it found -- is
     * kept, and Back from the folder comes back to it.
     *
     * **And a level of Browse, when the jump was made from it** (2026-11-09): Browse's Search turns
     * any level into a list of results, so "more from this author" from tunes found below Modland's
     * Protracker comes back to them, the words still in the field. A jump from the playlist or Now
     * Playing has nowhere in Browse to go back to: Back leaves, as before.
     */
    fun searchToReturnTo(current: BrowseState, fromBrowse: Boolean = false): BrowseState? = current.takeIf {
        !it.arrivedByJump && (it.domain == BrowseDomain.SEARCH ||
            fromBrowse && it.domain in setOf(BrowseDomain.ONLINE, BrowseDomain.LOCAL) && SessionSource.placeOf(it) != null)
    }

    /** The search a digression came from, as it is shown again: no longer a jump, and not loading. */
    fun returningTo(search: BrowseState, current: BrowseState = search): BrowseState = search.copy(
        arrivedByJump = false, loading = false, navigationVisit = current.navigationVisit + 1,
    )
}
