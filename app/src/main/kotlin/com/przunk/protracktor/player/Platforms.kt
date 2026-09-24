// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * Which machine a tune was written for.
 *
 * The filter offers platforms rather than **Modland, ASMA, The Mod Archive**, which would be our
 * plumbing showing through. Somebody looking for C64 music does not care which archive holds it,
 * and asking them to know that ASMA is Atari 8-bit is asking them to know about us rather than
 * about music (`docs/WISHLIST.md` B23).
 *
 * **The table is the decision, and it can be wrong in two directions.** A platform nobody picks is a
 * wasted row; a format filed under the wrong platform *hides music*, which is worse. So it is built
 * from the archive rather than from memory: Modland's 339 format directories were counted, and the
 * mapping below covers the ones that matter — the top twenty directories are 84% of the archive and
 * 240 of the 339 hold fewer than a hundred files each.
 *
 * **What is deliberately not here is how much of each platform plays.** That is counted from the
 * index at the moment the filter is drawn, because it is a fact about this build and this user's
 * downloads, not about the platforms. A hard-coded "supported" flag would be a claim that goes stale
 * the day a backend lands — and one did land yesterday.
 *
 * Two ways in, because the two sources name formats differently. A catalogue row carries Modland's
 * directory name in its `format` column; a local file carries only its own name. Both are listed
 * per platform so neither source needs a second table.
 */
object Platforms {

    data class Platform(
        val id: String,
        /** Shown on the chip. Proper nouns, so the same in every language. */
        val name: String,
        /** Modland's directory names, lower-cased. What a catalogue row's `format` column holds. */
        val catalogueFormats: Set<String>,
        /** Extensions and Amiga-style prefixes, matching [SupportedFormats]. */
        val names: Set<String>,
    )

    val AMIGA = Platform(
        id = "amiga",
        name = "Amiga",
        catalogueFormats = setOf(
            "protracker", "soundtracker", "soundtracker 2.6", "startrekker", "startrekker amos",
            "noisetracker", "his master's noise", "octamed mmd0", "octamed mmd1", "octamed mmd2",
            "octamed mmd3", "octamed", "med", "ahx", "hively tracker", "oktalyzer",
            "digibooster pro", "digibooster", "tfmx", "delitracker custom", "musicline editor",
            "iff-smus", "future composer", "future composer 1.3", "future composer 1.4",
            "sidmon 1.0", "sidmon 2.0", "the player 6.1a", "the player 6.0a", "the player 5.0a",
            "art of noise", "dynamic synthesizer", "sound programming language", "puma tracker",
            "tcb tracker", "grave composer", "unic tracker", "kris tracker", "sonic arranger",
            "symphonie", "digital symphony", "in stereo", "audiosculpture", "musicmaker",
            "images music system", "jamcracker", "protracker ist", "soundfx", "bp soundmon 2",
            "bp soundmon 3",
            "david whittaker", "sidmon 2", "delta music 2", "hippel coso", "digital mugician",
            "pretracker", "hippel", "fredmon", "musicmaker v8", "dave lowe", "dave lowe new",
            "ben daglish", "synthesis", "mark ii", "sound images", "beathoven synthesizer",
            "special fx", "aprosys", "activision pro", "wally beben", "sound master ii v3",
        ),
        names = setOf(
            "mod", "med", "mmd0", "mmd1", "mmd2", "mmd3", "ahx", "hvl", "okt", "okta", "dbm",
            "digi", "stk", "sfx", "ice", "gmc", "unic", "kris", "puma", "tcb", "fc", "fc13",
            "fc14", "smod", "dsym", "symmod", "gtk", "mms",
            // UADE's, and `SupportedFormats` says which directory each was measured in.
            "ml", "cus", "sa", "bp", "bp3", "aon", "dw", "sid2", "dm2", "hipc", "dmu", "prt",
            "hip", "fred", "mm8", "mm4", "jam", "dl", "dln", "bd", "syn", "mk2", "tw", "bss", "jd",
            "aps", "avp", "wb", "sm3", "mdat", "cust",
        ),
    )

    val C64 = Platform(
        id = "c64",
        name = "Commodore 64",
        catalogueFormats = setOf("hvsc", "realsid", "sidplayer", "stereo sidplayer", "sid"),
        names = setOf("sid", "psid", "rsid"),
    )

    val ATARI_ST = Platform(
        id = "atari-st",
        name = "Atari ST",
        catalogueFormats = setOf(
            "sndh", "sc68", "ym", "ymst", "quartet st", "quartet", "face the music", "easytrax",
            "hippel st", "hippel st coso", "special fx st",
        ),
        // The last four are ST replays that UADE runs on its emulated Amiga.
        names = setOf("sndh", "sc68", "ym", "etx", "soc", "sog", "hst", "doda"),
    )

