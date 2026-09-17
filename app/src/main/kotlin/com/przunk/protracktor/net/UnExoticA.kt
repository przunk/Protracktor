// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

/**
 * UnExoticA — Amiga **game** soundtracks, one archive per game.
 *
 * Modland is organised by musician and is mostly demoscene; that is the shape of the archive
 * rather than an omission, and it leaves out the music most people remember from games.
 *
 * **This whole file is the feature**, and deliberately: ExoticA have not answered whether per-file
 * fetching is acceptable (`docs/PLAN_UNEXOTICA.md`), so the catalogue has to be removable. [ENABLED]
 * is the switch, and the removal list is in that document — this file, two native files, and two
 * lines elsewhere.
 *
 * Three things make it a different shape from every catalogue before it:
 *
 *  - **The index is somebody else's.** ExoticA publishes no machine-readable list, so the track
 *    listing comes from the community `songdb` project (`docs/reference/songdb.md`), which already
 *    holds one. That means the app never walks their directory tree.
 *  - **The unit of download is a game, not a tune.** Every file lives inside a `.lha`, so playing
 *    one tune fetches the archive its game ships as and unpacks the one member. The archive lands
 *    in the ordinary file cache, so the second tune from the same game costs nothing.
 *  - **The address is a query, not a path.** `files.exotica.org.uk` serves through `?file=...`, and
 *    answers a cold client with a "verifying your browser" page before it will serve anything --
 *    handled once, generically, in [RemoteFiles].
 */
