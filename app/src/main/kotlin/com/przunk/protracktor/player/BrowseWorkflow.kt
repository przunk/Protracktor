// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.net.ModArchive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Coordinates Browse interactions; read services and playback retain their existing owners. */
internal class BrowseWorkflow(
    private val scope: CoroutineScope,
    private val browse: MutableStateFlow<BrowseState>,
    private val search: LibrarySearch,
    private val actions: Actions,
    private val onSubmitted: (String) -> Unit = {},
    private val onSearchFailed: (Throwable) -> Unit,
    private val onLiveResult: (ModArchive.Outcome) -> Unit,
) {
    private class SearchOwner(
        val generation: Long,
        val request: LibrarySearch.Request,
        val domain: BrowseDomain,
        var loadingPublished: Boolean = false,
    ) {
        fun matches(state: BrowseState) = state.domain == domain &&
            state.query == request.query && state.searchScope == request.scope
    }

    private var searchGeneration = 0L
    private var searchOwner: SearchOwner? = null
    private var searchJob: Job? = null

    /** Input changes/resubmissions retire intents even when the same words and scope return. */
    val searchRevision: Long get() = searchGeneration

    private fun owns(owner: SearchOwner, state: BrowseState) =
        owner.generation == searchGeneration && searchOwner === owner && owner.matches(state)

    /** Retire ownership before cancellation: a read may ignore cancellation or finish inline. */
    private fun retireSearch(): Long {
        val generation = ++searchGeneration
        val previous = searchOwner
        val job = searchJob
        searchOwner = null
        searchJob = null
        if (previous?.loadingPublished == true) browse.update {
            if (generation == searchGeneration && previous.matches(it)) it.copy(loading = false) else it
        }
        job?.cancel()
        return generation
    }

    fun cancelSearch() { retireSearch() }

    interface Actions {
        fun refreshFolders()
        fun refreshCatalogues()
        fun refreshPlatformCounts()
        fun refreshFavouriteCount()
        fun openHistory()
        fun openSavedPlaces()
        fun openFormat(format: String)
        fun openCatalogue(summary: CatalogueSummary)
        fun localLevel(state: BrowseState, at: String): BrowseState
    }

    fun openDomain(domain: BrowseDomain) {
        cancelSearch()
        browse.update { BrowseNavigation.enteringDomain(it, domain) }
        when (domain) {
            BrowseDomain.LOCAL -> actions.refreshFolders()
            BrowseDomain.ONLINE -> actions.refreshCatalogues()
            BrowseDomain.SEARCH -> {
                actions.refreshCatalogues()
                actions.refreshPlatformCounts()
            }
            BrowseDomain.HISTORY -> actions.openHistory()
            BrowseDomain.SAVED -> actions.openSavedPlaces()
            // The root has a consumer too: Random's scope sheet is opened from here and draws the
            // same chips. Without the counts every platform reads as "nothing indexed" and the
            // whole sheet is disabled.
            //
            // Only when they are absent. This is a grouped scan of every catalogue row, the root is
            // returned to on every step back out of a folder, and the answer only changes when an
            // index does.
            BrowseDomain.ROOT -> {
                if (browse.value.platformCounts.isEmpty()) actions.refreshPlatformCounts()
                // And the favourite count, for the same sheet and the same reason: it is set by
                // `refreshCatalogues`, which the root does not call, so without this the Favourites
                // chip reads as "not downloaded" for anyone who has not visited the catalogue list
                // this session. Unguarded, because unlike the
                // platform counts it is one `COUNT` over a thousand rows rather than a grouped scan
                // of half a million, and because zero is a real answer here rather than "not asked
                // yet" -- a guard on emptiness could never tell the two apart.
                actions.refreshFavouriteCount()
            }
        }
    }

    /** One step back up the browse hierarchy. Returns false when already at the top. */
    fun browseBack(): Boolean {
        cancelSearch()
        val current = browse.value

        if (current.returnToSavedPlaces) {
            openDomain(BrowseDomain.SAVED)
            return true
        }

        // A jump is one step, not a descent. "More from this author" puts you three levels deep
        // without your having passed through any of them, so back should return you to where you
        // actually were -- the playlist -- rather than making you climb out of a hierarchy you
        // never climbed into, one press per level.
        if (current.arrivedByJump) {
            browse.update { it.copy(arrivedByJump = false) }
            return false
        }

        // Inside search there is one level, and it is the scope. The filter row and the results are
        // the same screen -- filters above, list below -- so there is nothing to navigate between;
        // the only thing that can be undone is having narrowed the search. Back widens it, which is
        // visible in the field's label going from `Amiga` to `Everywhere`, and a second press
        // leaves.
        //
        // What back must never do here is destroy the typed query or the results. The rest of this
        // method clears `tracks` on the way out of a domain, which is right for a folder you walked
        // out of and wrong for a search you may be coming straight back to. See `docs/WISHLIST.md`
        // B23: back always meaning "leave" is only safe because leaving costs nothing.
        if (current.domain == BrowseDomain.SEARCH && current.searchScope != SearchScope.Everywhere) {
            browse.update { it.copy(searchScope = SearchScope.Everywhere).withoutStaleResults() }
            return true
        }

        val next = when {
            current.openAuthor != null -> current.copy(openAuthor = null, tracks = emptyList())
                .also { actions.openFormat(current.openFormat.orEmpty()) }
            current.openFormat != null -> current.copy(openFormat = null, tracks = emptyList())
                .also { current.openCatalogue?.let(actions::openCatalogue) }
            current.openCatalogue != null -> current.copy(openCatalogue = null, groups = emptyList())
            // Up the folder's own tree first, a level at a time, and out of it only from its top.
            current.openFolder != null && LocalTree.parent(current.folderAt, current.folderRoot) != null ->
                actions.localLevel(current, LocalTree.parent(current.folderAt, current.folderRoot)!!)
            current.openFolder != null -> current.copy(openFolder = null, tracks = emptyList(), folderAll = emptyList(), subfolders = emptyList())
            // Search keeps its results and its query; every other domain drops them.
            current.domain == BrowseDomain.SEARCH -> current.copy(domain = BrowseDomain.ROOT)
            current.domain != BrowseDomain.ROOT -> current.copy(domain = BrowseDomain.ROOT, tracks = emptyList())
            else -> return false
        }
        browse.update { next }
        return true
    }

    fun setQuery(query: String) {
        if (query == browse.value.query) return
        cancelSearch()
        browse.update { it.copy(query = query) }
    }

    fun searchFor(query: String) {
        setQuery(query)
        runSearch()
    }

    /** Ticks one catalogue inside an online scope. Only meaningful while that is the scope. */
    fun toggleSearchCatalogue(id: String) {
        if (browse.value.searchScope !is SearchScope.Online) return
        cancelSearch()
        browse.update {
            val scope = it.searchScope as? SearchScope.Online ?: return@update it
            val ids = if (id in scope.catalogueIds) scope.catalogueIds - id else scope.catalogueIds + id
            it.copy(searchScope = SearchScope.Online(ids))
        }
    }

    /** Ticks one platform. Same shape, and the same rule: empty means all of them. */
    fun toggleSearchPlatform(id: String) {
        if (browse.value.searchScope !is SearchScope.ByPlatform) return
        cancelSearch()
        browse.update {
            val scope = it.searchScope as? SearchScope.ByPlatform ?: return@update it
            val ids = if (id in scope.platformIds) scope.platformIds - id else scope.platformIds + id
            it.copy(searchScope = SearchScope.ByPlatform(ids))
        }
    }

    /**
     * Results belong to the *kind* of search that produced them.
     *
     * Left on screen after switching between local, online and by-platform they are a lie the app
     * tells with a straight face: switching to `Online` and seeing local files sitting there makes
     * them look like an answer. The label changing on its own makes it worse rather than better --
     * two things on one screen disagreeing about what you are looking at.
     *
     * **Ticking one more catalogue or platform does not do this.** It
     * widens the same question rather than asking a different one, so throwing the answer away
     * because Modland has been joined by ASMA reads as the app losing your place. The results are
     * incomplete until you search again, which is the ordinary state of a filter you are still
     * adjusting.
     *
     * This is the counterpart to back keeping results, not a contradiction of it. What must survive
     * is **leaving and returning**; what must not is a list produced by a different kind of search.
     */
    private fun BrowseState.withoutStaleResults() =
        copy(tracks = emptyList(), searchMatches = 0, searched = false, liveSearchNeededQuery = false)

    /**
     * Runs the search the scope describes.
     *
     * **An empty query is a question, not a mistake.** `%%` matches every row, so "everything on
     * the Amiga" is a scope with nothing typed, which is the natural way to ask it and must not
     * return silently. The per-source cap and the count beside it are built for exactly this shape
     * of answer.
     */
    fun runSearch() {
        val current = browse.value
        val generation = retireSearch()
        if (generation != searchGeneration) return
        onSubmitted(current.query)
        val request = LibrarySearch.Request(
            current.query, current.searchScope, current.catalogues.filter { it.indexed }.map { it.id }.toSet(),
        )
        val owner = SearchOwner(generation, request, current.domain)
        if (generation != searchGeneration || !owner.matches(browse.value)) return
        searchOwner = owner
        // Assign before starting: Main.immediate may complete or reenter during start().
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (!owns(owner, browse.value)) return@launch
                owner.loadingPublished = true
                browse.update {
                    if (owns(owner, it)) it.copy(loading = true, tracks = emptyList(), searched = false,
                        searchMatches = 0, liveSearchNeededQuery = false) else it
                }
                if (!owns(owner, browse.value)) return@launch
                val result = search.run(request) { if (owns(owner, browse.value)) onLiveResult(it) }
                browse.update {
                    if (owns(owner, it)) it.copy(tracks = result.tracks, searchMatches = result.matches,
                        searched = true, liveSearchNeededQuery = result.liveSearchNeededQuery, loading = false)
                    else it
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (owns(owner, browse.value)) {
                    browse.update { if (owns(owner, it)) it.copy(loading = false) else it }
                    if (owns(owner, browse.value)) onSearchFailed(failure)
                }
            } finally {
                if (generation == searchGeneration) {
                    browse.update { if (owns(owner, it)) it.copy(loading = false) else it }
                    if (generation == searchGeneration) {
                        searchOwner = null
                        searchJob = null
                    }
                }
            }
        }
        searchJob = job
        job.start()
    }

    /**
     * Changes what the search covers.
     *
     * Does not re-run the search. Choosing a scope is setting up a question, not asking it -- and
     * re-running on every tick of a platform chip would fire a query per tap while somebody picks
     * three of them.
     */
    fun setSearchScope(scope: SearchScope) {
        cancelSearch()
        browse.update { it.copy(searchScope = scope).withoutStaleResults() }
    }
}
