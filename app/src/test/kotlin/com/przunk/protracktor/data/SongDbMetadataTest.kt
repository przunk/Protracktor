// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing fifteen megabytes of somebody else's tab-separated text.
 *
 * The rows below are real ones, copied from the published file, because a fixture written from
 * memory would agree with whatever the parser does.
 */
class SongDbMetadataTest {

    @Test
    fun `a full row gives all five fields`() {
        val rows = SongDbMetadata.parse("00000b104a70\tDevastator\tShrimps Design\tCrunched Chips #5\t1995")
        assertEquals(1, rows.size)
        assertEquals(
            SongDbMetadata.Entry("00000b104a70", "Devastator", "Shrimps Design", "Crunched Chips #5", "1995"),
            rows[0],
        )
    }

    /**
     * A row that stops short is tolerated, though `metadata.tsv` never does it.
     *
     * Measured: all 380,282 of its rows carry five fields. The sibling tables in the same
     * repository are not all so tidy, and this parser is the obvious thing to point at them —
     * indexing blindly would throw on the first ragged line.
     */
    @Test
    fun `a row that stops early is still a row`() {
        val rows = SongDbMetadata.parse("004340d8fba1\t4-Mat")
        assertEquals(1, rows.size)
        assertEquals("4-Mat", rows[0].author)
        assertEquals("", rows[0].year)
    }

    /** A hash with nothing attached is not worth a row in our table. */
    @Test
    fun `a row that says nothing is dropped`() {
        assertTrue(SongDbMetadata.parse("00002cf7031f\t\t\t\t").isEmpty())
        assertTrue(SongDbMetadata.parse("").isEmpty())
    }

    /**
     * Keys that are not the publisher's shape are dropped rather than repaired.
     *
     * A full 32-character MD5 in this column would be a line we do not understand — and quietly
     * keeping it would put an entry in the table that no lookup can ever match.
     */
    @Test
    fun `only twelve hex characters count as a key`() {
        assertTrue(SongDbMetadata.parse("0009c3ef4c4c58f5d597a21df3fbb6d7\tSomebody").isEmpty())
        assertTrue(SongDbMetadata.parse("zzzzzzzzzzzz\tSomebody").isEmpty())
        assertTrue(SongDbMetadata.parse("# a comment\tSomebody").isEmpty())
    }

    /**
     * The lookup key is the first twelve characters of the file's MD5.
     *
     * Stated as a test because getting it wrong fails silently in the worst way: the table fills,
     * every query runs, and nothing is ever found.
     */
    @Test
    fun `the key is the published prefix, not the whole hash`() {
        assertEquals("0009c3ef4c4c", SongDbMetadata.keyOf("0009c3ef4c4c58f5d597a21df3fbb6d7"))
        assertEquals("0009c3ef4c4c", SongDbMetadata.keyOf("0009C3EF4C4C58F5D597A21DF3FBB6D7"))
    }

    @Test
    fun `several rows parse in file order`() {
        val rows = SongDbMetadata.parse(
            """
            00000b104a70	Devastator	Shrimps Design	Crunched Chips #5	1995
            00002cf7031f	Cybarite			
            003753e423a3	4mat	Slipstream	Musicdisk 7	1990
            """.trimIndent()
        )
        assertEquals(listOf("Devastator", "Cybarite", "4mat"), rows.map { it.author })
        assertEquals(listOf("1995", "", "1990"), rows.map { it.year })
    }
}
