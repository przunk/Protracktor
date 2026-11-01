// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.net.Amp
import com.przunk.protracktor.net.CatalogueEntry
import com.przunk.protracktor.net.Modland
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AmpCatalogueStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val entry = CatalogueEntry("A/Artist/MOD.song.gz", "MOD", "Artist", "MOD.song", 42)

    @Before fun reset() {
        Amp.access.setEnabled(true)
        ProtracktorDatabase.of(context).writableDatabase.delete("catalogues", null, null)
    }

    @After fun restore() { Amp.access.setEnabled(true) }

    @Test fun `a retired AMP write cannot replace an existing index after an off-on cycle`() = runBlocking {
        val store = CatalogueStore(context)
        store.replaceIndex(Amp, listOf(entry), "test")
        val ticket = Amp.access.capture()!!
        Amp.access.setEnabled(false)
        Amp.access.setEnabled(true)
        val error = runCatching {
            store.replaceIndex(Amp, listOf(entry.copy(path = "A/Artist/MOD.late.gz")), "test", ticket)
        }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.CancellationException)
        assertEquals(entry.path, store.tracks(Amp.id, "MOD", "Artist").single().path)
    }

    @Test fun `turning off and pruning preserves playlists History and bookmarks`() = runBlocking {
        val catalogues = CatalogueStore(context)
        catalogues.replaceIndex(Amp, listOf(entry), "test")
        val ref = com.przunk.protracktor.player.TrackRef(
            Amp.urlFor(entry.path), entry.title, fileName = entry.title, author = entry.author,
        )
        val library = LibraryStore(context)
        val playlist = library.createPlaylist("AMP trial")
        library.replaceTracks(playlist, listOf(ref))
        val history = HistoryStore(context)
        history.record(ref.id, ref.title, "AMP", ref.fileName, ref.author, 42)
        val places = SavedPlacesStore(context)
        val place = com.przunk.protracktor.player.SavedPlace(
            com.przunk.protracktor.player.SavedPlaceKey(Amp.id, "MOD", "Artist"), Amp.displayName, 1,
        )
        places.save(place)
        Amp.access.setEnabled(false)
        catalogues.pruneUnknownCatalogues()
        assertEquals(listOf(ref.id), library.tracksIn(playlist).map { it.id })
        assertTrue(history.page(0, 100).any { it.trackId == ref.id })
        assertTrue(places.list().any { it.key == place.key })
        ProtracktorDatabase.of(context).close()
        assertEquals(listOf(ref.id), LibraryStore(context).tracksIn(playlist).map { it.id })
    }

    @Test fun `off excludes retained AMP rows from every query and on restores them without reindexing`() = runBlocking {
        val store = CatalogueStore(context)
        store.replaceIndex(Amp, listOf(entry), "test")
        store.replaceIndex(Modland, listOf(entry.copy(path = "Protracker/Artist/MOD.other", title = "MOD.other")), "test")
        assertEquals(2, store.countMatches("", emptySet()))
        assertEquals(2, store.platformCounts()["amiga"])
        assertEquals(listOf("MOD"), store.formats(Amp.id).map { it.name })
        assertEquals(1, store.tracks(Amp.id, "MOD", "Artist").size)
        Amp.access.setEnabled(false)
        assertFalse(store.summaries().any { it.id == Amp.id })
        assertEquals(listOf(Modland.id), store.search("", emptySet(), 100).map { it.catalogueId })
        assertEquals(1, store.countMatches("", emptySet()))
        assertEquals(0, store.countMatches("", setOf(Amp.id)))
        assertEquals(1, store.platformCounts()["amiga"])
        assertTrue(store.search("", setOf(Amp.id), 100).isEmpty())
        assertEquals(listOf(Modland.id), store.randomSample(10).map { it.catalogueId })
        assertTrue(store.randomSample(10, setOf(Amp.id)).isEmpty())
        assertTrue(store.formats(Amp.id).isEmpty())
        assertTrue(store.authors(Amp.id, "MOD").isEmpty())
        assertTrue(store.tracks(Amp.id, "MOD", "Artist").isEmpty())
        assertFalse(store.hasGroup(Amp.id, "MOD", "Artist"))
        assertNull(store.locate(Amp.id, entry.path))
        store.pruneUnknownCatalogues()
        Amp.access.setEnabled(true)
        assertEquals(2, store.countMatches("", emptySet()))
        assertTrue(store.summaries().any { it.id == Amp.id && it.indexed })
        assertEquals(entry.path, store.locate(Amp.id, entry.path)?.path)
    }
}
