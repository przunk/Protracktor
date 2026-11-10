// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.przunk.protracktor.R
import com.przunk.protracktor.data.SavedPlaylist
import com.przunk.protracktor.player.TrackRef
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** B62: the floating button, Add music from the editor, and the clearer list and editor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "en-w360dp-h800dp-xhdpi")
class AlarmUxUiTest {
    private class Host : org.junit.rules.ExternalResource() {
        lateinit var controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
        override fun before() { controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
        override fun after() { controller.close() }
    }
    private val hostRule = Host()
    private val host get() = hostRule.controller
    @get:Rule val compose: androidx.compose.ui.test.junit4.ComposeContentTestRule =
        androidx.compose.ui.test.junit4.AndroidComposeTestRule(hostRule) { hostRule.controller.get() }
    private fun text(id: Int) = host.get().getString(id)
    private val song = AlarmSong("song", "a".repeat(64), "Morning tune", "Artist", "amiga", durationMillis = 3000)
    private val lotus = TrackRef("content://music/lotus.mod", "Lotus 3", fileName = "lotus.mod", author = "Patrick Phelan")
    private val sources = object : AlarmMusicSources {
        override suspend fun playlists() = listOf(SavedPlaylist(1, "Wake-up", trackCount = 1))
        override suspend fun tracksIn(playlist: Long) = listOf(AlarmCandidates.of(lotus))
        override suspend fun history() = emptyList<AlarmCandidate>()
        override suspend fun search(query: String) = AlarmCandidates.search(listOf(lotus), emptyList(), query)
    }
    private val saved = { _: MusicAlarm -> AlarmSaveResult.Saved(AlarmScheduleResult.Idle) }
    private fun frame(ms: Long = 400) {
        compose.mainClock.advanceTimeBy(ms)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
        compose.waitForIdle()
    }

    @Test fun `the floating button makes a new alarm, and on Music adds music`() {
        compose.setContent { MaterialTheme { AlarmManagement(AlarmBook(songs = listOf(song)), null, false, true, true, true, false,
            usable = { true }, onBack = {}, onSave = saved, onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {},
            sources = sources) } }
        compose.onNodeWithTag("alarm-new").assert(hasContentDescription(text(R.string.alarm_new))).performClick()
        frame()
        // A new alarm says so in the editor's title.
        compose.onNodeWithText(text(R.string.alarm_new)).assertExists()
        compose.onNodeWithText(text(R.string.alarm_edit)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.alarm_back)).performClick()
        frame()
        compose.onNodeWithText(text(R.string.alarm_music_tab)).performClick()
        frame()
        compose.onNodeWithTag("alarm-new").assertDoesNotExist()
        compose.onNodeWithTag("alarm-add-music").assert(hasContentDescription(text(R.string.alarm_add_music))).performClick()
        frame()
        compose.onNodeWithTag("alarm-music-picker").assertExists()
    }

    @Test fun `at the limit the floating button says why instead of opening an editor`() {
        val alarms = (0 until AlarmLimits.MAX_ALARMS).map { MusicAlarm("a$it", it % 24, it % 60, songIds = listOf(song.id)) }
        compose.setContent { MaterialTheme { AlarmManagement(AlarmBook(songs = listOf(song), alarms = alarms), null, false, true, true, true, false,
            usable = { true }, onBack = {}, onSave = saved, onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {}) } }
        compose.onNodeWithTag("alarm-new").performClick()
        frame()
        compose.onNodeWithText(text(R.string.alarm_limit_reached)).assertExists()
        compose.onNodeWithText(text(R.string.alarm_save)).assertDoesNotExist()
    }

    @Test fun `music added from the editor is ticked in the alarm, which still waits for Save`() {
        val book = mutableStateOf(AlarmBook(songs = listOf(song)))
        val picks = mutableStateOf(emptyList<AlarmPick>())
        val savedAlarms = mutableListOf<MusicAlarm>()
        val added = mutableListOf<TrackRef>()
        compose.setContent { MaterialTheme { AlarmManagement(book.value, null, false, true, true, true, false,
            usable = { true }, onBack = {}, onSave = { savedAlarms += it; AlarmSaveResult.Saved(AlarmScheduleResult.Idle) },
            onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {},
            sources = sources, picks = picks.value, onAddMusic = { added += it; picks.value = listOf(AlarmPick(it, AlarmPickState.Preparing)) }) } }
        compose.onNodeWithTag("alarm-new").performClick()
        frame()
        compose.onNodeWithTag("alarm-editor-add-music").performScrollTo().performClick()
        frame()
        compose.onNodeWithText("Wake-up").performClick()
        frame()
        compose.onNodeWithText(text(R.string.alarm_pick_add)).performClick()
        frame()
        assertEquals(listOf(lotus), added)
        compose.onNodeWithText(text(R.string.alarm_pick_preparing)).assertExists()
        // Prepared: the library has it, and the pick says which tune it became.
        val prepared = AlarmSong("lotus", "b".repeat(64), "Lotus 3", "Patrick Phelan", "amiga", durationMillis = 3000)
        book.value = book.value.copy(songs = book.value.songs + prepared)
        picks.value = listOf(AlarmPick(lotus, AlarmPickState.Added(prepared.id)))
        frame()
        compose.onNodeWithText(text(R.string.alarm_pick_added)).assertExists()
        compose.onNodeWithText(text(R.string.alarm_picker_done)).performClick()
        frame()
        assertTrue(savedAlarms.isEmpty())
        compose.onNodeWithText(text(R.string.alarm_save)).performClick()
        frame()
        assertEquals(setOf(song.id, prepared.id), savedAlarms.single().songIds.toSet())
    }

