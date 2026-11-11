// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import com.przunk.protracktor.data.CatalogueStore
import com.przunk.protracktor.data.HistoryStore
import com.przunk.protracktor.data.LibraryStore
import com.przunk.protracktor.data.PlayedTrack
import com.przunk.protracktor.data.SavedPlaylist
import com.przunk.protracktor.data.SearchTerms
import com.przunk.protracktor.player.CatalogueTracks
import com.przunk.protracktor.player.TrackRef

/** A tune the alarm editor's *Add music* offers, with where it is from in words. */
internal data class AlarmCandidate(val ref: TrackRef, val detail: String)

/**
 * Where *Add music* finds tunes (B62): the saved playlists, History, and a typed search over both and
 * the catalogues held on the phone. Read from the player's own stores, which the alarm package may
 * use; nothing here is on the wake-up path, which needs only prepared excerpts.
 */
internal interface AlarmMusicSources {
    suspend fun playlists(): List<SavedPlaylist>
    suspend fun tracksIn(playlist: Long): List<AlarmCandidate>
    suspend fun history(): List<AlarmCandidate>
    suspend fun search(query: String): List<AlarmCandidate>
}

internal class StoredAlarmMusicSources(context: Context) : AlarmMusicSources {
    private val library = LibraryStore(context)
    private val played = HistoryStore(context)
    private val catalogues = CatalogueStore(context)

    override suspend fun playlists() = library.playlists()
    override suspend fun tracksIn(playlist: Long) = library.tracksIn(playlist).map(AlarmCandidates::of)
    override suspend fun history() = played.page(0, AlarmCandidates.LIMIT).map { AlarmCandidates.of(it.ref()) }
    override suspend fun search(query: String): List<AlarmCandidate> {
        if (SearchTerms.of(query).isEmpty()) return emptyList()
        val saved = library.playlists().flatMap { library.tracksIn(it.id) }
        val held = catalogues.summaries().filter { it.indexed && !it.isOnlineOnly }.map { it.id }.toSet()
        val found = if (held.isEmpty()) emptyList()
            else catalogues.search(query, held, AlarmCandidates.LIMIT).map(CatalogueTracks::referenceOf)
        return AlarmCandidates.search(saved, found, query)
    }
    private fun PlayedTrack.ref() = TrackRef(trackId, title, subtitle, sizeBytes, fileName = fileName, author = author)
}

/** The rules of the picker's lists, apart from Android. */
internal object AlarmCandidates {
    /** As many as a list offers: a picker is for choosing a few, not for paging through thousands. */
    const val LIMIT = 200

    fun of(ref: TrackRef) = AlarmCandidate(ref, listOf(ref.author, ref.subtitle).filter { it.isNotBlank() }.distinct()
        .joinToString(" · "))

    /**
     * Saved tunes that match, then catalogue tunes that do, each once: every word, in the title,
     * the file's name or the author -- the rule the player's own search keeps.
     */
    fun search(saved: List<TrackRef>, catalogue: List<TrackRef>, query: String): List<AlarmCandidate> {
        if (SearchTerms.of(query).isEmpty()) return emptyList()
        val mine = saved.filter { SearchTerms.matchesAny(query, it.title, it.fileName, it.author) }
        return (mine + catalogue).distinctBy { it.id }.take(LIMIT).map(::of)
    }
}
