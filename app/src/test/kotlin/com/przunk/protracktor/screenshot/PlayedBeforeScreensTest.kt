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
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.TrackRef
import com.przunk.protracktor.ui.RandomScreen
import com.przunk.protracktor.ui.BrowseScreen
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class PlayedBeforeScreensTest {
    private fun pictures(locale: String, browse: Boolean = false) {
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
                            if (browse) BrowseScreen(
                                browse = BrowseState(domain = BrowseDomain.HISTORY, tracks = tracks,
                                    historyTotal = tracks.size, listenedHere = setOf("a", "c"), cachedHere = setOf("a", "b")),
                                playlistName = "Saved playlist", contentPadding = PaddingValues(),
                                onOpenDomain = {}, onPickFolder = {}, onPickFiles = {}, onOpenFolder = {},
                                onForgetFolder = {}, onScanFolder = {}, onIndexCatalogue = {},
                                onDownloadSongMetadata = {}, onDownloadReplayRoutines = {}, onOpenCatalogue = {},
                                onOpenGroup = {}, onRandom = {}, onChooseRandomScope = {}, onQueryChange = {},
                                onScope = {}, onToggleCatalogue = {}, onTogglePlatform = {}, onSearch = {},
                                onClearHistory = {}, playingId = null, loadingId = null,
                                onShowNeighbours = {}, onShareFile = {}, onShareAudio = {}, onShareLink = {},
                                onSendToWeb = {}, onPlay = {}, onAdd = {},
                            ) else RandomScreen(
                                state = PlayerUiState(randomPicks = tracks, randomIndex = 1, listenedIds = setOf("a", "c")),
                                browse = BrowseState(), listState = rememberLazyListState(),
                                onPlayAt = {}, onRemoveAt = {}, onFilter = {}, onShowNeighbours = {},
                                onShareFile = {}, onShareAudio = {}, onShareLink = {}, onSendToWeb = {},
                                onAddToOtherPlaylist = {}, onAddSelectedToPlaylist = {}, contentPadding = PaddingValues(),
                            )
                        }
                    }
                }
            }.saveAs("b43-column-${if (browse) "browse" else "random"}-$locale-${if (dark) "dark" else "light"}")
        }
    }

    @Test fun english() = pictures("en")
    @Test fun englishBrowse() = pictures("en", browse = true)
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun polish() = pictures("pl")
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun polishBrowse() = pictures("pl", browse = true)
}
