// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * The tar reader that unpacks UADE's replay routines.
 *
 * **Written because this is the only parser in the app that reads a filename out of somebody
 * else's archive and then writes a file with it.** The archive is fetched over the network from a
 * server nobody here controls, which is the whole population of inputs a path-traversal check
 * exists for — `../../databases/protracktor.db` is a legal tar member name.
 *
 * The rest is arithmetic that is easy to get wrong and impossible to notice: entries are padded to
 * 512 bytes, the size is octal, and a header read one block out of step turns the next tune into a
 * filename.
 */
class UnpackPlayersTest {

    @get:Rule val folder = TemporaryFolder()

    /** A ustar header: name at 0, size at 124 as octal, type at 156. Nothing else is read. */
    private fun header(name: String, size: Int, type: Char = '0'): ByteArray {
        val block = ByteArray(512)
        name.toByteArray().copyInto(block, 0)
        "%011o\u0000".format(size).toByteArray().copyInto(block, 124)
        block[156] = type.code.toByte()
        return block
    }

    private fun entry(name: String, body: ByteArray, type: Char = '0'): ByteArray {
        val padded = ByteArray((body.size + 511) / 512 * 512)
        body.copyInto(padded)
        return header(name, body.size, type) + padded
    }

    private fun unpack(vararg entries: ByteArray): Pair<Int, File> {
        val into = folder.newFolder()
        val archive = entries.fold(ByteArray(0)) { all, one -> all + one } + ByteArray(1024)
        val written = UadePlayers.unpackPlayers(ByteArrayInputStream(archive), into)
        return written to into
    }

    @Test
    fun `the files under players are written under their own names`() {
        val (written, into) = unpack(
            entry("uade-d40dcc7-players/players/TFMX-Pro", ByteArray(700) { 1 }),
            entry("uade-d40dcc7-players/players/DavidWhittaker", ByteArray(40) { 2 }),
        )
        assertEquals(2, written)
        assertEquals(700, File(into, "TFMX-Pro").length())
        assertEquals(40, File(into, "DavidWhittaker").length())
    }

    @Test
    fun `an entry longer than a block does not shift the next one`() {
        // 700 bytes is two blocks with 324 of padding. Read as one, the next header lands inside
        // this file's body and every name after it is nonsense.
        val (written, into) = unpack(
            entry("uade-d40dcc7-players/players/first", ByteArray(700)),
            entry("uade-d40dcc7-players/players/second", ByteArray(3)),
        )
        assertEquals(2, written)
        assertTrue(File(into, "second").isFile)
    }

    @Test
    fun `a name that climbs out of the directory is refused`() {
        val (written, into) = unpack(
            entry("uade-d40dcc7-players/players/../../escaped", ByteArray(8)),
            entry("uade-d40dcc7-players/players/kept", ByteArray(8)),
        )
        assertEquals("only the safe one is written", 1, written)
        assertTrue(File(into, "kept").isFile)
        assertFalse(File(into.parentFile, "escaped").exists())
    }

    @Test
    fun `directories and anything outside players are skipped`() {
        val (written, into) = unpack(
            entry("uade-d40dcc7-players/", ByteArray(0), type = '5'),
            entry("uade-d40dcc7-players/players/", ByteArray(0), type = '5'),
            entry("uade-d40dcc7-players/README", ByteArray(20)),
            entry("uade-d40dcc7-players/players/Fred", ByteArray(12)),
        )
        assertEquals(1, written)
        assertTrue(File(into, "Fred").isFile)
        assertFalse(File(into, "README").exists())
    }

    @Test
    fun `an archive that ends early stops rather than throwing`() {
        val into = folder.newFolder()
        val truncated = entry("uade-d40dcc7-players/players/half", ByteArray(700)).copyOfRange(0, 600)
        val written = UadePlayers.unpackPlayers(ByteArrayInputStream(truncated), into)
        assertEquals(0, written)
    }

    @Test
    fun `the player configurations one level down keep their place and are not counted`() {
        // `players/ENV/EaglePlayer/` holds eleven configurations in the real archive. The first
        // version kept only files directly under `players/`, and dropped them.
        val (written, into) = unpack(
            entry("uade-d40dcc7-players/players/TFMX-Pro", ByteArray(10)),
            entry("uade-d40dcc7-players/players/ENV/EaglePlayer/EP-TFMX_Pro.cfg", ByteArray(5)),
        )
        assertEquals("replay routines only", 1, written)
        assertTrue(File(into, "ENV/EaglePlayer/EP-TFMX_Pro.cfg").isFile)
    }
}
