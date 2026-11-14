// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.PlayerUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Now Playing's actions in one row (2026-11-14): all as wide as the longest short label, scrolling
 * when they do not fit, the cut edge carrying an arrow that moves the row; nothing of the sort when
 * they fit; the full names said to TalkBack.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class ScrollingActionRowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val calls = mutableListOf<String>()

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }

    @After fun stop() {
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    private fun row(count: Int, rowWidth: Int = 312, label: (Int) -> String = { "Act $it" }) {
        val actions = (1..count).map { i ->
            RowAction(label(i), "Full action $i") { label, said, m ->
                LabelledAction(PlayerIcons.Play, label, { calls += label }, m.semantics { contentDescription = said }
                    .then(Modifier.testTag("act-$i")), slim = true)
            }
        }
        activity.get().setContent { MaterialTheme { Surface { ScrollingActionRow(actions, Modifier.width(rowWidth.dp)) } } }
        compose.waitForIdle()
    }

    @Test fun `actions that do not fit scroll, with an arrow only on the side there is more`() {
        row(7)
        compose.onNodeWithTag("action-row-back").assertDoesNotExist()
        compose.onNodeWithTag("action-row-forward").assertExists()
        // The arrow moves the row; then there is something behind, too.
        compose.onNodeWithTag("action-row-forward").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("action-row-back").assertExists()
        // To the end: no arrow onward.
        repeat(5) {
            if (compose.onAllNodesWithTag("action-row-forward").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithTag("action-row-forward").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("action-row-forward").assertDoesNotExist()
        compose.onNodeWithTag("act-7").assertIsDisplayed()
        // And back.
        compose.onNodeWithTag("action-row-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("action-row-forward").assertExists()
    }

    @Test fun `actions that fit stand side by side, with no arrows`() {
        row(2)
        compose.onNodeWithTag("action-row-scroll").assertDoesNotExist()
        compose.onNodeWithTag("action-row-back").assertDoesNotExist()
        compose.onNodeWithTag("action-row-forward").assertDoesNotExist()
    }

    @Test fun `every action is as wide as the others, and says its full name`() {
        // Labels of different lengths, so equal widths are the row's doing and not the words'.
        row(4, label = { listOf("A", "Act 2", "A longer one", "Act 4")[it - 1] })
        val widths = (1..3).map { compose.onNodeWithTag("act-$it").fetchSemanticsNode().boundsInRoot.width }
        assertTrue(widths.toString(), widths.all { kotlin.math.abs(it - widths[0]) < 0.5f })
        compose.onNodeWithTag("act-1").assertContentDescriptionEquals("Full action 1").assertTextEquals("A").performClick()
        compose.waitForIdle()
        assertEquals(listOf("A"), calls)
    }

    @Test fun `Now Playing offers its actions in the row, short on screen and whole for TalkBack`() {
        activity.get().setContent { MaterialTheme { Surface {
            NowPlaying(state = PlayerUiState(), onSeek = {}, onShowInPlaylist = { calls += "playlist" },
                onShowNeighbours = { calls += "author" }, onShareFile = {}, onShareAudio = null, onShareLink = null,
                onUseForAlarms = { calls += "alarm" }, onAddToOtherPlaylist = { calls += "add" })
        } } }
        compose.waitForIdle()
        val app = activity.get()
        compose.onNodeWithContentDescription(app.getString(R.string.action_show_neighbours))
            .assertTextEquals(app.getString(R.string.np_short_author)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("author"), calls)
        compose.onNodeWithTag("action-row-forward").assertExists()
    }
}
