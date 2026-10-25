// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.player.FloatingControlsState
import com.przunk.protracktor.player.PlaylistHeart
import com.przunk.protracktor.player.TrackRef
import com.przunk.protracktor.ui.FloatingControlsPanel
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Host previews of the production panel, not evidence of an overlay above Google Maps. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class FloatingControlsScreensTest {
    private fun picture(language: String, scale: Float = 1f) {
        val heart = PlaylistHeart(PlaylistHeart.Request("song", 7), TrackRef("song", "Song"), "My list", false, false)
        render(720, if (scale > 1f) 900 else 600, atMillis = 300) {
            ProtracktorTheme(AppTheme.DARK, dynamicColour = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                Box(Modifier.fillMaxSize().background(Color(0xFFE5E8DF))) {
                    Text("Panel preview", color = Color.DarkGray, modifier = Modifier.padding(16.dp))
                    FloatingControlsPanel(FloatingControlsState(true, true, false, true, true, heart),
                        {}, {}, {}, {}, {}, { _, _ -> }, {},
                        Modifier.width(320.dp).align(Alignment.TopCenter).padding(top = 76.dp))
                }
                }
            }
        }.saveAs("floating-controls-$language")
    }
    @Test fun english() = picture("en")
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi") fun polish() = picture("pl")
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi") fun enlargedPolish() = picture("pl-large", 2f)
}
