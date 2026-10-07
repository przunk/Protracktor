// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.TrackRef
import com.przunk.protracktor.ui.RandomScreen
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class PlayedBeforeScreensTest {
    private fun pictures(locale: String) {
        val tracks = listOf(
            TrackRef("a", "Previously explored track", "Modland", fileName = "song.mod", author = "Artist"),
            TrackRef("b", "Unplayed track", "Modland", fileName = "new.mod", author = "Artist"),
            TrackRef("c", "A very long title that should remain readable despite the discreet mark", "Modland", fileName = "long.mod", author = "Another musician with a long name"),
        )
        for (dark in listOf(false, true)) {
            render(720, 720) {
                ProtracktorTheme(theme = if (dark) AppTheme.DARK else AppTheme.LIGHT, dynamicColour = false) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                            RandomScreen(
                                state = PlayerUiState(randomPicks = tracks, randomIndex = 1, listenedIds = setOf("a", "c")),
                                browse = BrowseState(), listState = rememberLazyListState(),
                                onPlayAt = {}, onRemoveAt = {}, onFilter = {}, onShowNeighbours = {},
                                onShareFile = {}, onShareAudio = {}, onShareLink = {}, onSendToWeb = {},
                                onAddToOtherPlaylist = {}, onAddSelectedToPlaylist = {}, contentPadding = PaddingValues(),
                            )
                        }
                    }
                }
            }.saveAs("b43-$locale-${if (dark) "dark" else "light"}")
        }
    }

    @Test fun english() = pictures("en")
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun polish() = pictures("pl")
}
