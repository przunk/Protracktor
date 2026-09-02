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
package com.przunk.protracktor.net

/** One entry of an online catalogue's index, before it reaches the database. */
data class CatalogueEntry(
    val path: String,
    val format: String,
    val author: String,
    val title: String,
    val size: Long,
)

/**
 * An online archive of retro music.
 *
 * Each of these publishes its contents differently, which is why parsing is part of the definition
 * rather than something shared. What they have in common is the shape the app needs: a downloadable
 * index, and a rule for turning an entry's path into a URL.
 */
sealed class Catalogue(
    val id: String,
    val displayName: String,
    val indexUrl: String,
) {
    /** The URL a track is fetched from. */
    abstract fun urlFor(path: String): String

    /**
     * The inverse of [urlFor]: the catalogue path a reference points at, or null if it is not ours.
     *
     * Needed to get from a track back to where it came from -- the author's folder it was found in
     * (`docs/WISHLIST.md` B2). A reference carries the URL and nothing else, so without this the
     * only way back would be guessing from the title.
     *
     * Returning null for a reference belonging to another catalogue is how the caller finds which
     * catalogue a track is from: it asks all of them.
     */
    abstract fun pathFrom(id: String): String?

    /**
     * A link to the collection itself, for when a single file has no address of its own.
     */
    abstract val homeUrl: String

    /**
     * An address for one tune that a person receiving it could actually open, or null if this
     * catalogue publishes none.
     *
     * Modland serves every file over HTTP, so its track URL **is** the shareable link. An archive
     * catalogue has no per-file address at all — what it publishes is one zip — so it answers null,
     * and the share falls back to naming the collection and the path inside it. That is a real
     * thing to act on, and it is the reason this returns null rather than the app quietly offering
     * a link that would mean nothing anywhere but this phone.
     */
    open fun webUrlFor(path: String): String? = urlFor(path)

    /**
     * Whether the thing at [indexUrl] is the whole archive rather than a list of what is in it.
     *
     * Two shapes exist and they are not variations of one another. Modland publishes an index and
     * serves files individually, so browsing is offline and playing needs the network. ASMA
     * publishes **everything** in one 20 MB zip, so downloading it once makes both offline — at the
     * cost of taking the whole thing whether you want six files or six thousand.
     */
    open val isArchive: Boolean get() = false

    /**
     * Whether this catalogue is an online live search service rather than a local offline database.
     */
    open val isOnlineOnly: Boolean get() = false

    /** Turns the downloaded index into entries. Given the raw bytes; several ship them zipped. */
    abstract fun parseIndex(bytes: ByteArray, keep: (String) -> Boolean): List<CatalogueEntry>

    companion object {
        /**
         * **`by lazy` is load-bearing, not style.**
         *
         * A companion's properties are static fields of [Catalogue] itself, so initialising this
         * eagerly meant that *touching `Modland` directly* ran `Catalogue`'s static initialiser
         * first -- which built this list while `Modland`'s own initialiser was still running, and
         * so put a **null** in it, permanently, for the life of the process. The crash would then
         * arrive somewhere else entirely, from a list that could not contain a null.
         *
         * Nothing in the app referred to a catalogue object directly, so nothing had hit it; a
         * test for `owning` did, immediately. Deferring to first access is the fix, because by then
         * every object in the list has finished initialising.
         */
        val all: List<Catalogue> by lazy { listOf(Modland, Asma, ModArchive) }

        fun byId(id: String): Catalogue? = all.firstOrNull { it.id == id }

        /** Which catalogue a track reference belongs to, if any. Each recognises only its own. */
        fun owning(trackId: String): Catalogue? = all.firstOrNull { it.pathFrom(trackId) != null }
    }
}

/**
 * Modland — the big one, and the only one wired up so far.
 *
 * Its whole index is a single 5.75 MB zip holding one text file of about half a million lines, each
 * `<size>\t<Format>/<Author>/<title>`. That is why browsing works with no network at all: the index
 * is downloaded once and lives in the database. The network is first needed when a track is played.
 *
 * Measured on 2026-08-31: the file endpoint answers `Range` requests, and one 212 KB fetch took
 * four seconds — slow enough that fetching the next track ahead matters.
 */
