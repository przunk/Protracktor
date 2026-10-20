// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
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
class QueueRowLayoutTest : PlaybackShellTest() {
    private val manual = tune.copy(id = "manual", title = "Manual tune")

    private fun open(manualPlaying: Boolean = false) {
        compose.runOnIdle {
            val context = ActiveQueue(QueueSource.Playlist(player.value.activePlaylistId),
                PlayQueue(listOf(tune, tune.copy(id = "tail", title = "Source tail"))).startAt(0))
            var schedule = PlaybackSchedule().contextSelected(context, SchedulePlacement.REPLACE).enqueue(manual)
            if (manualPlaying) schedule = (schedule.next(context) as ScheduledStep.Select).schedule
            player.value = player.value.withPlayback(context).copy(schedule = schedule, playing = false,
                positionSeconds = 12.5, metadata = mapOf("title" to "Preserved metadata"))
        }
        frame()
        compose.onNodeWithTag("queue-toggle").performClick()
        frame(600)
        compose.onNodeWithTag("playback-list").performScrollToIndex(0)
        frame(300)
    }

    private fun title(text: String) = compose.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag("playback-list")), useUnmergedTree = true)
    private fun left(text: String) = title(text).fetchSemanticsNode().boundsInRoot.left
    private fun clear() {
        compose.onNodeWithTag("queue-clear").performTouchInput { click() }
        frame(400)
    }

    @Test fun `Clear aligns retained current and new manual requests with the original source content`() {
        open()
        val sourceLeft = left(tune.title)
        clear()
        assertEquals(sourceLeft, left(tune.title), 1f)
        controller.enqueueTrack(manual)
        frame(400)
        assertEquals(sourceLeft, left(manual.title), 1f)
        assertEquals(12.5, player.value.positionSeconds, 0.0)
    }

    @Test fun `manual inserts remain indented only inside a visible source and Undo restores that layout`() {
        open(manualPlaying = true)
        val sourceLeft = left(tune.title)
        val insertedLeft = left(manual.title)
        assertEquals(32f, insertedLeft - sourceLeft, 1f)
        clear()
        assertEquals(sourceLeft, left(manual.title), 1f)
        compose.onNodeWithText("Undo").performTouchInput { click() }
        frame(400)
        assertEquals(insertedLeft, left(manual.title), 1f)
        assertFalse(player.value.playing)
    }

    @Test fun `playing manual highlight includes its entire indented margin`() {
        open(manualPlaying = true)
        val id = player.value.schedule.current!!.id
        val row = compose.onNodeWithTag("playback-row-manual:$id").fetchSemanticsNode().boundsInRoot
        val view = activity.get().window.decorView
        val margin = view.renderedPixel(Offset(row.left + 4f, row.top + 12f))
        val body = view.renderedPixel(Offset(row.left + 100f, row.top + 12f))
        assertEquals("Playing colour must reach the viewport edge, including the inset", body, margin)
    }

    @Test fun `manual inset belongs to the same hold for Info without starting playback`() {
        open(manualPlaying = true)
        val before = player.value
        val id = before.schedule.current!!.id
        compose.onNodeWithTag("playback-row-manual:$id").performTouchInput { longClick(Offset(4f, center.y)) }
        frame(200)
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.runOnIdle { assertEquals(before, player.value) }
    }
}
