// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSearchTest {
    private val tracks = listOf(
        TrackRef("one", "Space Ninja", fileName = "lotus_3.mod", author = "Barry Leitch"),
        TrackRef("two", "Studium Fałszu", fileName = "studium.it", author = "Jakim"),
        TrackRef("three", "Space echoes", subtitle = "Modland/Protracker/Michał", fileName = "echo.mod"),
    )

    @Test fun `blank search restores every occurrence in original order`() {
        assertEquals(listOf(0, 1, 2), PlaylistSearch.indices(tracks, " \t\n"))
    }

    @Test fun `title author filename and path use the existing accent insensitive word rule`() {
        for ((query, expected) in listOf("SPACE" to listOf(0, 2), "falszu JAKIM" to listOf(1),
            "lotus_3" to listOf(0), "michal echo" to listOf(2), "Barry ninja" to listOf(0),
            "missing" to emptyList())) {
            assertEquals(query, expected, PlaylistSearch.indices(tracks, query))
        }
    }

    @Test fun `filtered positions still address the full editor without modifying it`() {
        val queue = PlayQueue(tracks).startAt(0)
        val visible = PlaylistSearch.indices(queue.tracks, "space")
        val selected = queue.startAt(visible[1])
        assertEquals(tracks, selected.tracks)
        assertEquals(2, selected.currentIndex)
        assertEquals(0, queue.currentIndex)
    }
}
