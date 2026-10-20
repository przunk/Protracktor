// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers
import kotlin.math.abs

/** Presentation changes through the production shell, with a controlled frame clock. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class QueuePresentationTest : PlaybackShellTest() {
    private fun open(pendingCount: Int = 2, withTail: Boolean = true): List<ManualRequest> {
        compose.runOnIdle {
            val context = ActiveQueue(QueueSource.Playlist(player.value.activePlaylistId),
                PlayQueue(if (withTail) listOf(tune, tune.copy(id = "tail", title = "Source tail")) else listOf(tune)).startAt(0))
            var schedule = PlaybackSchedule().contextSelected(context, SchedulePlacement.REPLACE)
                .enqueue(tune.copy(id = "manual", title = "Current manual"))
            repeat(pendingCount) { index ->
                schedule = schedule.enqueue(tune.copy(id = "duplicate", title = "Waiting $index"))
            }
            val selected = schedule.next(context) as ScheduledStep.Select
            player.value = player.value.withPlayback(context).copy(schedule = selected.schedule,
                playing = false, metadata = mapOf("title" to "Current manual"))
        }
        frame()
        compose.onNodeWithTag("queue-toggle").performClick()
        frame(600)
        return player.value.schedule.pending
    }

    private fun row(id: Long) = compose.onNodeWithTag("playback-row-manual:$id")

    @Test fun `help is on demand and dismissing it preserves playback and list position`() {
        val pending = open()
        val before = player.value
        val top = row(pending.last().id).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Swipe left").assertDoesNotExist()
        compose.onNodeWithTag("queue-help").performTouchInput { click() }
        frame(200)
        compose.onNodeWithText("Swipe left").assertIsDisplayed()
        compose.onNodeWithText("Swipe right").assertIsDisplayed()
        compose.onNodeWithText("Hold a row").assertIsDisplayed()
        compose.onNodeWithText("Drag a grip").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        frame(200)
        compose.onNodeWithText("Swipe left").assertDoesNotExist()
        assertEquals(top, row(pending.last().id).fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { assertEquals(before, player.value) }
    }

    @Test fun `physical row hold requests long press haptic and opens Info without playing`() {
        val pending = open()
        val before = player.value
        val view = descendants(activity.get().window.decorView).single {
            it.javaClass.simpleName == "AndroidComposeView"
        }
        compose.runOnIdle { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        row(pending.first().id).performTouchInput { longClick() }
        frame(200)
        compose.onNodeWithText("Close").assertIsDisplayed()
        assertEquals(HapticFeedbackConstants.LONG_PRESS, shadowOf(view).lastHapticFeedbackPerformed())
        compose.runOnIdle { assertEquals(before, player.value) }
    }

    @Test fun `row Info hold survives playback progress recompositions without starting audio`() {
        val pending = open()
        val current = player.value.schedule.current
        val epoch = ReflectionHelpers.getField<Long>(controller, "playbackEpoch")
        row(pending.first().id).performTouchInput { down(center) }
        repeat(8) {
            compose.runOnIdle { player.value = player.value.copy(positionSeconds = player.value.positionSeconds + 0.1) }
            frame(100)
        }
        compose.onNodeWithTag("playback-panel").performTouchInput { up() }
        frame(200)
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(current, player.value.schedule.current)
            assertEquals(epoch, ReflectionHelpers.getField<Long>(controller, "playbackEpoch"))
            assertFalse(player.value.playing)
        }
    }

    @Test fun `consuming manual current moves surviving rows through intermediate positions`() {
        assertConsumptionMotion(showSourceAnchor = true)
    }

    @Test fun `consumption also animates at the initially visible current row`() {
        assertConsumptionMotion(showSourceAnchor = false)
    }

    private fun assertConsumptionMotion(showSourceAnchor: Boolean) {
        val pending = open()
        val currentId = player.value.schedule.current!!.id
        if (showSourceAnchor) compose.onNodeWithTag("playback-list").performScrollToIndex(0)
        frame(400)
        val before = row(pending.last().id).fetchSemanticsNode().boundsInRoot.top
        controller.nextFile()
        frame(32)
        compose.runOnIdle { assertEquals(pending.first(), player.value.schedule.current!!.request) }
        frame(48)
        val during = row(pending.last().id).fetchSemanticsNode().boundsInRoot.top
        frame(400)
        val after = row(pending.last().id).fetchSemanticsNode().boundsInRoot.top
        assertTrue("Before=$before, during=$during, after=$after", during > after + 1f && during < before - 1f)
        row(currentId).assertDoesNotExist()
    }

    @Test fun `departing last manual row fades without leaving an actionable ghost`() {
        open(pendingCount = 0, withTail = false)
        val id = player.value.schedule.current!!.id
        compose.onNodeWithTag("playback-list").performScrollToIndex(0)
        frame(400)
        val bounds = row(id).fetchSemanticsNode().boundsInRoot
        val point = Offset(bounds.center.x, bounds.top + 12f)
        val before = pixel(point)
        ReflectionHelpers.callInstanceMethod<Unit>(controller, "handleTrackEnded",
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
        // Sample draw passes throughout the exit rather than guessing one intermediate frame.
        val frames = (1..12).map {
            frame(16)
            if (it == 2) {
                compose.runOnIdle { assertTrue(player.value.schedule.consumed) }
                row(id).assertDoesNotExist()
            }
            compose.mainClock.currentTime to pixel(point)
        }
        val stopped = player.value.schedule
        compose.onRoot().performTouchInput { longClick(Offset(point.x, bounds.center.y)) }
        compose.onNodeWithText("Close").assertDoesNotExist()
        frame(400)
        val after = pixel(point)
        val fullDistance = distance(before, after)
        val partial = frames.filter { distance(it.second, after) in 1 until fullDistance }
        assertTrue("Before=$before, frames=$frames, after=$after", fullDistance > 0 && partial.size >= 2)
        compose.runOnIdle { assertEquals(stopped, player.value.schedule) }
    }

    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish Help remains discoverable and all gesture sections can be read`() {
        open()
        compose.onNodeWithText("Pomoc").assertIsDisplayed()
        compose.onNodeWithTag("queue-help").performClick()
        frame(200)
        compose.onNodeWithText("Obsługa kolejki").assertIsDisplayed()
        listOf("Przesuń w lewo", "Przesuń w prawo", "Przytrzymaj wiersz", "Przeciągnij uchwyt").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    private fun pixel(point: Offset): Int = activity.get().window.decorView.renderedPixel(point)

    private fun distance(a: Int, b: Int): Int =
        abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
    }
}
