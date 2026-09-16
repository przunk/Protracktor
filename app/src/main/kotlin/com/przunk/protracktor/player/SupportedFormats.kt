// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * Which files a folder scan picks up.
 *
 * **Provisional, and by extension only.** The right answer is to probe the content, the way ZXTune
 * does, because extensions in this world are unreliable, absent, or shared between unrelated
 * formats — `docs/ARCHITECTURE.md` §5 says so. Probing means reading every candidate during a scan,
 * which is a cost that belongs with the persistent index (R9), not with a scan that currently
 * happens in the foreground. Until then this list keeps a scan from filling the playlist with
 * photographs.
 *
 * Prefixed names matter too: ProTracker files are conventionally `mod.title`, not `title.mod`.
 */
object SupportedFormats {

    /** What the backends handle today. Grows as more are added. */
    val extensions: Set<String> = setOf(
        // The mainstream trackers
        "mod", "xm", "s3m", "it", "mptm",
        // Amiga and Atari lineage
        // "ahx" and "hvl" were here from the start on the assumption that libopenmpt handled them.
        // It does not -- no AHX loader exists in its source -- so they were removed on 2026-09-04
        // after measuring 0 of 12 and 0 of 6. They are back because HivelyTracker's replayer is now
        // a backend, and this time the number is 80 of 80: loaded from a buffer, audible, and
        // reaching a song end (`docs/PLAN_FORMATS.md` §6). Identified by content -- "THX" and "HVL"
        // at offset zero -- so these two only decide what a folder scan picks up.
        "ahx", "hvl",
        "med", "okt", "dbm", "digi", "stk", "sfx", "ice", "gmc", "unic", "kris",
        "puma", "tcb", "fc", "fc13", "fc14", "smod", "dsym", "symmod", "ftm", "etx",
        // The same two formats under the names their archives actually use. libopenmpt identifies
        // OctaMED by an "MMD" magic and Oktalyzer by its own, and never looks at the filename --
        // so these played all along and were never offered. Measured 2026-09-04: 5,558 files in
        // Modland alone, 6 of 6 sampled from each playing (`docs/PLAN_FORMATS.md` §4).
        "mmd0", "mmd1", "mmd2", "mmd3", "okta",
        // Two more of the same kind, found by widening the sweep from 60 formats to 150. Small --
        // 95 files between them in Modland -- and free, which is the whole argument. Both play 3
        // of 3 sampled.
        "gtk", "mms",
        // PC and console lineage
        "far", "gdm", "imf", "mdl", "mtm", "ptm", "stm", "ult", "669", "amf", "ams", "dmf",
        "dsm", "dtm", "j2b", "mt2", "psm", "plm", "rtm", "c67", "cba", "gt2", "mo3", "xmf",
        // Atari ST, through sc68. SNDH is 68000 machine code rather than note data, which is why it
        // needs an emulator and why nothing else in this list resembles it.
        // "snd" is deliberately absent: in Modland it means Westwood SND, a DOS format nothing here
        // plays, and listing it only indexes files that cannot open.
        "sndh", "sc68",
        // Atari 8-bit, through ASAP. "fc" is already above and means the Amiga Future Composer to
        // libopenmpt and the Atari one to ASAP -- whichever loads it wins, which is the only way to
        // settle that without probing content.
        "sap", "cmc", "cm3", "cmr", "cms", "dmc", "dlt", "mpt", "mpd", "rmt", "tmc", "tm2", "tm8",
        // Consoles, through game-music-emu. It identifies by content, so these extensions only
        // decide what a folder scan picks up -- not what plays.
        // "gym" is deliberately absent. Measured 2026-09-04: all 40 sampled Modland GYM files are
        // packed, and game-music-emu refuses packed GYM unconditionally -- "Packed GYM file not
        // supported" is in its source with no build option behind it. Listing the extension only
        // indexed 265 files that cannot open.
        "nsf", "nsfe", "spc", "gbs", "vgm", "vgz", "hes", "ay", "kss",
        // ZX Spectrum, through ZXTune. AY-3-8912 trackers: 23,891 Modland files, of which this app
        // played 58 before -- and those 58 only because `.ay` is a name game-music-emu also uses.
        // Measured 2026-09-07: 72 of 72 sampled files played (`docs/PLAN_FORMATS.md` §7).
        // "ay" is deliberately absent from this group: it stays with game-music-emu, whose AY
        // support is the ZX Spectrum *snapshot* format rather than a tracker, and ZXTune's own
        // reader for it is the one plugin whose licence we cannot take.
        "pt3", "pt2", "pt1", "stc", "st1", "st3", "asc", "as0", "sqt", "stp", "psm", "ftc", "gtr",
        // Register dumps rather than trackers, and the same AY chip plays them back. "ym" was
        // listed with the Atari ST group from the start on the assumption that sc68 handled it; it
        // never did, and 4,961 Modland files sat in every index unopenable (`docs/STATUS.md` C20).
        // Vendoring lhasa made ZXTune's `ym_vtx` decoder buildable -- Modland's YM files are
        // LHA-packed -- and the measurement is 20 of 20 and 20 of 20 (`docs/PLAN_FORMATS.md` §8).
        //
        // **"vtx" is new here, and it costs a re-index.** Adding a name changes `fingerprint`, so
        // every stored index goes stale and the owner downloads Modland's 40 MB again. It buys 879
        // files that play, which is the version of that bargain worth taking -- the one C20 argued
        // against was paying the same price to *remove* rows.
        "ym", "vtx",
        // Commodore 64, through libsidplayfp. Identified by a four-byte magic, so these extensions
        // only decide what a folder scan picks up.
        "sid", "psid", "rsid",
        // Containers libopenmpt unpacks itself
        "mmcmp", "pp20", "xpk", "umx",
    )

