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

    @Test
    fun `a block with no message, or an empty one, reads as before`() {
        assertEquals(mapOf("title" to "a", "artist" to "b"), DescribeBlock.parse("title\ta\nartist\tb\n"))
        assertEquals("", DescribeBlock.parse("title\ta\nmessage\t")["message"])
        assertEquals("only", DescribeBlock.parse("message\tonly")["message"])
    }
}
