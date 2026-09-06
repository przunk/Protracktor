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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generated cover's colours.
 *
 * There is nothing to look at here on a device until something is wrong, which is the argument for
 * testing it: a hue whose contrast fails makes the label vanish on some tunes and not others, and
 * the tunes it fails on are whichever ones happen to hash there. That is not a bug anybody reports
 * usefully.
 */
class TrackArtworkTest {

    /**
     * The label must be readable on every colour the generator can produce.
     *
     * All 360, not a sample: the set is small enough to check exhaustively, and a sample would pass
     * while leaving the one hue that fails in the app. The generator aims each hue at a fixed
     * luminance rather than a fixed lightness, so this should come back not merely above the bar
     * but **uniform** — and the uniformity is the assertion worth making, because it is what tells
     * a working construction from one that happens to pass today.
     */
    @Test
    fun `every hue carries the label at the same readable contrast`() {
        val ratios = (0 until 360).map { hue ->
            TrackArtwork.contrastRatio(TrackArtwork.backgroundForHue(hue), TrackArtwork.FOREGROUND)
        }
        assertTrue("worst contrast was ${ratios.min()}", ratios.min() >= 4.5f)
        assertTrue(
            "contrast ranges ${ratios.min()}..${ratios.max()}, which is not one design",
            ratios.max() - ratios.min() < 0.2f,
        )
    }

    /** The same file always looks the same, or the notification would flicker between tracks. */
    @Test
    fun `the colour is stable for a seed`() {
        assertEquals(
            TrackArtwork.backgroundOf("mod.elysium"),
            TrackArtwork.backgroundOf("mod.elysium"),
        )
    }

    /**
     * Names that differ by one character must not land next to each other.
     *
     * This is why the hash has an avalanche step on the end. A folder of `part 1`, `part 2`,
     * `part 3` is the normal case in these archives, and those are the covers a listener sees one
     * after another — if the hash maps them to adjacent integers, every track change looks like no
     * change at all. Java's polynomial hash puts them at exactly consecutive hues; plain FNV-1a
     * fixed the neighbours and left every *second* one two degrees away.
     *
     * Two claims, because they fail differently: consecutive names must be far apart, and the set
     * as a whole must not sit in one corner of the wheel.
     */
    @Test
    fun `a run of similar names spreads across the wheel`() {
        val hues = (1..8).map { TrackArtwork.hueOf("mod.part $it") }
        for (i in 0 until hues.size - 1) {
            val apart = kotlin.math.abs(hues[i] - hues[i + 1]).let { minOf(it, 360 - it) }
            assertTrue("hues $hues barely move between $i and ${i + 1}", apart > 20)
        }
        assertTrue("hues $hues all sit together", hues.max() - hues.min() > 180)
    }

    /** The seed is the file name, because titles arrive late and would change the colour. */
    @Test
    fun `the seed prefers the file name over the title`() {
        assertEquals("mod.elysium", TrackArtwork.seedFor("mod.elysium", "Elysium", "content://x"))
        assertEquals("Elysium", TrackArtwork.seedFor("", "Elysium", "content://x"))
        assertEquals("content://x", TrackArtwork.seedFor("", "", "content://x"))
    }

    /** Different files look different. Trivially true, and the whole point of the feature. */
    @Test
    fun `different files get different colours`() {
        assertNotEquals(
            TrackArtwork.backgroundOf("mod.elysium"),
            TrackArtwork.backgroundOf("cruisin.ahx"),
        )
    }
}
