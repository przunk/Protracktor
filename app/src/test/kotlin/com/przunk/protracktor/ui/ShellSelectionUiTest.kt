// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.player.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Redesign E3: while rows are selected, the navigation bar steps aside for the selection's bar. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class ShellSelectionUiTest : PlaybackShellTest() {
    private val tracks = (1..3).map { TrackRef("content://selection.test/$it.mod", "Tune $it", author = "Someone") }
    private fun back() { compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }; frame(400) }

    @Test fun `selecting playlist rows hides the bar, and leaving the selection brings it back`() {
        compose.runOnIdle { player.value = player.value.copy(queue = PlayQueue(tracks)) }
        frame(300)
        compose.onNodeWithTag("shell-navigation").assertExists()
        compose.onNodeWithText("Tune 2").performTouchInput { longClick() }
        frame(400)
        compose.onNodeWithTag("shell-navigation").assertDoesNotExist()
        back()
        compose.onNodeWithTag("shell-navigation").assertExists()
    }

    @Test fun `selecting in Browse hides the bar too`() {
        compose.onNodeWithTag("nav-browse").performClick()
        frame(300)
        compose.runOnIdle {
            browse.value = browse.value.copy(domain = BrowseDomain.ONLINE,
                openCatalogue = CatalogueSummary("modland", "Modland", 3, 1L), openFormat = "Protracker",
                openAuthor = "Someone", tracks = tracks, loading = false)
        }
        frame(400)
        compose.onNodeWithText("Tune 1").performTouchInput { longClick() }
        frame(400)
        compose.onNodeWithTag("shell-navigation").assertDoesNotExist()
        back()
        compose.onNodeWithTag("shell-navigation").assertExists()
    }
}
