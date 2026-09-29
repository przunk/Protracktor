// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The owner's own example, 2026-09-29: `Music { Atari { good, bad }, Amiga { xray }, C64 { stupidsounds } }`. */
class LocalTreeTest {

    private fun tune(dir: String, name: String) = dir to TrackRef(id = "$dir/$name", title = name)

    private val music = listOf(
        tune("Music", "intro.mod"),
        tune("Music/Atari/good", "a.sap"), tune("Music/Atari/good", "b.sap"),
        tune("Music/Atari/bad", "c.sap"),
        tune("Music/Amiga/xray", "d.mod"),
        tune("Music/C64/stupidsounds", "e.sid"),
    )

    @Test
    fun `the top shows its folders and only its own files`() {
        val top = LocalTree.level(music, "Music")
        assertEquals(listOf("Amiga", "Atari", "C64"), top.folders.map { it.name })
        assertEquals(listOf(1, 3, 1), top.folders.map { it.tunes })
        assertEquals(listOf("intro.mod"), top.files.map { it.title })
    }

    @Test
    fun `a folder leads on to its own folders, and the deepest holds the files`() {
        val atari = LocalTree.level(music, "Music/Atari")
        assertEquals(listOf("bad", "good"), atari.folders.map { it.name })
        assertEquals("Music/Atari/good", atari.folders[1].path)
        assertEquals(emptyList<TrackRef>(), atari.files)
        assertEquals(listOf("a.sap", "b.sap"), LocalTree.level(music, "Music/Atari/good").files.map { it.title })
    }

    @Test
    fun `a folder named like the start of another is not inside it`() {
        val tunes = listOf(tune("Music/Atari", "a.sap"), tune("Music/Atari2", "b.sap"))
        assertEquals(listOf("a.sap"), LocalTree.level(tunes, "Music/Atari").files.map { it.title })
        assertEquals(emptyList<LocalTree.Folder>(), LocalTree.level(tunes, "Music/Atari").folders)
    }

    @Test
    fun `back goes up one level and stops at the top`() {
        assertEquals("Music/Atari", LocalTree.parent("Music/Atari/good", "Music"))
        assertEquals("Music", LocalTree.parent("Music/Atari", "Music"))
        assertNull(LocalTree.parent("Music", "Music"))
    }

    @Test
    fun `the tree starts at the granted folder, or where every file is when that is not a path`() {
        val paths = music.map { it.first }
        assertEquals("Music", LocalTree.root(paths, "Music"))
        assertEquals("Music", LocalTree.root(paths, "some label"))
        assertEquals("Music/Atari", LocalTree.root(listOf("Music/Atari/good", "Music/Atari/bad"), "x"))
    }
}