    val ATARI_8BIT = Platform(
        id = "atari-8bit",
        name = "Atari 8-bit",
        catalogueFormats = setOf(
            "slight atari player", "chaos music composer", "raster music tracker",
            "theta music composer", "delta music composer", "music protracker",
        ),
        names = setOf(
            "sap", "cmc", "cm3", "cmr", "cms", "dmc", "dlt", "mpt", "mpd", "rmt", "tmc", "tm2",
            "tm8",
        ),
    )

    val PC = Platform(
        id = "pc",
        name = "PC",
        catalogueFormats = setOf(
            "fasttracker 2", "fasttracker", "impulsetracker", "screamtracker 3", "screamtracker",
            "multitracker", "ad lib", "psycle", "digitrakker", "madtracker 2", "imago orpheus",
            "composer 669", "farandole composer", "general digimusic", "graoumftracker",
            "epic megagames masi", "liquid tracker", "octamed soundstudio", "x-tracker",
            "screamtracker 2",
        ),
        names = setOf(
            "xm", "it", "s3m", "mptm", "far", "gdm", "imf", "mdl", "mtm", "ptm", "stm", "ult",
            "669", "amf", "ams", "dmf", "dsm", "dtm", "j2b", "mt2", "psm", "plm", "rtm", "c67",
            "cba", "gt2", "mo3", "xmf", "mmcmp", "pp20", "xpk", "umx",
        ),
    )

    val NINTENDO = Platform(
        id = "nintendo",
        name = "Nintendo",
        catalogueFormats = setOf(
            "nintendo spc", "nintendo sound format", "gameboy sound system", "famitracker",
            "super nintendo sound format", "nintendo ds sound format", "gameboy sound format",
            "ultra64 sound format", "gameboy advance sound format",
        ),
        // `ftm` is here on the archive's evidence rather than on ours: 1,779 of Modland's 1,874
        // are FamiTracker and 95 are Face The Music, whose directory is filed under Atari ST above
        // where the path makes it certain. The extension alone cannot be, so it goes with the
        // majority -- and libopenmpt, awkwardly, is the one that plays the minority
        // (`docs/PLAN_FORMATS.md` §0b). A name standing for two formats is the normal case here.
        names = setOf("spc", "nsf", "nsfe", "gbs", "ftm"),
    )

    val SEGA = Platform(
        id = "sega",
        name = "Sega",
        catalogueFormats = setOf(
            "video game music", "deflemask", "saturn sound format", "dreamcast sound format",
            "genecyst", "sega master system", "sgc", "megadrive gym",
        ),
        names = setOf("vgm", "vgz"),
    )

    val MSX = Platform(
        id = "msx",
        name = "MSX",
        catalogueFormats = setOf(
            "kss", "moonblaster", "mgsdrv", "fac soundtracker", "musica", "oplldrv",
            "soundtracker pro", "musica2",
        ),
        names = setOf("kss"),
    )

    val PC_ENGINE = Platform(
        id = "pc-engine",
        name = "PC Engine",
        catalogueFormats = setOf("hes"),
        names = setOf("hes"),
    )

    val SPECTRUM = Platform(
        id = "spectrum",
        name = "ZX Spectrum",
        catalogueFormats = setOf("spectrum", "ay emul", "zx spectrum"),
        names = setOf(
            "ay",
            // Through ZXTune. Without it this platform has one name and 58 playable files out of
            // 23,891, which is what a greyed-out chip was saying.
            "pt3", "pt2", "pt1", "stc", "st1", "st3", "asc", "as0", "sqt", "stp", "ftc", "gtr",
            // `.vtx` too: a register dump rather than a tracker, but all 879 of Modland's sit
            // under `Spectrum/` and that is the platform they came off.
            "vtx",
            // `psm` is **not** here, and it is the same awkward case as `ftm`: Modland has 90 under
            // "Epic Megagames MASI" and 51 under "Spectrum", where it means Pro Sound Maker. The
            // directories are certain and mapped separately; the extension goes with the majority,
            // which is PC. ZXTune is still offered the file first, so the Spectrum ones open.
        ),
    )

    val SHARP = Platform(
        id = "sharp",
        name = "Sharp X68000",
        catalogueFormats = setOf("mdx", "piston collage", "piston collage protected"),
        names = emptySet(),
    )

    /**
     * NEC's PC-98, which is not the Sharp X68000 however Japanese both are.
     *
     * They were one entry until `./scripts/probe-platforms.py` was pointed at the archive: FMP, PMD
     * and S98 are PC-98 sound drivers and had been filed under Sharp, 22,513 files of them. Nothing
     * on screen would have looked wrong, because neither platform plays anything yet — which is the
     * argument for measuring a table instead of reading it.
     */
    val PC98 = Platform(
        id = "pc98",
        name = "NEC PC-98",
        catalogueFormats = setOf("fm sound driver (fmp)", "pmd", "s98", "euphony"),
        names = emptySet(),
    )

    val SONY = Platform(
        id = "sony",
        name = "PlayStation",
        catalogueFormats = setOf("playstation sound format", "playstation 2 sound format"),
        names = emptySet(),
    )

