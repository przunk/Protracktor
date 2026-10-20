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

    private fun openPanel() {
        compose.onNodeWithTag("queue-toggle").performClick()
        frame(600)
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
        openPanel()
        compose.onNodeWithTag("playback-row-manual:${player.value.schedule.pending.single().id}").assertIsDisplayed()
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
        openPanel()
        compose.onNodeWithTag("playback-row-manual:${pending[0].id}").performTouchInput { swipeLeft() }
        frame(500)
        compose.runOnIdle {
            assertEquals(listOf(pending[1]), player.value.schedule.pending)
            assertEquals(source, player.value.queue)
        }
        compose.onNodeWithText("Undo").assertIsDisplayed()
            .performTouchInput { click() }
        frame(200)
        compose.runOnIdle { assertEquals(pending, player.value.schedule.pending) }
        compose.onNodeWithTag("playback-row-manual:${pending[0].id}").assertIsDisplayed()
        compose.onNodeWithTag("queue-info-manual:${pending[0].id}").assertIsDisplayed()
        compose.onNodeWithTag("playback-row-manual:${pending[1].id}").performTouchInput { swipeLeft() }
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
        openPanel()
        compose.onNodeWithTag("playback-row-manual:${pending[1].id}").performTouchInput { click() }
        frame(200)
        compose.runOnIdle {
            assertEquals(second.id, player.value.current!!.id)
            assertEquals(pending[1].id, player.value.schedule.current!!.id)
            assertEquals(listOf(pending[0]), player.value.schedule.pending)
            assertEquals(listOf(tune), player.value.queue.tracks)
        }
        compose.onNodeWithContentDescription("Now playing")
            .assert(hasAnyAncestor(hasTestTag("playback-row-manual:${pending[1].id}")))
        val epoch = org.robolectric.util.ReflectionHelpers.getField<Long>(controller, "playbackEpoch")
        compose.onNodeWithTag("playback-row-manual:${pending[1].id}").performTouchInput { click() }
        frame(200)
        compose.runOnIdle {
            assertTrue(org.robolectric.util.ReflectionHelpers.getField<Long>(controller, "playbackEpoch") > epoch)
            assertEquals(listOf(pending[0]), player.value.schedule.pending)
        }
    }

    @Test fun `unified panel stops above the same operable dock and Back only closes the panel`() {
        playlist()
        openPanel()
        val panel = compose.onNodeWithTag("playback-panel").fetchSemanticsNode().boundsInRoot
        val dockAction = compose.onNodeWithTag("queue-toggle").assertIsDisplayed()
        assertTrue(dockAction.fetchSemanticsNode().boundsInRoot.top >= panel.bottom)
        compose.onNodeWithContentDescription("Play").assertIsDisplayed().performTouchInput { click() }
        frame(200)
        compose.runOnIdle { assertEquals(tune.id, player.value.current!!.id) }
        val selected = player.value.current
        compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
        frame(600)
        compose.onNodeWithTag("playback-panel").assertDoesNotExist()
        compose.runOnIdle { assertEquals(selected, player.value.current) }
    }

    @Test fun `physical right swipe in playlist and History uses real enqueue callback and origin`() {
        playlist()
        compose.onNodeWithTag("enqueue-row-0").performTouchInput { swipeRight() }
        frame(200)
        compose.onNodeWithTag("enqueue-row-0").performTouchInput { swipeRight() }
        frame(200)
        compose.runOnIdle { assertEquals(2, player.value.schedule.pending.size) }
        compose.onNodeWithText("Browse").performClick()
        frame()
        compose.runOnIdle { browse.value = browse.value.copy(domain = BrowseDomain.HISTORY, tracks = listOf(tune), historyTotal = 1) }
        frame()
        compose.onNodeWithTag("enqueue-row-0").performTouchInput { swipeRight() }
        frame(200)
        compose.runOnIdle {
            assertEquals(3, player.value.schedule.pending.size)
            assertEquals(listOf(false, false, true), player.value.schedule.pending.map { it.fromHistory })
            assertEquals(listOf(tune), player.value.queue.tracks)
        }
    }

    @Test fun `source row swipe changes only session and Undo preserves source and pending copies`() {
        val second = tune.copy(id = "content://navigation.test/second.mod", title = "Second")
        compose.runOnIdle {
            val queue = PlayQueue(listOf(tune, second)).startAt(0)
            player.value = player.value.copy(queue = queue, playback = null)
        }
        frame()
        openPanel()
        compose.onNodeWithTag("playback-row-source:1").performTouchInput { swipeRight() }
        frame(200)
        val pending = player.value.schedule.pending
        compose.onNodeWithTag("playback-row-source:1").performTouchInput { swipeLeft() }
        frame(200)
        compose.runOnIdle {
            assertEquals(listOf(tune, second), player.value.queue.tracks)
            assertEquals(pending, player.value.schedule.pending)
            assertEquals(setOf(1L), player.value.activePlayback.omitted)
        }
        compose.onNodeWithText("Undo").performTouchInput { click() }
        frame(200)
        compose.onNodeWithTag("playback-row-source:1").assertIsDisplayed()
        compose.runOnIdle { assertTrue(player.value.activePlayback.omitted.isEmpty()) }
    }

    @Test fun `copying current manual row retains its own origin over a History context`() {
        compose.runOnIdle {
            val context = ActiveQueue(QueueSource.Collection(SessionSource.History), PlayQueue(listOf(tune)).startAt(0))
            val selected = PlaybackSchedule().enqueue(tune.copy(title = "Manual"), false).next(context) as ScheduledStep.Select
            player.value = player.value.copy(playback = context, schedule = selected.schedule)
        }
        frame()
        val id = player.value.schedule.current!!.id
        openPanel()
        compose.onNodeWithTag("playback-row-manual:$id").performTouchInput { swipeRight() }
        frame(200)
        compose.runOnIdle {
            assertEquals(listOf(false), player.value.schedule.pending.map { it.fromHistory })
            assertEquals(id, player.value.schedule.current!!.id)
        }
    }
}
