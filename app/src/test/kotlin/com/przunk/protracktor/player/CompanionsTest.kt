// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which file a TFMX song needs beside it.
 *
 * Small, and the one part of multifile playback that can be checked without a phone: if the name
 * comes out wrong, the fetch asks the archive for a file that is not there and UADE plays nothing,
 * with no error anywhere that says why.
 */
class CompanionsTest {

    @Test
    fun `a TFMX song needs its samples`() {
        assertEquals(listOf("smpl.turrican 2 - title"), Companions.namesFor("mdat.turrican 2 - title"))
    }

    @Test
    fun `the rest of the name is kept exactly, dots and all`() {
        assertEquals(listOf("smpl.x.out.1"), Companions.namesFor("mdat.x.out.1"))
    }

    @Test
    fun `an upper-case prefix asks for an upper-case sibling`() {
        // Amiga disks were not consistent; the sibling is spelled the way its neighbour is.
        assertEquals(listOf("SMPL.Unicorn"), Companions.namesFor("MDAT.Unicorn"))
    }

    @Test
    fun `every single-file format needs nothing`() {
        for (name in listOf("elysium.mod", "alfred chicken.dw", "5th gear.hip", "noextension", ".mdat", "mdat.")) {
            assertTrue(name, Companions.namesFor(name).isEmpty())
        }
    }

    @Test
    fun `a sibling path stays in its directory`() {
        assertEquals(
            listOf("https://modland.com/pub/modules/TFMX/Chris Huelsbeck/smpl.turrican"),
            Companions.siblingPaths("https://modland.com/pub/modules/TFMX/Chris Huelsbeck/mdat.turrican"),
        )
        assertEquals(listOf("Turrican/smpl.title"), Companions.siblingPaths("Turrican/mdat.title"))
        assertEquals(listOf("smpl.title"), Companions.siblingPaths("mdat.title"))
    }
}
