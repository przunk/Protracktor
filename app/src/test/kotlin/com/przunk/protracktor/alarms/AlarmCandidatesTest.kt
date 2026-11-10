// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import com.przunk.protracktor.player.TrackRef
import org.junit.Assert.assertEquals
import org.junit.Test

/** B62: the lists *Add music* offers. */
class AlarmCandidatesTest {
    private val lotus = TrackRef("content://a/lotus.mod", "Lotus 3", fileName = "lotus3.mod", author = "Patrick Phelan")
    private val enigma = TrackRef("content://a/enigma.mod", "Enigma", fileName = "enigma.mod", author = "Phenomena")
    private val fromCatalogue = TrackRef("https://modland.com/x/lotus2.mod", "lotus2.mod", "Modland / Protracker", fileName = "lotus2.mod")

    @Test fun `saved tunes that match come first, then the catalogue's, each once`() {
        val found = AlarmCandidates.search(listOf(lotus, enigma), listOf(fromCatalogue, lotus), "LOTUS")
        assertEquals(listOf(lotus.id, fromCatalogue.id), found.map { it.ref.id })
    }

    @Test fun `every word, in the title, the file's name or the author`() {
        assertEquals(listOf(lotus.id), AlarmCandidates.search(listOf(lotus, enigma), emptyList(), "phelan 3").map { it.ref.id })
        assertEquals(listOf(enigma.id), AlarmCandidates.search(listOf(lotus, enigma), emptyList(), "enigma.mod").map { it.ref.id })
        assertEquals(emptyList<String>(), AlarmCandidates.search(listOf(lotus, enigma), listOf(fromCatalogue), "  ").map { it.ref.id })
    }

    @Test fun `a candidate says who and where, without repeating itself`() {
        assertEquals("Patrick Phelan", AlarmCandidates.of(lotus).detail)
        assertEquals("Modland / Protracker", AlarmCandidates.of(fromCatalogue).detail)
        assertEquals("A", AlarmCandidates.of(lotus.copy(author = "A", subtitle = "A")).detail)
    }

    @Test fun `a long answer is cut at the list's limit`() {
        val many = (1..(AlarmCandidates.LIMIT + 50)).map { TrackRef("content://x/$it", "tune $it") }
        assertEquals(AlarmCandidates.LIMIT, AlarmCandidates.search(many, emptyList(), "tune").size)
    }
}
