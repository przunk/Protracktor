// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser, against the shapes the real 5.2 MB file actually contains.
 *
 * The fixture is copied from it verbatim rather than invented, including the CRLF line endings and
 * the comment above every entry, because the failures worth catching here are the ones where the
 * file is not quite what it was assumed to be.
 */
class SongLengthsTest {

    private val real = """
        [Database]
        ; /DEMOS/0-9/10_Orbyte.sid
        5f08a730b280e54fd1e75a7046b93fdc=1:17
        ; /DEMOS/0-9/12th_Sector_Music.sid
        c7c299ce06ec5ccffb2261fb11b42a73=4:33.108
        ; /MUSICIANS/H/Hubbard_Rob/Commando.sid
        6d019ecba831a9f853675aac29a61c10=3:55.594 1:01.288 0:06 0:01.124
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun `entries are read and the header and comments are not`() {
        val entries = SongLengths.parse(real)
        assertEquals(3, entries.size)
        assertEquals("5f08a730b280e54fd1e75a7046b93fdc", entries[0].md5)
        assertEquals(listOf(77.0), entries[0].seconds)
    }

    @Test
    fun `every subsong is kept, not just the first`() {
        val commando = SongLengths.parse(real).last()
        assertEquals(4, commando.seconds.size)
        assertEquals(235.594, commando.seconds[0], 0.0005)
        assertEquals(1.124, commando.seconds[3], 0.0005)
    }

    @Test
    fun `a fraction is a decimal fraction of a second, not milliseconds`() {
        // The distinction that makes 0:03.5 three and a half seconds rather than three and five
        // thousandths. Getting it wrong is invisible in the numbers and wrong in every readout.
        assertEquals(3.5, SongLengths.parseTime("0:03.5")!!, 0.0005)
        assertEquals(3.05, SongLengths.parseTime("0:03.05")!!, 0.0005)
        assertEquals(3.005, SongLengths.parseTime("0:03.005")!!, 0.0005)
    }

    @Test
    fun `minutes past ten and the longest tune in the file both parse`() {
        assertEquals(2026.332, SongLengths.parseTime("33:46.332")!!, 0.0005)
    }

    @Test
    fun `something that is not a time is not silently read as one`() {
        assertNull(SongLengths.parseTime("3:70"))
        assertNull(SongLengths.parseTime("3"))
        assertNull(SongLengths.parseTime("3:5"))
        assertNull(SongLengths.parseTime(""))
    }

    @Test
    fun `a line with one bad time is dropped whole rather than half-kept`() {
        val entries = SongLengths.parse(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=1:00 nonsense 2:00\n" +
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=1:00"
        )
        assertEquals(1, entries.size)
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", entries[0].md5)
    }

    @Test
    fun `an unknown section costs its own lines and not the whole file`() {
        val entries = SongLengths.parse(
            "[Database]\n[SomethingNew]\nnot an entry\ncccccccccccccccccccccccccccccccc=0:30"
        )
        assertEquals(1, entries.size)
        assertEquals(30.0, entries[0].seconds[0], 0.0005)
    }

    @Test
    fun `keys are lowercased so a lookup does not miss on case`() {
        val entries = SongLengths.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=0:30")
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", entries[0].md5)
    }

    @Test
    fun `packing and unpacking a row returns the same seconds`() {
        val seconds = listOf(235.594, 61.288, 6.0, 1.124)
        assertEquals(seconds, SongLengths.unpack(SongLengths.pack(seconds)))
        // Whole seconds do not gain a decimal point on the way through, which is most of the file.
        assertTrue(SongLengths.pack(listOf(6.0)) == "6")
    }
}
