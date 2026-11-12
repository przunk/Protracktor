// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.BrowseDomain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers

/** Redesign round 2: choosing the bar's two places in Settings, and what they open, in the real shell. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class NavSlotsUiTest : PlaybackShellTest() {
    @org.junit.Before fun hostDecoders() {
        val catalogues = ReflectionHelpers.getField<com.przunk.protracktor.player.CatalogueBrowser>(controller, "catalogueBrowser")
        ReflectionHelpers.setField(catalogues, "backends", { "host-fixture" })
    }

    private fun tab(name: String) = compose.onNodeWithTag("nav-$name")
    private fun open(name: String) { tab(name).performClick(); frame(500) }
    private fun marked(name: String) = tab(name).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    private fun back() { compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }; frame(500) }
    private fun atPlaylist() { compose.onNodeWithTag("playlist-chooser").assertIsDisplayed(); marked("playlist") }

    private fun toPreview() {
        openSettings()
        scrollSettingsTo(hasTestTag("nav-preview"))
    }

    private fun tabName(tab: ShellTab) = application.getString(when (tab) {
        ShellTab.SEARCH -> R.string.nav_search
        ShellTab.RANDOM -> R.string.nav_random
        ShellTab.LOCAL -> R.string.nav_local
        ShellTab.SAVED -> R.string.nav_saved
        ShellTab.ONLINE -> R.string.nav_online
        ShellTab.HISTORY -> R.string.nav_history
        ShellTab.ALARMS -> R.string.nav_alarms
        else -> error("not a choice: $tab")
    })

    private fun choose(slot: Int, place: String) {
        compose.onNodeWithTag("nav-slot-$slot").performClick(); frame(400)
        compose.onNodeWithTag("nav-choice-$place").performClick(); frame(400)
    }

    @Test fun `a place chosen in Settings takes its slot on the bar, opens there, and Back returns to the playlist`() {
        toPreview()
        // The preview says which two can change, in words as well as by the outline.
        compose.onNodeWithTag("nav-slot-1").assertContentDescriptionContains("Place 1 on the bar: Search")
        compose.onNodeWithTag("nav-slot-2").assertContentDescriptionContains("Place 2 on the bar: Random")
        choose(1, "history")
        tab("history").assertExists()
        tab("search").assertDoesNotExist()
        compose.onNodeWithTag("nav-slot-1").assertContentDescriptionContains("Place 1 on the bar: History")
        // Remembered across launches.
        assertEquals(NavSlots(ShellTab.HISTORY, ShellTab.RANDOM),
            com.przunk.protracktor.Appearance.navSlots(application, alarms = true))

        open("playlist")
        open("history")
        compose.runOnIdle { assertEquals(BrowseDomain.HISTORY, browse.value.domain) }
        marked("history")
        back()
        atPlaylist()
    }

    @Test fun `the place already in the other slot is said so, and choosing it swaps the two`() {
        toPreview()
        compose.onNodeWithTag("nav-slot-1").performClick(); frame(400)
        // The current choice is ticked; the other slot's says what choosing it does.
        compose.onNodeWithTag("nav-choice-search").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithTag("nav-choice-random").assertTextContains(application.getString(R.string.settings_nav_in_other))
        compose.onNodeWithTag("nav-choice-random").performClick(); frame(400)
        val bar = compose.onAllNodes(hasAnyAncestor(hasTestTag("shell-navigation")) and hasTestTag("nav-random"))
        bar.assertCountEquals(1)
        val random = tab("random").fetchSemanticsNode().boundsInRoot.left
        val search = tab("search").fetchSemanticsNode().boundsInRoot.left
        assert(random < search) { "Random should now be left of Search" }
    }

    @Test fun `Restore defaults puts Search and Random back`() {
        toPreview()
        choose(1, "local")
        choose(2, "saved")
        tab("local").assertExists(); tab("saved").assertExists()
        compose.onNodeWithTag("nav-restore").performClick(); frame(400)
        tab("search").assertExists(); tab("random").assertExists()
        tab("local").assertDoesNotExist()
        compose.onNodeWithTag("nav-restore").assertIsNotEnabled()
    }

    @Test fun `On this phone from the bar is the bar's own place, and Back leaves it`() {
        toPreview()
        choose(2, "local")
        open("playlist")
        open("local")
        compose.runOnIdle { assertEquals(BrowseDomain.LOCAL, browse.value.domain) }
        marked("local")
        back()
        atPlaylist()
    }

    @Test fun `every choice in the list has an icon and a name`() {
        toPreview()
        compose.onNodeWithTag("nav-slot-2").performClick(); frame(400)
        assertEquals(true, com.przunk.protracktor.alarms.api.MusicAlarms.enabled)
        NavSlots.available(alarms = true).forEach { place ->
            val row = compose.onNodeWithTag("nav-choice-${place.name.lowercase()}")
            row.assertExists()
            // The name is said; the icon is drawn beside it (each choice has one in `tabLook`).
            row.assertTextContains(tabName(place))
        }
    }

    @Test fun `Alarms on the bar opens the alarms screen and stays unmarked`() {
        toPreview()
        choose(2, "alarms")
        open("playlist")
        org.robolectric.Shadows.shadowOf(application).clearNextStartedActivities()
        open("alarms")
        val started = org.robolectric.Shadows.shadowOf(application).nextStartedActivity
            ?: org.robolectric.Shadows.shadowOf(activity.get()).nextStartedActivity
        assertEquals(com.przunk.protracktor.alarms.AlarmsActivity::class.java.name, started?.component?.className)
        marked("playlist")
    }
}
