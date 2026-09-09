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
     * The one that matters, and it changed on 2026-09-10.
     *
     * A local file's identity is a storage grant to one app on one phone, so its *music* cannot
     * travel. Its **place in the list** can, and must: an outside listener opened a shared link and
     * his list numbered itself differently from the owner's, which is the defect `docs/BACKLOG.md`
     * A28 records. So the row goes as a name under a `phone:` scheme and the page draws it greyed,
     * in its own position, unplayable.
     */
    @Test
    fun `local files travel as names in their own places`() {
        val packed = QueueLink.pack(
            listOf(
                track("content://com.android.providers.media.documents/document/audio%3A42", "a local file.mod"),
                track("https://modland.com/pub/modules/Protracker/4-Mat/hi%20there.mod", "hi there.mod"),
            )
        )
        // `sent` counts what will play; the ghost is not one of them and is not "left" either,
        // because it did travel.
        assertEquals(1, packed.sent)
        assertEquals(0, packed.left)
        assertEquals(
            listOf("phone:a local file.mod", "Protracker/4-Mat/hi there.mod"),
            unpack(packed.fragment),
        )
    }

    @Test
    fun `a queue with nothing portable packs nothing`() {
        // Every row a ghost is a list with no music in it. There is no link worth sending, and the
        // count says so rather than handing over two greyed rows and calling it a handoff.
        val packed = QueueLink.pack(listOf(track("content://x/1"), track("content://x/2")))
        assertEquals(0, packed.sent)
        assertEquals(2, packed.left)
        assertEquals("", packed.fragment)
    }

    /**
     * The names are the luxury, and they are what goes when the link would get long.
     *
     * The real tracks always travel. A link is a URL, about two thousand characters is safe
     * everywhere, and a playlist of local files could otherwise push a working link past it — so
     * over that length the placeholders drop out and are reported, which is what the app did for
     * all of them before A28.
     */
    @Test
    fun `a long queue drops the names before it drops the music`() {
        // **Deflate makes the ghosts nearly free**, and finding that out is most of what this test
        // is worth. A hundred tracks of ordinary English filenames pack to well under the limit
        // whether their names travel or not -- so the limit is reached only by a long queue of
        // *unlike* names, which is what the cryptic ones below are. It fires rarely, which is the
        // right shape for it: most handoffs keep their ghosts and only an unusual one pays.
        //
        // A small LCG rather than `Random`, so the queue is the same on every run.
        fun name(i: Int): String {
            var x = (i * 1103515245L + 12345L) and 0x7fffffff
            return buildString {
                repeat(8) { append("0123456789abcdefghijklmnopqrstuvwxyz"[(x % 36).toInt()]); x /= 36 }
            }
        }

        val real = (1..100).map {
            track("https://modland.com/pub/modules/Protracker/${name(it)}/${name(it + 500)}.mod",
                  "${name(it + 500)}.mod")
        }
        val ghosts = (1..100).map { track("content://x/$it", "${name(it + 9000)}.mod") }
        val packed = QueueLink.pack(real + ghosts)

        assertEquals(100, packed.sent)
        assertEquals(100, packed.left)
        val lines = unpack(packed.fragment)
        assertTrue("no ghost should survive", lines.none { it.startsWith("phone:") })
        assertEquals(100, lines.size)
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
