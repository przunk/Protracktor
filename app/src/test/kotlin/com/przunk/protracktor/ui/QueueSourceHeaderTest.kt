// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import com.przunk.protracktor.R
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class QueueSourceHeaderTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private var opened = 0
    private var cleared = 0
    @Before fun start() { activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
    @After fun stop() { activity.close() }

    private fun show(source: String, width: Int = 360, scale: Float = 1f) {
        activity.get().setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                MaterialTheme {
                    Column(Modifier.width(width.dp)) {
                        Box(Modifier.testTag("before")) { QueueHeader(source, { cleared++ }) }
                        Box(Modifier.testTag("after")) { QueueHeader(source, { cleared++ }, { opened++ }) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun node(tag: String, parent: String) = compose.onNode(
        hasTestTag(tag) and hasAnyAncestor(hasTestTag(parent)))

    private fun unchangedGeometry() {
        val before = compose.onNodeWithTag("before").fetchSemanticsNode().boundsInRoot
        val after = compose.onNodeWithTag("after").fetchSemanticsNode().boundsInRoot
        assertEquals("No new header row or height", before.height, after.height, 0.5f)
        assertEquals(before.width, after.width, 0.5f)
        for (tag in listOf("queue-clear", "queue-help")) {
            val old = node(tag, "before").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val new = node(tag, "after").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("$tag stays horizontally fixed", old.left, new.left, 0.5f)
            assertEquals("$tag stays vertically fixed", old.top - before.top, new.top - after.top, 0.5f)
            assertEquals(old.width, new.width, 0.5f)
            assertEquals(old.height, new.height, 0.5f)
            val layouts = mutableListOf<TextLayoutResult>()
            node(tag, "after").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val label = activity.get().getString(if (tag == "queue-clear") R.string.queue_clear else R.string.queue_help)
            assertTrue("Full action caption remains visible: $label", layouts.isNotEmpty() && layouts.all {
                !it.didOverflowHeight && (0 until it.lineCount).none(it::isLineEllipsized) &&
                    it.getLineEnd(it.lineCount - 1, true) == label.length
            })
            for (layout in layouts) for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line, true)
                assertTrue("Caption wraps between words: $label, line $line ends at $end",
                    end > 0 && (label[end - 1].isWhitespace() || label.getOrNull(end)?.isWhitespace() == true))
            }
        }
        val source = node("queue-source", "after").fetchSemanticsNode().boundsInRoot
        val clear = node("queue-clear", "after").fetchSemanticsNode().boundsInRoot
        assertTrue("The source uses the full header action height: source=$source clear=$clear header=$after",
            source.height >= clear.height - 0.5f)
        assertTrue(source.right <= node("queue-clear", "after").fetchSemanticsNode().boundsInRoot.left)
    }

    @Test fun `Random action preserves header height and Clear Help placement`() {
        show("Random")
        unchangedGeometry()
        compose.onNodeWithContentDescription("Go to source: Random").assertHasClickAction()
            .performTouchInput { click(Offset(center.x, 2f)) }
        assertEquals(1, opened)
        assertEquals(0, cleared)
        node("queue-clear", "after").performTouchInput { click() }
        assertEquals(1, cleared)
        assertEquals(1, opened)
    }

    @Test fun `long source on a narrow viewport retains the same two-line header geometry`() {
        show("Modland / Video Game Music / Sega Megadrive / A very long original folder name", width = 280)
        unchangedGeometry()
    }

    @Test fun `large text retains action geometry without an extra caption`() {
        show("Search: a long search query in the original scope", width = 320, scale = 2f)
        unchangedGeometry()
        compose.onNodeWithText("Go to source").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish source at enlarged text retains Clear Help placement`() {
        show("Playlista: Moja ulubiona muzyka z dawnych lat", width = 320, scale = 2f)
        unchangedGeometry()
    }
}
