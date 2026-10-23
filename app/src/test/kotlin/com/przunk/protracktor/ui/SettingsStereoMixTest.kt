// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppLanguage
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.R
import com.przunk.protracktor.player.CacheAhead
import com.przunk.protracktor.player.StereoMix
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class SettingsStereoMixTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val selected = mutableStateOf(StereoMix.ORIGINAL)
    private val requests = mutableListOf<StereoMix>()

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().getSharedPreferences("protracktor_preferences", Context.MODE_PRIVATE)
            .edit().remove("stereo_mix").commit()
    }

    @After fun stop() {
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    private fun show() {
        selected.value = Appearance.stereoMix(activity.get())
        activity.get().setContent {
            MaterialTheme {
                SettingsScreen(
                    playAllSubsongs = false, stereoMix = selected.value,
                    volumeNormalization = false, onVolumeNormalizationChanged = {},
                    onStereoMixSelected = { mode ->
                        requests += mode
                        Appearance.selectStereoMix(activity.get(), mode)
                        selected.value = mode
                    },
                    selectedLanguage = AppLanguage.ENGLISH,
                    cacheBytes = 0, archiveBytes = emptyMap(), databaseBytes = 0,
                    replayCount = 0, playerCount = 0, replayBytes = 0, catalogues = emptyList(),
                    webPlayer = "http://localhost", onWebPlayerChanged = {}, songLengthCount = 0,
                    trackMetadataCount = 0, songDbLengthCount = 0, contentPadding = PaddingValues(),
                    selectedTheme = AppTheme.SYSTEM, dynamicColour = false,
                    onThemeSelected = {}, onDynamicColourChanged = {}, onToggleAllSubsongs = {},
                    fallbackLengthSeconds = 300, onFallbackLengthChanged = {},
                    cacheAhead = CacheAhead.DEFAULT, onCacheAheadSelected = {},
                    shareAudioMinutes = 3, onShareAudioMinutesSelected = {}, onLanguageSelected = {},
                    onClearCache = {}, onDeleteIndex = {}, onDeleteSongMetadata = {}, onDeleteReplayRoutines = {},
                )
            }
        }
    }

    @Test fun `Settings exposes three labelled actions and clicks deliver the selected mode`() {
        show()
        compose.onNodeWithText("Stereo mix").assertExists()
        compose.onNodeWithText("Original").assertIsSelected()
        compose.onNodeWithText("Narrow").assertIsNotSelected().performClick()
        compose.onNodeWithText("Narrow").assertIsSelected()
        compose.onNodeWithText("Original").assertIsNotSelected()
        compose.onNodeWithText("Current: Narrow").assertExists()
        compose.onNodeWithText("Mono").performClick().assertIsSelected()
        compose.onNodeWithText("Original").performClick().assertIsSelected()
        assertEquals(listOf(StereoMix.NARROW, StereoMix.MONO, StereoMix.ORIGINAL), requests)
        assertEquals(StereoMix.ORIGINAL, Appearance.stereoMix(activity.get()))
    }

    @Test fun `reopening Settings reflects the stored mode without requesting a new selection`() {
        Appearance.selectStereoMix(activity.get(), StereoMix.MONO)
        show()
        compose.onNodeWithText("Mono").assertIsSelected()
        compose.onNodeWithText("Current: Mono").assertExists()
        assertEquals(emptyList<StereoMix>(), requests)
    }

    @Test
    @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish Settings uses localised labels and a selected-state explanation`() {
        show()
        compose.onNodeWithText("Miks stereo").assertExists()
        compose.onNodeWithText("Oryginał").assertIsSelected()
        compose.onNodeWithText("Zwężone").performClick().assertIsSelected()
        compose.onNodeWithText(activity.get().getString(R.string.settings_stereo_mix_selected, "Zwężone")).assertExists()
        assertEquals(StereoMix.NARROW, Appearance.stereoMix(activity.get()))
    }
}
