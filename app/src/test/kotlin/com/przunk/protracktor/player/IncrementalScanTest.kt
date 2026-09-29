// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.IndexedFile
import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementalScanTest {

    private fun candidate(uri: String, size: Long, path: String = "Music") =
        MediaScanner.Candidate(uri = uri, path = path, fileName = "$uri.mod", sizeBytes = size)

    private fun indexed(uri: String, size: Long) = IndexedFile(
        uri = uri, folderUri = "tree", path = "Music", fileName = "$uri.mod", sizeBytes = size,
        backend = "openmpt", format = "MOD", title = "Title $uri", author = "", durationMs = 1, subsongs = 1,
    )

    @Test
    fun `a rescan opens only what is new or changed`() {
        val plan = IncrementalScan.plan(
            candidates = listOf(candidate("a", 100), candidate("b", 200), candidate("c", 300)),
            indexed = listOf(indexed("a", 100), indexed("b", 999)),
            stale = false,
        )
        assertEquals(listOf("a"), plan.kept.map { it.uri })
        assertEquals("Title a", plan.kept.single().title)
        assertEquals(listOf("b", "c"), plan.toOpen.map { it.uri })
    }

    @Test
    fun `an index made by other decoders is opened again, all of it`() {
        val plan = IncrementalScan.plan(listOf(candidate("a", 100)), listOf(indexed("a", 100)), stale = true)
        assertEquals(emptyList<IndexedFile>(), plan.kept)
        assertEquals(listOf("a"), plan.toOpen.map { it.uri })
    }

    @Test
    fun `a file gone from the folder is not kept, and a moved one takes its new place`() {
        val plan = IncrementalScan.plan(
            listOf(candidate("a", 100, path = "Music/Atari")),
            listOf(indexed("a", 100), indexed("gone", 5)),
            stale = false,
        )
        assertEquals(listOf("a"), plan.kept.map { it.uri })
        assertEquals("Music/Atari", plan.kept.single().path)
    }
}
