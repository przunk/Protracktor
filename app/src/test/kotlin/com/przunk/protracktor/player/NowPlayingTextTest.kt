// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingTextTest {
    private fun state(track: TrackRef, metadata: Map<String, String> = emptyMap()) =
        PlayerUiState(queue = PlayQueue(listOf(track)).startAt(0), metadata = metadata)
    private fun text(state: PlayerUiState) = NowPlayingText.subtitle(state, "Loading", "Idle")

    @Test fun `author platform and year retain the dock ordering`() {
        assertEquals("Author · Amiga · 1992", text(state(
            TrackRef("song", "Title", fileName = "song.mod", author = "Author"), mapOf("year" to "1992"))))
    }
    @Test fun `without an author the platform replaces the format`() {
        assertEquals("Amiga · 1992", text(state(
            TrackRef("song", "Title", fileName = "song.mod"), mapOf("format" to "MOD", "year" to "1992"))))
    }
    @Test fun `source folder remains the existing author fallback`() {
        assertEquals("Folder author · Amiga", text(state(
            TrackRef("song", "Title", subtitle = "Modland · Tracks/Folder author", fileName = "song.mod"))))
    }
    @Test fun `unknown platform falls back to the decoder format`() {
        assertEquals("Format", text(state(TrackRef("song", "Title", fileName = "unknown.xyz"),
            mapOf("format" to "Format"))))
    }
    @Test fun `unknown fields do not leave empty separators`() {
        assertEquals("1992", text(state(TrackRef("song", "Title"), mapOf("year" to "1992"))))
        assertEquals("", text(state(TrackRef("song", "Title"))))
    }
    @Test fun `loading and idle use the supplied localised statuses`() {
        assertEquals("Ładowanie", NowPlayingText.subtitle(PlayerUiState(loadingTrack = true), "Ładowanie", "Gotowy"))
        assertEquals("Gotowy", NowPlayingText.subtitle(PlayerUiState(), "Ładowanie", "Gotowy"))
    }
    @Test fun `missing or invalid year leaves author and platform intact`() {
        assertEquals("Author · Amiga", text(state(
            TrackRef("song", "Title", fileName = "song.mod", author = "Author"), mapOf("year" to "0"))))
    }
}
