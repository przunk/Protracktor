// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.data.CatalogueStore
import com.przunk.protracktor.data.HistoryStore
import com.przunk.protracktor.data.LibraryStore
import com.przunk.protracktor.data.ProtracktorDatabase
import com.przunk.protracktor.net.CatalogueEntry
import com.przunk.protracktor.net.Modland
import com.przunk.protracktor.player.TrackRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** B62: *Add music* reads the player's playlists, History and catalogues as they are stored. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AlarmMusicSourcesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `playlists, History and a search over both and the catalogues`() { runBlocking {
        ProtracktorDatabase.of(context).writableDatabase.delete("catalogues", null, null)
        val lotus = TrackRef("content://music/lotus.mod", "Lotus 3", fileName = "lotus.mod", author = "Patrick Phelan")
        val library = LibraryStore(context)
        val wake = library.createPlaylist("Wake-up")
        library.replaceTracks(wake, listOf(lotus))
        HistoryStore(context).record("content://music/heard.mod", "Heard lotus", "", "heard.mod", "Someone", 42)
        CatalogueStore(context).replaceIndex(Modland, listOf(
            CatalogueEntry("Protracker/Lotus/lotus2.mod", "Protracker", "Lotus", "lotus2.mod", 42)), "test")

        val sources = StoredAlarmMusicSources(context)
        val list = sources.playlists().single { it.name == "Wake-up" }
        assertEquals(listOf(lotus.id), sources.tracksIn(list.id).map { it.ref.id })
        assertEquals(listOf("Heard lotus"), sources.history().map { it.ref.title })
        val found = sources.search("lotus").map { it.ref.id }
        assertEquals(lotus.id, found.first())
        assertTrue(found.any { it.startsWith("https://") && it.endsWith("lotus2.mod") })
        assertEquals(emptyList<String>(), sources.search(" ").map { it.ref.id })
    } }
}