    /**
     * Names this build can play that **no catalogue carries**.
     *
     * **A separate set, and the separation is the whole point.** `extensions` is what a catalogue
     * index is filtered through *and* what `fingerprint` is computed from, so a name added there
     * marks every stored index stale — the owner re-downloads Modland's 40 MB. Modland, ASMA, The
     * Mod Archive and UnExoticA hold no MP3 between them, so putting `.mp3` in that list would cost
     * a re-index to gain nothing (`docs/BACKLOG.md` A29).
     *
     * A folder scan does not consult either list: it opens every file and lets the decoder answer
     * (`MediaScanner.listFiles`), which is why an MP3 in a scanned folder needs nothing here at all.
     * This set exists for the one place a *name* still has to be judged — deciding whether a
     * failure means "this app cannot play this format" or "this file is broken".
     */
    val localOnlyExtensions: Set<String> = setOf("mp3")

    /** Filename prefixes used instead of extensions by several Amiga trackers. */
    val prefixes: Set<String> = setOf(
        // "ahx" and "hvl" are listed for symmetry with the extensions, though Modland files all
        // use the suffix form -- 1,389 and 44, none with a prefix. Costs nothing, and the archives
        // that do use prefixes are not all indexed yet.
        "ahx", "hvl",
        "mod", "med", "okt", "dbm", "digi", "stk", "sfx", "ice", "fc", "smod",
        "sndh",
    )

    /**
     * A short label for what a file is, for the playlist row.
     *
     * The extension, upper-cased — `SAP`, `MOD`, `SPC`. Crude, and right nearly always: the backend
     * knows better but only after the file has been opened, and a row has to say something before
     * that. Amiga prefix names (`mod.title`) are handled the same way from the other end.
     */
    fun labelFor(fileName: String): String {
        val name = fileName.lowercase()
        val extension = name.substringAfterLast('.', "")
        if (extension.isNotEmpty() && extension in extensions) return extension.uppercase()
        val prefix = name.substringBefore('.', "")
        if (prefix.isNotEmpty() && name.contains('.') && prefix in prefixes) return prefix.uppercase()
        return extension.uppercase()
    }

    /**
     * A short, stable digest of this list, for telling a stale index from a current one.
     *
     * An index — of a folder or of an online catalogue — is filtered at build time to names this
     * object accepts, so it is only as good as **this list** and the decoders together. Recording
     * only the decoders was half the truth and the half that had not yet bitten: on 2026-09-04
     * five names were added for formats libopenmpt had been able to play all along, and every
     * existing index was instantly missing 5,558 Modland files while still reporting itself
     * current, because no decoder had changed.
     *
     * Order-independent and cheap, so adding a name here is all it takes to invalidate what the
     * name would have changed. It is a fingerprint, not a checksum: it only has to differ when the
     * list differs.
     */
    val fingerprint: String
        get() = "names:%08x".format(
            (extensions.sorted() + "|" + prefixes.sorted()).joinToString(",").hashCode()
        )

    /**
     * Whether a name is one this build claims **anywhere** — a catalogue or a local file.
     *
     * Used where the question is "should this have worked?": an MP3 that fails to open is a broken
     * file, not an unsupported format, and the app should say so.
     */
    fun looksPlayable(fileName: String): Boolean =
        inCatalogueIndex(fileName) ||
            fileName.lowercase().substringAfterLast('.', "") in localOnlyExtensions

    /**
     * Whether a name earns a row in a downloaded catalogue index.
     *
     * **The narrower question, and the one `fingerprint` is about.** Half a million Modland paths
     * are filtered through this; a name that no archive carries only costs disk and a re-index.
     */
    fun inCatalogueIndex(fileName: String): Boolean =
        extensionOf(fileName) in extensions || prefixOf(fileName) in prefixes

    /**
     * The two halves a name is judged by, **kept apart from the judging**.
     *
     * A catalogue index stores these beside every row and decides playability from them at read
     * time (`docs/ROADMAP_FORMATS.md` step 0). That is the whole point: they depend on the
     * *filename* and not on this list, so adding a format re-decides 516,107 stored rows with one
     * `UPDATE` — 228ms, measured — instead of asking every user to download Modland's 40 MB again.
     *
     * Empty when there is none, and empty is in neither set, so an empty answer is simply not a
     * match. `mod.title` has a prefix and no extension worth having; `tune.mod` the reverse; a name
     * with no dot at all has neither.
     */
    fun extensionOf(fileName: String): String =
        fileName.lowercase().substringAfterLast('.', "")

    fun prefixOf(fileName: String): String {
        val name = fileName.lowercase()
        return if (name.contains('.')) name.substringBefore('.', "") else ""
    }
}
