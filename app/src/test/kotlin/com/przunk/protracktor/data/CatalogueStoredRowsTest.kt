// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.player.TrackRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * C143 on a phone that already holds the mistake: catalogue rows stored by an import -- the address
 * as subtitle and file, and for The Mod Archive the format as author -- read back right from a
 * playlist, with nothing in the database changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CatalogueStoredRowsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @After fun close() { ProtracktorDatabase.of(context).close() }

    @Test fun `an imported Mod Archive row reads back as the archive's own tune`() = runBlocking {
        val id = "https://api.modarchive.org/downloads.php?moduleid=48956#lotus3_4.mod"
        val stored = TrackRef(id = id, title = "L3_CD4-SpaceNinja", subtitle = "https://api.modarchive.org",
            fileName = "downloads.php?moduleid=48956#lotus3_4.mod", author = "MOD")
        val library = LibraryStore(context)
        val playlist = library.createPlaylist("Favorites")
        library.replaceTracks(playlist, listOf(stored))
        val read = library.tracksIn(playlist).single()
        assertEquals("lotus3_4.mod", read.fileName)
        assertEquals("The Mod Archive", read.subtitle)
        assertEquals("", read.displayAuthor)
        assertEquals(read, library.allTracks().single { it.id == id })
    }

    @Test fun `an imported Modland row reads back filed under Modland`() = runBlocking {
        val id = com.przunk.protracktor.net.Modland.urlFor("SNDH/Jess/Middle_Earth_Theme.sndh")
        val stored = TrackRef(id = id, title = "Middle-Earth Theme", subtitle = id.substringBeforeLast('/'),
            fileName = "Middle_Earth_Theme.sndh", author = "Jess")
        val library = LibraryStore(context)
        val playlist = library.createPlaylist("Imported")
        library.replaceTracks(playlist, listOf(stored))
        val read = library.tracksIn(playlist).single()
        assertEquals("Modland/SNDH/Jess", read.subtitle)
        assertEquals("Middle_Earth_Theme.sndh", read.fileName)
        assertEquals("Jess", read.author)
    }
}
