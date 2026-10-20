// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Physical actions through the real shell, VM and controller; native audio is not opened. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class QueueClearUiTest : PlaybackShellTest() {
    private fun open(manual: Boolean = false) {
        compose.runOnIdle {
            val context = ActiveQueue(QueueSource.Playlist(player.value.activePlaylistId, "Source playlist"),
                PlayQueue(listOf(tune, tune.copy(id = "tail", title = "Source tail"))).startAt(0))
            var schedule = PlaybackSchedule().contextSelected(context, SchedulePlacement.REPLACE)
                .enqueue(tune.copy(id = "request", title = "Manual request"))
                .enqueue(tune.copy(id = "request", title = "Duplicate request"))
            if (manual) schedule = (schedule.next(context) as ScheduledStep.Select).schedule
            player.value = player.value.withPlayback(context).copy(schedule = schedule,
                playing = false, positionSeconds = 12.5, metadata = mapOf("title" to "Current metadata"))
        }
        frame()
        compose.onNodeWithTag("queue-toggle").performClick()
        frame(600)
    }

    private fun clear() {
        compose.onNodeWithTag("queue-clear").assertIsDisplayed().performTouchInput { click() }
        frame(400)
    }

    @Test fun `Clear button retains only current and real snackbar Undo restores plan without resetting audio state`() {
        open()
        val before = player.value
        clear()
        val actual = player.value.schedule.current!!
        compose.onNodeWithTag("playback-row-manual:${actual.id}").assertIsDisplayed()
        compose.onAllNodes(hasTestTag("playback-row-source:0")).assertCountEquals(0)
        compose.onNodeWithTag("playback-row-source:1").assertDoesNotExist()
        compose.onNodeWithTag("playback-row-manual:${before.schedule.pending.first().id}").assertDoesNotExist()
        compose.onNodeWithText("Manual queue").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(before.current, player.value.current)
            assertEquals(before.queue, player.value.queue)
            assertEquals(before.metadata, player.value.metadata)
            assertEquals(12.5, player.value.positionSeconds, 0.0)
            assertFalse(player.value.playing)
            assertFalse(player.value.canGoPreviousFile)
            player.value = player.value.copy(positionSeconds = 19.0)
        }
        frame()
        compose.onNodeWithText("Undo").assertIsDisplayed().performTouchInput { click() }
        frame(400)
        compose.runOnIdle {
            assertEquals(before.activePlayback, player.value.activePlayback)
            assertEquals(before.schedule.pending, player.value.schedule.pending)
            assertEquals(before.queue, player.value.queue)
            assertEquals(19.0, player.value.positionSeconds, 0.0)
            assertFalse(player.value.playing)
        }
        compose.onNodeWithTag("playback-row-source:0").assertIsDisplayed()
    }

    @Test fun `Clear during manual current retains it and right swipes build a fresh duplicate queue`() {
        open(manual = true)
        val current = player.value.schedule.current!!
        clear()
        val row = compose.onNodeWithTag("playback-row-manual:${current.id}")
        row.performTouchInput { swipeRight() }
        frame(300)
        row.performTouchInput { swipeRight() }
        frame(300)
        val pending = player.value.schedule.pending
        compose.runOnIdle {
            assertEquals(2, pending.size)
            assertEquals(listOf(current.track, current.track), pending.map { it.track })
            assertNotEquals(pending[0].id, pending[1].id)
        }
        compose.onNodeWithTag("playback-row-manual:${pending.first().id}").performTouchInput { click() }
        frame(400)
        row.assertDoesNotExist()
        compose.onNodeWithTag("playback-row-manual:${pending.first().id}").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(pending.last()), player.value.schedule.pending)
            assertFalse(player.value.canGoPreviousFile)
            assertEquals(QueueSource.None, player.value.activePlayback.source)
        }
    }

    @Test fun `visible expired Undo explains completion and repeated Clear gives feedback`() {
        open()
        clear()
        compose.runOnIdle { player.value = player.value.copy(schedule = player.value.schedule.finishCurrent()) }
        frame()
        compose.onNodeWithText("Undo").performTouchInput { click() }
        frame(400)
        compose.onNodeWithText("Cannot undo: the playback plan has changed.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(QueueSource.None, player.value.activePlayback.source) }
        clear()
        compose.onNodeWithText("Queue is already clear.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish Clear label and Help expose the operation without hiding the dock`() {
        open()
        compose.onNodeWithText("Wyczyść kolejkę").assertIsDisplayed()
        val clearBounds = compose.onNodeWithTag("queue-clear").fetchSemanticsNode().boundsInRoot
        val helpBounds = compose.onNodeWithTag("queue-help").fetchSemanticsNode().boundsInRoot
        assertTrue(clearBounds.right <= helpBounds.left)
        compose.onNodeWithTag("queue-help").performTouchInput { click() }
        frame(200)
        compose.onNodeWithText("Zmień kolejność oczekujących utworów ręcznych w obrębie wcięcia. Bieżący utwór i wiersze kontekstu pozostają na miejscu.")
            .performScrollTo()
        compose.onNodeWithText("Zachowaj tylko bieżący utwór, bez przerywania odtwarzania. Usuń kontekst i oczekujące utwory; zapisane playlisty pozostają bez zmian. Cofnij przywraca plan, dopóki odtwarzanie nie przejdzie dalej lub nie ułożysz nowej kolejki.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Zamknij").performClick()
        frame(200)
        clear()
        compose.onNodeWithText("Kolejka ręczna").assertIsDisplayed()
        compose.onNodeWithTag("queue-toggle").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish header labels remain readable at double font scale within 320 dp`() {
        activity.get().setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    Box(Modifier.width(320.dp)) { QueueHeader("A very long source playlist name", {}) }
                }
            }
        }
        frame()
        listOf("Wyczyść kolejkę", "Pomoc").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            // Fractional glyph widths can exceed an integer layout by less than one pixel;
            // check actual text truncation, not that rounding-only didOverflowWidth flag.
            assertTrue("Clipped header label: $label ${layouts.map { "${it.size}, end=${it.getLineEnd(0, true)}, ellipsis=${it.isLineEllipsized(0)}" }}",
                layouts.isNotEmpty() && layouts.all {
                    !it.didOverflowHeight && !it.isLineEllipsized(0) && it.getLineEnd(0, true) == label.length
                })
        }
        val clear = compose.onNodeWithTag("queue-clear").fetchSemanticsNode().boundsInRoot
        val help = compose.onNodeWithTag("queue-help").fetchSemanticsNode().boundsInRoot
        assertTrue(clear.right <= help.left)
        assertTrue(help.right <= 640f)
    }
}
