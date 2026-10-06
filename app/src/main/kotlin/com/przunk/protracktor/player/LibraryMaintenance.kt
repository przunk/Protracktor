// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.Modland
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Deletes downloaded copies in the existing scope; playback and local user data remain outside it. */
internal class LibraryMaintenance(
    private val scope: CoroutineScope,
    private val backgroundWork: CoroutineDispatcher,
    private val store: Store,
    private val files: Files,
    private val state: LibraryMaintenanceState,
    private val native: Native,
    private val ui: Ui,
    private val text: Text,
) {
    interface Store {
        suspend fun clearIndex(catalogueId: String)
        suspend fun clearFavourites()
        suspend fun clearSongLengths()
        suspend fun clearTrackMetadata()
        suspend fun clearSongDbLengths()
    }

    interface Files {
        fun cacheBytes(): Long
        fun clearCache()
        fun deleteArchive(catalogueId: String): Boolean
        fun deleteSc68(): Boolean
        fun deleteUade(): Boolean
        fun deleteAdoptedUade(): Boolean
    }

    interface Native {
        fun installedVersion(): String
        fun configureUade(version: String)
    }

    interface Ui {
        fun beginDownload(key: String, label: String): Boolean
        fun endDownload(key: String)
        fun refreshOnPhone()
        fun refreshCatalogues()
        fun leaveFavourites()
    }

    interface Text {
        fun deleting(): String
        fun message(notice: Notice): Message
    }

    sealed interface Notice {
        data class Freed(val bytes: Long) : Notice
        data object IndexDeleted : Notice
        data object MetadataDeleted : Notice
        data class DeleteFailed(val name: String) : Notice
        data object MetadataDeleteFailed : Notice
        data object ReplaysDeleteFailed : Notice
    }

    /**
     * Everything here deletes something the app can fetch again.
     *
     * That is the rule, not a coincidence: the storage screen may only offer to delete what is a
     * copy. The user's playlists, their granted folders and their history are not on it, and the
     * one thing that looks like a copy but is not — a local library index, which is a record of
     * files only that phone has seen — is only ever *rebuilt*, never dropped.
     *
     * Each of these ends in `refreshCatalogues()` so the numbers the user is looking at become
     * true immediately. A delete that leaves the old size on screen reads as a delete that failed.
     */
    fun clearFetchedCache() {
        scope.launch {
            val freed = withContext(backgroundWork) {
                val before = files.cacheBytes()
                files.clearCache()
                before - files.cacheBytes()
            }
            // ASMA's tunes are still here; what went was the cache.
            ui.refreshOnPhone()
            state.cacheCleared(freed)
            ui.refreshCatalogues()
        }
    }

    /**
     * Throws away one catalogue's index.
     *
     * **Playback is deliberately left alone.** A queued catalogue track carries its own id, and
     * the URL to fetch it is derived
     * from that id by `Catalogue.urlFor` rather than read from `catalogue_tracks` — so a track that
     * is playing keeps playing, and one further down the playlist still plays when it is reached.
     * What actually degrades is the format label (`catalogueFormatOf` finds nothing and returns
     * null) and "more from this author". Both are cosmetic and both come back with the index.
     *
     * **An archive catalogue's index and its archive are one thing**, so this deletes both. ASMA
     * publishes a single zip that `indexCatalogue` stores whole and parses in place; removing the
     * removing the rows and keeping the zip would leave a catalogue reporting its full track
     * count, browsing normally and playing nothing.
     */
    fun deleteCatalogueIndex(catalogueId: String) {
        // **The row answers at once** (`docs/STATUS.md` C76). Deleting Modland is half a million
        // rows and their indexes -- 5.1 s on a desktop, measured, and 10 to 15 on a phone -- and
        // for all of it the confirmed delete looked like a press that had done nothing. So the
        // screen is told first: the catalogue shows as not indexed, its storage row goes, and its
        // Browse row turns into "deleting…", which also refuses a new download until this is done.
        if (!ui.beginDownload(catalogueId, text.deleting())) return
        // **Said now, with the row going, not when the database has finished** (C77). Several
        // deletes in a row queue on the one database and used to finish -- and speak -- almost
        // together, seconds after their rows had gone. The size is known before anything is
        // deleted, so the message can be the true one. Only a failure speaks again later.
        val displayName = state.catalogueName(catalogueId)
        val archived = state.archiveBytes(catalogueId)
        state.say(if (archived > 0L) Notice.Freed(archived) else Notice.IndexDeleted)
        state.startDeleting(catalogueId)
        scope.launch {
            try {
                runCatching { store.clearIndex(catalogueId) }.onFailure {
                    state.say(Notice.DeleteFailed(displayName))
                    return@launch
                }
                if (catalogueId == Modland.id) dropFavourites()
                withContext(backgroundWork) {
                    if (Catalogue.byId(catalogueId)?.isArchive == true) files.deleteArchive(catalogueId)
                }
                // Rows of that archive on screen are no longer on the phone.
                ui.refreshOnPhone()
                state.dropCatalogueScope(catalogueId)
                ui.refreshCatalogues()
            } finally {
                withContext(NonCancellable) {
                    ui.endDownload(catalogueId)
                    state.finishDeleting(catalogueId)
                }
            }
        }
    }

    /**
     * Modland's favourites go with Modland's index: they are Modland paths, meaningless without it.
     * Random leaves that scope, since a scope whose contents are gone would draw nothing -- and the
     * chip that set it is then disabled, leaving no way back but a restart.
     */
    private suspend fun dropFavourites() {
        store.clearFavourites()
        if (state.inFavourites) ui.leaveFavourites()
    }

    /**
     * Throws away every downloaded fact about files: HVSC's lengths, songdb's credits and lengths.
     *
     * **What this phone learnt by playing stays** (`learned_lengths`, A50): it was not downloaded,
     * cannot be downloaded again, and costs next to nothing. The button deletes what it fetched.
     *
     * The row goes and the message comes at the press; the tables empty behind them (C77).
     */
    fun deleteSongMetadata() {
        state.say(Notice.MetadataDeleted)
        state.clearMetadata()
        scope.launch {
            runCatching {
                store.clearSongLengths()
                store.clearTrackMetadata()
                store.clearSongDbLengths()
            }.onFailure {
                state.say(Notice.MetadataDeleteFailed)
            }
            ui.refreshCatalogues()
        }
    }

    /**
     * Throws both sets of replay routines away: `.sc68` goes back to mostly silent and the Amiga
     * custom formats stop playing; SNDH does not care. One delete, as there is one download.
     *
     * The size is known before anything goes, so the true message is said at the press (C77).
     */
    fun deleteReplayRoutines() {
        state.say(Notice.Freed(state.replayBytes()))
        state.clearReplays()
        scope.launch {
            val removed = withContext(backgroundWork) {
                val sc68 = files.deleteSc68()
                val uade = files.deleteUade()
                // The copies UADE actually reads go too, or it would go on playing from them with
                // nothing on the storage screen saying they were there.
                files.deleteAdoptedUade()
                sc68 && uade
            }
            if (!removed) {
                state.say(Notice.ReplaysDeleteFailed)
            }
            val version = native.installedVersion()
            withContext(backgroundWork) { native.configureUade(version) }
            ui.refreshCatalogues()
        }
    }
}
