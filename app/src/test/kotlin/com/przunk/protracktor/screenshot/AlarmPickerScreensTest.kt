// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.screenshot

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.alarms.*
import com.przunk.protracktor.data.SavedPlaylist
import com.przunk.protracktor.player.TrackRef
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** B62: Add music with a playlist open, its tunes and the picks' states, once they have been read. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class AlarmPickerScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var activity: ActivityController<ComponentActivity>? = null
    private val refs = listOf(
        TrackRef("content://m/1", "Lotus 3 · Space ninja", author = "Patrick Phelan"),
        TrackRef("content://m/2", "Enigma", author = "Phenomena"),
        TrackRef("content://m/3", "Hybris", author = "Mr. Man"),
        TrackRef("content://m/4", "Stardust Memories", author = "Jester"),
        TrackRef("content://m/5", "Space Debris", author = "Captain"),
    )
    private val sources = object : AlarmMusicSources {
        override suspend fun playlists() = listOf(SavedPlaylist(1, "Wake-up", trackCount = refs.size))
        override suspend fun tracksIn(playlist: Long) = refs.map(AlarmCandidates::of)
        override suspend fun history() = refs.map(AlarmCandidates::of)
        override suspend fun search(query: String) = emptyList<AlarmCandidate>()
    }
    private val picks = listOf(AlarmPick(refs[0], AlarmPickState.Added("x")), AlarmPick(refs[1], AlarmPickState.Preparing),
        AlarmPick(refs[2], AlarmPickState.Waiting), AlarmPick(refs[3], AlarmPickState.Failed(com.przunk.protracktor.R.string.alarm_prepare_failed)))

    @After fun close() { activity?.close() }

    private fun shot(name: String, theme: AppTheme) {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible().also { controller ->
            controller.get().setContent { ProtracktorTheme(theme, false) {
                AlarmMusicPicker(sources, picks, {}, {}, initialPlaylist = SavedPlaylist(1, "Wake-up", trackCount = refs.size))
            } }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("alarm-candidate")).fetchSemanticsNodes().size >= 4 }
        compose.waitForIdle()
        val view = activity!!.get().window.decorView
        android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            .also { view.draw(android.graphics.Canvas(it)) }.saveAs(name)
    }

    @Test fun `picker with a playlist open`() = shot("b62-picker-playlist-en-dark", AppTheme.DARK)
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi") fun `picker in Polish`() = shot("b62-picker-playlist-pl-light", AppTheme.LIGHT)
}
