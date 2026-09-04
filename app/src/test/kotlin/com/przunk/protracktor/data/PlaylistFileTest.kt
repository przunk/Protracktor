/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.data

import com.przunk.protracktor.player.TrackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A playlist written to a file and read back.
 *
 * `docs/BACKLOG.md` A25. The format is M3U so other players can read it, with our own lines added
 * as comments they will ignore — so the tests that matter are the two directions of that bargain:
 * a file we wrote comes back whole, and a file somebody else wrote is still usable.
 */
class PlaylistFileTest {

    private val local = TrackRef(
        id = "content://com.android.externalstorage.documents/document/primary%3AMusic%2Felysium.mod",
        title = "elysium",
        subtitle = "Protracker/4-Mat",
        sizeBytes = 104_928,
        fileName = "elysium.mod",
        author = "4-Mat",
    )
    private val remote = TrackRef(
        id = "https://modland.com/pub/modules/Protracker/Jester/elysium.mod",
        title = "elysium",
        subtitle = "Modland/Protracker/Jester",
        sizeBytes = 62_000,
        fileName = "elysium.mod",
        author = "Jester",
    )

    @Test
    fun `what we wrote comes back`() {
        val entries = PlaylistFile.read(PlaylistFile.write("Favourites", listOf(local, remote)))
        assertEquals(2, entries.size)
        assertEquals(local.id, entries[0].id)
        assertEquals("elysium", entries[0].title)
        assertEquals("4-Mat", entries[0].author)
        assertEquals(104_928, entries[0].sizeBytes)
    }

    @Test
    fun `a catalogue track's location is a real URL another player could fetch`() {
        val text = PlaylistFile.write("Favourites", listOf(remote))
        assertTrue(text.lineSequence().any { it == remote.id })
    }

    @Test
    fun `a local file's location is a path, not a document URI`() {
        val text = PlaylistFile.write("Favourites", listOf(local))
        // The URI means nothing on another device -- it is issued by a provider on this one -- so
        // the line another player reads is the path. The URI still goes in a comment, because on
        // *this* device it restores the playlist exactly.
        assertTrue(text.lineSequence().any { it == "Protracker/4-Mat/elysium.mod" })
        assertTrue(text.contains("#PROTRACKTOR:id=${local.id}"))
        assertTrue(text.lineSequence().none { !it.startsWith("#") && it.startsWith("content://") })
    }

    @Test
    fun `a plain M3U from another program is read`() {
        val entries = PlaylistFile.read(
            """
            #EXTM3U
            #EXTINF:212,Rob Hubbard - Commando
            /sdcard/Music/commando.sid
            #EXTINF:-1,unknown.mod
            music/unknown.mod
            """.trimIndent()
        )
        assertEquals(2, entries.size)
        assertEquals("Commando", entries[0].title)
        assertEquals("Rob Hubbard", entries[0].author)
        // Nothing of ours in it, so no exact identity -- matching will have to be by name and size.
        assertNull(entries[0].id)
        assertEquals("commando.sid", entries[0].fileName)
    }

    @Test
    fun `comments we do not understand cost their own line and not the file`() {
        val entries = PlaylistFile.read(
            """
            #EXTM3U
            #PLAYLIST:Something another program writes
            #EXTGRP:Chiptunes
            #EXTINF:-1,A tune
            a.mod
            """.trimIndent()
        )
        assertEquals(1, entries.size)
        assertEquals("a.mod", entries[0].location)
    }

    @Test
    fun `a title with a dash in it survives`() {
        val entries = PlaylistFile.read("#EXTINF:-1,4-Mat - hi-score\nx.mod")
        // Split on the first " - " only: "4-Mat" and "hi-score" both contain dashes, and a greedy
        // split would put the artist's name in the title.
        assertEquals("4-Mat", entries[0].author)
        assertEquals("hi-score", entries[0].title)
    }

    @Test
    fun `an entry with no EXTINF is still an entry`() {
        val entries = PlaylistFile.read("#EXTM3U\nsomewhere/tune.sndh")
        assertEquals(1, entries.size)
        assertEquals("tune.sndh", entries[0].title)
        assertEquals(0L, entries[0].sizeBytes)
    }

    @Test
    fun `an empty playlist writes a header and reads back as nothing`() {
        val text = PlaylistFile.write("Favourites", emptyList())
        assertTrue(text.startsWith("#EXTM3U"))
        assertTrue(PlaylistFile.read(text).isEmpty())
    }

    /**
     * The list's own name survives a round trip.
     *
     * It is written because the alternative — guessing from the filename — produced
     * `primary:Download/Favorites` on import: a document identifier rather than a title. `#PLAYLIST:`
     * is the extended-M3U tag other players already understand, so this costs no compatibility.
     */
    @Test
    fun `the playlist name is written and read back`() {
        val text = PlaylistFile.write("Fjortis facials", listOf(local))
        assertEquals("Fjortis facials", PlaylistFile.nameIn(text))
    }

    /** A file from another player has entries and no name, which is not an error. */
    @Test
    fun `a file without a name yields null rather than a guess`() {
        val text = """
            #EXTM3U
            #EXTINF:-1,Somebody - A tune
            /music/a tune.mod
        """.trimIndent()
        assertNull(PlaylistFile.nameIn(text))
        assertEquals(1, PlaylistFile.read(text).size)
    }

    @Test
    fun `a blank name is not written`() {
        assertNull(PlaylistFile.nameIn(PlaylistFile.write("   ", listOf(local))))
    }

    /** Names are user text: spaces, punctuation and non-Latin script all have to survive. */
    @Test
    fun `an awkward name survives`() {
        for (name in listOf("Ulubione — 2026", "d-bug #197", "Ćma barowa", "a, b, c")) {
            assertEquals(name, PlaylistFile.nameIn(PlaylistFile.write(name, listOf(local))))
        }
    }
}
