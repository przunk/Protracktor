// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.player.SavedPlace
import com.przunk.protracktor.player.SavedPlaceKey
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
class SavedPlacesStoreTest {
    @Before fun clearFixture() {
        // Robolectric can reuse the process singleton across tests with the same SDK.
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = ProtracktorDatabase.of(context).writableDatabase
        db.delete("saved_places", null, null)
        db.delete("catalogues", null, null)
        db.delete("play_history", null, null)
    }

    @Test fun `exact composite identity survives duplicate save restart index and history deletion`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = SavedPlacesStore(context)
        val a = SavedPlace(SavedPlaceKey("modland", "Protracker", "O'Neil / A+B"), "Modland", 1)
        val b = a.copy(key = a.key.copy(format = "Fasttracker"), savedAt = 2)
        val c = a.copy(key = a.key.copy(catalogueId = "asma"), savedAt = 3)
        val d = a.copy(key = a.key.copy(author = "o'neil / A+B"), savedAt = 4)
        listOf(a, b, c, d).forEach { assertTrue(store.save(it)) }
        assertFalse(store.save(a.copy(savedAt = 100, catalogueName = "Changed")))
        assertEquals(listOf(d, c, b, a), store.list())
        val helper = ProtracktorDatabase.of(context)
        helper.writableDatabase.execSQL("INSERT INTO catalogues(id,display_name) VALUES('modland','Modland')")
        helper.writableDatabase.execSQL("INSERT INTO catalogue_tracks(catalogue_id,path,format,author,title,size) " +
            "VALUES('modland','old.mod',?,?,'Old',123)", arrayOf(a.key.format, a.key.author))
        helper.writableDatabase.execSQL("DELETE FROM catalogue_tracks")
        helper.writableDatabase.execSQL("DELETE FROM catalogues")
        HistoryStore(context).clear()
        helper.writableDatabase.execSQL("INSERT INTO catalogues(id,display_name) VALUES('modland','Modland')")
        helper.writableDatabase.execSQL("INSERT INTO catalogue_tracks(catalogue_id,path,format,author,title,size) " +
            "VALUES('modland','new.mod',?,?,'New',456)", arrayOf(a.key.format, a.key.author))
        helper.close()
        assertEquals(listOf(d, c, b, a), SavedPlacesStore(context).list())
        store.remove(b.key)
        assertEquals(listOf(d, c, a), store.list())
    }

    @Test fun `group lookup uses all exact keys and current playable index contents`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val helper = ProtracktorDatabase.of(context)
        helper.writableDatabase.execSQL("INSERT INTO catalogues(id,display_name) VALUES('modland','Modland')")
        helper.writableDatabase.execSQL("INSERT INTO catalogue_tracks(catalogue_id,path,format,author,title,size,playable) " +
            "VALUES('modland','a.mod','Protracker','Artist','a',123,1)")
        val catalogues = CatalogueStore(context)
        assertTrue(catalogues.hasGroup("modland", "Protracker", "Artist"))
        assertFalse(catalogues.hasGroup("asma", "Protracker", "Artist"))
        assertFalse(catalogues.hasGroup("modland", "Fasttracker", "Artist"))
        assertFalse(catalogues.hasGroup("modland", "Protracker", "artist"))
        helper.writableDatabase.execSQL("UPDATE catalogue_tracks SET playable=0")
        assertFalse(catalogues.hasGroup("modland", "Protracker", "Artist"))
    }
}
