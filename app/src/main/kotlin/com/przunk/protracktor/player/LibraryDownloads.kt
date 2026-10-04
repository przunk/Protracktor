// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.SongLengths
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.CatalogueEntry
import com.przunk.protracktor.net.Modland
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Downloads reference data using the existing scope, stores and Browse progress owner. */
internal class LibraryDownloads(
    private val scope: CoroutineScope,
    private val sources: Sources,
    private val ui: Ui,
    private val text: DownloadText,
) {
    interface Sources {
        suspend fun fetchIndex(url: String): ByteArray?
        suspend fun storeArchive(id: String, bytes: ByteArray): Boolean
        suspend fun replaceIndex(catalogue: Catalogue, entries: List<CatalogueEntry>): Int
        suspend fun replaceSongLengths(entries: List<SongLengths.Entry>)
        suspend fun replaceTrackMetadata(bytes: ByteArray): Int
        suspend fun replaceSongDbLengths(bytes: ByteArray): Int
        suspend fun replaceFavourites(bytes: ByteArray): Int
        suspend fun playableFavourites(): Int
    }

    interface Ui {
        fun begin(key: String, label: String): Boolean
        fun end(key: String)
        suspend fun catalogueStored()
        fun songLengthsStored(count: Int)
        fun trackMetadataStored(metadataCount: Int, lengthCount: Int)
        fun favouritesStored(playable: Int, listed: Int)
        fun say(message: Message)
        fun refreshCatalogues()
        fun refreshPlatformCounts()
    }

    companion object {
        /**
         * Where HVSC's song lengths come from -- DeepSID's copy of the collection, since 2026-10-01.
         *
         * HVSC asked, on 2026-09-30, that apps not fetch from its website; it allowed this file there
         * only until something else was built. DeepSID's author allowed its API and files on
         * 2026-10-01 (`docs/PLAN_CATALOGUES.md`). Byte-identical to HVSC's own copy that day: 5,205,150
         * bytes, SHA-256 3f2bb5c1...dc397e. A dead URL here costs a message about a failed download,
         * not a crash and not a wrong duration. The host is named in the privacy policy, and
         * `LegalTextTest` fails when it is not.
         */
        internal const val SONG_LENGTHS_URL =
            "https://deepsid.chordian.net/music/_High%20Voltage%20SID%20Collection/DOCUMENTS/Songlengths.md5"

        /**
         * The songdb metadata table: author, publisher, album and year for 380,282 hashes.
         *
         * Fetched from GitHub's raw host rather than from any of the ~400 archives the database was
         * built from -- it is one file, versioned, and the project that assembles it is the one
         * asking to be credited. GPL-2.0-or-later (`docs/LICENSES.md`).
         */
        private const val TRACK_METADATA_LABEL = "Track metadata and lengths"

        /**
         * The songdb revision both files come from, **pinned** (`docs/PLAN_SONGDB_LENGTHS.md` D2).
         *
         * The metadata followed `master` until A52. The repository's README says its author
         * "reserve[s] the right to change the format or location … at any time", and a parser
         * pointed at a moving target breaks on somebody's phone the day it moves. `1bad3e8` is
         * 2026-08-22, the commit A52 was measured against; moving it is a deliberate act.
         */
        private const val SONGDB_REVISION = "1bad3e8"
        private const val SONGDB_BASE =
            "https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/$SONGDB_REVISION/tsv/pretty/md5/"
        private const val TRACK_METADATA_URL = SONGDB_BASE + "metadata.tsv"

        /**
         * songdb's song lengths: every subsong of 476,919 files, the Amiga formats among them
         * (A52). Fetched with the metadata, under the same tick -- one idea, "what songdb knows
         * about a file" (D1). 11.9 MB.
         */
        private const val SONGDB_LENGTHS_URL = SONGDB_BASE + "songlengths.tsv"

        /**
         * Modland's favourites, as `audacious-uade-tools` republishes them.
         *
         * The same repository and licence as the metadata table above (GPL-2.0-or-later), chosen
         * over scraping modland.com for the same reason: it is one file, versioned, and already
         * parsed into a shape somebody maintains. 142 KB, which is why this one download asks for
         * no warning about its size.
         */
        private const val FAVOURITES_LABEL = "Modland favourites"
        private const val FAVOURITES_URL =
            "https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/master/" +
                "songdb/sources/site/modland_favourites.tsv"

        private const val SONG_LENGTHS_LABEL = "SID song lengths"
    }

    /** The run of downloads the offer started, kept so that [cancelDownloads] has something to stop. */
    private var selectedDownloads: Job? = null

    /** What one step of [downloadEverything] did: whether it landed, and what it would have said. */
    private data class Fetched(val ok: Boolean, val message: Message)

    /**
     * Downloads a catalogue's index and stores it.
     *
     * Only entries whose filename a backend might handle are kept. Modland lists about half a
     * million files across formats nothing here can play yet; indexing them all would cost minutes
     * and disk to browse a list of tracks that cannot be opened. Re-run this after adding a backend
     * -- noted in docs/BACKLOG.md so it is not discovered by wondering where the SIDs went.
     */
    fun indexCatalogue(id: String) {
        val catalogue = Catalogue.byId(id) ?: return
        scope.launch {
            val fetched = fetchCatalogue(catalogue) ?: return@launch
            // **Modland brings its favourites** (decided 2026-09-21): Modland's own list of Modland
            // paths, one idea with the archive. Their failure is said only if the index landed.
            if (catalogue.id == Modland.id && fetched.ok) {
                val list = fetchFavourites()
                if (list != null && !list.ok) {
                    ui.say(list.message)
                    return@launch
                }
            }
            ui.say(fetched.message)
        }
    }

    /**
     * One catalogue, fetched and indexed, reporting what happened rather than announcing it.
     *
     * Split from [indexCatalogue] so that [downloadEverything] can run it as one step of several:
     * a combined download must not raise six notices, and it has to know which steps landed.
     * Returns null when this catalogue is already downloading, which is not a failure.
     */
    private suspend fun fetchCatalogue(catalogue: Catalogue): Fetched? {
        if (!ui.begin(catalogue.id, catalogue.displayName)) return null
        run {
            val bytes = sources.fetchIndex(catalogue.indexUrl)
            if (bytes == null) {
                ui.end(catalogue.id)
                return Fetched(false, text.message(DownloadNotice.IndexDownloadFailed(catalogue.displayName)))
            }
            // An archive catalogue's "index" IS the archive, so it is kept rather than parsed and
            // discarded -- afterwards both browsing and playing work with no network at all.
            if (catalogue.isArchive && !sources.storeArchive(catalogue.id, bytes)) {
                ui.end(catalogue.id)
                return Fetched(
                    false,
                    text.message(DownloadNotice.ArchiveStoreFailed(catalogue.displayName)),
                )
            }

            val entries = withContext(Dispatchers.Default) {
                // **Everything the archive lists, and nothing decided here**
                // (`docs/ROADMAP_FORMATS.md` step 0). What this build can play is written beside
                // each row as it is stored and re-decided locally when the format list changes, so
                // the index is no longer a function of the decoders — and adding a format no longer
                // costs every user the whole 40 MB again.
                catalogue.parseIndex(bytes)
            }
            val playable =
                sources.replaceIndex(catalogue, entries)
            ui.end(catalogue.id)
            ui.catalogueStored()
            // **Both numbers, because there are now two** (`docs/ROADMAP_FORMATS.md` step 0).
            // The index keeps everything the archive lists and the app offers what it can open, so
            // saying only the first makes the count on the catalogue's own row look wrong -- a
            // snackbar saying 500,000-odd over a row saying 341,842.
            return Fetched(
                true,
                text.message(DownloadNotice.Indexed(catalogue.displayName, entries.size, playable)),
            )
        }
    }

    /**
     * HVSC's SID lengths and songdb's credits and lengths, as one press with one message.
     *
     * **One button, because nobody should need to know which database says what** about a file.
     * HVSC's lengths rode with Modland until now; they answer "how long is this SID" about tunes
     * from anywhere, a granted folder included, so they belong with the other facts about files
     * rather than with one catalogue (decided 2026-09-21).
     */
    fun downloadSongMetadata() {
        if (!ui.begin(DownloadKeys.SONG_METADATA, text.label(DownloadText.Label.SONG_METADATA))) return
        scope.launch {
            try {
                val results = listOfNotNull(fetchSongLengths(), fetchTrackMetadata())
                val failed = results.firstOrNull { !it.ok }
                ui.say(failed?.message ?: text.message(DownloadNotice.SongMetadataDone))
            } finally {
                withContext(NonCancellable) {
                    ui.end(DownloadKeys.SONG_METADATA)
                    ui.refreshCatalogues()
                }
            }
        }
    }

    /** HVSC's lengths, as one step. See [fetchCatalogue] for why the steps are shaped like this. */
    private suspend fun fetchSongLengths(): Fetched? {
        if (!ui.begin(DownloadKeys.SONG_LENGTHS, SONG_LENGTHS_LABEL)) return null
        val bytes = sources.fetchIndex(SONG_LENGTHS_URL)
        if (bytes == null) {
            ui.end(DownloadKeys.SONG_LENGTHS)
            return Fetched(false, text.message(DownloadNotice.SongLengthsFailed))
        }
        val entries = withContext(Dispatchers.Default) {
            SongLengths.parse(bytes.toString(Charsets.ISO_8859_1))
        }
        if (entries.isEmpty()) {
            ui.end(DownloadKeys.SONG_LENGTHS)
            return Fetched(false, text.message(DownloadNotice.SongLengthsEmpty))
        }
        sources.replaceSongLengths(entries)
        ui.end(DownloadKeys.SONG_LENGTHS)
        ui.songLengthsStored(entries.size)
        return Fetched(
            true,
            text.message(DownloadNotice.SongLengthsDone(entries.size)),
        )
    }

    /** The songdb table, as one step. See [fetchCatalogue]. */
    private suspend fun fetchTrackMetadata(): Fetched? {
        if (!ui.begin(DownloadKeys.TRACK_METADATA, TRACK_METADATA_LABEL)) return null
        run {
            val bytes = sources.fetchIndex(TRACK_METADATA_URL)
            if (bytes == null) {
                ui.end(DownloadKeys.TRACK_METADATA)
                return Fetched(false, text.message(DownloadNotice.TrackMetadataFailed))
            }
            // Parsed straight into the table rather than into a list first. Fifteen megabytes of
            // this becomes 1.9 million strings, and holding them alongside the download peaks near
            // 150 MB -- fine on the JVM these tests run on, an out-of-memory crash on a phone.
            val written = sources.replaceTrackMetadata(bytes)
            if (written == 0) {
                ui.end(DownloadKeys.TRACK_METADATA)
                return Fetched(false, text.message(DownloadNotice.TrackMetadataEmpty))
            }
            // The lengths, second and on the same tick (D1). A failure here does not undo the
            // metadata that did arrive: the two are useful apart, and the message says which half
            // is missing rather than calling the whole download a failure.
            val lengthBytes = sources.fetchIndex(SONGDB_LENGTHS_URL)
            val lengths = if (lengthBytes == null) 0 else sources.replaceSongDbLengths(lengthBytes)
            ui.end(DownloadKeys.TRACK_METADATA)
            ui.trackMetadataStored(written, lengths)
            if (lengths == 0) {
                return Fetched(false, text.message(DownloadNotice.SongDbLengthsFailed))
            }
            return Fetched(
                true,
                text.message(DownloadNotice.TrackMetadataDone(written)),
            )
        }
    }

    /**
     * Modland's favourites on their own. **Not a row any more** -- they come with Modland -- but
     * the Random scope sheet still offers them when the favourites scope is empty: a phone that
     * indexed Modland before the two travelled together has the index and not the list, and a
     * whole Modland re-download to fix that would be six megabytes for 142 KB.
     */
    fun downloadFavourites() {
        scope.launch { fetchFavourites()?.let { ui.say(it.message) } }
    }

    /** Modland's favourites, as one step. See [fetchCatalogue]. */
    private suspend fun fetchFavourites(): Fetched? {
        if (!ui.begin(DownloadKeys.FAVOURITES, FAVOURITES_LABEL)) return null
        val bytes = sources.fetchIndex(FAVOURITES_URL)
        if (bytes == null) {
            ui.end(DownloadKeys.FAVOURITES)
            return Fetched(false, text.message(DownloadNotice.FavouritesFailed))
        }
        val written = sources.replaceFavourites(bytes)
        if (written == 0) {
            ui.end(DownloadKeys.FAVOURITES)
            return Fetched(false, text.message(DownloadNotice.FavouritesEmpty))
        }
        val playable = sources.playableFavourites()
        ui.end(DownloadKeys.FAVOURITES)
        ui.favouritesStored(playable, written)
        return Fetched(
            true,
            text.message(DownloadNotice.FavouritesDone(playable, written)),
        )
    }

    /**
     * Everything an empty install needs, in one press.
     *
     * **Why a button and not a background job at first launch.** It is 46 MB (measured 2026-09-17:
     * Modland 5.5, ASMA 19.2, UnExoticA 1.7, HVSC 5.0, songdb 14.1, favourites 0.1), and an app
     * that spends that on somebody's mobile data four seconds after it is installed has taken a
     * decision that was not its to take. Pressed, with the size on the button, it is the user's
     * decision and `Data safety`'s "fetched on demand" stays true.
     *
     * **Sequential, and the order is the point.** Modland is the smallest useful thing here and the
     * largest catalogue, so somebody who gives up after twenty seconds already has half a million
     * tracks to browse; the 14 MB metadata table, which only improves what is written under a
     * title, goes last. In parallel they would fight for the same connection and write to the same
     * database, and nothing would finish sooner.
     *
     * **Each step keeps its own row spinner** (its own [DownloadKeys] entry), so the screen shows
     * what is happening rather than one opaque bar. A step already running is skipped rather than
     * started twice, which is what makes pressing this while one catalogue downloads harmless.
     *
     * **Partial success is reported as partial.** Six downloads from five hosts will not all land
     * every time, and "done" over a failed ASMA would be a lie the user finds out about later.
     */
    fun downloadSelected(ids: Set<String>) {
        val steps = DownloadPlan.stepsFor(ids)
        if (steps.isEmpty()) return
        if (!ui.begin(DownloadKeys.EVERYTHING, text.label(DownloadText.Label.ALL_RUNNING))) return
        var landed = 0
        var failed = 0
        selectedDownloads = scope.launch {
            try {
                for (step in steps) {
                    val done = runDownloadStep(step) ?: continue
                    if (done.ok) landed++ else failed++
                }
                ui.say(
                    text.message(DownloadNotice.AllDownloaded(landed, failed))
                )
            } finally {
                // **`NonCancellable`, or stopping leaves the screen mid-download for ever.** The
                // spinners are state, not a side effect of the coroutine: cancelled without this,
                // every row the run had reached keeps spinning until the app is restarted, and the
                // offer cannot be pressed again because its own key is still there.
                withContext(NonCancellable) {
                    steps.forEach { ui.end(it) }
                    ui.end(DownloadKeys.EVERYTHING)
                    selectedDownloads = null
                    ui.refreshCatalogues()
                    ui.refreshPlatformCounts()
                    if (!isActive) {
                        ui.say(text.message(DownloadNotice.DownloadsStopped(landed)))
                    }
                }
            }
        }
    }

    /** Everything a fresh install needs, which is every box ticked. */
    fun downloadEverything() = downloadSelected(DownloadPlan.choices().toSet())

    /**
     * Stops a run of downloads.
     *
     * **What is already on this phone stays.** Each step writes its own table when it finishes, so
     * stopping after Modland leaves Modland indexed and the rest untouched -- which is the whole
     * reason the steps are sequential and separate rather than one transaction. What is lost is at
     * most the file being fetched at the moment of the press.
     *
     * A blocking read cannot always be interrupted where it stands, so the step in flight may run
     * to its end; nothing after it will start.
     */
    fun cancelDownloads() {
        selectedDownloads?.cancel()
    }

    /** One step of [downloadSelected], by the key `DownloadPlan` named it with. */
    private suspend fun runDownloadStep(step: String): Fetched? = when (step) {
        DownloadKeys.SONG_LENGTHS -> fetchSongLengths()
        DownloadKeys.FAVOURITES -> fetchFavourites()
        DownloadKeys.TRACK_METADATA -> fetchTrackMetadata()
        else -> Catalogue.byId(step)?.let { fetchCatalogue(it) }
    }

}
