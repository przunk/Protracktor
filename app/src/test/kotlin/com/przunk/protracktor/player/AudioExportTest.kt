// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A62: how long a tune shared as audio is, how it ends and what it is called. */
class AudioExportTest {

    @Test
    fun `a tune with a length is sent whole and ends by itself`() {
        assertEquals(AudioExport.Plan(95.5, fade = false), AudioExport.plan(95.5, limitMinutes = 3))
    }

    @Test
    fun `a tune longer than the setting is cut at it and fades`() {
        assertEquals(AudioExport.Plan(3 * 60.0, fade = true), AudioExport.plan(6 * 60.0, limitMinutes = 3))
    }

    @Test
    fun `a tune exactly as long as the setting ends by itself`() {
        assertEquals(AudioExport.Plan(180.0, fade = false), AudioExport.plan(180.0, limitMinutes = 3))
    }

    @Test
    fun `a tune with no length runs to the setting and fades`() {
        assertEquals(AudioExport.Plan(5 * 60.0, fade = true), AudioExport.plan(0.0, limitMinutes = 5))
    }

    @Test
    fun `the fade falls from full to silence over its last frames only`() {
        assertEquals(1f, AudioExport.gainAt(0, 1000, 100), 0f)
        assertEquals(1f, AudioExport.gainAt(899, 1000, 100), 0f)
        assertEquals(1f, AudioExport.gainAt(900, 1000, 100), 0f)
        assertEquals(0.75f, AudioExport.gainAt(925, 1000, 100), 0.001f)
        assertEquals(0.5f, AudioExport.gainAt(950, 1000, 100), 0.001f)
        assertEquals(0.01f, AudioExport.gainAt(999, 1000, 100), 0.001f)
        assertEquals(1f, AudioExport.gainAt(999, 1000, 0), 0f)
    }

    @Test
    fun `a stored limit off the list falls back to the default`() {
        assertEquals(3, AudioExport.limitFromStored(0))
        assertEquals(3, AudioExport.limitFromStored(7))
        assertEquals(10, AudioExport.limitFromStored(10))
        assertTrue(AudioExport.DEFAULT_LIMIT_MINUTES in AudioExport.LIMIT_MINUTES)
    }

    @Test
    fun `the file is named after author and title, without the tune's own extension`() {
        assertEquals("4-Mat - elysium.m4a", AudioExport.fileName("elysium.mod", "4-Mat"))
        assertEquals("Bonio.m4a", AudioExport.fileName("Bonio.sap", ""))
        assertEquals("Commando.m4a", AudioExport.fileName(" Commando ", " "))
    }

    @Test
    fun `a title with a dot in its words keeps them`() {
        assertEquals("Dr. Who theme.m4a", AudioExport.fileName("Dr. Who theme", ""))
    }

    @Test
    fun `characters a file system refuses are replaced`() {
        val name = AudioExport.fileName("What? Yes/No: \"maybe\"", "A|B")
        assertFalse(name.any { it in "\\/:*?\"<>|" })
        assertEquals("tune.m4a", AudioExport.fileName("", ""))
    }
}
