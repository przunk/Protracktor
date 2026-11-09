// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import org.junit.Assume.assumeTrue
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** AMP's daily module map (C134, 2026-11-09): parsing, identity and the module address. */
@RunWith(RobolectricTestRunner::class)
class AmpTest {
    private fun module(id: Long, path: String, size: Long = 42, fileName: String = path.substringAfterLast('/')) =
        """{"module_id":$id,"title":"x","authors":["A","B"],"file_name":"$fileName","file_size":$size,""" +
            """"download_file_name":"x.mod","md5":"f1d27584cf9018189cfda2002d072e97","path":"$path"}"""
    private fun map(vararg modules: String) =
        """{"generated_at":"2026-11-09T07:29:06Z","module_count":${modules.size},"modules":[${modules.joinToString(",")}]}""".toByteArray()

    @Test fun `the map gives the folder as author, the stored name as title, and the number`() {
        val parsed = Amp.parseIndex(map(module(49993, "N/Nork/MOD.Nk-SaveTheFunky.gz", 284182),
            module(182426, "N/Nooze/XM.Strap-on.gz", 15072)))
        assertEquals(listOf("MOD.Nk-SaveTheFunky", "XM.Strap-on"), parsed.map { it.title })
        assertEquals(listOf("MOD", "XM"), parsed.map { it.format })
        assertEquals(listOf("Nork", "Nooze"), parsed.map { it.author })
        assertEquals(listOf(284182L, 15072L), parsed.map { it.size })
        assertEquals(listOf(49993L, 182426L), parsed.map { it.remoteId })
        assertEquals("N/Nork/MOD.Nk-SaveTheFunky.gz", parsed[0].path)
    }

    @Test fun `invalid paths, numbers and sizes do not enter the index, a path twice keeps its first`() {
        val bad = listOf("../Artist/MOD.song.gz", "A/../MOD.song.gz", "A/B/c/d.gz", "A/B/", "A/B/a\\\\b.gz")
        assertTrue(Amp.parseIndex(map(*bad.mapIndexed { i, p -> module(i + 1L, p) }.toTypedArray())).isEmpty())
        assertTrue(Amp.parseIndex(map(module(0, "A/B/MOD.song.gz"), module(5, "A/B/MOD.song.gz", size = -1))).isEmpty())
        val twice = Amp.parseIndex(map(module(14, "0-9/708/XM.Serenity.gz"), module(120610, "0-9/708/XM.Serenity.gz")))
        assertEquals(listOf(14L), twice.map { it.remoteId })
    }

    @Test fun `a broken map is no index rather than a crash`() {
        assertTrue(Amp.parseIndex("<!DOCTYPE html>".toByteArray()).isEmpty())
        assertTrue(Amp.parseIndex("""{"modules":[{"module_id":1,""".toByteArray()).isEmpty())
    }

    @Test fun `FTM on AMP is Face The Music, an Amiga archive's, not Nintendo FamiTracker`() {
        val entry = Amp.parseIndex(map(module(7, "A/Artist/FTM.song.gz"))).single()
        assertEquals("Face The Music", entry.format)
        assertEquals(com.przunk.protracktor.player.Platforms.ATARI_ST,
            com.przunk.protracktor.player.Platforms.forCatalogueRow(Amp.id, entry.format, entry.title))
    }

    @Test fun `identity keeps the path, a download is the module number`() {
        val path = "M/Major Tom/MOD.a+b %?#é.gz"
        assertEquals(path, Amp.pathFrom(Amp.urlFor(path)))
        assertEquals("https://amp.dascene.net/module/49993", Amp.downloadUrl(49993))
        assertNull(Amp.downloadUrl(0))
        assertNull(Amp.pathFrom("modland://$path"))
        assertNull(Amp.fileUrlFor(path))
        assertTrue(Amp.controls(Amp.downloadUrl(49993)!!))
        assertTrue(Amp.controls(Amp.indexUrl))
        assertEquals("https://amp.dascene.net/download/amp_modules.json", Amp.indexUrl)
        assertFalse(Amp.controls("https://amp.dascene.net.evil.example/module/1"))
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
        assumeTrue("an AMP-enabled build; listed in app/src/test/amp-enabled-only.txt", Amp.ENABLED)
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
