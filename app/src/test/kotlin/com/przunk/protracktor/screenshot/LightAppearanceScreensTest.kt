// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.screenshot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.R
import com.przunk.protracktor.player.*
import com.przunk.protracktor.ui.*
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Host previews of production components; they do not reproduce MIUI's renderer. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
@OptIn(ExperimentalMaterial3Api::class)
class LightAppearanceScreensTest {
    private val tracks = listOf(
        TrackRef("one", "Battle of honour", "Modland", fileName = "battle of honour.it", author = "Treks"),
        TrackRef("two", "Studium Fałszu", "Modland", fileName = "studium falszu.mod", author = "Jakim"),
        TrackRef("three", "Space Ninja", "Modland", fileName = "space ninja.mod", author = "Lotus 3"),
    )

    private fun picture(name: String, random: Boolean = false, dirty: Boolean = false, dark: Boolean = false) {
        val state = PlayerUiState(
            queue = PlayQueue(tracks).startAt(0), dirty = dirty,
            playing = true, durationSeconds = 200.0, positionSeconds = 24.0, subsongCount = 5,
            randomPicks = tracks, randomIndex = 0,
        )
        render(720, 1600) {
            ProtracktorTheme(if (dark) AppTheme.DARK else AppTheme.LIGHT, dynamicColour = false) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Scaffold(
                        topBar = {
                            if (random) TopAppBar(
                                title = { Text(stringResource(R.string.domain_random_title)) },
                                actions = { PlaylistReturnAction({}, Modifier.padding(end = 12.dp)) },
                            ) else PlaylistTopBar(
                                state, false, {}, {}, {}, {}, {}, {}, {},
                            )
                        },
                        bottomBar = {
                            PlayerDock(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                        },
                    ) { padding ->
                        if (random) RandomScreen(
                            state, BrowseState(), rememberLazyListState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, padding,
                        ) else PlaylistScreen(
                            state, rememberLazyListState(), {}, {}, { _, _ -> }, {}, {}, {}, {}, {},
                            onBrowse = {}, onPickDownloads = {}, canBrowse = true, stateKnown = true,
                            contentPadding = padding,
                        )
                    }
                }
            }
        }.saveAs(name)
    }

    @Test fun `English Light playlist`() = picture("redmi-en-light-playlist")
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish Light playlist`() {
        picture("redmi-pl-light-playlist")
        picture("redmi-pl-light-playlist-dirty", dirty = true)
    }
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish Random themes`() {
        picture("redmi-pl-light-random", random = true)
        picture("redmi-pl-dark-random", random = true, dark = true)
    }
}
