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
     * The case that matters most.
     *
     * A local file's identity is a storage grant to one app on one phone, so its *music* cannot
     * travel. Its **place in the list** can, and must: otherwise the two people looking at the
     * same shared link number their lists differently, which is the defect `docs/BACKLOG.md` A28
     * records. So the row goes as a name under a `phone:` scheme and the page draws it greyed, in
     * its own position, unplayable.
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

    /**
     * Arithmetic before it is a preference.
     *
     * The whole handoff rests on a tracker module being kilobytes: Modland's median is 20 KB and the
     * budget for a *whole queue* is eight megabytes. One four-minute MP3 is more than that budget by
     * itself. So an MP3 never travels -- it goes as a name, in its own place, and the page draws it
     * greyed and unplayable, exactly as a file that stayed on the phone does.
     */
    @Test
    fun `an mp3 never travels, wherever it came from`() {
        val packed = QueueLink.pack(
            listOf(
                track("content://x/1", "a recording.mp3"),
                // Even with a perfectly good HTTP address, which no catalogue here would give one --
                // the rule is "always" rather than "when we cannot fetch it".
                track("https://example.org/music/live set.mp3", "live set.mp3"),
                track("https://modland.com/pub/modules/Protracker/4-Mat/hi%20there.mod", "hi there.mod"),
            )
        )
        assertEquals(1, packed.sent)
        assertEquals(0, packed.left)
        assertEquals(
            listOf("phone:a recording.mp3", "phone:live set.mp3", "Protracker/4-Mat/hi there.mod"),
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
     * Without this a Mod Archive row reads `lotus3_4.mod` in the browser and `L3_CD4-SpaceNinja`
     * on the phone, because the URL is the only thing that travels.
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
     * settle both. A tunnel is the case that makes it matter: forty random characters that change
     * whenever it restarts, and nobody is typing those twice.
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
    fun `a non-Modland catalogue goes as the address a browser can fetch`() {
        // ASMA as its own file on asma.atari.org, not the `asma://` this phone reads it by.
        val packed = QueueLink.pack(listOf(track("asma://asma/Games/Rob_Hubbard/tune.sap", "tune.sap")))
        assertEquals(listOf("https://asma.atari.org/asma/Games/Rob_Hubbard/tune.sap"), unpack(packed.fragment))
    }

    @Test
    fun `an ASMA tune goes as a link the page plays`() {
        val tune = track("asma://asma/Composers/Aki/Robots.sap", "Robots")
        val link = QueueLink.trackLink("https://pi.example/src/", tune)!!
        assertEquals(
            listOf("https://asma.atari.org/asma/Composers/Aki/Robots.sap\tRobots"),
            unpack(link.substringAfter("#play:")),
        )
    }

    /**
     * Share with Protracktor: one tune, marked so the page plays it rather than taking it for a
     * queue to replace its list with.
     */
    @Test
    fun `one tune goes as a link the page plays`() {
        val tune = track("https://modland.com/pub/modules/Protracker/Jogeir%20Liljedahl/zoolook.mod", "zoolook")
        val link = QueueLink.trackLink("https://pi.example/src/", tune)!!
        assertTrue(link, link.startsWith("https://pi.example/src/#play:"))
        assertEquals(
            listOf("Protracker/Jogeir Liljedahl/zoolook.mod\tzoolook"),
            unpack(link.substringAfter("#play:")),
        )
    }

    /** `docs/BACKLOG.md` A38: several ticked tunes, as one link that plays the lot. */
    @Test
    fun `several tunes go as one link the page plays`() {
        val link = QueueLink.tracksLink(
            "https://pi.example/src/",
            listOf(
                track("https://modland.com/pub/modules/Protracker/4-Mat/hi%20there.mod", "hi there.mod"),
                track("https://modland.com/pub/modules/AHX/Pink/frog.ahx", "frog.ahx"),
            ),
        )!!
        assertTrue(link, link.startsWith("https://pi.example/src/#play:"))
        assertEquals(
            listOf("Protracker/4-Mat/hi there.mod", "AHX/Pink/frog.ahx"),
            unpack(link.substringAfter("#play:")),
        )
    }

    @Test
    fun `the ones that cannot travel are left out of a link, not sent as placeholders`() {
        val link = QueueLink.tracksLink(
            "https://pi.example/src/",
            listOf(
                track("content://x/1", "mine.mod"),
                track("https://modland.com/pub/modules/AHX/Pink/frog.ahx", "frog.ahx"),
                track("https://example.org/music/live set.mp3", "live set.mp3"),
            ),
        )!!
        assertEquals(listOf("AHX/Pink/frog.ahx"), unpack(link.substringAfter("#play:")))
        assertEquals(null, QueueLink.tracksLink("https://pi.example/src/", listOf(track("content://x/1", "a.mod"))))
    }

    @Test
    fun `a tune the page could not fetch makes no link`() {
        // A file on this phone, an MP3 with an address, and a tune inside an UnExoticA archive.
        listOf(
            track("content://x/1", "mine.mod"),
            track("https://example.org/music/live set.mp3", "live set.mp3"),
            track("unexotica://Game/Composer/Title.lha/Title/mod.name", "mod.name"),
        ).forEach { tune ->
            assertEquals(tune.id, null, QueueLink.trackLink("https://pi.example/src/", tune))
            assertTrue(tune.id, !QueueLink.canSend(tune))
        }
    }

    // --- read back: a link to the page's permanent address opens in the app (A40) ---------------

    private val page = "https://przunk.github.io/Protracktor/src/"

    @Test
    fun `a tune link made here opens back into the same tune, id and title`() {
        val tunes = listOf(
            TrackRef(id = "https://modland.com/pub/modules/Protracker/Jester%20%28Volker%20Tripp%29/elysium.mod", title = "Elysium", fileName = "elysium.mod"),
            TrackRef(id = "https://modland.com/pub/modules/AHX/Pink/frog%2Bprince.ahx", title = "frog+prince.ahx", fileName = "frog+prince.ahx"),
        )
        val link = QueueLink.tracksLink(page, tunes)!!
        val opened = QueueLink.open(link)!!
        assertTrue(opened.play)
        assertEquals(tunes.map { it.id }, opened.tracks.map { it.id })
        assertEquals(listOf("Elysium", "frog+prince.ahx"), opened.tracks.map { it.title })
        assertEquals("Modland/Protracker/Jester (Volker Tripp)", opened.tracks[0].subtitle)
        assertEquals(0, opened.stayed)
    }

    @Test
    fun `an ASMA tune keeps its web address, so it plays whether or not ASMA is on this phone`() {
        val link = QueueLink.tracksLink(page, listOf(TrackRef(id = "asma://asma/Composers/Aki/Robots.sap", title = "Robots.sap", fileName = "Robots.sap")))!!
        assertEquals(listOf("https://asma.atari.org/asma/Composers/Aki/Robots.sap"), QueueLink.open(link)!!.tracks.map { it.id })
    }

    @Test
    fun `a queue link counts the files that stayed on the phone rather than dropping them silently`() {
        val link = QueueLink.linkTo(page, QueueLink.pack(listOf(
            track("https://modland.com/pub/modules/AHX/Pink/frog.ahx", "frog.ahx"),
            track("content://x/1", "mine.mod"),
        )).fragment)
        val opened = QueueLink.open(link)!!
        assertTrue(!opened.play)
        assertEquals(listOf("https://modland.com/pub/modules/AHX/Pink/frog.ahx"), opened.tracks.map { it.id })
        assertEquals(1, opened.stayed)
    }

    @Test
    fun `a link a browser made opens here too`() {
        // Deflated by zlib as `CompressionStream('deflate')` does, and base64url'd as the page does.
        // Three rows: a Modland path, The Mod Archive's address with a title, a file left on a phone.
        val browser = "eJwVysEKwjAMgOGzewrBsw3o8DAQD-6o4BtIWMJabJuQtsreXnr8-f6XSTVcPmwwHp9YgeNWQksuCQ2-Vi0TAGrojbb48GUntgLJL0dBKk693pJQixzoOp4OUWorfd89zu_7fBnUS-Ypbfsiee3wB7YUKeQ"
        val opened = QueueLink.open("$page#$browser")!!
        assertEquals(
            listOf("https://modland.com/pub/modules/Protracker/4-Mat/elysium.mod", "https://api.modarchive.org/downloads.php?moduleid=42#lotus.mod"),
            opened.tracks.map { it.id },
        )
        assertEquals(listOf("elysium.mod", "L3_CD6"), opened.tracks.map { it.title })
        assertEquals("lotus.mod", opened.tracks[1].fileName)
        assertEquals(1, opened.stayed)
    }

    @Test
    fun `only the page's own address is claimed`() {
        assertTrue(QueueLink.isPageLink("$page#play:abc"))
        assertTrue(QueueLink.isPageLink("https://przunk.github.io/Protracktor/#abc"))
        // The lowercase path is a 404 on GitHub Pages; another host is not ours; no fragment is no link.
        assertTrue(!QueueLink.isPageLink("https://przunk.github.io/protracktor/src/#abc"))
        assertTrue(!QueueLink.isPageLink("https://example.org/Protracktor/src/#abc"))
        assertTrue(!QueueLink.isPageLink("http://przunk.github.io/Protracktor/src/#abc"))
        assertTrue(!QueueLink.isPageLink(page))
    }

    @Test
    fun `a link that is not one says so instead of opening nothing`() {
        assertEquals(null, QueueLink.open("$page#not-deflate-at-all"))
        assertEquals(null, QueueLink.open(page))
    }

    @Test
    fun `Share with Protracktor points at the public page, which the app itself opens`() {
        // The owner, 2026-09-25: the link is for somebody else, so it goes to the page anybody can
        // reach, not to the one this phone is paired with. And it lands in `src/`, since the root
        // forwards with a refresh that drops the fragment.
        val link = QueueLink.shareWithProtracktor(
            listOf(TrackRef("https://modland.com/pub/modules/Protracker/4-Mat/elysium.mod", "elysium.mod"))
        )!!
        assertTrue(link, link.startsWith("https://przunk.github.io/Protracktor/src/#"))
        assertTrue(link, QueueLink.isPageLink(link))
        assertEquals(1, QueueLink.open(link)?.tracks?.size)
    }
}