object Modland : Catalogue(
    id = "modland",
    displayName = "Modland",
    indexUrl = "https://modland.com/allmods.zip",
) {
    private const val FILE_BASE = "https://modland.com/pub/modules/"

    override val homeUrl: String = "https://modland.com/"

    override fun urlFor(path: String): String =
        FILE_BASE + path.split('/').joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    override fun pathFrom(id: String): String? {
        if (!id.startsWith(FILE_BASE)) return null
        // Decoded segment by segment rather than whole, so a literal "+" in a filename survives:
        // URLDecoder turns "+" into a space, and urlFor deliberately encoded spaces as %20.
        return id.removePrefix(FILE_BASE).split('/').joinToString("/") { segment ->
            java.net.URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8")
        }
    }

    override fun parseIndex(bytes: ByteArray, keep: (String) -> Boolean): List<CatalogueEntry> {
        val text = java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            zip.nextEntry ?: return emptyList()
            zip.readBytes().toString(Charsets.UTF_8)
        }

        val entries = ArrayList<CatalogueEntry>(200_000)
        text.lineSequence().forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val size = line.substring(0, tab).toLongOrNull() ?: return@forEach
            val path = line.substring(tab + 1)

            val title = path.substringAfterLast('/')
            if (!keep(title)) return@forEach

            // "Format/Author/title" is the convention, but coop releases and unknown authors give
            // deeper and shallower paths. Taking the first segment as the format and everything
            // between as the author keeps both readable instead of dropping them.
            val parts = path.split('/')
            val format = parts.firstOrNull().orEmpty()
            val author = when {
                parts.size >= 3 -> parts.subList(1, parts.size - 1).joinToString("/")
                else -> ""
            }
            entries += CatalogueEntry(path, format, author, title, size)
        }
        return entries
    }
}


/**
 * ASMA — the Atari SAP Music Archive, and the reason ASAP was worth integrating.
 *
 * A different shape from Modland: the whole collection arrives as one zip of about 20 MB holding
 * 6,335 `.sap` files, so there is no index to parse separately — **the archive's own entry list is
 * the index**. Downloading it once makes browsing *and* playing work with no network at all.
 *
 * `asma/Docs/Asma.txt` looked like it might be a metadata index and is not: it is four lines of
 * version banner. The structure is in the paths, which run
 * `asma/<section>/<author>/<title>.sap`.
 */
object Asma : Catalogue(
    id = "asma",
    displayName = "ASMA (Atari 8-bit)",
    indexUrl = "https://asma.atari.org/asmadb/asma.zip",
) {
    /** Read out of the stored archive rather than fetched. The player understands this scheme. */
    override fun urlFor(path: String): String = "asma://$path"

    override val homeUrl: String = "https://asma.atari.org/"

    // No per-file address exists: ASMA publishes one archive, not a file tree.
    override fun webUrlFor(path: String): String? = null

    override fun pathFrom(id: String): String? = id.removePrefix("asma://").takeIf { it != id }

    override val isArchive: Boolean get() = true

    override fun parseIndex(bytes: ByteArray, keep: (String) -> Boolean): List<CatalogueEntry> {
        val entries = ArrayList<CatalogueEntry>(7000)
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue

                val path = entry.name
                val title = path.substringAfterLast('/')
                if (!keep(title)) continue

                // asma/<section>/<author>/<title>. Anything shallower keeps what it has rather than
                // being dropped: a file filed loosely is still a file.
                val parts = path.split('/')
                entries += CatalogueEntry(
                    path = path,
                    format = parts.getOrElse(1) { "" },
                    author = if (parts.size >= 4) parts.subList(2, parts.size - 1).joinToString("/") else "",
                    title = title,
                    size = entry.size.coerceAtLeast(0),
                )
            }
        }
        return entries
    }
}
