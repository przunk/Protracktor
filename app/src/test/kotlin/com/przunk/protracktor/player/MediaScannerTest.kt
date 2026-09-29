// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which files a scan reads: names a decoder claims, and not too large (`MediaScanner.worthReading`).
 *
 * It used to be size alone -- `docs/STATUS.md` C4, `docs/BACKLOG.md` A6: the decoders decided. The
 * owner turned that round on 2026-09-29, after a folder whose MIDI and text files took a quarter of
 * an hour to be refused one by one (A67).
 */
class MediaScannerTest {

    @Test
    fun `a name a decoder claims is read, whatever its case`() {
        listOf("MODULE.MOD", "tune.sndh", "Bonio.sap", "mdat.turrican", "song.xm", "live.mp3").forEach { name ->
            assertTrue("$name should be read", MediaScanner.worthReading(name, 4L * 1024))
        }
    }

    @Test
    fun `a name nothing here plays is not read at all`() {
        // The owner's folder: as many MIDI files as SAPs, and some text.
        listOf("song.mid", "readme.txt", "cover.jpg", "no-extension", "archive.zip", "", ".hidden").forEach { name ->
            assertFalse("$name should not be read", MediaScanner.worthReading(name, 4L * 1024))
        }
    }

    @Test
    fun `size still counts`() {
        val limit = MediaScanner.MAX_PROBE_BYTES
        assertTrue(MediaScanner.worthReading("tune.mod", limit))
        assertFalse(MediaScanner.worthReading("tune.mod", limit + 1))
        // Some providers report no size at all; refusing those would drop whole network shares.
        assertTrue(MediaScanner.worthReading("tune.mod", 0L))
    }
}
