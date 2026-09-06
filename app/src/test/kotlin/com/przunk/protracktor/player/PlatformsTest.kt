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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The platform table.
 *
 * `docs/WISHLIST.md` B23 says this can be wrong in two directions and that one of them is worse: a
 * platform nobody picks is a wasted chip, a format filed under the wrong platform **hides music**.
 * These tests are aimed at the second.
 */
class PlatformsTest {

    /**
     * Every name the app claims belongs to some platform.
     *
     * This is the test that matters. `SupportedFormats` is the promise that a file will open; if a
     * name is missing here then picking a platform silently drops files the app can play, and
     * nothing on screen says so. It failed while the table was being written — `gtk` and `mms` had
     * been added to the format list a day earlier and were in no platform at all.
     */
    @Test
    fun `every claimed name belongs to a platform`() {
        val orphans = (SupportedFormats.extensions + SupportedFormats.prefixes)
            .filter { name -> Platforms.all.none { name in it.names } }
        assertTrue("names in no platform: $orphans", orphans.isEmpty())
    }

    /** And nothing is claimed for a platform that the app does not actually recognise. */
    @Test
    fun `no platform claims a name the app does not`() {
        val known = SupportedFormats.extensions + SupportedFormats.prefixes
        val invented = Platforms.all.flatMap { p -> p.names.map { p.name to it } }
            .filter { it.second !in known }
        assertTrue("names no backend claims: $invented", invented.isEmpty())
    }

    /**
     * No name and no catalogue directory sits in two platforms.
     *
     * The lookups are maps built by folding the table, so a duplicate does not fail — it silently
     * picks whichever came last, and half the archive moves platform without a word.
     */
    @Test
    fun `nothing belongs to two platforms`() {
        for (field in listOf(
            "extension" to Platforms.all.flatMap { it.names },
            "catalogue format" to Platforms.all.flatMap { it.catalogueFormats },
        )) {
            val twice = field.second.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertTrue("${field.first}s in two platforms: $twice", twice.isEmpty())
        }
    }

    /** Modland writes its directories in its own capitalisation; the table states them lower-cased. */
    @Test
    fun `catalogue formats match whatever case the archive used`() {
        assertEquals(Platforms.AMIGA, Platforms.forCatalogueFormat("Protracker"))
        assertEquals(Platforms.AMIGA, Platforms.forCatalogueFormat("  protracker "))
        assertEquals(Platforms.C64, Platforms.forCatalogueFormat("HVSC"))
        assertEquals(Platforms.ATARI_ST, Platforms.forCatalogueFormat("SNDH"))
        assertNull(Platforms.forCatalogueFormat("Organya"))
    }

    /** Both conventions, because Modland files ProTracker as `mod.title` and others as `title.mod`. */
    @Test
    fun `a file is placed by either end of its name`() {
        assertEquals(Platforms.AMIGA, Platforms.forFileName("4mat-elysium.mod"))
        assertEquals(Platforms.AMIGA, Platforms.forFileName("mod.elysium"))
        assertEquals(Platforms.AMIGA, Platforms.forFileName("cruisin.ahx"))
        assertEquals(Platforms.C64, Platforms.forFileName("commando.sid"))
        assertEquals(Platforms.NINTENDO, Platforms.forFileName("zelda.spc"))
        assertNull(Platforms.forFileName("README"))
        assertNull(Platforms.forFileName("notes.txt"))
    }

    /**
     * `matches` is what filters a local search, so its "no platforms chosen" case is load-bearing.
     *
     * An empty set means *all platforms* everywhere else in this feature — the label says "All
     * platforms" — so a `matches` that returned true for everything would be wrong here and a
     * caller that forgets to check would filter everything out. The callers check; this pins the
     * behaviour they are checking around.
     */
    @Test
    fun `matches answers about the platforms it was given`() {
        assertTrue(Platforms.matches("4mat-elysium.mod", setOf("amiga")))
        assertTrue(Platforms.matches("commando.sid", setOf("amiga", "c64")))
        assertTrue(!Platforms.matches("commando.sid", setOf("amiga")))
        assertTrue(!Platforms.matches("README", setOf("amiga")))
        assertTrue("an empty choice matches nothing here", !Platforms.matches("x.mod", emptySet()))
    }

    /** One `IN (…)` clause per search, however many platforms were ticked. */
    @Test
    fun `catalogue formats of several platforms are one set`() {
        val both = Platforms.catalogueFormatsOf(setOf("amiga", "c64"))
        assertTrue("protracker" in both)
        assertTrue("hvsc" in both)
        assertTrue("sndh" !in both)
        assertTrue(Platforms.catalogueFormatsOf(emptySet()).isEmpty())
    }
}

/** What the search field's label has to say, minus the wording, which lives in resources. */
class SearchScopeTest {

    @Test
    fun `a scope knows which sources it covers`() {
        assertTrue(SearchScope.Everywhere.searchesLocal && SearchScope.Everywhere.searchesOnline)
        assertTrue(SearchScope.Local.searchesLocal && !SearchScope.Local.searchesOnline)
        assertTrue(!SearchScope.Online().searchesLocal && SearchScope.Online().searchesOnline)
        // A platform is a statement about the music, so it reaches both sides.
        assertTrue(SearchScope.ByPlatform().searchesLocal && SearchScope.ByPlatform().searchesOnline)
    }

    @Test
    fun `the label spells out two names and counts the rest`() {
        assertEquals(listOf<String>() to 0, SearchScope.parts(emptyList()).let { it.shown to it.hidden })
        assertEquals(listOf("Amiga") to 0, SearchScope.parts(listOf("Amiga")).let { it.shown to it.hidden })
        assertEquals(
            listOf("Amiga", "PC") to 0,
            SearchScope.parts(listOf("Amiga", "PC")).let { it.shown to it.hidden },
        )
        assertEquals(
            listOf("Amiga", "PC") to 2,
            SearchScope.parts(listOf("Amiga", "PC", "Sega", "MSX")).let { it.shown to it.hidden },
        )
    }
}
