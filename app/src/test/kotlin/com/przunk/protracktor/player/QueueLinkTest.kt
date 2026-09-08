// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import java.util.Base64
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packing a playlist into a link.
 *
 * The unpacking happens in a browser, so what is checked here is that the bytes are what the page's
 * `DecompressionStream('deflate')` expects: zlib-wrapped deflate, URL-safe base64, newline-separated
 * paths. An `Inflater` is the same format from the other side.
 */
class QueueLinkTest {

    private fun track(id: String, title: String = id) = TrackRef(id = id, title = title, subtitle = "")

    private fun unpack(fragment: String): List<String> {
        val bytes = Base64.getUrlDecoder().decode(fragment)
        val inflater = Inflater()
        inflater.setInput(bytes)
        val out = ByteArray(64 * 1024)
        val n = inflater.inflate(out)
        inflater.end()
        return String(out, 0, n, Charsets.UTF_8).split("\n")
    }

    @Test
    fun `modland tracks travel as paths and come back whole`() {
        val packed = QueueLink.pack(
            listOf(
                track("https://modland.com/pub/modules/Protracker/4-Mat/hi%20there.mod", "hi there.mod"),
                track("https://modland.com/pub/modules/AHX/Pink/frog.ahx", "frog.ahx"),
            )
        )
        assertEquals(2, packed.sent)
        assertEquals(0, packed.left)
        assertEquals(
            listOf("Protracker/4-Mat/hi there.mod", "AHX/Pink/frog.ahx"),
            unpack(packed.fragment),
        )
    }

    /**
     * The one that matters, and the reason `left` exists.
     *
     * A local file's identity is a storage grant to one app on one phone, so it cannot mean anything
     * in a browser. Dropping it silently would shorten the playlist and look like success --
     * `docs/PLAN_WEB.md` §8 calls that worse than refusing.
     */
    @Test
    fun `local files are counted, not quietly dropped`() {
        val packed = QueueLink.pack(
            listOf(
                track("content://com.android.providers.media.documents/document/audio%3A42", "a local file.mod"),
                track("https://modland.com/pub/modules/Protracker/4-Mat/hi%20there.mod", "hi there.mod"),
            )
        )
        assertEquals(1, packed.sent)
        assertEquals(1, packed.left)
        assertEquals(listOf("Protracker/4-Mat/hi there.mod"), unpack(packed.fragment))
    }

    @Test
    fun `a queue with nothing portable packs nothing`() {
        val packed = QueueLink.pack(listOf(track("content://x/1"), track("content://x/2")))
        assertEquals(0, packed.sent)
        assertEquals(2, packed.left)
        assertEquals("", packed.fragment)
    }

    /** The measurement the whole design rests on, as a test rather than as a claim in a document. */
    @Test
    fun `fifty tracks fit in a link that is safe everywhere`() {
        val tracks = (1..50).map {
            track(
                "https://modland.com/pub/modules/Protracker/Some%20Artist/a%20tune%20number%20$it.mod",
                "a tune number $it.mod",
            )
        }
        val packed = QueueLink.pack(tracks)
        assertEquals(50, packed.sent)
        val link = QueueLink.linkTo(QueueLink.DEFAULT_BASE, packed.fragment)
        assertTrue("$link is ${link.length} characters", link.length < 2000)
    }

    /**
     * The owner's report: a Mod Archive row read `lotus3_4.mod` in the browser and
     * `L3_CD4-SpaceNinja` on the phone, because the URL is the only thing that travelled.
     */
    @Test
    fun `a title the address does not carry travels with it`() {
        val packed = QueueLink.pack(
            listOf(track("https://api.modarchive.org/downloads.php?moduleid=42#lotus3_4.mod", "L3_CD4-SpaceNinja"))
        )
        assertEquals(
            listOf("https://api.modarchive.org/downloads.php?moduleid=42#lotus3_4.mod\tL3_CD4-SpaceNinja"),
            unpack(packed.fragment),
        )
    }

    @Test
    fun `a title that only repeats the filename is not sent`() {
        val packed = QueueLink.pack(
            listOf(track("https://modland.com/pub/modules/Protracker/4-Mat/hi%20there.mod", "hi there.mod"))
        )
        assertEquals(listOf("Protracker/4-Mat/hi there.mod"), unpack(packed.fragment))
    }

    /**
     * The page and the pairing address are two paths on one machine, which is what lets a scan
     * settle both. The owner's tunnel is the case that makes it matter: forty random characters
     * that change whenever it restarts, and nobody is typing those twice.
     */
    @Test
    fun `the page address sits beside the pairing address`() {
        val pairing = "https://dallas-retreat-hygiene-advances.trycloudflare.com/pair/" +
            "0123456789abcdef".repeat(2)
        assertEquals(
            "https://dallas-retreat-hygiene-advances.trycloudflare.com/src",
            pairing.substringBefore("/pair/") + "/src",
        )
        val link = QueueLink.linkTo(
            pairing.substringBefore("/pair/") + "/src",
            QueueLink.pack(listOf(track("https://modland.com/pub/modules/AHX/Pink/frog.ahx", "frog.ahx"))).fragment,
        )
        assertTrue(link, link.startsWith("https://dallas-retreat-hygiene-advances.trycloudflare.com/src/#"))
    }

    @Test
    fun `a non-Modland catalogue keeps its whole URL`() {
        val packed = QueueLink.pack(listOf(track("asma://asma/Games/Rob_Hubbard/tune.sap", "tune.sap")))
        assertEquals(listOf("asma://asma/Games/Rob_Hubbard/tune.sap"), unpack(packed.fragment))
    }
}
