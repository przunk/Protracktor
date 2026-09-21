// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * The other files a song needs, named from the file that was chosen.
 *
 * **Because a song is not always a file.** TFMX keeps its notes in `mdat.name` and its samples in
 * `smpl.name`, side by side, and the replay routine asks for the second while it plays. Handed the
 * first alone, UADE plays nothing — which is how the first measurement made Modland's largest Amiga
 * custom format look unplayable (`docs/PLAN_FORMATS.md` §4).
 *
 * A name rule rather than a search, and only the rules a measurement needed: the four TFMX
 * spellings `scripts/probe-uade.py` fetches companions for. The indexed half is the one this app
 * offers; the sample half is not a tune and never appears as a row.
 *
 * The case of the chosen file is kept. Modland stores these in lower case and an Amiga disk may
 * not, and the sibling is spelled the way its neighbour is.
 */
object Companions {

    /** The indexed half's prefix → the prefix of the file it needs beside it. */
    private val RULES = mapOf(
        "mdat" to "smpl",
        "tfmx" to "smpl",
        "tfmx1.5" to "smpl",
        "tfmx7v" to "smpl",
    )

    /** File names to fetch beside [fileName]; empty for every format that is one file. */
    fun namesFor(fileName: String): List<String> {
        val dot = fileName.indexOf('.')
        if (dot <= 0 || dot == fileName.lastIndex) return emptyList()
        val prefix = fileName.substring(0, dot)
        val wanted = RULES[prefix.lowercase()] ?: return emptyList()
        val spelled = if (prefix.all { !it.isLetter() || it.isUpperCase() }) wanted.uppercase() else wanted
        return listOf(spelled + fileName.substring(dot))
    }

    /**
     * The same sibling, as a path in the same directory as [path] — a URL, an archive member, a
     * document id — or null when [path] needs none.
     */
    fun siblingPaths(path: String): List<String> {
        val slash = path.lastIndexOf('/')
        val directory = if (slash >= 0) path.substring(0, slash + 1) else ""
        return namesFor(path.substring(slash + 1)).map { directory + it }
    }
}
