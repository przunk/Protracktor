// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class ManualQueueShellTest : PlaybackShellTest() {
    private fun playlist() {
        compose.runOnIdle { player.value = player.value.copy(queue = PlayQueue(listOf(tune)), playback = null) }
        frame()
    }

    private fun pendingTab() {
        compose.onNodeWithText("Queue").performClick()
        frame(600)
        compose.onNodeWithTag("playback-pending-tab").performClick()
        frame(200)
    }

    @Test fun `playlist Add to queue uses the real row menu without starting audio or changing source`() {
        playlist()
        val before = player.value
        compose.onNodeWithContentDescription("Actions for Current tune").performClick()
        frame(200)
        compose.onNodeWithText("Add to queue").performClick()
        frame(200)
        compose.runOnIdle {
            assertEquals(before.queue, player.value.queue)
            assertEquals(before.activePlayback, player.value.activePlayback)
            assertFalse(player.value.playing)
            assertEquals(tune, player.value.schedule.pending.single().track)
            assertFalse(player.value.schedule.pending.single().fromHistory)
        }
        pendingTab()
        compose.onNodeWithTag("manual-request-${player.value.schedule.pending.single().id}").assertIsDisplayed()
    }

    @Test fun `History row Add to queue captures its origin independently of parked playlist`() {
        compose.onNodeWithText("Browse").performClick()
        frame()
        compose.runOnIdle {
            browse.value = browse.value.copy(domain = BrowseDomain.HISTORY, tracks = listOf(tune), historyTotal = 1)
        }
        frame()
        compose.onNodeWithContentDescription("Actions for Current tune").performClick()
        frame(200)
        compose.onNodeWithText("Add to queue").performClick()
        frame(200)
        compose.runOnIdle {
            assertEquals(tune, player.value.schedule.pending.single().track)
            assertTrue(player.value.schedule.pending.single().fromHistory)
            assertFalse(player.value.playing)
            assertEquals(BrowseDomain.HISTORY, browse.value.domain)
        }
    }

    @Test fun `physical pending swipe removes one duplicate and actual snackbar Undo restores it`() {
        playlist()
        controller.enqueueTrack(tune)
        controller.enqueueTrack(tune)
        frame()
        val pending = player.value.schedule.pending
        val source = player.value.queue
        pendingTab()
        compose.onNodeWithTag("manual-request-${pending[0].id}").performTouchInput { swipeLeft() }
        frame(500)
        compose.runOnIdle {
            assertEquals(listOf(pending[1]), player.value.schedule.pending)
            assertEquals(source, player.value.queue)
        }
        compose.onNodeWithText("Undo").assert(hasAnyAncestor(hasTestTag("playback-panel")))
            .performTouchInput { click() }
        frame(200)
        compose.runOnIdle { assertEquals(pending, player.value.schedule.pending) }
        compose.onNodeWithTag("manual-request-${pending[0].id}").assertIsDisplayed()
        compose.onNodeWithTag("play-request-${pending[0].id}").assertIsDisplayed()
        compose.onNodeWithTag("remove-request-${pending[1].id}").performClick()
        frame(200)
        compose.runOnIdle {
            assertEquals(listOf(pending[0]), player.value.schedule.pending)
            assertEquals(source, player.value.queue)
            assertFalse(player.value.playing)
        }
    }

    @Test fun `pending Play action selects only that occurrence and retains earlier requests`() {
        playlist()
        val first = tune.copy(id = "content://navigation.test/first.mod", title = "First request")
        val second = tune.copy(id = "content://navigation.test/second.mod", title = "Second request")
        controller.enqueueTrack(first)
        controller.enqueueTrack(second)
        frame()
        val pending = player.value.schedule.pending
        pendingTab()
        compose.onNodeWithTag("play-request-${pending[1].id}").performClick()
        frame(200)
        compose.runOnIdle {
            assertEquals(second.id, player.value.current!!.id)
            assertEquals(pending[1].id, player.value.schedule.current!!.id)
            assertEquals(listOf(pending[0]), player.value.schedule.pending)
            assertEquals(listOf(tune), player.value.queue.tracks)
        }
        compose.onNodeWithTag("queue-current-track").assertTextEquals("Current track: Second request")
        compose.onNodeWithText("Manual request").assertIsDisplayed()
    }
}
