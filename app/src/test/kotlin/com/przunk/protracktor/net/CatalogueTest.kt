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
package com.przunk.protracktor.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The way back from a track to where it came from.
 *
 * `pathFrom` is the inverse of `urlFor`, and an inverse that is subtly wrong is worse than none:
 * it would send "more from this author" to a folder that does not exist, or to the wrong one.
 */
class CatalogueTest {

    @Test
    fun `a Modland path survives the trip out to a URL and back`() {
        val paths = listOf(
            "Protracker/4-Mat/elysium.mod",
            // Spaces, brackets and ampersands are all common in this archive's filenames.
            "Protracker/Jester (Volker Tripp)/elysium & friends.mod",
            "Ad Lib/Unknown/a+b.sng",
            "Future Composer 1.4/Mad Max/cool.fc14",
        )
        paths.forEach { path ->
            assertEquals(path, Modland.pathFrom(Modland.urlFor(path)))
        }
    }

    @Test
    fun `a catalogue does not claim another catalogue's reference`() {
        assertNull(Modland.pathFrom("asma://asma/Composers/Aki/Atari_Style.sap"))
        assertNull(Asma.pathFrom("https://modland.com/pub/modules/Protracker/4-Mat/elysium.mod"))
        // A local file is a document URI and belongs to nobody.
        assertNull(Modland.pathFrom("content://com.android.providers/document/1234"))
        assertNull(Asma.pathFrom("content://com.android.providers/document/1234"))
    }

    @Test
    fun `an ASMA path survives the trip out and back`() {
        val path = "asma/Composers/Pieczko_Szymon/SimCity_2000.sap"
        assertEquals(path, Asma.pathFrom(Asma.urlFor(path)))
    }

    @Test
    fun `owning finds the catalogue a reference belongs to, and admits when there is none`() {
        assertEquals(Modland, Catalogue.owning(Modland.urlFor("Protracker/4-Mat/elysium.mod")))
        assertEquals(Asma, Catalogue.owning(Asma.urlFor("asma/Games/Abracadabra.sap")))
        assertNull(Catalogue.owning("content://com.android.providers/document/1234"))
    }
}
