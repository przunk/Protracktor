// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.player.PlayQueue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Redesign E2 in the real shell: the dock's form follows the grip and is remembered. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class DockShellUiTest : PlaybackShellTest() {
    private fun seed() {
        compose.runOnIdle { player.value = player.value.copy(queue = PlayQueue(listOf(tune)).startAt(0), playing = true,
            durationSeconds = 200.0, positionSeconds = 20.0) }
        frame(300)
    }

    @Test fun `dragging the dock down collapses it, up expands it, and the choice is remembered`() {
        seed()
        assertFalse(Appearance.dockCollapsed(application))
        compose.onNodeWithTag("dock-collapsed").assertDoesNotExist()
        compose.onNodeWithTag("player-dock").performTouchInput { down(Offset(centerX, 20f)); moveBy(Offset(0f, 60f)); moveBy(Offset(0f, 60f)); up() }
        frame(500)
        compose.onNodeWithTag("dock-collapsed").assertExists()
        assertTrue(Appearance.dockCollapsed(application))
        compose.onNodeWithTag("player-dock").performTouchInput { down(Offset(centerX, centerY)); moveBy(Offset(0f, -60f)); moveBy(Offset(0f, -60f)); up() }
        frame(500)
        compose.onNodeWithTag("dock-collapsed").assertDoesNotExist()
        assertFalse(Appearance.dockCollapsed(application))
    }

    @Test fun `a remembered collapsed dock comes back collapsed, above the navigation`() {
        Appearance.selectDockCollapsed(application, true)
        // The shell built again, as a new launch builds it: the form is read from what was chosen.
        compose.runOnIdle {
            activity.get().setContent { androidx.compose.material3.MaterialTheme {
                ProtracktorApp(viewModel = com.przunk.protracktor.player.PlayerViewModel(application)) } }
        }
        frame(500)
        compose.onNodeWithTag("dock-collapsed").assertExists()
        val dock = compose.onNodeWithTag("dock-collapsed").fetchSemanticsNode().boundsInRoot
        val nav = compose.onNodeWithTag("shell-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(dock.bottom <= nav.top + 1f)
        Appearance.selectDockCollapsed(application, false)
    }
}