    @Test fun `Back with changes asks, and Keep editing keeps them`() {
        val alarm = MusicAlarm("morning", 7, 30, "Morning", setOf(1), listOf(song.id), true)
        var left = 0
        compose.setContent { MaterialTheme { AlarmEditor(alarm, listOf(song), { true }, { left++ }, onSave = saved) } }
        // Unchanged: Back just leaves.
        compose.onNodeWithText(text(R.string.alarm_back)).performClick()
        frame()
        assertEquals(1, left)
        compose.onNodeWithText("Morning").performTextReplacement("Weekend")
        compose.onNodeWithText(text(R.string.alarm_back)).performClick()
        frame()
        compose.onNodeWithText(text(R.string.alarm_discard_title)).assertExists()
        compose.onNodeWithText(text(R.string.alarm_keep_editing)).performClick()
        frame()
        assertEquals(1, left)
        compose.onNodeWithText("Weekend").assertExists()
        compose.onNodeWithText(text(R.string.alarm_back)).performClick()
        frame()
        compose.onNodeWithText(text(R.string.alarm_discard)).performClick()
        frame()
        assertEquals(2, left)
    }

    @Test fun `Save that cannot be pressed says why`() {
        compose.setContent { MaterialTheme { AlarmEditor(MusicAlarm("new", 7, 0), listOf(song), { true }, {}, onSave = saved) } }
        compose.onNodeWithText(text(R.string.alarm_save_needs_music)).assertExists()
        compose.onNodeWithText("Morning tune").performScrollTo().performClick()
        frame()
        compose.onNodeWithText(text(R.string.alarm_save_needs_music)).assertDoesNotExist()
    }

    @Test fun `cards say values, the next alarm comes first, and the card opens the editor`() {
        val at = java.time.Instant.now().plusSeconds(3600 * 3 + 60).toEpochMilli()
        val alarm = MusicAlarm("morning", 7, 30, "Gym", setOf(1, 2, 3, 4, 5), listOf(song.id), true, at)
        val off = MusicAlarm("later", 9, 0, "", emptySet(), listOf(song.id), false)
        compose.setContent { MaterialTheme { AlarmManagement(AlarmBook(songs = listOf(song), alarms = listOf(alarm, off)), at, false, true, true, true, false,
            usable = { true }, onBack = {}, onSave = saved, onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {}) } }
        compose.onNodeWithTag("alarm-next").assert(hasAnyDescendant(hasText(text(R.string.alarm_next_title))))
            .assert(hasAnyDescendant(hasText("in 3 h", substring = true)))
        compose.onNodeWithText(text(R.string.alarm_days_weekdays)).assertExists()
        compose.onNodeWithText(text(R.string.alarm_days_once)).assertExists()
        compose.onAllNodesWithText("Morning tune").assertCountEquals(2)
        compose.onNodeWithText(text(R.string.alarm_disabled)).assertExists()
        compose.onNodeWithText("Name ·", substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.alarm_edit)).assertDoesNotExist()
        compose.onAllNodesWithTag("alarm-card")[0].performClick()
        frame()
        compose.onNodeWithText(text(R.string.alarm_edit)).assertExists()
        compose.onNodeWithText("Gym").assertExists()
    }

    @Test fun `Music says what uses a tune, and deleting it names those alarms`() {
        val alarm = MusicAlarm("morning", 7, 30, "Gym", setOf(1), listOf(song.id), true)
        compose.setContent { MaterialTheme { AlarmManagement(AlarmBook(songs = listOf(song), alarms = listOf(alarm)), null, false, true, true, true, false,
            usable = { true }, onBack = {}, onSave = saved, onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {}) } }
        compose.onNodeWithText(text(R.string.alarm_music_tab)).performClick()
        frame()
        compose.onNodeWithText(host.get().resources.getQuantityString(R.plurals.alarm_used_by, 1, 1)).assertExists()
        compose.onNodeWithText(text(R.string.alarm_delete)).performScrollTo().performClick()
        frame()
        compose.onNodeWithText("Gym", substring = true).assertExists()
    }
}
