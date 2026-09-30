// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Can this machine make real pixels at all? If not, every screenshot after this one is a lie. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureProbeTest {
    @Test
    fun `a flat colour comes back as that colour`() {
        val bitmap = render(120, 80) { Box(Modifier.fillMaxSize().background(Color(0xFF2196F3))) }
        bitmap.saveAs("probe")
        assertEquals(0xFF2196F3.toInt(), bitmap.getPixel(60, 40))
    }
}
