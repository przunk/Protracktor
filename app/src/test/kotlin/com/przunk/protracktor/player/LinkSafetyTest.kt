// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import com.przunk.protracktor.ui.ForeignHosts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.zip.Deflater

/**
 * RV-B001 and RV-B032: what a shared link may make the app fetch without a question, and how much a
 * link may inflate to.
 */
class LinkSafetyTest {
    private val page = "https://przunk.github.io/Protracktor/src/"

    /** As the page and the app pack a list: deflate, URL-safe base64. */
    private fun link(vararg rows: String): String {
        val raw = rows.joinToString("\n").toByteArray()
        val deflater = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(raw); finish() }
        val buffer = ByteArray(raw.size + 1024)
        var total = 0
        while (!deflater.finished()) total += deflater.deflate(buffer, total, buffer.size - total)
        deflater.end()
        return page + "#" + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.copyOf(total))
    }

    @Test fun `the catalogues' own addresses are not foreign`() {
        val opened = QueueLink.open(link(
            "Protracker/4-Mat/elysium.mod",
            "https://modland.com/pub/modules/AHX/Pink/frog.ahx",
            "https://asma.atari.org/asma/Composers/Aki/Robots.sap",
            "https://api.modarchive.org/downloads.php?moduleid=42#lotus.mod\tL3_CD6",
        ))!!
        assertEquals(4, opened.tracks.size)
        assertEquals(emptyList<String>(), opened.foreign)
    }

    @Test fun `an address on any other server is named as foreign, and kept for the question`() {
        val opened = QueueLink.open(link(
            "Protracker/4-Mat/elysium.mod",
            "https://evil.example/x.mod",
            "http://192.168.1.5/y.sid",
            "https://modland.com.evil.example/pub/modules/z.mod",
        ))!!
        assertEquals(4, opened.tracks.size)
        assertEquals(
            listOf("https://evil.example/x.mod", "http://192.168.1.5/y.sid", "https://modland.com.evil.example/pub/modules/z.mod"),
            opened.foreign,
        )
    }

    @Test fun `a document or file address in a link is not opened at all`() {
        val opened = QueueLink.open(link(
            "content://com.android.providers.downloads.documents/document/42",
            "file:///sdcard/Music/secret.mod",
            "Protracker/4-Mat/elysium.mod",
        ))!!
        assertEquals(listOf("https://modland.com/pub/modules/Protracker/4-Mat/elysium.mod"), opened.tracks.map { it.id })
        assertEquals(emptyList<String>(), opened.foreign)
    }

    @Test fun `a list that inflates past the bound is not read`() {
        val huge = Array(QueueLink.MAX_INFLATED / 20 + 1) { "Protracker/A/tune$it.mod" }
        assertNull(QueueLink.open(link(*huge)))
        assertNotNull(QueueLink.open(link(*huge.copyOf(1000).requireNoNulls())))
        assertNull(QueueLink.decode("@@not base64@@"))
        assertNull(QueueLink.decode("AAAA"))
    }

    @Test fun `the question names each server once, the first three`() {
        assertEquals("evil.example", ForeignHosts.of(listOf("https://evil.example/a.mod", "https://evil.example/b.mod")))
        assertEquals("a.example, b.example, c.example, …",
            ForeignHosts.of(listOf("https://a.example/1", "https://b.example/2", "https://c.example/3", "https://d.example/4")))
        assertTrue(ForeignHosts.of(listOf("http://192.168.1.5/y.sid")).contains("192.168.1.5"))
    }
}
