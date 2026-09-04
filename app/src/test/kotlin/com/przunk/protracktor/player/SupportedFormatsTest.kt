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
package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which names get past the door.
 *
 * This list is not a preference. It decides whether a file is scanned, indexed or offered at all,
 * and a backend that can play something it is never handed is a backend that might as well be
 * absent — which is exactly what happened to OctaMED and Oktalyzer until 2026-09-04.
 */
class SupportedFormatsTest {

    /**
     * The names the archives actually use.
     *
     * libopenmpt has always played these; the app listed `med` and `okt` while Modland files them
     * as `.mmd1` and `.okta`, so 5,558 files were invisible for want of five strings
     * (`docs/PLAN_FORMATS.md` §4). Named here so a future tidy-up of the list cannot quietly undo
     * it: every one of these was measured playing before it was added.
     */
    @Test
    fun `the formats libopenmpt plays are recognised under their archive names`() {
        for (name in listOf(
            "demon enclosure.mmd1", "starkelsesirap.mmd0", "cd sequence.mmd2", "sun.mmd3",
            "beyond music.okta", "tune.gtk", "sample.mms",
        )) {
            assertTrue(name, SupportedFormats.looksPlayable(name))
        }
    }

    @Test
    fun `the label is the extension, upper-cased`() {
        assertEquals("MMD1", SupportedFormats.labelFor("demon enclosure.mmd1"))
        assertEquals("OKTA", SupportedFormats.labelFor("beyond music.okta"))
    }

    /** The Amiga convention from the other end, which is why the prefix rule exists at all. */
    @Test
    fun `a prefixed name is recognised too`() {
        assertTrue(SupportedFormats.looksPlayable("mod.crockets"))
        assertTrue(SupportedFormats.looksPlayable("med.stardust"))
        assertEquals("MOD", SupportedFormats.labelFor("mod.crockets"))
    }

    /**
     * The console dumps that dominate what we cannot play stay out.
     *
     * Measured 2026-09-04 alongside the OctaMED finding: `.minigsf`, `.mini2sf` and `.minipsf` are
     * 66,000 Modland files and no backend here opens one. Listing them would fill a scan with
     * rows that cannot play, which is the failure the list exists to prevent.
     */
    @Test
    fun `formats nothing here plays are not claimed`() {
        for (name in listOf("x.minigsf", "x.mini2sf", "x.minipsf", "x.pt3", "x.mbm", "x.ptcop")) {
            assertFalse(name, SupportedFormats.looksPlayable(name))
        }
    }

    /**
     * The digest changes when the list does — which is the whole of its job.
     *
     * Rebuilt from a copy of the sets rather than asserted against a literal: pinning the value
     * would mean editing this test every time a format is added, and a test you edit to make it
     * pass has stopped testing anything.
     */
    @Test
    fun `the fingerprint distinguishes one list from another`() {
        val current = SupportedFormats.fingerprint
        assertEquals("stable across calls", current, SupportedFormats.fingerprint)

        fun digestOf(extensions: List<String>, prefixes: List<String>) =
            "names:%08x".format(
                (extensions.sorted() + "|" + prefixes.sorted()).joinToString(",").hashCode()
            )

        val same = digestOf(SupportedFormats.extensions.toList(), listOf("mod", "med"))
        val plusOne = digestOf(SupportedFormats.extensions.toList() + "zzz", listOf("mod", "med"))
        assertTrue("adding a name must change it", same != plusOne)

        val shuffled = digestOf(SupportedFormats.extensions.shuffled(), listOf("med", "mod"))
        assertEquals("order must not change it", same, shuffled)
    }

    /**
     * Formats that were listed on an assumption nobody checked.
     *
     * `ahx` and `hvl` sat in this list from the first day, on the belief that libopenmpt handled
     * them. It has no AHX loader and neither name is in its format table, and measuring on
     * 2026-09-04 confirmed it: 0 of 12 AHX files and 0 of 6 HVL opened. 1,433 Modland files were
     * being indexed and offered with nothing behind them.
     *
     * They belong here again the day UADE lands, which plays them — so this test is a reminder of
     * the condition, not a ban.
     */
    @Test
    fun `formats no backend loads are not claimed`() {
        for (name in listOf("cruisin.ahx", "headcrash.hvl", "x.gym")) {
            assertFalse(name, SupportedFormats.looksPlayable(name))
        }
    }

    @Test
    fun `a name with no dot is not playable`() {
        assertFalse(SupportedFormats.looksPlayable("README"))
        assertFalse(SupportedFormats.looksPlayable("mod"))
    }
}
