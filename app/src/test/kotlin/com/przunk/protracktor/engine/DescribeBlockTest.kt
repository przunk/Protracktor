// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** `docs/STATUS.md` C40: a module's message arrived with its first line only. */
class DescribeBlockTest {

    @Test
    fun `the message comes back whole, every line and its spacing`() {
        val fields = DescribeBlock.parse(
            "title\tzoolook\nformat\tProTracker MOD (M.K.)\nchannels\t4\nmessage\tgreetings to\n  all  the scene\n\n"
        )
        assertEquals("zoolook", fields["title"])
        assertEquals("4", fields["channels"])
        assertEquals("greetings to\n  all  the scene", fields["message"])
    }

    @Test
    fun `a line of the message holding a tab is not taken for a field`() {
        val fields = DescribeBlock.parse("title\tx\nmessage\tname\tsize\nloop\t0012")
        assertEquals("name\tsize\nloop\t0012", fields["message"])
        assertFalse("loop" in fields)
        assertEquals(setOf("title", "message"), fields.keys)
    }

    @Test
    fun `the word message inside another value is only a word`() {
        val fields = DescribeBlock.parse("comment\tmessage\there\nartist\tRob Hubbard")
        assertEquals("message\there", fields["comment"])
        assertEquals("Rob Hubbard", fields["artist"])
        assertFalse("message" in fields)
    }

    /** `docs/PLAN_INSTRUMENT_NAMES.md`: the names travel as one line each, before the message. */
    @Test
    fun `instrument and sample names stay one field each, before a message of several lines`() {
        val us = Char(0x1F)
        val fields = DescribeBlock.parse(
            "title\tx\nsample_names\tgreetings${us}${us}  to all\ninstrument_names\tbass${us}lead\n" +
                "message\tline one\nline two"
        )
        assertEquals(listOf("greetings", "", "  to all"), DescribeBlock.names(fields["sample_names"]))
        assertEquals(listOf("bass", "lead"), DescribeBlock.names(fields["instrument_names"]))
        assertEquals("line one\nline two", fields["message"])
    }

    @Test
    fun `no names, or only blank ones, are no list at all`() {
        val us = Char(0x1F)
        assertEquals(emptyList<String>(), DescribeBlock.names(null))
        assertEquals(emptyList<String>(), DescribeBlock.names(" $us $us"))
    }

    @Test
    fun `a block with no message, or an empty one, reads as before`() {
        assertEquals(mapOf("title" to "a", "artist" to "b"), DescribeBlock.parse("title\ta\nartist\tb\n"))
        assertEquals("", DescribeBlock.parse("title\ta\nmessage\t")["message"])
        assertEquals("only", DescribeBlock.parse("message\tonly")["message"])
    }
}
