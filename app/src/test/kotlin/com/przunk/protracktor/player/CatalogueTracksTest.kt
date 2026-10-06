// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogueTracksTest {
    @Test
    fun `Modland reference keeps encoded URL identity and the catalogue title as file name`() {
        val row = CatalogueTrack("modland", "Protracker/A + B/file name.mod", "Protracker", "A + B", "Display title", 123)
        assertEquals(TrackRef(
            id = "https://modland.com/pub/modules/Protracker/A%20%2B%20B/file%20name.mod",
            title = "Display title", subtitle = "Modland/Protracker/A + B", sizeBytes = 123,
            fileName = "Display title", author = "A + B",
        ), CatalogueTracks.referenceOf(row))
    }

    @Test
    fun `archive identity and root path subtitle follow the catalogue contract`() {
        val row = CatalogueTrack("asma", "root.sap", "SAP", "Composer", "Root", 7)
        assertEquals(TrackRef(
            id = "asma://root.sap", title = "Root", subtitle = "ASMA (Atari 8-bit)", sizeBytes = 7,
            fileName = "Root", author = "Composer",
        ), CatalogueTracks.referenceOf(row))
    }

    @Test
    fun `unknown catalogues keep raw paths and omit absent or blank subtitle segments`() {
        for ((path, subtitle) in listOf("Folder/tune" to "Folder", "tune" to "", " /tune" to "")) {
            val row = CatalogueTrack("unknown", path, "unused", "", "Tune", 0)
            assertEquals(TrackRef(
                id = path, title = "Tune", subtitle = subtitle, sizeBytes = 0,
                fileName = "Tune", author = "",
            ), CatalogueTracks.referenceOf(row))
        }
    }
}
