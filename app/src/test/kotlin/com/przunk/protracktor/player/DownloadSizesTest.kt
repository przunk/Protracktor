// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The number on the button is the number it will cost.
 *
 * A size written on a control is a promise, and the one way it goes wrong quietly is somebody
 * editing one archive's line and not the total. This adds them up instead.
 */
class DownloadSizesTest {

    @Test
    fun `the advertised total is the sum of what it downloads`() {
        val parts = listOf(
            DownloadSizes.MODLAND_MB,
            DownloadSizes.ASMA_MB,
            DownloadSizes.UNEXOTICA_MB,
            DownloadSizes.SONG_LENGTHS_MB,
            DownloadSizes.TRACK_METADATA_MB,
            DownloadSizes.FAVOURITES_MB,
        )
        assertEquals(parts.sum(), DownloadSizes.EVERYTHING_MB)
    }

    @Test
    fun `every part is rounded up from what was measured`() {
        // The measurements are in `DownloadSizes`' own table. Rounding down would understate the
        // cost of a download somebody may be paying for by the megabyte.
        val measured = mapOf(
            DownloadSizes.MODLAND_MB to 5.49,
            DownloadSizes.ASMA_MB to 19.18,
            DownloadSizes.UNEXOTICA_MB to 1.68,
            DownloadSizes.SONG_LENGTHS_MB to 4.96,
            DownloadSizes.TRACK_METADATA_MB to 14.11,
            DownloadSizes.FAVOURITES_MB to 0.14,
        )
        measured.forEach { (declared, actual) ->
            assertTrue("$declared MB declared for $actual MB measured", declared >= actual)
        }
    }
}
