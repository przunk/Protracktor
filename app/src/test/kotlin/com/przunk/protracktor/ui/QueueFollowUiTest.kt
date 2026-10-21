// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Observe drawn rows throughout automatic following, not just their settled coordinates. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class QueueFollowUiTest : PlaybackShellTest() {
    private val tracks = (0..40).map { tune.copy(id = "content://follow.test/$it.mod", title = "Source item $it") }
    private fun row(index: Int) = compose.onNodeWithTag("playback-row-source:$index")

    private fun open(): Int {
        compose.runOnIdle {
            val context = ActiveQueue(QueueSource.Playlist(player.value.activePlaylistId), PlayQueue(tracks).startAt(0))
            player.value = player.value.withPlayback(context).copy(
                schedule = PlaybackSchedule().contextSelected(context, SchedulePlacement.REPLACE), playing = false)
        }
        frame()
        compose.onNodeWithTag("queue-toggle").performClick()
        frame(600)
        val viewport = compose.onNodeWithTag("playback-list").fetchSemanticsNode().boundsInRoot
        val height = row(0).fetchSemanticsNode().boundsInRoot.height
        val lastFull = ((viewport.bottom - viewport.top) / height).toInt() - 1
        assertTrue("Fixture needs a long visible list", lastFull >= 3)
        return lastFull
    }

    private fun select(index: Int) {
        compose.runOnIdle {
            val context = player.value.activePlayback.withOrder(player.value.activePlayback.order.startAt(index))
            player.value = player.value.withPlayback(context).copy(
                schedule = player.value.schedule.contextSelected(context, SchedulePlacement.APPEND))
        }
        frame(400)
    }

    private fun next() = compose.onNode(SemanticsMatcher("Next transport action") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == "Next track"
    }).performTouchInput { click() }

    private fun ink(): Int {
        val bounds = row(2).fetchSemanticsNode().boundsInRoot
        // Centre of the playlist glyph's upper horizontal stroke, away from anti-aliased edges.
        return activity.get().window.decorView.renderedPixel(Offset(bounds.left + 44f, bounds.center.y - 12f))
    }

    @Test fun `Next following past the bottom never fades surviving source rows`() {
        val lastFull = open()
        select(lastFull)
        val view = activity.get().window.decorView
        val before = ink()
        val bounds = row(2).fetchSemanticsNode().boundsInRoot
        assertNotEquals("Fixture must sample glyph ink, not the background", before,
            view.renderedPixel(Offset(bounds.left + 4f, bounds.center.y)))
        val topBefore = bounds.top
        next()
        val frames = (1..40).map { frame(16); ink() }
        assertEquals(lastFull + 1, player.value.activePlayback.order.currentIndex)
        assertTrue("Next must actually scroll this fixture", row(2).fetchSemanticsNode().boundsInRoot.top < topBefore - 1f)
        assertTrue("Untouched row changed opacity: before=$before, frames=$frames", frames.all { it == before })
    }

    @Test fun `Next inside the viewport changes selection without scrolling or fading other rows`() {
        open()
        val before = ink()
        val top = row(2).fetchSemanticsNode().boundsInRoot.top
        next()
        val frames = (1..40).map { frame(16); ink() }
        assertEquals(1, player.value.activePlayback.order.currentIndex)
        assertEquals(top, row(2).fetchSemanticsNode().boundsInRoot.top, 1f)
        assertTrue("Visible selection must not fade unrelated rows", frames.all { it == before })
    }

    @Test fun `rapid Next presses still settle the actual current row wholly above the dock`() {
        val lastFull = open()
        select(lastFull)
        repeat(3) { next(); frame(32) }
        frame(1200)
        val current = lastFull + 3
        assertEquals(current, player.value.activePlayback.order.currentIndex)
        val viewport = compose.onNodeWithTag("playback-list").fetchSemanticsNode().boundsInRoot
        val currentBounds = row(current).fetchSemanticsNode().boundsInRoot
        assertTrue(currentBounds.top >= viewport.top - 1f)
        assertTrue(currentBounds.bottom <= viewport.bottom + 1f)
        compose.onAllNodesWithContentDescription("Now playing").assertCountEquals(1)
    }
}
