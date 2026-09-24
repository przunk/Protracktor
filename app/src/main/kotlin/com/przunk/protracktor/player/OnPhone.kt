// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.UnExoticA

/**
 * How to tell whether a catalogue tune is on this phone -- the question behind the phone-with-a-tick
 * on a Browse row (the owner, 2026-09-24: "every one we have on the phone").
 *
 * Each archive keeps its tunes differently, so each is asked its own way: a file fetched on its own
 * is in the cache by its address; ASMA's are all in the archive downloaded whole; UnExoticA's sit in
 * their game's `.lha`, cached by that archive's address once any tune of the game has played.
 *
 * **A file of the phone's own library is not marked.** Every row of a local list is on the phone, so
 * a tick on each would say nothing. No Android here, so the rule is a JVM test.
 */
object OnPhone {

    sealed interface Check {
        /** On the phone when the fetched-file cache holds [url]. */
        data class Cached(val url: String) : Check
        /** On the phone when the catalogue [catalogueId], downloaded as one archive, is here. */
        data class Archive(val catalogueId: String) : Check
        /** Not a question with a useful answer: a local file, or nothing this app fetches. */
        data object Never : Check
    }

    fun checkFor(id: String): Check {
        UnExoticA.pathFrom(id)?.let { path ->
            return UnExoticA.archiveUrlFor(path)?.let { Check.Cached(it) } ?: Check.Never
        }
        Catalogue.all.firstOrNull { it.isArchive && id.startsWith("${it.id}://") }?.let { return Check.Archive(it.id) }
        if (id.startsWith("http://") || id.startsWith("https://")) return Check.Cached(id)
        return Check.Never
    }
}
