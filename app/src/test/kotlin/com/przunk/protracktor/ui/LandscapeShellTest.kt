// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.player.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers
import androidx.compose.ui.test.onAllNodesWithTag

/**
 * C144: turned to landscape, the list was left almost no room -- on a Fairphone 5 the tunes went
 * behind the seek bar and the playing card. A phone on its side, about 800 by 360 dp.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w800dp-h360dp-land-xhdpi")
class LandscapeShellTest : PlaybackShellTest() {
    override val windowPx = 1600 to 720

    @Test fun `on its side the phone keeps room for the list above the dock`() {
        player.value = player.value.copy(queue = com.przunk.protracktor.player.PlayQueue(listOf(tune)).startAt(0),
            playing = true, durationSeconds = 200.0, positionSeconds = 20.0)
        frame(500)
        val density = application.resources.displayMetrics.density
        val dockTop = compose.onAllNodesWithTag("player-dock").fetchSemanticsNodes().single().boundsInRoot.top / density
        val navBottom = compose.onAllNodesWithTag("shell-navigation").fetchSemanticsNodes().single().boundsInRoot.bottom / density
        println("C144 dock top ${dockTop}dp, navigation bottom ${navBottom}dp")
        // Room for a header and at least two rows of tunes above the dock.
        org.junit.Assert.assertTrue("dock top at ${dockTop}dp", dockTop >= 220f)

        // It can still be opened, for this time only: the upright phone's choice is not overwritten.
        val expand = application.getString(com.przunk.protracktor.R.string.dock_expand)
        val dock = compose.onAllNodesWithTag("player-dock").fetchSemanticsNodes().single()
        val action = dock.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].single { it.label == expand }
        compose.runOnIdle { action.action() }
        frame(600)
        val expandedTop = compose.onAllNodesWithTag("player-dock").fetchSemanticsNodes().single().boundsInRoot.top / density
        org.junit.Assert.assertTrue("expanded dock top at ${expandedTop}dp", expandedTop < dockTop)
        org.junit.Assert.assertFalse(com.przunk.protracktor.Appearance.dockCollapsed(application))
    }
}
