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

    /** What libopenmpt handles today. Grows as backends are added. */
    val extensions: Set<String> = setOf(
        // The mainstream trackers
        "mod", "xm", "s3m", "it", "mptm",
        // Amiga and Atari lineage
        "med", "okt", "dbm", "digi", "ahx", "hvl", "stk", "sfx", "ice", "gmc", "unic", "kris",
        "puma", "tcb", "fc", "fc13", "fc14", "smod", "dsym", "symmod", "ftm", "etx",
        // PC and console lineage
        "far", "gdm", "imf", "mdl", "mtm", "ptm", "stm", "ult", "669", "amf", "ams", "dmf",
        "dsm", "dtm", "j2b", "mt2", "psm", "plm", "rtm", "c67", "cba", "gt2", "mo3", "xmf",
        // Containers libopenmpt unpacks itself
        "mmcmp", "pp20", "xpk", "umx",
    )

    /** Filename prefixes used instead of extensions by several Amiga trackers. */
    private val prefixes: Set<String> = setOf(
        "mod", "med", "okt", "dbm", "digi", "ahx", "hvl", "stk", "sfx", "ice", "fc", "smod",
    )

    fun looksPlayable(fileName: String): Boolean {
        val name = fileName.lowercase()
        val extension = name.substringAfterLast('.', "")
        if (extension.isNotEmpty() && extension in extensions) return true
        val prefix = name.substringBefore('.', "")
        return prefix.isNotEmpty() && name.contains('.') && prefix in prefixes
    }
}