object UnExoticA : Catalogue(
    id = "unexotica",
    displayName = "UnExoticA (Amiga games)",
    // songdb's raw source table for this archive: 16,282 lines, 8,713 of which name a file. Not
    // the pretty `tsv/pretty/*/unexotica.tsv`, which is metadata keyed by hash and holds no paths.
    indexUrl = "https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/master/" +
        "songdb/sources/site/unexotica.tsv",
) {
    /**
     * Whether the catalogue is offered at all.
     *
     * A `const` rather than a setting: it answers a question ExoticA have not, so it is not the
     * user's to decide. `false` hides the catalogue *and* makes [CatalogueStore] drop the rows
     * already stored for it, which is the difference between "turned off" and "hidden".
     */
    const val ENABLED: Boolean = true

    /**
     * How a file is actually fetched.
     *
     * Not a directory path: `files.exotica.org.uk` serves everything through one endpoint with the
     * path as a **percent-encoded query value**, slashes included. This was read off the download
     * link on the site's own wiki page rather than guessed -- every plausible directory form of the
     * same address answers 404, which is why `docs/PLAN_CATALOGUES.md` recorded the wrong rule for
     * a day.
     */
    private const val FILE_BASE = "https://files.exotica.org.uk/?file=exotica/"
    private const val MEDIA_PREFIX = "media/audio/UnExoticA/"

    override val homeUrl: String = "https://www.exotica.org.uk/wiki/UnExoticA"

    /** Read out of an archive this catalogue fetches on demand. Not a URL anything else understands. */
    override fun urlFor(path: String): String = "unexotica://$path"

    override fun pathFrom(id: String): String? = id.removePrefix("unexotica://").takeIf { it != id }

    /**
     * The game's archive, which is a real address a person can open.
     *
     * ASMA answers null here because it publishes one 20 MB zip and nothing smaller. UnExoticA
     * publishes a file per game, so there *is* something to send — it is the whole soundtrack
     * rather than the one tune, which is more than was asked for but never less.
     */
    override fun webUrlFor(path: String): String? = archiveUrlFor(path)

    /**
     * Splits `Game/Composer/Title.lha/Title/mod.name` into the archive and the member inside it.
     *
     * Returns null for a path with no `.lha` segment, which should not exist and would otherwise
     * become a fetch of something that is not an archive.
     */
    fun split(path: String): Pair<String, String>? {
        val marker = path.indexOf(".lha/")
        if (marker < 0) return null
        return path.substring(0, marker + 4) to path.substring(marker + 5)
    }

    /** The URL of the `.lha` holding [path], or null if it names no archive. */
    fun archiveUrlFor(path: String): String? {
        val archive = split(path)?.first ?: return null
        // Encoded whole, slashes and all: they are part of a query value here, not path separators.
        // `URLEncoder` writes a space as "+", which this endpoint reads literally -- and UnExoticA
        // has no spaces in its directory names, but a rule that only works for today's filenames is
        // the kind that breaks silently.
        val encoded = java.net.URLEncoder.encode(MEDIA_PREFIX + archive, "UTF-8").replace("+", "%20")
        return FILE_BASE + encoded
    }

    /**
     * songdb's raw source table, which is a TSV of two different row shapes.
     *
     * Rows of **eleven** columns name a file; rows of **four** state how long one of its later
     * subsongs is. Only the first shape matters here, and taking `NF == 11` is how they are told
     * apart -- the same trick `modland_favourites.tsv` needed, and for the same reason: this is one
     * file serving two purposes.
     *
     * Columns used: 6 is the module format, 8 the uncompressed size, 11 the path. The rest are a
     * hash, subsong numbering, song lengths and checksums.
     */
    override fun parseIndex(bytes: ByteArray, keep: (String) -> Boolean): List<CatalogueEntry> {
        val entries = ArrayList<CatalogueEntry>(9000)
        bytes.toString(Charsets.UTF_8).lineSequence().forEach { line ->
            val columns = line.split('\t')
            if (columns.size != 11) return@forEach
            val path = columns[10]
            if (!path.contains(".lha/")) return@forEach

            val title = path.substringAfterLast('/')
            if (!keep(title)) return@forEach

            entries += CatalogueEntry(
                path = path,
                // "Game" or "Demo" -- the archive's own top-level split, and the only two values.
                format = path.substringBefore('/'),
                // **The game first, then the composer.** Browsing is catalogue → format → author
                // → tracks, and search matches this column and the title — so this one string has
                // to answer both "which game" and "who wrote it". The game leads because the
                // archive is organised around games, which is what people come here looking for;
                // the composer follows so that searching a name in the path finds the tune.
                author = groupOf(path),
                title = title,
                size = columns[7].toLongOrNull() ?: 0L,
            )
        }
        return entries
    }

    /** `Game/Whittaker_David/Total_Recall.lha/...` → `Total Recall · David Whittaker`. */
    private fun groupOf(path: String): String {
        val game = path.substringBefore(".lha/").substringAfterLast('/').replace('_', ' ')
        val composer = composerOf(path.substringAfter('/').substringBefore('/'))
        // "Unknown" is 340 tunes and says nothing. A row reading "Titus the Fox · Unknown" is
        // longer than one reading "Titus the Fox" and carries exactly as much.
        return if (composer.isEmpty() || composer == "Unknown") game else "$game · $composer"
    }

    /**
     * `Whittaker_David` → `David Whittaker`, and `Pipe_Smokers_Cough` → `Pipe Smokers Cough`.
     *
     * **Reversed only for two words**, because that is where the archive's convention is certain:
     * measured 2026-09-09, 524 of 571 composer folders are exactly `Surname_Firstname`, 30 are
     * one-word handles, and the remaining 17 are Dutch surnames (`van_der_Valk_Paul`) or group
     * names (`Pipe_Smokers_Cough`) where any reordering rule guesses. Those keep their own order:
     * it reads slightly oddly and is never *wrong*.
     *
     * The search does not care either way — "Phelan" matches "Phelan Patrick" as happily as
     * "Patrick Phelan". This is for reading.
     */
    private fun composerOf(folder: String): String {
        val words = folder.split('_').filter { it.isNotEmpty() }
        return if (words.size == 2) "${words[1]} ${words[0]}" else words.joinToString(" ")
    }
}

/**
 * Reading a file out of an LHA archive, through lhasa.
 *
 * Amiga archives are LHA the way PC ones are ZIP, and the JVM unpacks the second and not the first.
 * The library is already in the native build for an unrelated reason — ZXTune's `.ym` decoder reads
 * an LHA-compressed stream (`docs/PLAN_FORMATS.md` §8) — so this is a wrapper over something that
 * was going to be linked anyway.
 */
object Lha {
    init {
        // The engine's library, which is where the JNI lives. Loading it twice is a no-op, and this
        // object can be touched before anything has opened a decoder.
        System.loadLibrary("protracktor_engine")
    }

    /** Every member's name as `directory/file`. Empty when the bytes are not an archive. */
    fun list(archive: ByteArray): List<String> = nativeList(archive).toList()

    /**
     * One member's bytes, or null when it is not in there.
     *
     * Matched case-insensitively and with `\` read as `/`: LHA archives written on an Amiga use
     * either separator, and UnExoticA's index names members with one while its archives store them
     * with the other.
     */
    fun extract(archive: ByteArray, member: String): ByteArray? = nativeExtract(archive, member)

    @JvmStatic private external fun nativeList(archive: ByteArray): Array<String>
    @JvmStatic private external fun nativeExtract(archive: ByteArray, member: String): ByteArray?
}
