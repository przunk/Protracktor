// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import com.przunk.protracktor.net.Catalogue

/**
 * What sharing several tunes at once sends (B59, 2026-11-09): pure, so the rules hold on the JVM.
 *
 * One tune's link is built here too, so a single share and a selection cannot word it differently.
 */
internal object SelectionShare {
    /** A tune's link: its catalogue's own address, or the collection named with the path. */
    fun linkFor(ref: TrackRef): String? {
        val catalogue = Catalogue.owning(ref.id) ?: return null
        val path = catalogue.pathFrom(ref.id) ?: return null
        return catalogue.webUrlFor(path)
            ?: "${ref.title} — ${catalogue.displayName}, at $path — ${catalogue.homeUrl}"
    }

    data class Links(val text: String, val shared: Int, val without: Int)

    /** One link per line, in selection order; a file on the phone has none and is counted. */
    fun links(tracks: List<TrackRef>): Links {
        val lines = tracks.mapNotNull(::linkFor)
        return Links(lines.joinToString("\n"), lines.size, tracks.size - lines.size)
    }

    /**
     * Names for the copies handed to the share sheet, unique within one share: two `intro.mod` from
     * different authors would otherwise overwrite each other in the share directory. The second
     * becomes `intro (2).mod`.
     */
    fun uniqueNames(names: List<String>): List<String> {
        val taken = mutableSetOf<String>()
        return names.map { wanted ->
            val name = wanted.ifBlank { "tune" }
            if (taken.add(name.lowercase())) return@map name
            val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
            var n = 2
            var candidate: String
            do { candidate = "${name.substring(0, dot)} ($n)${name.substring(dot)}"; n++ }
            while (!taken.add(candidate.lowercase()))
            candidate
        }
    }
}
