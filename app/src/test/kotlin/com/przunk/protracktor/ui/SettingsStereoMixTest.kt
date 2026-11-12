// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppLanguage
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.R
import com.przunk.protracktor.player.CacheAhead
import com.przunk.protracktor.player.StereoMix
import com.przunk.protracktor.screenshot.saveAs
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private val shareChoices = mutableListOf<Int>()

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().getSharedPreferences("protracktor_preferences", Context.MODE_PRIVATE)
            .edit().remove("stereo_mix").putBoolean("amp_enabled", false).commit()
    }

    @After fun stop() {
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    private fun show() {
        selected.value = Appearance.stereoMix(activity.get())
        activity.get().setContent {
            ProtracktorTheme(AppTheme.DARK, false) {
                Surface {
                    SettingsScreen(
                        playAllSubsongs = false, stereoMix = selected.value,
                        volumeNormalization = false, onVolumeNormalizationChanged = {},
                        floatingControlsEnabled = false, onFloatingControlsEnabledChanged = {},
                        navSlots = NavSlots.DEFAULT, alarmsAvailable = false, onNavSlotsChanged = {},
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
                        shareAudioMinutes = 3, onShareAudioMinutesSelected = { shareChoices += it }, onLanguageSelected = {},
                        onClearCache = {}, onDeleteIndex = {}, onDeleteSongMetadata = {}, onDeleteReplayRoutines = {},
                    )
                }
            }
        }
    }

    @Test fun `the stereo mix is one three-part pill and clicks deliver the selected mode`() {
        show()
        compose.onNodeWithText("Stereo mix").assertExists()
        compose.onNodeWithText("Original").assertIsSelected()
        compose.onNodeWithText("Narrow").assertIsNotSelected().performClick()
        compose.onNodeWithText("Narrow").assertIsSelected()
        compose.onNodeWithText("Original").assertIsNotSelected()
        compose.onNodeWithText("Mono").performClick().assertIsSelected()
        compose.onNodeWithText("Original").performClick().assertIsSelected()
        assertEquals(listOf(StereoMix.NARROW, StereoMix.MONO, StereoMix.ORIGINAL), requests)
        assertEquals(StereoMix.ORIGINAL, Appearance.stereoMix(activity.get()))
    }

    @Test fun `Alarms is not a Settings destination`() {
        show()
        compose.onNodeWithText(activity.get().getString(R.string.alarm_title)).assertDoesNotExist()
        compose.onNodeWithText(activity.get().getString(R.string.settings_playback)).assertExists()
        if (System.getProperty("protracktor.screenshots") != null) {
            val view = activity.get().window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
                it.saveAs("settings-without-alarm-destination")
            }
        }
    }

    // A short viewport keeps the unrelated native About footer outside this host check.
    @Test
    @Config(qualifiers = "en-w360dp-h360dp-xhdpi")
    fun `AMP availability is not a Settings control`() {
        show()
        val list = compose.onNode(hasScrollToIndexAction()).assertExists()
        val heading = activity.get().getString(R.string.settings_online_section)
        list.performScrollToNode(hasText(heading))
        // Keep every control immediately below the section heading in the viewport.
        val offset = compose.onNodeWithText(heading).fetchSemanticsNode().boundsInRoot.top -
            list.fetchSemanticsNode().boundsInRoot.top
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, offset) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("AMP catalogue").assertDoesNotExist()
    }

    @Test fun `reopening Settings reflects the stored mode without requesting a new selection`() {
        Appearance.selectStereoMix(activity.get(), StereoMix.MONO)
        show()
        compose.onNodeWithText("Mono").assertIsSelected()
        assertEquals(emptyList<StereoMix>(), requests)
    }

    @Test
    @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish Settings uses localised labels`() {
        show()
        compose.onNodeWithText("Miks stereo").assertExists()
        compose.onNodeWithText("Oryginał").assertIsSelected()
        compose.onNodeWithText("Zwężone").performClick().assertIsSelected()
        assertEquals(StereoMix.NARROW, Appearance.stereoMix(activity.get()))
    }

    private fun text(id: Int) = activity.get().getString(id)

    /** Scrolls the list until [label] shows and returns its top. */
    private fun topOf(label: String): Float {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        compose.waitForIdle()
        return compose.onAllNodesWithText(label).fetchSemanticsNodes().first().boundsInRoot.top
    }

    // Tall enough for the list down to the web player at once, so the order is read without scrolling
    // past a title; short enough to leave About, which asks the native engine, unbuilt.
    @Test
    @Config(qualifiers = "en-w360dp-h1900dp-xhdpi")
    fun `every setting sits under the section it belongs to`() {
        show()
        fun top(label: String): Float {
            val nodes = compose.onAllNodesWithText(label).fetchSemanticsNodes()
            assertTrue("$label is not on the screen", nodes.isNotEmpty())
            return nodes.first().boundsInRoot.top
        }
        // Titles, then what each holds, in this order down the screen.
        val order = listOf(
            text(R.string.settings_playback), text(R.string.settings_stereo_mix), text(R.string.settings_fallback_length),
            text(R.string.settings_controls), text(R.string.settings_nav_bar), text(R.string.floating_show),
            text(R.string.settings_sharing), text(R.string.settings_share_audio_length),
            text(R.string.settings_appearance_language), text(R.string.settings_theme), text(R.string.settings_language),
            text(R.string.settings_online_section), text(R.string.settings_web_player_section),
        )
        val tops = order.map(::top)
        order.zip(tops).zipWithNext().forEach { (a, b) ->
            assertTrue("${b.first} should be below ${a.first}", b.second > a.second)
        }
        compose.onNodeWithText(text(R.string.settings_appearance)).assertDoesNotExist()
    }

    @Test fun `the share length is a slider over the same four lengths`() {
        show()
        topOf(text(R.string.settings_share_audio_length))
        val slider = compose.onNodeWithTag("share-length-slider")
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        compose.waitForIdle()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.waitForIdle()
        assertEquals(listOf(10, 1), shareChoices)
        compose.onNodeWithText(activity.get().resources.getQuantityString(R.plurals.minutes, 1, 1)).assertExists()
    }
}
