// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class AmpTest {
    private fun row(path: String, size: String = "42", format: String = "Protracker v1") =
        listOf("hash", "1", "3000", "player", "uade", format, "0", size, "crc", "crc", path)
            .joinToString("\t")

    @Test fun `parser uses author and unpacked filename and ignores subsong rows`() {
        val bytes = (row("M/Musician/MOD.song.gz") + "\r\nhash\t2\t1000\tplayer\n" +
            row("M/Musician/XM.other.gz")).toByteArray()
        val parsed = Amp.parseIndex(bytes)
        assertEquals(listOf("MOD.song", "XM.other"), parsed.map { it.title })
        assertEquals(listOf("MOD", "XM"), parsed.map { it.format })
        assertEquals("Musician", parsed[0].author)
        assertEquals(42L, parsed[0].size)
        assertEquals("M/Musician/MOD.song.gz", parsed[0].path)
    }

    @Test fun `invalid paths and sizes do not enter the index`() {
        val paths = listOf("../Artist/MOD.song.gz", "A/../MOD.song.gz", "A/B/c/d.gz", "A/B/", "A/B/a\\b.gz")
        assertTrue(Amp.parseIndex(paths.joinToString("\n") { row(it) }.toByteArray()).isEmpty())
        assertTrue(Amp.parseIndex(row("A/B/MOD.song.gz", "oops").toByteArray()).isEmpty())
        paths.forEach { assertNull(Amp.downloadUrl(it)) }
    }

    @Test fun `source format disambiguates Face The Music from Nintendo FamiTracker`() {
        val entry = Amp.parseIndex(row("A/Artist/FTM.song.gz", format = "FaceTheMusic").toByteArray()).single()
        assertEquals("Face The Music", entry.format)
        assertEquals(com.przunk.protracktor.player.Platforms.ATARI_ST,
            com.przunk.protracktor.player.Platforms.forCatalogueRow(Amp.id, entry.format, entry.title))
    }

    @Test fun `identity round trips literal punctuation and URL encodes each path segment`() {
        val path = "M/Major Tom/MOD.a+b %?#é.gz"
        assertEquals(path, Amp.pathFrom(Amp.urlFor(path)))
        assertEquals("https://amp.dascene.net/modules/M/Major%20Tom/MOD.a%2Bb%20%25%3F%23%C3%A9.gz", Amp.downloadUrl(path))
        assertNull(Amp.pathFrom("modland://$path"))
        assertNull(Amp.fileUrlFor(path))
        assertTrue(Amp.controls(Amp.downloadUrl(path)!!))
        assertTrue(Amp.controls(Amp.indexUrl))
        assertFalse(Amp.controls("https://amp.dascene.net.evil.example/modules/a"))
    }

    @Test fun `gzip is unpacked but plain indexed exceptions are kept and HTML is rejected`() {
        val original = "Synthetic module bytes".toByteArray()
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(original) } }.toByteArray()
        assertArrayEquals(original, Amp.moduleBytes(zipped))
        assertArrayEquals(original, Amp.moduleBytes(original))
        assertArrayEquals("<demo> title".toByteArray(), Amp.moduleBytes("<demo> title".toByteArray()))
        assertNull(Amp.moduleBytes("  <!DOCTYPE html>Access denied".toByteArray()))
        assertNull(Amp.moduleBytes(zipped.copyOf(12)))
        assertNull(Amp.moduleBytes(byteArrayOf()))
    }

    @Test fun `off cancels only registered work and old epochs remain invalid after reenable`() {
        val access = AmpAccess()
        val ticket = access.capture()!!
        var cancelled = 0
        val finished = { cancelled += 100 }
        access.attach(ticket, finished)
        access.detach(finished)
        assertTrue(access.attach(ticket) { cancelled++ })
        assertTrue(access.setEnabled(false))
        assertEquals(1, cancelled)
        assertNull(access.capture())
        assertFalse(access.attach(ticket) { cancelled++ })
        assertFalse(access.setEnabled(false))
        access.setEnabled(true)
        assertFalse(access.allows(ticket))
        assertTrue(access.allows(access.capture()!!))
    }

    @Test fun `oversized expanded gzip is refused`() {
        val packed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { gzip ->
                val chunk = ByteArray(65536)
                repeat(1025) { gzip.write(chunk) }
            }
        }.toByteArray()
        assertNull(Amp.moduleBytes(packed))
    }
}
