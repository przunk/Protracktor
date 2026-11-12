// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.screenshot

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.player.*
import com.przunk.protracktor.ui.PlaybackShellTest
import com.przunk.protracktor.ui.ProtracktorApp
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers

/**
 * The redesigned shell as the app draws it -- the real playlist, dock and navigation, in the app's
 * own theme -- in each state the redesign changed, to look at before an APK goes out.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class ShellScreensTest : PlaybackShellTest() {
    private val tracks = listOf("Battle of honour" to "Treks", "Studium Fałszu" to "Jakim", "Space Ninja" to "Lotus 3",
        "Elysium" to "Jester", "Stardust Memories" to "Jester", "Hybris" to "Mr. Man", "Enigma" to "Phenomena",
        "Space Debris" to "Captain").mapIndexed { i, (title, author) ->
        TrackRef("content://shell.screens/$i.mod", title, author = author, fileName = "$i.mod")
    }

    private fun shell(theme: AppTheme) {
        val catalogues = ReflectionHelpers.getField<CatalogueBrowser>(controller, "catalogueBrowser")
        ReflectionHelpers.setField(catalogues, "backends", { "host-fixture" })
        com.przunk.protracktor.Appearance.selectDockCollapsed(application, false)
        compose.runOnIdle {
            activity.get().setContent { ProtracktorTheme(theme, dynamicColour = false) { ProtracktorApp(PlayerViewModel(application)) } }
        }
        frame(500)
        compose.runOnIdle {
            player.value = player.value.copy(queue = PlayQueue(tracks).startAt(0), playback = null, playing = true,
                positionSeconds = 64.0, durationSeconds = 200.0, metadata = mapOf("title" to tracks[0].title))
        }
        frame(400)
    }

    private fun shot(name: String) {
        frame(400)
        val view = activity.get().window.decorView
        android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            .also { view.draw(android.graphics.Canvas(it)) }.saveAs(name)
    }

    private fun pictures(locale: String) {
        for (theme in listOf(AppTheme.LIGHT, AppTheme.DARK)) {
            val suffix = "$locale-${theme.name.lowercase()}"
            shell(theme)
            shot("e4-playlist-$suffix")
            com.przunk.protracktor.Appearance.selectDockCollapsed(application, true)
            compose.runOnIdle { activity.get().setContent { ProtracktorTheme(theme, dynamicColour = false) { ProtracktorApp(PlayerViewModel(application)) } } }
            shot("e4-collapsed-$suffix")
            com.przunk.protracktor.Appearance.selectDockCollapsed(application, false)
            compose.runOnIdle { activity.get().setContent { ProtracktorTheme(theme, dynamicColour = false) { ProtracktorApp(PlayerViewModel(application)) } } }
            frame(400)
            compose.onNodeWithText(tracks[2].title).performTouchInput { longClick() }; shot("e4-selecting-$suffix")
            compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
            compose.runOnIdle { player.value = player.value.copy(dirty = true) }; shot("e4-editing-$suffix")
            compose.runOnIdle { player.value = player.value.copy(dirty = false) }
            compose.onNodeWithTag("nav-browse").performClick(); shot("e4-browse-$suffix")
            compose.onNodeWithTag("nav-search").performClick(); shot("e4-search-$suffix")
            compose.onNodeWithTag("nav-random").performClick(); shot("e4-random-$suffix")
            compose.onNodeWithTag("nav-playlist").performClick()
        }
    }

    @Test fun english() = pictures("en")
    @Test @Config(sdk = [29], application = Application::class, qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun polish() = pictures("pl")
}
