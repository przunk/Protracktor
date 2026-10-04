// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.SongLengths
import com.przunk.protracktor.net.Asma
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.CatalogueEntry
import com.przunk.protracktor.net.Modland
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDownloadsTest {
    private companion object {
        const val METADATA = "https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/1bad3e8/tsv/pretty/md5/metadata.tsv"
        const val LENGTHS = "https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/1bad3e8/tsv/pretty/md5/songlengths.tsv"
        const val FAVOURITES = "https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/master/songdb/sources/site/modland_favourites.tsv"
        const val HASH = "0123456789abcdef0123456789abcdef"

        fun zip(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                for ((name, body) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(body.toByteArray())
                    zip.closeEntry()
                }
            }
        }.toByteArray()
    }

    private class Fixture(scope: CoroutineScope) : LibraryDownloads.Sources, LibraryDownloads.Ui, DownloadText {
        val downloads = LibraryDownloads(scope, this, this, this)
        val events = mutableListOf<String>()
        val busy = mutableSetOf<String>()
        val formatted = mutableListOf<DownloadNotice>()
        val shown = mutableListOf<DownloadNotice>()
        private val messages = mutableMapOf<Long, DownloadNotice>()
        val payloads = mutableMapOf<String, ByteArray?>(
            Modland.indexUrl to zip("allmods.txt" to "100\tProtracker/Artist/song.mod\n200\tUnknown/Artist/song.xyz\n"),
            Asma.indexUrl to zip("asma/Composers/Artist/song.sap" to "SAP", "asma/Docs/readme.txt" to "text"),
            LibraryDownloads.SONG_LENGTHS_URL to "$HASH=1:02 0:03.500\n".toByteArray(),
            METADATA to byteArrayOf(1), LENGTHS to byteArrayOf(2), FAVOURITES to byteArrayOf(3),
        )
        var beforeFetch: suspend (String) -> Unit = {}
        var storeArchiveSucceeds = true
        var playable = 1
        var metadataCount = 20
        var lengthCount = 30
        var favouritesCount = 40
        var favouritePlayable = 10
        val indexed = mutableMapOf<String, List<CatalogueEntry>>()
        var savedLengths = emptyList<SongLengths.Entry>()

        override suspend fun fetchIndex(url: String): ByteArray? {
            events += "fetch:$url"
            beforeFetch(url)
            return payloads[url]
        }
        override suspend fun storeArchive(id: String, bytes: ByteArray): Boolean {
            assertSame(payloads[Catalogue.byId(id)!!.indexUrl], bytes)
            events += "archive:$id"
            return storeArchiveSucceeds
        }
        override suspend fun replaceIndex(catalogue: Catalogue, entries: List<CatalogueEntry>): Int {
            events += "index:${catalogue.id}"
            indexed[catalogue.id] = entries
            return playable
        }
        override suspend fun replaceSongLengths(entries: List<SongLengths.Entry>) {
            events += "lengths"
            savedLengths = entries
        }
        override suspend fun replaceTrackMetadata(bytes: ByteArray): Int {
            assertSame(payloads[METADATA], bytes)
            events += "metadata"
            return metadataCount
        }
        override suspend fun replaceSongDbLengths(bytes: ByteArray): Int {
            assertSame(payloads[LENGTHS], bytes)
            events += "songdb-lengths"
            return lengthCount
        }
        override suspend fun replaceFavourites(bytes: ByteArray): Int {
            assertSame(payloads[FAVOURITES], bytes)
            events += "favourites"
            return favouritesCount
        }
        override suspend fun playableFavourites(): Int {
            events += "count-favourites"
            return favouritePlayable
        }
        override fun begin(key: String, label: String): Boolean {
            events += "begin:$key:$label"
            return busy.add(key)
        }
        override fun end(key: String) {
            events += "end:$key"
            busy -= key
        }
        override suspend fun catalogueStored() { events += "catalogue-stored" }
        override fun songLengthsStored(count: Int) { events += "lengths-stored:$count" }
        override fun trackMetadataStored(metadataCount: Int, lengthCount: Int) {
            events += "metadata-stored:$metadataCount:$lengthCount"
        }
        override fun favouritesStored(playable: Int, listed: Int) { events += "favourites-stored:$playable:$listed" }
        override fun say(message: Message) {
            shown += messages.getValue(message.id)
            events += "say:${messages.getValue(message.id)}"
        }
        override fun refreshCatalogues() { events += "refresh-catalogues" }
        override fun refreshPlatformCounts() { events += "refresh-platforms" }
        override fun label(label: DownloadText.Label) = label.name
        override fun message(notice: DownloadNotice): Message {
            formatted += notice
            events += "format:$notice"
            return Message(notice.toString()).also { messages[it.id] = notice }
        }

        fun fetched(): List<String> = events.filter { it.startsWith("fetch:") }.map { it.removePrefix("fetch:") }
        fun assertBefore(first: String, second: String) {
            assertTrue("$first missing", first in events)
            assertTrue("$second missing", second in events)
            assertTrue("$first before $second", events.indexOf(first) < events.indexOf(second))
        }
    }

    private suspend fun CoroutineScope.drain() = coroutineContext.job.children.toList().joinAll()

    @Test
    fun `Modland stores every listed format before fetching favourites and shows its retained index notice`() = runBlocking {
        val f = Fixture(this)
        f.downloads.indexCatalogue(Modland.id)
        drain()
        val indexed = DownloadNotice.Indexed(Modland.displayName, 2, 1)
        assertEquals(listOf("song.mod", "song.xyz"), f.indexed.getValue(Modland.id).map { it.title })
        assertEquals(listOf(Modland.indexUrl, FAVOURITES), f.fetched())
        assertEquals(listOf(indexed, DownloadNotice.FavouritesDone(10, 40)), f.formatted)
        assertEquals(listOf(indexed), f.shown)
        f.assertBefore("end:modland", "catalogue-stored")
        f.assertBefore("catalogue-stored", "format:$indexed")
        f.assertBefore("format:$indexed", "fetch:$FAVOURITES")
        assertTrue(f.busy.isEmpty())
    }

    @Test
    fun `catalogue download failure releases progress and does not fetch dependent favourites`() = runBlocking {
        val f = Fixture(this).apply { payloads[Modland.indexUrl] = null }
        f.downloads.indexCatalogue(Modland.id)
        drain()
        assertEquals(listOf(Modland.indexUrl), f.fetched())
        assertTrue(f.indexed.isEmpty())
        assertEquals(listOf(DownloadNotice.IndexDownloadFailed(Modland.displayName)), f.shown)
        assertTrue(f.busy.isEmpty())
    }

    @Test
    fun `an archive is saved before index replacement and a failed save prevents replacement`() = runBlocking {
        for (saved in listOf(true, false)) {
            val f = Fixture(this).apply { storeArchiveSucceeds = saved }
            f.downloads.indexCatalogue(Asma.id)
            drain()
            if (saved) {
                f.assertBefore("archive:asma", "index:asma")
                assertEquals(2, f.indexed.getValue(Asma.id).size)
                assertEquals(listOf(DownloadNotice.Indexed(Asma.displayName, 2, 1)), f.shown)
            } else {
                assertTrue(f.indexed.isEmpty())
                assertEquals(listOf(DownloadNotice.ArchiveStoreFailed(Asma.displayName)), f.shown)
            }
            assertTrue(f.busy.isEmpty())
        }
    }

    @Test
    fun `a failed favourites follow-up is reported without undoing the successful Modland index`() = runBlocking {
        val f = Fixture(this).apply { payloads[FAVOURITES] = null }
        f.downloads.indexCatalogue(Modland.id)
        drain()
        assertEquals(2, f.indexed.getValue(Modland.id).size)
        assertEquals(listOf(DownloadNotice.FavouritesFailed), f.shown)
    }

    @Test
    fun `song metadata runs the three sources in order and publishes counts before one final notice`() = runBlocking {
        val f = Fixture(this)
        f.downloads.downloadSongMetadata()
        drain()
        assertEquals(listOf(LibraryDownloads.SONG_LENGTHS_URL, METADATA, LENGTHS), f.fetched())
        assertEquals(listOf(SongLengths.Entry(HASH, listOf(62.0, 3.5))), f.savedLengths)
        f.assertBefore("lengths", "end:${DownloadKeys.SONG_LENGTHS}")
        f.assertBefore("end:${DownloadKeys.SONG_LENGTHS}", "lengths-stored:1")
        f.assertBefore("metadata", "fetch:$LENGTHS")
        f.assertBefore("end:${DownloadKeys.TRACK_METADATA}", "metadata-stored:20:30")
        assertEquals(listOf(DownloadNotice.SongMetadataDone), f.shown)
        assertEquals(listOf("end:${DownloadKeys.SONG_METADATA}", "refresh-catalogues"), f.events.takeLast(2))
        assertTrue(f.busy.isEmpty())
    }

    @Test
    fun `missing or empty SID lengths do not replace them and do not prevent songdb downloads`() = runBlocking {
        for ((bytes, failure) in listOf(null to DownloadNotice.SongLengthsFailed, byteArrayOf() to DownloadNotice.SongLengthsEmpty)) {
            val f = Fixture(this).apply { payloads[LibraryDownloads.SONG_LENGTHS_URL] = bytes }
            f.downloads.downloadSongMetadata()
            drain()
            assertTrue(f.savedLengths.isEmpty())
            assertFalse("lengths" in f.events)
            assertTrue("metadata-stored:20:30" in f.events)
            assertEquals(listOf(failure), f.shown)
            assertTrue(f.busy.isEmpty())
        }
    }

    @Test
    fun `failed or empty metadata leaves SID lengths intact and does not ask for songdb lengths`() = runBlocking {
        for (empty in listOf(false, true)) {
            val f = Fixture(this).apply {
                if (empty) metadataCount = 0 else payloads[METADATA] = null
            }
            f.downloads.downloadSongMetadata()
            drain()
            assertEquals(1, f.savedLengths.size)
            assertFalse(LENGTHS in f.fetched())
            assertEquals(listOf(if (empty) DownloadNotice.TrackMetadataEmpty else DownloadNotice.TrackMetadataFailed), f.shown)
            assertTrue(f.busy.isEmpty())
        }
    }

    @Test
    fun `missing songdb lengths leave stored credits usable and report that specific partial failure`() = runBlocking {
        for (missing in listOf(false, true)) {
            val f = Fixture(this).apply { if (missing) payloads[LENGTHS] = null else lengthCount = 0 }
            f.downloads.downloadSongMetadata()
            drain()
            assertTrue("metadata-stored:20:0" in f.events)
            assertEquals(listOf(DownloadNotice.SongDbLengthsFailed), f.shown)
            assertEquals(!missing, "songdb-lengths" in f.events)
        }
    }

    @Test
    fun `standalone favourites distinguish absent empty and stored but unplayable lists`() = runBlocking {
        for (case in 0..2) {
            val f = Fixture(this).apply {
                if (case == 0) payloads[FAVOURITES] = null
                if (case == 1) favouritesCount = 0
                favouritePlayable = 0
            }
            f.downloads.downloadFavourites()
            drain()
            assertEquals(listOf(when (case) {
                0 -> DownloadNotice.FavouritesFailed
                1 -> DownloadNotice.FavouritesEmpty
                else -> DownloadNotice.FavouritesDone(0, 40)
            }), f.shown)
            assertEquals(case == 2, "count-favourites" in f.events)
            assertEquals(case == 2, "favourites-stored:0:40" in f.events)
            assertTrue(f.busy.isEmpty())
        }
    }

    @Test
    fun `selected steps keep plan order count partial success and report only one summary`() = runBlocking {
        val f = Fixture(this).apply { payloads[LibraryDownloads.SONG_LENGTHS_URL] = null }
        f.downloads.downloadSelected(setOf(DownloadPlan.SONG_METADATA, Asma.id))
        drain()
        assertEquals(listOf(Asma.indexUrl, LibraryDownloads.SONG_LENGTHS_URL, METADATA, LENGTHS), f.fetched())
        assertEquals(listOf(DownloadNotice.AllDownloaded(2, 1)), f.shown)
        assertEquals(listOf("refresh-catalogues", "refresh-platforms"), f.events.takeLast(2))
        assertTrue(f.busy.isEmpty())
    }

    @Test
    fun `unknown empty and busy requests do not duplicate work or count skipped steps as failures`() = runBlocking {
        val f = Fixture(this)
        f.downloads.indexCatalogue("unknown")
        f.downloads.downloadSelected(setOf("unknown"))
        assertTrue(f.events.isEmpty())
        f.busy += Asma.id
        f.downloads.indexCatalogue(Asma.id)
        drain()
        assertTrue(f.fetched().isEmpty())
        f.downloads.downloadSelected(setOf(Asma.id, DownloadPlan.SONG_METADATA))
        f.downloads.downloadSelected(setOf(Asma.id, DownloadPlan.SONG_METADATA))
        drain()
        assertEquals(listOf(LibraryDownloads.SONG_LENGTHS_URL, METADATA, LENGTHS), f.fetched())
        assertEquals(listOf(DownloadNotice.AllDownloaded(2, 0)), f.shown)

        val grouped = Fixture(this)
        grouped.downloads.downloadSongMetadata()
        grouped.downloads.downloadSongMetadata()
        drain()
        assertEquals(3, grouped.fetched().size)
        assertEquals(listOf(DownloadNotice.SongMetadataDone), grouped.shown)
    }

    @Test(timeout = 5_000)
    fun `cancellation keeps completed data cleans up and retains the existing absent stopped notice`() = runBlocking {
        val f = Fixture(this)
        val waiting = CompletableDeferred<Unit>()
        f.beforeFetch = { url ->
            if (url == LibraryDownloads.SONG_LENGTHS_URL) {
                waiting.complete(Unit)
                awaitCancellation()
            }
        }
        f.downloads.downloadSelected(setOf(Asma.id, DownloadPlan.SONG_METADATA))
        waiting.await()
        f.downloads.cancelDownloads()
        drain()
        assertEquals(2, f.indexed.getValue(Asma.id).size)
        assertFalse(METADATA in f.fetched())
        assertTrue(f.busy.isEmpty())
        assertTrue(f.shown.isEmpty()) // 1.0.0 checks isActive in NonCancellable; tracked in STATUS.
        assertEquals(listOf("refresh-catalogues", "refresh-platforms"), f.events.takeLast(2))

        f.beforeFetch = {}
        f.downloads.downloadSelected(setOf(DownloadPlan.SONG_METADATA))
        drain()
        assertEquals(listOf(DownloadNotice.AllDownloaded(2, 0)), f.shown)
    }

    @Test
    fun `download everything uses all offered choices and keeps the Modland favourites dependency`() = runBlocking {
        val f = Fixture(this)
        f.downloads.downloadEverything()
        drain()
        val expected = DownloadPlan.stepsFor(DownloadPlan.choices().toSet()).flatMap { step ->
            when (step) {
                DownloadKeys.SONG_LENGTHS -> listOf(LibraryDownloads.SONG_LENGTHS_URL)
                DownloadKeys.FAVOURITES -> listOf(FAVOURITES)
                DownloadKeys.TRACK_METADATA -> listOf(METADATA, LENGTHS)
                else -> listOf(Catalogue.byId(step)!!.indexUrl)
            }
        }
        assertEquals(expected, f.fetched())
        assertEquals(1, f.shown.size)
        assertTrue(f.shown.single() is DownloadNotice.AllDownloaded)
        assertTrue(f.busy.isEmpty())
    }
}
