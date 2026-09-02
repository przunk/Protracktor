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
package com.przunk.protracktor.net

/** One file in the fetched-file cache, as the budget sees it. */
data class CacheEntry(
    val name: String,
    val sizeBytes: Long,
    /** When it was last read or written. Ordering only -- the budget never asks what "now" is. */
    val lastUsedMillis: Long,
)

/**
 * How much fetched music is kept, and what goes first when there is too much.
 *
 * **No Android imports, and no clock.** This decides *what to evict* given a list; reading the
 * directory and deleting files is [RemoteFiles]'s job. That split is what makes the rules testable
 * here rather than discoverable on somebody's phone — `docs/OPEN_QUESTIONS.md` Q5 stayed open for
 * weeks partly because there was nowhere to write a test for it.
 *
 * ### What counts toward the ceiling
 *
 * **Only the fetched-file cache**, and that is structural rather than a rule anybody has to
 * remember: the things that must never be evicted live in different directories entirely.
 *
 * | | where | counted |
 * | --- | --- | --- |
 * | fetched tracks | `cacheDir/remote` | **yes** |
 * | ASMA archive (20 MB) | `filesDir/catalogues` | no |
 * | HVSC song lengths | the database | no |
 * | the local library index | the database | no |
 * | copies made for sharing | `cacheDir/shared` | no |
 *
 * ### What is never evicted
 *
 * - **Incomplete downloads.** A `.part` file is a fetch in progress; deleting it corrupts a
 *   download that is still running, and counting it would charge the user for bytes that may never
 *   become a file. They are invisible to the budget in both directions.
 * - **Anything named as in use** — the track playing and the ones read ahead. Evicting a file
 *   while it is being read is a bug that would look like a corrupt download.
 *
 * A protected file still **counts**. It is real disk taken by real bytes, and pretending otherwise
 * would let the cache sit above its ceiling while reporting that it does not.
 */
object CacheBudget {

    /**
     * The default ceiling: 512 MB.
     *
     * These files are small — kilobytes to a few megabytes — so this is thousands of tunes, which
     * is a library rather than a cache. It is a number rather than a considered measurement, and
     * making it a setting is `docs/BACKLOG.md` A13.
     */
    const val DEFAULT_CEILING_BYTES: Long = 512L * 1024 * 1024

    /** The suffix an unfinished download carries until it is renamed into place. */
    const val PARTIAL_SUFFIX = ".part"

    /** What [entries] hold, ignoring downloads that have not finished. */
    fun totalBytes(entries: List<CacheEntry>): Long =
        entries.filterNot { it.name.endsWith(PARTIAL_SUFFIX) }.sumOf { it.sizeBytes }

    /**
     * Which files to delete to get under [ceilingBytes], oldest use first.
     *
     * Returns them in the order they should go, and stops as soon as the total is under the
     * ceiling: evicting more than necessary costs a re-download for nothing.
     *
     * When protected files alone exceed the ceiling the result is everything else and no more.
     * Deleting a file that is playing to satisfy an arithmetic target would be the cache breaking
     * the app to obey itself.
     */
    fun evictions(
        entries: List<CacheEntry>,
        inUse: Set<String> = emptySet(),
        ceilingBytes: Long = DEFAULT_CEILING_BYTES,
    ): List<String> {
        val counted = entries.filterNot { it.name.endsWith(PARTIAL_SUFFIX) }
        var total = counted.sumOf { it.sizeBytes }
        if (total <= ceilingBytes) return emptyList()

        val doomed = mutableListOf<String>()
        // Oldest use first, and the name as a tie-break so two files touched in the same
        // millisecond do not evict in whatever order the filesystem happened to list them. A
        // deterministic order is what makes this testable at all.
        counted.asSequence()
            .filterNot { it.name in inUse }
            .sortedWith(compareBy({ it.lastUsedMillis }, { it.name }))
            .forEach { entry ->
                if (total <= ceilingBytes) return@forEach
                doomed += entry.name
                total -= entry.sizeBytes
            }
        return doomed
    }
}
