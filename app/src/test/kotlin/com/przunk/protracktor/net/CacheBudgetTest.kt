// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the fetched-file cache keeps, and what goes first.
 *
 * `docs/OPEN_QUESTIONS.md` Q5 was open for weeks with nothing deleted ever, partly because the rule
 * lived nowhere a test could reach it. It lives in [CacheBudget] now, with no clock and no
 * filesystem, so all of this is arithmetic.
 */
class CacheBudgetTest {

    private fun entry(name: String, size: Long, used: Long) = CacheEntry(name, size, used)

    @Test
    fun `nothing is evicted while under the ceiling`() {
        val entries = listOf(entry("a", 40, 1), entry("b", 40, 2))
        assertEquals(emptyList<String>(), CacheBudget.evictions(entries, ceilingBytes = 100))
    }

    @Test
    fun `the least recently used goes first, and only as far as needed`() {
        val entries = listOf(
            entry("newest", 30, 300),
            entry("oldest", 30, 100),
            entry("middle", 30, 200),
        )
        // 90 held, ceiling 60: one file has to go and exactly one should.
        assertEquals(listOf("oldest"), CacheBudget.evictions(entries, ceilingBytes = 60))
        // Ceiling 30: two go, oldest first.
        assertEquals(listOf("oldest", "middle"), CacheBudget.evictions(entries, ceilingBytes = 30))
    }

    @Test
    fun `two files touched in the same millisecond evict in a defined order`() {
        val entries = listOf(entry("b", 30, 100), entry("a", 30, 100), entry("c", 30, 999))
        // Not whatever order the filesystem listed them in: a rule that cannot be predicted cannot
        // be tested, and an untested eviction rule is how a cache eats something it should not.
        assertEquals(listOf("a", "b"), CacheBudget.evictions(entries, ceilingBytes = 30))
    }

    @Test
    fun `an unfinished download is neither charged for nor deleted`() {
        val entries = listOf(
            entry("done", 50, 100),
            entry("half${CacheBudget.PARTIAL_SUFFIX}", 500, 1),
        )
        // The .part file is 500 bytes of a download still running. It must not push the cache over
        // its ceiling, and it must not be deleted out from under the fetch that is writing it.
        assertEquals(50L, CacheBudget.totalBytes(entries))
        assertEquals(emptyList<String>(), CacheBudget.evictions(entries, ceilingBytes = 60))
        assertTrue(CacheBudget.evictions(entries, ceilingBytes = 10).none { it.endsWith(".part") })
    }

    @Test
    fun `a file in use survives even when it is the oldest`() {
        val entries = listOf(entry("playing", 60, 1), entry("idle", 60, 999))
        // "playing" is the least recently used by timestamp and would go first. It is being read.
        assertEquals(listOf("idle"), CacheBudget.evictions(entries, setOf("playing"), ceilingBytes = 60))
    }

    @Test
    fun `protected files that alone exceed the ceiling do not force a deletion`() {
        val entries = listOf(entry("a", 100, 1), entry("b", 100, 2), entry("spare", 10, 3))
        val doomed = CacheBudget.evictions(entries, setOf("a", "b"), ceilingBytes = 50)
        // Everything else goes and no further. Deleting a file that is playing to satisfy an
        // arithmetic target would be the cache breaking the app in order to obey itself.
        assertEquals(listOf("spare"), doomed)
    }

    @Test
    fun `protected files still count toward the total`() {
        val entries = listOf(entry("a", 100, 1), entry("b", 40, 2))
        // They are real bytes on a real disk. Excluding them would let the cache sit above its
        // ceiling while reporting that it does not.
        assertEquals(140L, CacheBudget.totalBytes(entries))
    }

    @Test
    fun `an empty cache is not a special case`() {
        assertEquals(0L, CacheBudget.totalBytes(emptyList()))
        assertEquals(emptyList<String>(), CacheBudget.evictions(emptyList(), ceilingBytes = 0))
    }

    @Test
    fun `the default ceiling is the documented 512 MB`() {
        assertEquals(512L * 1024 * 1024, CacheBudget.DEFAULT_CEILING_BYTES)
    }

    /**
     * What a delete says it did.
     *
     * Both cases here are ones the obvious `bytes / MB` would get wrong. Deleting 300 KB really
     * did delete something, and "Freed 0 MB" reads as a button that failed — which sends the user
     * back to press it again. Deleting nothing has to say so plainly rather than claim a
     * suspiciously round success.
     */
    @Test
    fun `freeing a little is not the same as freeing nothing`() {
        assertEquals(CacheBudget.Freed.NOTHING, CacheBudget.describeFreed(0))
        assertEquals(CacheBudget.Freed.NOTHING, CacheBudget.describeFreed(-1))
        assertEquals(CacheBudget.Freed.LESS_THAN_A_MEGABYTE, CacheBudget.describeFreed(1))
        assertEquals(
            CacheBudget.Freed.LESS_THAN_A_MEGABYTE,
            CacheBudget.describeFreed(1024L * 1024L - 1),
        )
    }

    @Test
    fun `megabytes are whole and rounded down`() {
        assertEquals(
            CacheBudget.Freed.Megabytes(1),
            CacheBudget.describeFreed(1024L * 1024L),
        )
        assertEquals(
            CacheBudget.Freed.Megabytes(1),
            CacheBudget.describeFreed(2L * 1024 * 1024 - 1),
        )
        assertEquals(
            CacheBudget.Freed.Megabytes(512),
            CacheBudget.describeFreed(CacheBudget.DEFAULT_CEILING_BYTES),
        )
    }
}
