// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.player.*
import com.przunk.protracktor.ui.SavedPlaceHeader
import com.przunk.protracktor.ui.SavedPlacesScreen
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class SavedPlacesScreensTest {
    private fun pictures(locale: String) {
        val place = SavedPlace(SavedPlaceKey("modland", "Protracker", "A musician with a long name / collaboration"), "Modland", 1)
        val game = SavedPlace(SavedPlaceKey("unexotica", "Amiga", "A game soundtrack to return to later"), "UnExoticA", 2)
        for (dark in listOf(false, true)) {
            for (header in listOf(false, true)) {
                render(720, if (header) 500 else 1400) {
                    ProtracktorTheme(if (dark) AppTheme.DARK else AppTheme.LIGHT, dynamicColour = false) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                                val state = SavedPlacesState(listOf(
                                    SavedPlaceRow(place, SavedPlaceAvailability.AVAILABLE),
                                    SavedPlaceRow(game, SavedPlaceAvailability.INDEX_MISSING),
                                ), loading = false)
                                if (header) Column {
                                    SavedPlaceHeader(place.key, state.copy(rows = emptyList()), {}, {}, {})
                                    SavedPlaceHeader(place.key, state, {}, {}, {})
                                } else SavedPlacesScreen(state, {}, {}, {}, {})
                            }
                        }
                    }
                }.saveAs("b42-${if (header) "header" else "list"}-$locale-${if (dark) "dark" else "light"}")
            }
        }
    }
    @Test fun english() = pictures("en")
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi") fun polish() = pictures("pl")
}
