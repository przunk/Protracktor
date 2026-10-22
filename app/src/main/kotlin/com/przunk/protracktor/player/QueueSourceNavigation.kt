// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

enum class QueueSourceView { PLAYLIST, RANDOM, BROWSE }
enum class SourcePlaylistEdits { KEEP, SAVE, DISCARD }

/** Navigation follows the context, never the current manual request or the visible editor. */
object QueueSourceNavigation {
    fun hasView(source: QueueSource): Boolean = when (source) {
        QueueSource.None, QueueSource.External -> false
        is QueueSource.Playlist, is QueueSource.Random, is QueueSource.Collection -> true
    }

    fun destination(source: QueueSource, playlistIds: Set<Long>, randomSession: Long,
        snapshot: BrowseState?): QueueSourceView? = when (source) {
        QueueSource.None, QueueSource.External -> null
        is QueueSource.Playlist -> QueueSourceView.PLAYLIST.takeIf { source.id in playlistIds }
        is QueueSource.Random -> QueueSourceView.RANDOM.takeIf { source.session > 0 && source.session == randomSession }
        is QueueSource.Collection -> QueueSourceView.BROWSE.takeIf {
            snapshot != null && SessionSource.of(snapshot) == source.origin
        }
    }

    /** A route snapshot must not roll back downloads, storage, indexes or current cache knowledge. */
    fun restore(snapshot: BrowseState, current: BrowseState): BrowseState = current.copy(
        domain = snapshot.domain, navigationVisit = current.navigationVisit + 1, loading = false,
        openFolder = snapshot.openFolder, folderAll = snapshot.folderAll, folderRoot = snapshot.folderRoot,
        folderAt = snapshot.folderAt, subfolders = snapshot.subfolders,
        folderUnscanned = snapshot.folderUnscanned, folderStale = snapshot.folderStale,
        openCatalogue = snapshot.openCatalogue, openFormat = snapshot.openFormat, openAuthor = snapshot.openAuthor,
        groups = snapshot.groups, tracks = snapshot.tracks,
        query = snapshot.query, searchScope = snapshot.searchScope, searched = snapshot.searched,
        liveSearchNeededQuery = snapshot.liveSearchNeededQuery, searchMatches = snapshot.searchMatches,
        historyTotal = snapshot.historyTotal, historyPage = snapshot.historyPage, historyQuery = snapshot.historyQuery,
        historyVisit = if (snapshot.domain == BrowseDomain.HISTORY) current.historyVisit + 1 else current.historyVisit,
        historyReadFailed = snapshot.historyReadFailed,
        // Return to the source hierarchy, not an unrelated author jump or Saved Places detour.
        arrivedByJump = false, returnToSavedPlaces = false, cachedHere = emptySet(), aheadFetching = emptySet(),
    )
}
