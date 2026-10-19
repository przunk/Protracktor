// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.net.CatalogueEntry
import com.przunk.protracktor.net.Modland
import kotlinx.coroutines.runBlocking
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
class QueueCatalogueCompatibilityTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val database get() = ProtracktorDatabase.of(context).writableDatabase

    @Before fun seed() = runBlocking {
        database.delete("catalogues", null, null)
        CatalogueStore(context).replaceIndex(Modland,
            listOf(CatalogueEntry("Protracker/Artist/visible.mod", "Protracker", "Artist", "visible.mod", 42)), "test")
        database.execSQL("INSERT INTO catalogues(id, display_name, track_count, archive_count, complete) VALUES ('amp', 'AMP', 1, 1, 1)")
        database.execSQL("INSERT INTO catalogue_tracks(catalogue_id,path,format,author,title,size,ext,pre,playable,platform) VALUES ('amp','A/Artist/MOD.retained.gz','MOD','Artist','MOD.retained',42,'mod','mod',1,'amiga')")
    }

    @Test fun `startup preserves AMP index for return to its APK`() = runBlocking {
        CatalogueStore(context).pruneUnknownCatalogues()
        database.rawQuery("SELECT path FROM catalogue_tracks WHERE catalogue_id='amp'", null).use {
            assertTrue("AMP index must survive QUEUE startup", it.moveToFirst())
            assertEquals("A/Artist/MOD.retained.gz", it.getString(0))
        }
    }

    @Test fun `retained AMP never leaks into discovery or explicit catalogue reads`() = runBlocking {
        val store = CatalogueStore(context)
        assertEquals(1, store.countMatches("", emptySet()))
        assertEquals(listOf("modland"), store.search("", emptySet(), 10).map { it.catalogueId })
        assertEquals(listOf("modland"), store.randomSample(10).map { it.catalogueId })
        assertEquals(1, store.platformCounts()["amiga"])
        assertEquals(0, store.countMatches("", setOf("amp")))
        assertTrue(store.randomSample(10, setOf("amp")).isEmpty())
        assertTrue(store.formats("amp").isEmpty())
        assertTrue(store.authors("amp", "MOD").isEmpty())
        assertTrue(store.tracks("amp", "MOD", "Artist").isEmpty())
        assertFalse(store.hasGroup("amp", "MOD", "Artist"))
        assertNull(store.locate("amp", "A/Artist/MOD.retained.gz"))
    }

    @Test fun `AMP playlist History and bookmark survive pruning and database reopen`() = runBlocking {
        val ref = com.przunk.protracktor.player.TrackRef("amp://A/Artist/MOD.retained.gz", "MOD.retained")
        val library = LibraryStore(context)
        val playlist = library.createPlaylist("AMP interoperability")
        library.replaceTracks(playlist, listOf(ref))
        HistoryStore(context).record(ref.id, ref.title, "AMP", "MOD.retained", "Artist", 42)
        val place = com.przunk.protracktor.player.SavedPlace(
            com.przunk.protracktor.player.SavedPlaceKey("amp", "MOD", "Artist"), "AMP", 1,
        )
        SavedPlacesStore(context).save(place)
        CatalogueStore(context).pruneUnknownCatalogues()
        ProtracktorDatabase.of(context).close()
        assertEquals(listOf(ref.id), LibraryStore(context).tracksIn(playlist).map { it.id })
        assertTrue(HistoryStore(context).page(0, 100).any { it.trackId == ref.id })
        assertTrue(SavedPlacesStore(context).list().any { it.key == place.key })
        database.rawQuery("SELECT COUNT(*) FROM catalogue_tracks WHERE catalogue_id='amp'", null).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }
}
