// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.IndexedFile

/**
 * Which files a rescan has to open, and which it already knows (the owner, 2026-09-29: "folders
 * load very slowly").
 *
 * A scan opens every file with a decoder, one after another, and a rescan used to open the same
 * unchanged files all over again. A file the index already holds, at the same address and of the
 * same size, is kept as it was -- unless the index was made by decoders this build no longer has
 * ([stale]), when everything is opened again, since what they decided may have changed. Pure, so a
 * JVM test holds it.
 */
object IncrementalScan {
    class Plan(val kept: List<IndexedFile>, val toOpen: List<MediaScanner.Candidate>)

    fun plan(candidates: List<MediaScanner.Candidate>, indexed: List<IndexedFile>, stale: Boolean): Plan {
        if (stale) return Plan(emptyList(), candidates)
        val known = indexed.associateBy { it.uri }
        val kept = mutableListOf<IndexedFile>()
        val toOpen = mutableListOf<MediaScanner.Candidate>()
        for (candidate in candidates) {
            val before = known[candidate.uri]
            if (before != null && before.sizeBytes == candidate.sizeBytes) {
                // Where it lives now, in case a folder was renamed around it.
                kept += before.copy(path = candidate.path, fileName = candidate.fileName)
            } else {
                toOpen += candidate
            }
        }
        return Plan(kept, toOpen)
    }
}
