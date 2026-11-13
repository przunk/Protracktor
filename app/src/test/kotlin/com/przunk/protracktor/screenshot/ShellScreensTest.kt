// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.screenshot

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
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
            // History with two pages (2026-11-12): its paging, its rows, Clear all at the foot.
            if (theme == AppTheme.LIGHT) {
                val writes = ReflectionHelpers.getField<com.przunk.protracktor.player.HistoryWrites>(controller, "historyWrites")
                compose.runOnIdle { repeat(130) { i -> writes.record(tracks[i % tracks.size].copy(id = "history-$i"), writes.generation) } }
                frame(1500)
            }
            compose.onNodeWithTag("nav-browse").performClick(); frame(400)
            // Opened as its row opens it; the row sits under the fold of Browse's start.
            compose.runOnIdle { controller.openDomain(com.przunk.protracktor.player.BrowseDomain.HISTORY) }
            frame(500)
            // Shown once the writes have landed on the database's own thread.
            for (i in 0 until 50) { if (browse.value.historyTotal >= 130 && !browse.value.loading) break; frame(200) }
            frame(400); shot("h-history-$suffix")
            // Selecting, in English only: the second run's database already holds these entries
            // and goes on reordering them under the hold (selecting in Polish is a shell test).
            if (locale == "en") {
                // Let late writes finish reordering the list before holding a row.
                frame(2500)
                // Whichever tune is on top: the order follows when each was recorded.
                val onTop = tracks.mapNotNull { t ->
                    compose.onAllNodesWithText(t.title).fetchSemanticsNodes().map { it.boundsInRoot.top }
                        .filter { it > 140f }.minOrNull()?.let { t to it }
                }.minBy { it.second }.first
                compose.onAllNodesWithText(onTop.title).onFirst().performTouchInput { longClick() }; shot("h-history-selecting-$suffix")
                compose.onNodeWithTag("top-selection-exit").performClick(); frame(300)
            }
            compose.onNodeWithTag("nav-playlist").performClick()
        }
    }

    @Test fun english() = pictures("en")
    @Test @Config(sdk = [29], application = Application::class, qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun polish() = pictures("pl")
}
