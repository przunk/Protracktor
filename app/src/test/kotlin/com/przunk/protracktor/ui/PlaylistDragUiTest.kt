// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.PlayQueue
import com.przunk.protracktor.player.TrackRef
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers

/** Physical grips and rendered overlap through the production shell, never a native audio device. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class PlaylistDragUiTest : PlaybackShellTest() {
    private val tracks = (0..19).map { index ->
        TrackRef("content://playlist.drag.test/$index.mod", "Playlist item $index", author = "Known author")
    }

    private fun open() {
        compose.runOnIdle {
            player.value = player.value.copy(queue = PlayQueue(tracks).startAt(0), playback = null,
                playing = false, positionSeconds = 12.5, metadata = mapOf("title" to "Preserved metadata"))
        }
        frame(300)
    }

    private fun row(track: TrackRef) = compose.onNodeWithTag("enqueue-row-${player.value.queue.tracks.indexOfFirst { it.id == track.id }}")
    private fun grip(track: TrackRef) = compose.onNodeWithContentDescription(
        application.getString(R.string.a11y_reorder, track.title), useUnmergedTree = true)

    @Test fun `grip survives a small initial move and the following recomposition`() {
        open()
        val track = tracks.first()
        val height = row(track).fetchSemanticsNode().boundsInRoot.height
        val epoch = ReflectionHelpers.getField<Long>(controller, "playbackEpoch")
        grip(track).performTouchInput { down(center); moveBy(Offset(0f, height * 0.35f)) }
        frame()
        compose.onRoot().performTouchInput { moveBy(Offset(0f, height * 1.2f)) }
        frame()
        assertEquals("The original pointer must still move the same occurrence", 1,
            player.value.queue.tracks.indexOf(track))
        compose.onRoot().performTouchInput { moveBy(Offset(0f, height * 1.2f)) }
        frame()
        assertEquals("Reordering itself must not drop the pointer", 2, player.value.queue.tracks.indexOf(track))
        compose.onRoot().performTouchInput { up() }
        frame(200)
        assertEquals(epoch, ReflectionHelpers.getField<Long>(controller, "playbackEpoch"))
        assertEquals(track, player.value.current)
        assertFalse(player.value.playing)
        assertEquals(12.5, player.value.positionSeconds, 0.0)
        assertTrue(player.value.schedule.pending.isEmpty())
    }

    @Test fun `dragging the first row downward keeps the viewport fixed and row under the finger`() {
        open()
        val track = tracks.first()
        val before = row(track).fetchSemanticsNode().boundsInRoot
        val gripBefore = grip(track).fetchSemanticsNode().boundsInRoot
        val distance = before.height * 2.5f
        grip(track).performTouchInput { down(center); moveBy(Offset(0f, distance)) }
        frame(200)
        assertEquals("The viewport must not follow the moved first item's key", before.top,
            row(tracks[1]).fetchSemanticsNode().boundsInRoot.top, 2f)
        // The wrapper semantics describe its allocated slot; descendants include the draw layer.
        assertEquals("The grip should follow the finger, allowing platform touch slop", gripBefore.center.y + distance,
            grip(track).fetchSemanticsNode().boundsInRoot.center.y, before.height * 0.3f)
        assertEquals(2, player.value.queue.tracks.indexOf(track))
        compose.onRoot().performTouchInput { up() }
        frame(200)
    }

    @Test fun `downward dragged row paints over its neighbour instead of inside its original clip`() {
        assertOverlap(index = 0, direction = 1f)
    }

    @Test fun `upward dragged row also paints over its neighbour`() {
        assertOverlap(index = 1, direction = -1f)
    }

    private fun assertOverlap(index: Int, direction: Float) {
        open()
        val track = tracks[index]
        val before = row(track).fetchSemanticsNode().boundsInRoot
        val view = activity.get().window.decorView
        val colour = view.renderedPixel(Offset(before.left + 8f, before.top + 12f))
        val point = Offset(before.left + 8f, if (direction > 0) before.bottom + 12f else before.top - 12f)
        assertNotEquals("Fixture needs differently coloured neighbours", colour, view.renderedPixel(point))
        grip(track).performTouchInput { down(center); moveBy(Offset(0f, direction * before.height * 0.55f)) }
        frame(200)
        assertEquals("The whole raised row must paint outside its old slot", colour, view.renderedPixel(point))
        compose.onRoot().performTouchInput { up() }
        frame(200)
        assertEquals(tracks, player.value.queue.tracks)
    }

    @Test fun `successive input events before recomposition move the dragged row not its old neighbour`() {
        open()
        val track = tracks.first()
        val height = row(track).fetchSemanticsNode().boundsInRoot.height
        grip(track).performTouchInput { down(center); moveBy(Offset(0f, height * 0.35f)) }
        frame()
        compose.onRoot().performTouchInput {
            moveBy(Offset(0f, height * 1.2f), delayMillis = 0)
            moveBy(Offset(0f, height * 1.2f), delayMillis = 0)
        }
        frame()
        compose.onRoot().performTouchInput { up() }
        frame(200)
        assertEquals((listOf(tracks[1], tracks[2], track) + tracks.drop(3)).map { it.id },
            player.value.queue.tracks.map { it.id })
        assertEquals(track, player.value.current)
        assertTrue(player.value.dirty)
    }

    @Test fun `cancel resets displacement and a later grip and right swipe remain usable`() {
        open()
        val track = tracks.first()
        val before = row(track).fetchSemanticsNode().boundsInRoot
        grip(track).performTouchInput { down(center); moveBy(Offset(0f, before.height * 0.35f)) }
        frame()
        compose.onRoot().performTouchInput { cancel() }
        frame(200)
        assertEquals(before.top, row(track).fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals(tracks, player.value.queue.tracks)
        grip(track).performTouchInput { down(center); moveBy(Offset(0f, before.height * 1.4f)) }
        frame()
        compose.onRoot().performTouchInput { up() }
        frame(200)
        assertEquals(1, player.value.queue.tracks.indexOf(track))
        assertEquals(before.top + before.height, row(track).fetchSemanticsNode().boundsInRoot.top, 2f)
        row(track).performTouchInput { swipeRight() }
        frame(300)
        assertEquals(track, player.value.schedule.pending.single().track)
        assertFalse(player.value.playing)
    }
}
