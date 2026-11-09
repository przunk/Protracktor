// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import com.przunk.protracktor.net.Modland
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B59: what sharing several tunes sends. */
class SelectionShareTest {
    private val first = TrackRef(Modland.urlFor("Protracker/4-Mat/intro.mod"), "intro", fileName = "intro.mod")
    private val second = TrackRef(Modland.urlFor("Protracker/Jester/intro.mod"), "intro", fileName = "intro.mod")
    private val local = TrackRef("content://phone/song.xm", "song", fileName = "song.xm")

    @Test fun `links go one per line in selection order and the phone's files are counted`() {
        val links = SelectionShare.links(listOf(first, local, second))
        assertEquals(listOf(SelectionShare.linkFor(first), SelectionShare.linkFor(second)).joinToString("\n"), links.text)
        assertEquals(2, links.shared)
        assertEquals(1, links.without)
    }

    @Test fun `a file on the phone has no link`() {
        assertNull(SelectionShare.linkFor(local))
        assertEquals("https://modland.com/pub/modules/Protracker/4-Mat/intro.mod", SelectionShare.linkFor(first))
    }

    @Test fun `copies in one share get distinct names, extension kept`() {
        assertEquals(listOf("intro.mod", "intro (2).mod", "song.xm", "intro (3).mod", "Intro (2) (2).mod"),
            SelectionShare.uniqueNames(listOf("intro.mod", "intro.mod", "song.xm", "intro.mod", "Intro (2).mod")))
        assertEquals(listOf("tune", "tune (2)"), SelectionShare.uniqueNames(listOf("", "tune")))
    }
}
