// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers

/** Real handle gestures enter the production shell/VM/controller, without a native audio device. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class ManualQueueReorderUiTest : PlaybackShellTest() {
    private fun open(pendingCount: Int = 3): List<ManualRequest> {
        compose.runOnIdle {
            val context = ActiveQueue(QueueSource.Playlist(player.value.activePlaylistId),
                PlayQueue(listOf(tune, tune.copy(id = "tail", title = "Source tail"))).startAt(0))
            var schedule = PlaybackSchedule().contextSelected(context, SchedulePlacement.REPLACE)
                .enqueue(tune.copy(id = "manual-current", title = "Current manual"))
            repeat(pendingCount) { index ->
                schedule = schedule.enqueue(tune.copy(id = "pending:${index / 2}", title = "Request $index"), index % 2 == 0)
            }
            val selected = schedule.next(context) as ScheduledStep.Select
            player.value = player.value.withPlayback(context).copy(schedule = selected.schedule,
                playing = false, positionSeconds = 12.5, metadata = mapOf("title" to "Current manual"))
        }
        frame()
        compose.onNodeWithTag("queue-toggle").performClick()
        frame(600)
        return player.value.schedule.pending
    }

    private fun grip(id: Long) = compose.onNodeWithTag("queue-grip-$id")
    private fun row(id: Long) = compose.onNodeWithTag("playback-row-manual:$id")
    private fun actions(id: Long) = grip(id).fetchSemanticsNode().config[SemanticsActions.CustomActions]

    @Test fun `physical grip drag crosses several manual rows but never context or current boundaries`() {
        val pending = open()
        val before = player.value
        val epoch = ReflectionHelpers.getField<Long>(controller, "playbackEpoch")
        val last = pending.last().id
        val height = row(last).fetchSemanticsNode().boundsInRoot.height
        grip(last).performTouchInput { down(center); moveBy(Offset(0f, -height * 1.2f)) }
        frame()
        grip(last).performTouchInput { moveBy(Offset(0f, -height * 1.2f)) }
        frame()
        grip(last).performTouchInput { moveBy(Offset(0f, -height * 2f)) }
        frame()
        compose.onRoot().performTouchInput { up() }
        frame(200)
        compose.runOnIdle {
            assertEquals(listOf(pending[2], pending[0], pending[1]), player.value.schedule.pending)
            assertEquals(before.copy(schedule = player.value.schedule), player.value)
            assertEquals(epoch, ReflectionHelpers.getField<Long>(controller, "playbackEpoch"))
        }
        assertTrue(row(last).fetchSemanticsNode().boundsInRoot.top >=
            row(before.schedule.current!!.id).fetchSemanticsNode().boundsInRoot.bottom)
        compose.onNodeWithTag("queue-grip-${before.schedule.current!!.id}").assertDoesNotExist()
        compose.onNodeWithText("Close").assertDoesNotExist()
        controller.nextFile()
        frame(200)
        compose.runOnIdle { assertEquals(pending[2], player.value.schedule.current!!.request) }
    }

    @Test fun `accessible moves offer only valid neighbours and single pending has no grip`() {
        val pending = open()
        assertEquals(listOf("Move later"), actions(pending[0].id).map { it.label })
        assertEquals(listOf("Move earlier", "Move later"), actions(pending[1].id).map { it.label })
        assertEquals(listOf("Move earlier"), actions(pending[2].id).map { it.label })
        actions(pending[0].id).single().action()
        frame()
        compose.runOnIdle { assertEquals(listOf(pending[1], pending[0], pending[2]), player.value.schedule.pending) }
        actions(pending[0].id).first { it.label == "Move earlier" }.action()
        frame()
        compose.runOnIdle { assertEquals(pending, player.value.schedule.pending) }
        controller.removePendingRequest(pending[1].id)
        controller.removePendingRequest(pending[2].id)
        frame()
        grip(pending[0].id).assertDoesNotExist()
        row(pending[0].id).assertIsDisplayed()
    }

    @Test fun `holding a row opens Info but holding or moving its grip never opens Info or swipes`() {
        val pending = open()
        val before = player.value
        val id = pending.first().id
        grip(id).performTouchInput { down(center); advanceEventTime(700) }
        frame(700)
        compose.onNodeWithText("Close").assertDoesNotExist()
        grip(id).performTouchInput { moveBy(Offset(-600f, 0f)); up() }
        frame(200)
        compose.runOnIdle { assertEquals(before, player.value) }
        grip(id).performTouchInput { down(center); moveBy(Offset(-600f, 0f)); up() }
        frame(200)
        compose.runOnIdle { assertEquals(before, player.value) }
        grip(id).performTouchInput { down(center); moveBy(Offset(600f, 0f)); up() }
        frame(200)
        compose.runOnIdle { assertEquals(before, player.value) }
        row(id).performTouchInput { longClick() }
        frame(200)
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.runOnIdle { assertEquals(before, player.value) }
    }

    @Test fun `membership changes during drag cancel movement without consuming another request`() {
        val pending = open()
        val id = pending.last().id
        val height = row(id).fetchSemanticsNode().boundsInRoot.height
        grip(id).performTouchInput { down(center); moveBy(Offset(0f, -height * 1.2f)) }
        frame()
        controller.nextFile()
        frame(200)
        val afterNext = player.value.schedule
        grip(id).performTouchInput { moveBy(Offset(0f, height * 2f)); up() }
        frame(200)
        compose.runOnIdle {
            assertEquals(afterNext, player.value.schedule)
            assertEquals(pending[0], player.value.schedule.current!!.request)
        }
    }

    @Test fun `dragging near the viewport edge scrolls a long pending block without moving source rows`() {
        val pending = open(25)
        val id = pending.first().id
        val before = player.value
        val viewport = compose.onNodeWithTag("playback-list").fetchSemanticsNode().boundsInRoot
        val bounds = grip(id).fetchSemanticsNode().boundsInRoot
        grip(id).performTouchInput {
            down(center)
            moveTo(Offset(center.x, viewport.bottom - bounds.top - 10f))
        }
        frame()
        val indexBeforeScrolling = player.value.schedule.pending.indexOfFirst { it.id == id }
        frame(1500)
        compose.onRoot().performTouchInput { up() }
        frame(200)
        compose.runOnIdle {
            assertTrue(player.value.schedule.pending.indexOfFirst { it.id == id } > 4)
            assertTrue(player.value.schedule.pending.indexOfFirst { it.id == id } > indexBeforeScrolling)
            assertEquals(before.activePlayback, player.value.activePlayback)
            assertEquals(before.schedule.current, player.value.schedule.current)
            assertEquals(pending.toSet(), player.value.schedule.pending.toSet())
        }
    }
}
