// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StereoMixTest {
    @Test fun `modes use the agreed native widths and stable preference names`() {
        assertEquals(listOf("original", "narrow", "mono"), StereoMix.entries.map { it.stored })
        assertEquals(listOf(100, 50, 0), StereoMix.entries.map { it.widthPercent })
    }

    @Test fun `every stored mode round trips`() {
        StereoMix.entries.forEach { assertEquals(it, StereoMix.fromStored(it.stored)) }
    }

    @Test fun `absent or unknown settings leave playback in Original`() {
        listOf(null, "", "headphones", "future-mode").forEach {
            assertEquals(StereoMix.ORIGINAL, StereoMix.fromStored(it))
        }
        assertEquals(StereoMix.ORIGINAL, PlayerUiState().stereoMix)
    }
}
