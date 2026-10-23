// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.R
import com.przunk.protracktor.player.CatalogueBrowser
import org.robolectric.util.ReflectionHelpers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk=[29],application=Application::class,qualifiers="en-w360dp-h800dp-xhdpi")
class SettingsVolumeNormalizationTest: PlaybackShellTest() {
    @Test fun `physical Settings switch updates controller persistence and reopening without starting playback`() {
        // This host has no Android decoder library. Only the unrelated storage-overview stamp is fake;
        // the physical switch still enters the real app/VM/controller and preference writer.
        val catalogues=ReflectionHelpers.getField<CatalogueBrowser>(controller,"catalogueBrowser")
        ReflectionHelpers.setField(catalogues,"backends",{ "host-fixture" })
        assertFalse(player.value.volumeNormalization)
        val before=player.value
        compose.onNodeWithText("Settings").performClick(); frame(600)
        val label=application.getString(R.string.settings_volume_normalization)
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText(application.getString(R.string.settings_volume_normalization_detail)).assertIsDisplayed()
        compose.onNodeWithContentDescription(label).assertIsOff().performTouchInput { click() }
        frame()
        assertTrue(player.value.volumeNormalization)
        assertTrue(Appearance.volumeNormalization(application))
        assertEquals(before.activePlayback,player.value.activePlayback)
        assertEquals(before.playing,player.value.playing)
        compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }; frame(500)
        compose.onNodeWithText("Settings").performClick(); frame(500)
        compose.onNodeWithContentDescription(label).assertIsOn().performTouchInput { click() }; frame()
        assertFalse(player.value.volumeNormalization)
        assertFalse(Appearance.volumeNormalization(application))
    }
    @Test fun `normalization has concise non-experimental copy in both app languages`() {
        val expected=mapOf(
            "en" to ("Volume normalization" to
                "Reduces volume differences between tracks. First-play matching may be less precise. Does not affect exported audio."),
            "pl" to ("Normalizacja głośności" to
                "Zmniejsza różnice głośności między utworami. Przy pierwszym odsłuchu wyrównanie może być mniej dokładne. Nie wpływa na eksportowane audio."),
        )
        for ((language,copy) in expected) {
            val configuration=Configuration(application.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val localized=application.createConfigurationContext(configuration)
            assertEquals(copy.first,localized.getString(R.string.settings_volume_normalization))
            assertEquals(copy.second,localized.getString(R.string.settings_volume_normalization_detail))
        }
    }
    @Test fun `default Off neither writes preferences nor changes another playback preference`() {
        val prefs=application.getSharedPreferences("protracktor_preferences",Context.MODE_PRIVATE)
        assertFalse(Appearance.volumeNormalization(application))
        assertFalse(prefs.contains("volume_normalization"))
        val mix=Appearance.stereoMix(application)
        assertTrue(Appearance.selectVolumeNormalization(application,true))
        assertFalse(Appearance.selectVolumeNormalization(application,true))
        assertEquals(mix,Appearance.stereoMix(application))
        assertTrue(Appearance.volumeNormalization(android.content.ContextWrapper(application)))
    }
}
