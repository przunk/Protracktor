// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.net.Modland
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Checks cached rows and fetches eligible folders without owning Browse state or disk storage. */
internal class BrowseCache(
    private val files: Files,
    private val networkIsMetered: () -> Boolean,
    private val progress: Progress,
) {
    interface Files {
        fun isCached(url: String): Boolean
        fun hasArchive(catalogueId: String): Boolean
        suspend fun fetch(url: String): Boolean
    }

    interface Progress {
        fun clear()
        fun started(url: String)
        fun finished(url: String, cached: Boolean)
    }

    /** The folder fetching ahead is about: a Modland author's folder with its rows, or nothing. */
    data class Folder(val tracks: List<TrackRef>)

    companion object {
        fun folderOf(browse: BrowseState): Folder? {
            // Only a folder, only when its rows are in, and only Modland: ASMA is whole on the phone, an
            // UnExoticA folder is one archive fetched when its first tune plays, and The Mod Archive has
            // no folders (`docs/PLAN_ROUND_13.md` A55).
            if (browse.openAuthor == null || browse.loading || browse.tracks.isEmpty()) return null
            if (browse.openCatalogue?.id != Modland.id) return null
            return Folder(browse.tracks)
        }
    }

    /**
     * Which of [ids] are on this phone, each asked its archive's way ([OnPhone]). An archive
     * downloaded whole is looked at once, not once per row.
     */
    suspend fun onPhoneOf(ids: List<String>): Set<String> = withContext(Dispatchers.IO) {
        val archives = mutableMapOf<String, Boolean>()
        ids.filterTo(HashSet()) { id ->
            when (val check = OnPhone.checkFor(id)) {
                is OnPhone.Check.Cached -> files.isCached(check.url)
                is OnPhone.Check.Archive -> archives.getOrPut(check.catalogueId) {
                    files.hasArchive(check.catalogueId)
                }
                OnPhone.Check.Local -> true
                OnPhone.Check.Never -> false
            }
        }
    }

    /**
     * Marks what the open folder already holds, then, if the setting and the network allow it,
     * fetches the rest [FolderPrefetch.PARALLEL] at a time. Cancelled by leaving the folder.
     */
    suspend fun fetchFolderAhead(key: Folder?, mode: CacheAhead) {
        if (key == null) {
            // The marks are not this function's to clear: they follow whatever Browse lists.
            progress.clear()
            return
        }
        val cached = onPhoneOf(key.tracks.map { it.id })
        progress.clear()
        if (!mode.allows(metered = networkIsMetered())) return

        val wanted = FolderPrefetch.plan(
            key.tracks.map { FolderPrefetch.Candidate(it.id, it.sizeBytes) },
            cached,
        )
        try {
            FolderPrefetch.run(
                urls = wanted,
                fetch = files::fetch,
                // Asked before each start, so walking off Wi-Fi stops what has not begun.
                mayContinue = { mode.allows(metered = networkIsMetered()) },
                onStart = progress::started,
                onDone = progress::finished,
            )
        } finally {
            progress.clear()
        }
    }
}
