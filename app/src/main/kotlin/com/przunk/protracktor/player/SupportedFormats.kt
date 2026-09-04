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
        // "ahx" and "hvl" are deliberately absent, and were listed here from the start on the
        // assumption that libopenmpt handled them. It does not: there is no AHX loader in its
        // source and neither name appears in its format table. Measured 2026-09-04 -- 0 of 12 AHX
        // and 0 of 6 HVL open -- so listing them indexed 1,433 Modland files that nothing here can
        // play. **UADE plays them** (`docs/PLAN_FORMATS.md` §4), which is where they come back from.
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
        "sndh", "sc68", "ym",
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
        // Commodore 64, through libsidplayfp. Identified by a four-byte magic, so these extensions
        // only decide what a folder scan picks up.
        "sid", "psid", "rsid",
        // Containers libopenmpt unpacks itself
        "mmcmp", "pp20", "xpk", "umx",
    )

    /** Filename prefixes used instead of extensions by several Amiga trackers. */
    val prefixes: Set<String> = setOf(
        // "ahx" and "hvl" are absent here for the same reason as above: nothing we ship loads them.
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

    fun looksPlayable(fileName: String): Boolean {
        val name = fileName.lowercase()
        val extension = name.substringAfterLast('.', "")
        if (extension.isNotEmpty() && extension in extensions) return true
        val prefix = name.substringBefore('.', "")
        return prefix.isNotEmpty() && name.contains('.') && prefix in prefixes
    }
}