    /**
     * Every platform, in the order the chips appear.
     *
     * Ordered by how much of Modland each holds, biggest first, so the row reads like the archive
     * rather than like an alphabet. That order is fixed here rather than sorted at runtime: a filter
     * whose buttons move as an index grows is a filter you have to re-read every time.
     */
    val all: List<Platform> = listOf(
        AMIGA, PC, C64, NINTENDO, SEGA, ATARI_ST, ATARI_8BIT, SPECTRUM, PC98, SHARP, SONY, MSX,
        PC_ENGINE,
    )

    private val byCatalogueFormat: Map<String, Platform> =
        all.flatMap { p -> p.catalogueFormats.map { it to p } }.toMap()

    private val byName: Map<String, Platform> =
        all.flatMap { p -> p.names.map { it to p } }.toMap()

    fun byId(id: String): Platform? = all.firstOrNull { it.id == id }

    /**
     * Archives that hold one machine's music and nothing else: every row is that platform, whatever
     * directory or name it has (`docs/STATUS.md` C89). ASMA files its tunes under `Composers`,
     * `Games`, `Unknown`, `Misc` and `Groups`, which say who and not what -- and so, matched by
     * directory, not one of its 6,335 Atari tunes was ever Atari 8-bit to the filter.
     */
    val wholeCatalogues: Map<String, Platform> by lazy {
        mapOf("asma" to ATARI_8BIT, "unexotica" to AMIGA)
    }

    /**
     * The platform of a catalogue row, **the one rule** the index stores in its `platform` column
     * and the filter, the chips and the dice read (C89): the archive when it is one machine's, then
     * the directory the archive files it under, then the file's own name. The name comes last
     * because it can be two things -- `psm` is Epic MegaGames and Pro Sound Maker -- and the
     * directory knows which.
     */
    fun forCatalogueRow(catalogueId: String, format: String, fileName: String): Platform? =
        wholeCatalogues[catalogueId] ?: forCatalogueFormat(format) ?: forFileName(fileName)

    /**
     * Changes whenever anything that decides a row's platform does, so an index stored before the
     * change is re-decided at the next start -- part of `NativeEngine.backendsFingerprint`, beside
     * the list of names, for the same reason.
     */
    val fingerprint: String by lazy {
        val table = all.joinToString("|") { p ->
            p.id + ":" + p.catalogueFormats.sorted().joinToString(",") + ":" + p.names.sorted().joinToString(",")
        } + "|" + wholeCatalogues.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value.id}" }
        "platforms:%08x".format(table.hashCode())
    }

    /**
     * [forCatalogueRow] as one SQL `CASE`, over the `catalogue_id`, `format`, `ext` and `pre` columns,
     * and its arguments. Built from the same maps the function reads, in the same order -- whole
     * archive, directory, extension, prefix -- so the stored column and the function cannot
     * disagree; `CatalogueStore` writes the column with it and `PlatformColumnTest` holds the two to
     * one answer for every name and directory in the table.
     */
    fun sqlCase(): Pair<String, Array<String>> {
        val parts = StringBuilder("CASE")
        val arguments = mutableListOf<String>()
        fun whenIn(column: String, values: Collection<String>, platform: Platform) {
            if (values.isEmpty()) return
            parts.append(" WHEN $column IN (${values.joinToString(",") { "?" }}) THEN ?")
            arguments += values
            arguments += platform.id
        }
        wholeCatalogues.forEach { (catalogue, platform) -> whenIn("catalogue_id", listOf(catalogue), platform) }
        byCatalogueFormat.entries.groupBy({ it.value }, { it.key }).forEach { (platform, formats) ->
            whenIn("lower(format)", formats, platform)
        }
        val byNameGrouped = byName.entries.groupBy({ it.value }, { it.key })
        byNameGrouped.forEach { (platform, names) -> whenIn("ext", names, platform) }
        byNameGrouped.forEach { (platform, names) -> whenIn("pre", names, platform) }
        parts.append(" ELSE '' END")
        return parts.toString() to arguments.toTypedArray()
    }

    /** The platform a catalogue row belongs to, from its `format` column. Null when unmapped. */
    fun forCatalogueFormat(format: String): Platform? = byCatalogueFormat[format.trim().lowercase()]

    /**
     * The platform a file belongs to, from its name.
     *
     * Both conventions, the same two rules `SupportedFormats` uses: the extension, or the prefix
     * before the first dot. Modland names ProTracker files `mod.title`, so the prefix rule is not a
     * curiosity.
     */
    fun forFileName(fileName: String): Platform? {
        val name = fileName.lowercase()
        if (!name.contains('.')) return null
        byName[name.substringAfterLast('.')]?.let { return it }
        return byName[name.substringBefore('.')]
    }

    /** True when the file belongs to one of the given platforms. */
    fun matches(fileName: String, ids: Set<String>): Boolean =
        forFileName(fileName)?.id in ids
}
