// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.net

import com.przunk.protracktor.player.OpenFailure
import com.przunk.protracktor.player.TrackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** RV-B006: a Modland address with a `%` that is not an escape is not Modland's -- and throws nothing. */
class ModlandMalformedAddressTest {
    private val bad = listOf(
        "https://modland.com/pub/modules/%zz.mod",
        "https://modland.com/pub/modules/Protracker/tune%",
        "https://modland.com/pub/modules/%E0%A4%A/x.mod",
    )

    @Test fun `such an address belongs to no catalogue`() {
        bad.forEach { id ->
            assertNull(id, Modland.pathFrom(id))
            assertNull(id, Catalogue.owning(id))
            assertNull(id, Catalogue.sourceOf(id))
            assertNull(id, Catalogue.fileOf(id))
        }
    }

    @Test fun `a tune with such an address is read as it is`() {
        val ref = TrackRef(id = bad[0], title = "x", subtitle = "https://modland.com/pub/modules", fileName = "%zz.mod")
        assertSame(ref, ref.canonical())
    }

    @Test fun `the format named by a broken first folder is not guessed`() {
        assertNull(OpenFailure.modlandFormatOf("https://modland.com/pub/modules/Pro%zz/x.mod"))
        assertEquals("Protracker", OpenFailure.modlandFormatOf("https://modland.com/pub/modules/Protracker/x.mod"))
    }

    @Test fun `good addresses still decode, a literal plus kept`() {
        assertEquals("Protracker/A+B/x y.mod", Modland.pathFrom("https://modland.com/pub/modules/Protracker/A+B/x%20y.mod"))
    }
}
