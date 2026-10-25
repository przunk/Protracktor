// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.screenshot

import android.content.res.Configuration
import android.content.Context
import android.graphics.Canvas
import android.view.View
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
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.player.FloatingControlsState
import com.przunk.protracktor.player.PlaylistHeart
import com.przunk.protracktor.player.TrackRef
import com.przunk.protracktor.ui.FloatingControlsPanel
import com.przunk.protracktor.ui.FloatingDismissTarget
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
        val bitmap = render(720, if (scale > 1f) 900 else 600, atMillis = 300) {
            ProtracktorTheme(AppTheme.DARK, dynamicColour = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                Box(Modifier.fillMaxSize().background(Color(0xFFE5E8DF))) {
                    Text("Panel preview", color = Color.DarkGray, modifier = Modifier.padding(16.dp))
                    FloatingControlsPanel(FloatingControlsState(true, true, false, true, true, heart, true, true),
                        {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {},
                        Modifier.width(288.dp).align(Alignment.TopCenter).padding(top = 76.dp))
                }
                }
            }
        }
        // Draw the production native indicator explicitly: offscreen Compose rendering omits AndroidView.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(context.resources.configuration).apply { fontScale = scale }
        val target = FloatingDismissTarget(context.createConfigurationContext(config))
        val width = (136 * target.resources.displayMetrics.density).toInt()
        target.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        target.layout(0, 0, target.measuredWidth, target.measuredHeight)
        val canvas = Canvas(bitmap)
        canvas.saveLayerAlpha(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), 204)
        canvas.translate((bitmap.width - target.width) / 2f, (bitmap.height - target.height - 32).toFloat())
        target.draw(canvas)
        canvas.restore()
        bitmap.saveAs("floating-controls-$language")
    }
    @Test fun english() = picture("en")
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi") fun polish() = picture("pl")
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi") fun enlargedPolish() = picture("pl-large", 2f)
}
