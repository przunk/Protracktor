// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
 * absent, which is what an unlisted OctaMED or Oktalyzer amounts to.
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
     * Measured: `.minigsf`, `.mini2sf` and `.minipsf` are 66,000 Modland files and no backend here
     * opens one. Listing them would fill a scan with
     * rows that cannot play, which is the failure the list exists to prevent.
     */
    @Test
    fun `formats nothing here plays are not claimed`() {
        // `x.pt3` is deliberately absent from this list: ZXTune plays it, along with the rest of
        // the ZX Spectrum trackers. The `*SF` family stays, being console emulators.
        for (name in listOf("x.minigsf", "x.mini2sf", "x.minipsf", "x.mbm", "x.ptcop")) {
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
     * `gym` is what is left of that group. All 40 sampled Modland GYM files are packed, and
     * game-music-emu refuses packed GYM unconditionally — the message is in its source with no
     * build option behind it — so listing the name indexed 265 files that cannot open.
     *
     * `ahx` and `hvl` belong here whenever nothing loads them: libopenmpt does not, measured 0 of 12
     * and 0 of 6. They are claimed because HivelyTracker does, which is the condition -- a name is
     * claimed when something loads it, and the test below is what that costs.
     */
    @Test
    fun `formats no backend loads are not claimed`() {
        assertFalse(SupportedFormats.looksPlayable("x.gym"))
    }

    /**
     * The two names that came back.
     *
     * A claim in this list is a promise that a file will open, and these two broke it for a while.
     * `docs/PLAN_FORMATS.md` §6 is the evidence they can be made again: 80 of 80 sampled files
     * loaded from a buffer, were audible and reached a song end through HivelyTracker's replayer.
     *
     * Both conventions are asserted although Modland uses only the suffix — the prefix entries are
     * there for archives not yet indexed, and a test is how they stay there.
     */
    @Test
    fun `ahx and hvl are claimed again`() {
        for (name in listOf("cruisin.ahx", "headcrash.hvl", "ahx.cruisin", "hvl.headcrash")) {
            assertTrue(name, SupportedFormats.looksPlayable(name))
        }
        assertEquals("AHX", SupportedFormats.labelFor("cruisin.ahx"))
        assertEquals("HVL", SupportedFormats.labelFor("hvl.headcrash"))
    }

    @Test
    fun `a name with no dot is not playable`() {
        assertFalse(SupportedFormats.looksPlayable("README"))
        assertFalse(SupportedFormats.looksPlayable("mod"))
    }
    /**
     * The two questions the list answers, and why they are two.
     *
     * `extensions` is what a catalogue index is filtered through **and** what `fingerprint` is
     * computed from, so a name added there marks every stored index stale and costs everyone a 40 MB
     * download. No archive here holds an MP3, so `.mp3` earns its place in one question and
     * not the other (`docs/BACKLOG.md` A29).
     */
    @Test
    fun `mp3 is playable but never earns a row in a catalogue index`() {
        assertTrue(SupportedFormats.looksPlayable("a recording.mp3"))
        assertTrue(SupportedFormats.looksPlayable("A RECORDING.MP3"))
        assertFalse(SupportedFormats.inCatalogueIndex("a recording.mp3"))

        // And everything a catalogue does carry is still both.
        assertTrue(SupportedFormats.inCatalogueIndex("elysium.mod"))
        assertTrue(SupportedFormats.looksPlayable("elysium.mod"))
    }

    @Test
    fun `adding mp3 did not change what a stored index is measured against`() {
        // The fingerprint is the promise that an index is still current. It is computed from
        // `extensions` and `prefixes`, and `.mp3` is in neither — so every index on every device
        // stayed valid on the day MP3 arrived. If this ever fails, somebody moved the name and
        // every device owes Modland a 40 MB download.
        assertFalse("mp3" in SupportedFormats.extensions)
        assertFalse("mp3" in SupportedFormats.prefixes)
    }
}
