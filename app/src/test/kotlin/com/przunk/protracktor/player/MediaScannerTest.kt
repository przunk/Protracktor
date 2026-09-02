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
package com.przunk.protracktor.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that a scan does not judge a file by its name.
 *
 * This is `docs/STATUS.md` C4 and `docs/BACKLOG.md` A6: the old scan kept only files whose
 * extension looked playable, so a misnamed module was invisible and a misleadingly named photograph
 * was added and refused only when played. The scan now opens every file it lists and lets the
 * decoder decide.
 *
 * `MediaScanner.worthReading` is where that rule lives, and it is a separate function precisely so
 * this test can exist: the scan itself needs a `ContentResolver` and there is no emulator here.
 */
class MediaScannerTest {

    @Test
    fun `a file is not admitted or rejected on its name`() {
        val small = 4L * 1024
        // Names a decoder would never be offered under the old rule.
        listOf(
            "readme.txt",
            "cover.jpg",
            "no-extension",
            "MODULE.MOD",
            "tune.sndh",
            "something.exe",
            "",
            ".hidden",
            "archive.zip",
        ).forEach { name ->
            assertTrue("$name should be read and let the decoder decide", MediaScanner.worthReading(name, small))
        }
    }

    @Test
    fun `size is the only thing it judges on`() {
        val limit = MediaScanner.MAX_PROBE_BYTES
        assertTrue(MediaScanner.worthReading("anything", limit))
        assertTrue(MediaScanner.worthReading("anything", limit - 1))
        assertFalse(MediaScanner.worthReading("anything", limit + 1))
        // A film named like a module is still not worth pulling off a network share.
        assertFalse(MediaScanner.worthReading("holiday.mod", 4L * 1024 * 1024 * 1024))
    }

    @Test
    fun `a file whose size is unknown is still read`() {
        // Some providers report no size at all. Refusing those would silently drop whole folders on
        // exactly the network shares this app is for.
        assertTrue(MediaScanner.worthReading("mystery", 0L))
    }
}
