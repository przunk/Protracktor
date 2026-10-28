// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.ui.theme.ProtracktorLightColors
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29])
class LightPaletteTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun `enabled text and action colours retain readable contrast`() {
        val c = ProtracktorLightColors
        val pairs = listOf(
            c.onPrimary to c.primary, c.onPrimaryContainer to c.primaryContainer,
            c.onSecondary to c.secondary, c.onSecondaryContainer to c.secondaryContainer,
            c.onTertiary to c.tertiary, c.onTertiaryContainer to c.tertiaryContainer,
            c.onBackground to c.background, c.onSurfaceVariant to c.surfaceVariant,
            c.inverseOnSurface to c.inverseSurface, c.onError to c.error,
            c.onErrorContainer to c.errorContainer,
        ) + listOf(c.surface, c.surfaceDim, c.surfaceBright, c.surfaceContainerLowest,
            c.surfaceContainerLow, c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest)
            .flatMap { listOf(c.onSurface to it, c.onSurfaceVariant to it) }
        pairs.forEach { (text, background) ->
            assertTrue("Text contrast >= 4.5:1 for $text on $background", contrast(text, background) >= 4.5f)
        }
        assertTrue("An active outline remains distinct", contrast(c.outline, c.surface) >= 3f)
    }

    @Test fun `app theme selection owns Light Dark and System even without wallpaper colours`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val selection = mutableStateOf(AppTheme.LIGHT to true)
        var actual: ColorScheme? = null
        try {
            controller.get().setContent {
                val (theme, night) = selection.value
                val config = Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides config) {
                    // API 29 must use the app palette even with this preference enabled.
                    ProtracktorTheme(theme = theme, dynamicColour = true) {
                        val colors = MaterialTheme.colorScheme
                        SideEffect { actual = colors }
                    }
                }
            }
            for ((theme, night) in listOf(AppTheme.LIGHT to true, AppTheme.LIGHT to false,
                AppTheme.DARK to false, AppTheme.SYSTEM to true, AppTheme.SYSTEM to false)) {
                compose.runOnIdle { selection.value = theme to night }
                compose.waitForIdle()
                val shouldBeLight = theme == AppTheme.LIGHT || (theme == AppTheme.SYSTEM && !night)
                if (shouldBeLight) assertEquals(ProtracktorLightColors.primary, actual!!.primary)
                else assertTrue("Dark stays dark", actual!!.surface.luminance() < 0.1f)
            }
        } finally { controller.close() }
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}
