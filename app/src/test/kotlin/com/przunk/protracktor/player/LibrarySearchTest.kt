// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.net.ModArchive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {
    private fun track(id: String, fileName: String = "$id.mod", title: String = "song") =
        TrackRef(id = id, title = title, fileName = fileName)

    private data class Query(val text: String, val ids: Set<String>, val limit: Int, val platforms: Set<String>)

    private class Sources : LibrarySearch.Sources {
        var local = emptyList<TrackRef>()
        var playlists = emptyList<TrackRef>()
        var online = emptyList<TrackRef>()
        var live: ModArchive.Outcome = ModArchive.Outcome.Found(emptyList())
        var localCount = 0
        var onlineCount = 0
        val calls = mutableListOf<String>()
        val localQueries = mutableListOf<Query>()
        val onlineQueries = mutableListOf<Query>()
        val countedOnline = mutableListOf<Query>()
        val outcomes = mutableListOf<ModArchive.Outcome>()

        override suspend fun searchLocal(query: String, limit: Int, platforms: Set<String>): List<TrackRef> {
            calls += "local"
            localQueries += Query(query, emptySet(), limit, platforms)
            return local
        }

        override suspend fun playlistTracks(): List<TrackRef> {
            calls += "playlists"
            return playlists
        }

        override suspend fun searchCatalogues(query: String, ids: Set<String>, limit: Int, platforms: Set<String>): List<TrackRef> {
            calls += "catalogues"
            onlineQueries += Query(query, ids, limit, platforms)
            return online
        }

        override suspend fun searchLive(query: String): ModArchive.Outcome {
            calls += "live:$query"
            return live
        }

        override suspend fun countLocal(query: String): Int {
            calls += "count-local:$query"
            return localCount
        }

        override suspend fun countCatalogues(query: String, ids: Set<String>, platforms: Set<String>): Int {
            calls += "count-catalogues:$query"
            countedOnline += Query(query, ids, 0, platforms)
            return onlineCount
        }

        suspend fun search(
            scope: SearchScope = SearchScope.Everywhere,
            query: String = "song",
            indexed: Set<String> = setOf("modland", "asma", ModArchive.id),
        ): LibrarySearch.Result = LibrarySearch(this).run(LibrarySearch.Request(query, scope, indexed)) {
            calls += "live-result"
            outcomes += it
        }
    }

    @Test
    fun `local scope reads the index before playlists and never asks the network`() = runBlocking {
        val sources = Sources().apply {
            local = listOf(track("indexed", title = "Index song"))
            playlists = listOf(track("playlist"), track("miss", title = "different"))
        }
        val result = sources.search(SearchScope.Local)
        assertEquals(listOf("local", "playlists", "live-result"), sources.calls)
        assertEquals(listOf("indexed", "playlist"), result.tracks.map { it.id })
        assertEquals(Query("song", emptySet(), SearchResults.PER_SOURCE_LIMIT, emptySet()), sources.localQueries.single())
        assertEquals(0, result.matches)
        assertFalse(result.liveSearchNeededQuery)
    }

    @Test
    fun `an explicit online choice is used even when another archive is indexed`() = runBlocking {
        val sources = Sources()
        sources.search(SearchScope.Online(setOf("asma")), indexed = setOf("modland", ModArchive.id))
        assertEquals(listOf("catalogues", "live-result"), sources.calls)
        assertEquals(Query("song", setOf("asma"), SearchResults.PER_SOURCE_LIMIT, emptySet()), sources.onlineQueries.single())
    }

    @Test
    fun `an empty online choice searches every indexed archive with live search separate`() = runBlocking {
        val sources = Sources()
        sources.search(SearchScope.Online())
        assertEquals(listOf("catalogues", "live:song", "live-result"), sources.calls)
        assertEquals(setOf("modland", "asma"), sources.onlineQueries.single().ids)
    }

    @Test
    fun `a blank query never contacts live search and explains a live-only scope`() = runBlocking {
        val onlyLive = Sources()
        val result = onlyLive.search(SearchScope.Online(setOf(ModArchive.id)), query = "  ")
        assertEquals(listOf("live-result"), onlyLive.calls)
        assertTrue(result.tracks.isEmpty())
        assertTrue(result.liveSearchNeededQuery)
        for (scope in listOf(SearchScope.Everywhere, SearchScope.Local, SearchScope.Online())) {
            val sources = Sources()
            assertFalse(sources.search(scope, query = "").liveSearchNeededQuery)
            assertFalse(sources.calls.any { it.startsWith("live:") })
        }
    }

    @Test
    fun `platform scope narrows SQL inputs and filters both playlist and live rows`() = runBlocking {
        val sources = Sources().apply {
            playlists = listOf(track("local-amiga"), track("local-atari", "tune.sap"))
            live = ModArchive.Outcome.Found(listOf(track("live-amiga"), track("live-atari", "tune.sap")))
        }
        val result = sources.search(SearchScope.ByPlatform(setOf("amiga")))
        assertEquals(listOf("local-amiga", "live-amiga"), result.tracks.map { it.id })
        assertEquals(setOf("amiga"), sources.localQueries.single().platforms)
        assertEquals(setOf("amiga"), sources.onlineQueries.single().platforms)
    }

    @Test
    fun `no selected platforms means all platforms rather than an empty result`() = runBlocking {
        val sources = Sources().apply {
            playlists = listOf(track("amiga"), track("atari", "tune.sap"))
        }
        val result = sources.search(SearchScope.ByPlatform())
        assertEquals(listOf("amiga", "atari"), result.tracks.map { it.id })
        assertTrue(sources.localQueries.single().platforms.isEmpty())
        assertTrue(sources.onlineQueries.single().platforms.isEmpty())
    }

    @Test
    fun `copies have source precedence while live-only rows stay ahead of catalogue bulk`() = runBlocking {
        val indexCopy = track("duplicate", title = "Index song")
        val sources = Sources().apply {
            local = listOf(indexCopy)
            playlists = listOf(track("duplicate"), track("playlist"))
            online = listOf(track("duplicate"), track("catalogue"))
            live = ModArchive.Outcome.Found(listOf(track("duplicate"), track("live")))
        }
        val result = sources.search()
        assertEquals(listOf("duplicate", "playlist", "live", "catalogue"), result.tracks.map { it.id })
        assertSame(indexCopy, result.tracks.first())
    }

    @Test
    fun `reaching an index cap asks for totals after publishing the live outcome`() = runBlocking {
        val sources = Sources().apply {
            local = (0 until SearchResults.PER_SOURCE_LIMIT).map { track("local$it") }
            localCount = 3_000
            onlineCount = 8_000
        }
        val result = sources.search(SearchScope.ByPlatform(setOf("amiga")))
        assertEquals(11_000, result.matches)
        assertEquals(listOf("live-result", "count-local:song", "count-catalogues:song"), sources.calls.takeLast(3))
        assertEquals(Query("song", setOf("modland", "asma"), 0, setOf("amiga")), sources.countedOnline.single())
    }

    @Test
    fun `a catalogue cap counts its chosen archives without querying local storage`() = runBlocking {
        val sources = Sources().apply {
            online = (0 until SearchResults.PER_SOURCE_LIMIT).map { track("online$it") }
            onlineCount = 5_000
        }
        val result = sources.search(SearchScope.Online(setOf("asma")))
        assertEquals(5_000, result.matches)
        assertEquals(listOf("catalogues", "live-result", "count-catalogues:song"), sources.calls)
        assertEquals(setOf("asma"), sources.countedOnline.single().ids)
    }

    @Test
    fun `playlist results are capped without adding a total query`() = runBlocking {
        val sources = Sources().apply {
            playlists = (0..SearchResults.PER_SOURCE_LIMIT).map { track("playlist$it") }
        }
        val result = sources.search(SearchScope.Local, query = "")
        assertEquals(SearchResults.PER_SOURCE_LIMIT, result.tracks.size)
        assertEquals(0, result.matches)
        assertFalse(sources.calls.any { it.startsWith("count-") })
    }

    @Test
    fun `live failures are reported without discarding successful sources`() = runBlocking {
        for (failure in listOf(ModArchive.Outcome.NotReached, ModArchive.Outcome.Unreadable)) {
            val sources = Sources().apply {
                online = listOf(track("catalogue"))
                live = failure
            }
            val result = sources.search(SearchScope.Online())
            assertEquals(listOf(failure), sources.outcomes)
            assertEquals(listOf("catalogue"), result.tracks.map { it.id })
        }
    }
}
