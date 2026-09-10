// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ModlandFavouritesTest {

    /** Two real rows of each shape, copied from the published file rather than invented. */
    private val sample = """
        009bb8aac8225ab250ef9a2b6d71a131\t1\t194050\tplayer\tuade\tSoundtracker II (15 instr.)\t0\t123989\t8fb5eab1\t3bf87580\tProtracker/Nightlight/rainyday.mod
        088ee3352e95285fd2dc187908a38147\t2\t184445\tplayer
        0cbc167f098942468d2bda9692efb616\t10\t114599\tloop
        0115668c8d39fe73ad8245b065d1d205\t1\t183386\tplayer\tft2play\tFasttracker\t4\t109036\t65d587da\t208f02c2\tProtracker/- unknown/something about you.mod
    """.trimIndent().lines().map { it.replace("\\t", "\t") }

    @Test
    fun `only the rows that name a file are taken`() {
        assertEquals(
            listOf(
                "Protracker/Nightlight/rainyday.mod",
                "Protracker/- unknown/something about you.mod",
            ),
            ModlandFavourites.parse(sample.asSequence()).toList(),
        )
    }

    /**
     * The trap this parser exists to avoid.
     *
     * The last field of a four-column row is `player`, `loop`, `silence` or `nosound` -- a subsong
     * classification, not a path. Taking the last field of every row would have written 172 of
     * those into the table, where they would match nothing, index nothing, and look exactly like a
     * favourites list that happened to be smaller than expected.
     */
    @Test
    fun `subsong rows are not mistaken for paths`() {
        val parsed = ModlandFavourites.parse(sample.asSequence()).toList()
        assertEquals(emptyList<String>(), parsed.filter { !it.contains('/') })
    }

    @Test
    fun `blank lines and short rows are ignored`() {
        assertEquals(
            emptyList<String>(),
            ModlandFavourites.parse(sequenceOf("", "   ", "one\ttwo")).toList(),
        )
    }
}
