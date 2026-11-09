// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * Where a session plays from, when it is a list in Browse -- what the playlist's cover names, and
 * where its way back leads (`docs/BACKLOG.md` A61, the owner's variant (A), 2026-09-25).
 *
 * **The cover used to say "Playing from search" whatever was playing** -- a folder of Modland, a
 * local folder, History -- which the owner called what it was: a lie. This is read from the Browse
 * state the tune was played from, so it names that and nothing else. Pure, so a JVM test holds it.
 */
sealed interface SessionSource {
    /** A search, with its words; blank when it was a scope and nothing typed. */
    data class Search(val query: String) : SessionSource

    /** A folder: `Modland / Protracker / 4-Mat`, or a local folder's name. */
    data class Folder(val path: String) : SessionSource

    data object History : SessionSource

    /**
     * Tunes Browse's Search found below a place (B61): `Modland / Protracker` and the words. The
     * place alone would be a lie twice over -- the queue is not that folder, and the way back has to
     * bring the words with it, or it lands on hundreds of authors with the tune nowhere in sight.
     */
    data class Found(val place: String, val query: String) : SessionSource

    companion object {
        /** The source a list on [browse] is, or null when Browse shows no list of tunes. */
        fun of(browse: BrowseState): SessionSource? = when (browse.domain) {
            BrowseDomain.SEARCH -> Search(browse.query.trim())
            BrowseDomain.HISTORY -> History
            BrowseDomain.ONLINE -> browse.openAuthor?.let { author ->
                Folder(
                    listOfNotNull(browse.openCatalogue?.displayName, browse.openFormat, author.ifBlank { null })
                        .joinToString(" / ")
                )
            }
            BrowseDomain.LOCAL -> browse.openFolder?.let { Folder(it.displayName) }
            BrowseDomain.ROOT, BrowseDomain.SAVED -> null
        }

        /** Where on [browse] a list is, by name: an archive's level or a folder of the phone's. */
        fun placeOf(browse: BrowseState): String? = when (browse.domain) {
            BrowseDomain.ONLINE -> browse.openCatalogue?.let { catalogue ->
                listOfNotNull(catalogue.displayName, browse.openFormat, browse.openAuthor?.ifBlank { null })
                    .joinToString(" / ")
            }
            BrowseDomain.LOCAL -> browse.openFolder?.let { folder ->
                browse.folderAt.ifBlank { folder.displayName }.split('/').joinToString(" / ")
            }
            else -> null
        }
    }
}
