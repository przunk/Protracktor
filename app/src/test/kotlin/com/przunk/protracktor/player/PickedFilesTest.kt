// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** C135: a file picked one by one joins a playlist only if the app can play it. */
class PickedFilesTest {
    private fun picked(name: String) = TrackRef("content://picked/$name", name, fileName = name)

    @Test fun `a camera photo is refused and music is kept`() {
        val split = PickedFiles.split(listOf(picked("PXL_20261108_101500123.jpg"), picked("tune.mod"),
            picked("Commando.sid"), picked("MOD.amiga prefix"), picked("notes.txt")))
        assertEquals(listOf("tune.mod", "Commando.sid", "MOD.amiga prefix"), split.accepted.map { it.fileName })
        assertEquals(listOf("PXL_20261108_101500123.jpg", "notes.txt"), split.refused.map { it.fileName })
    }

    @Test fun `the same rule as a folder scan`() {
        for (name in listOf("a.jpg", "b.mod", "c.sid", "d.png", "e.xm", "f.pdf", "g.nsf")) {
            assertEquals(name, SupportedFormats.looksPlayable(name), PickedFiles.split(listOf(picked(name))).accepted.isNotEmpty())
        }
    }

    @Test fun `a refusal names a few files and then stops`() {
        assertEquals("a.jpg", PickedFiles.named(listOf(picked("a.jpg"))))
        assertEquals("a.jpg, b.png, c.pdf, …", PickedFiles.named(listOf("a.jpg", "b.png", "c.pdf", "d.heic").map(::picked)))
    }
}
