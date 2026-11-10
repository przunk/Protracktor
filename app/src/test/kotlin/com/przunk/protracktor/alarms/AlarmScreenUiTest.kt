// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.przunk.protracktor.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "en-w360dp-h800dp-xhdpi")
class AlarmScreenUiTest {
    private class Host : org.junit.rules.ExternalResource() {
        lateinit var controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
        override fun before() { controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
        override fun after() { controller.close() }
    }
    private val hostRule = Host()
    private val host get() = hostRule.controller
    @get:Rule val compose: androidx.compose.ui.test.junit4.ComposeContentTestRule =
        androidx.compose.ui.test.junit4.AndroidComposeTestRule(hostRule) { hostRule.controller.get() }
    private val song = AlarmSong("song", "a".repeat(64), "Morning tune", "Artist", "amiga", durationMillis = 3000)
    private val quiz = AlarmQuiz(AlarmQuestion.PLATFORM, listOf(AlarmChoice("a", "Amiga"), AlarmChoice("b", "C64"), AlarmChoice("c", "Atari ST")), "a")
    private val active = RingingAlarm("token", setOf("alarm"), song, quiz, 0, 600000, 1)
    private fun text(id: Int) = host.get().getString(id)
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MaterialTheme { content() } }
        compose.waitForIdle()
    }
    private fun frame() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(100); compose.waitForIdle()
    }

    @Test fun `wrong answer leaves choices intact and correct tap takes the durable answer path`() {
        val state = mutableStateOf(AlarmBook(songs = listOf(song), ringing = active))
        val rules = AlarmRules(AlarmQuizFactory(emptyList())) { true }
        val commands = mutableListOf<String>()
        val completion = mutableStateOf<AlarmCompletion?>(null)
        val wrong = mutableStateOf<String?>(null)
        show { AlarmRingingScreen(state.value.ringing?.presented(), false, false, onAnswer = { token, choice ->
            commands += choice
            val result = rules.answer(state.value, token, choice, AlarmClockReading(100, 100, 1))
            state.value = result.book
            completion.value = if (result.accepted) AlarmCompletion.ANSWERED else null
            wrong.value = choice.takeUnless { result.accepted }
        }, onStop = { fail("Answer routed through emergency stop") }, onClose = {}, completion = completion.value, wrongChoice = wrong.value) }
        compose.onNodeWithText("C64").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText(text(R.string.alarm_wrong)).assertExists()
        assertEquals(active, state.value.ringing)
        compose.onNodeWithText("Amiga").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText(text(R.string.alarm_correct)).assertExists()
        assertNull(state.value.ringing)
        assertEquals(listOf("b", "a"), commands)
    }
    @Test fun `accessible emergency action requires confirmation before stopping`() {
        val stopped = mutableListOf<String>()
        show { AlarmRingingScreen(active.presented(), false, false, { _, _ -> }, stopped::add, {}) }
        compose.onNodeWithText(text(R.string.alarm_hold_stop)).performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        frame()
        assertTrue(stopped.isEmpty())
        compose.onNodeWithText(text(R.string.alarm_stop_confirm)).assertExists()
        compose.onNode(hasText(text(R.string.alarm_stop_anyway)) and hasClickAction()).performClick()
        assertEquals(listOf("token"), stopped)
    }
    @Test fun `emergency requires a real sustained touch while an ordinary tap is harmless`() {
        val stopped = mutableListOf<String>()
        show { AlarmRingingScreen(active.presented(), false, false, { _, _ -> }, stopped::add, {}) }
        val control = compose.onNodeWithText(text(R.string.alarm_hold_stop)).performScrollTo()
        control.performTouchInput { click() }
        assertTrue(stopped.isEmpty())
        control.performTouchInput { down(center) }
        compose.waitUntil(5_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            stopped.isNotEmpty()
        }
        control.performTouchInput { up() }
        assertEquals(listOf("token"), stopped)
    }
    @Test fun `backup audio never asks a question about inaudible music`() {
        val stopped = mutableListOf<String>()
        show { AlarmRingingScreen(active.copy(backup = true, quiz = null).presented(), false, false, { _, _ -> fail() }, stopped::add, {}) }
        compose.onNodeWithText("Amiga").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.alarm_stop_anyway)).performClick()
        assertEquals(listOf("token"), stopped)
    }
    @Test fun `missing exact access has an actionable reason without claiming an armed time`() {
        val requested = mutableListOf<AlarmAccess>()
        val alarm = MusicAlarm("alarm", 7, 30, songIds = listOf(song.id), enabled = true)
        show { AlarmManagement(AlarmBook(songs = listOf(song), alarms = listOf(alarm)), null, false,
            false, true, true, false, usable = { true }, onBack = {}, onSave = { AlarmSaveResult.Saved(AlarmScheduleResult.Idle) }, onDelete = { true },
            onRemoveMusic = {}, onTest = {}, onAccess = requested::add) }
        compose.onNodeWithText(text(R.string.alarm_exact_action)).performScrollTo().performClick()
        assertEquals(listOf(AlarmAccess.EXACT), requested)
        compose.onNodeWithText(text(R.string.alarm_not_armed), substring = true).performScrollTo().assertExists()
    }
    @Test fun `new alarm editor saves the selected prepared song`() {
        val saved = mutableListOf<MusicAlarm>()
        show { AlarmManagement(AlarmBook(songs = listOf(song)), null, false, true, true, true, false,
            usable = { true }, onBack = {}, onSave = { saved += it; AlarmSaveResult.Saved(AlarmScheduleResult.Idle) }, onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {}) }
        compose.onNodeWithTag("alarm-new").performClick()
        frame()
        compose.onNodeWithText(text(R.string.alarm_save)).performClick()
        assertEquals(listOf(song.id), saved.single().songIds)
        assertTrue(saved.single().enabled)
    }
    @Test fun `waiting for audio is explicit without rerolling the choices`() {
        val audio = mutableStateOf(AlarmAudioStatus.WAITING_FOCUS)
        show { AlarmRingingScreen(active.presented(), false, false, { _, _ -> }, {}, {}, audioStatus = audio.value) }
        compose.onNodeWithText(text(R.string.alarm_waiting_focus)).assertExists()
        compose.onNodeWithText("Amiga").assertExists()
        compose.runOnIdle { audio.value = AlarmAudioStatus.PLAYING }
        compose.onNodeWithText(text(R.string.alarm_listen)).assertExists()
        compose.onNodeWithText("Amiga").assertExists()
    }

    @Test fun `finger movement during emergency hold does not restart its countdown`() {
        val stopped = mutableListOf<String>()
        show { AlarmRingingScreen(active.presented(), false, false, { _, _ -> }, stopped::add, {}) }
        val control = compose.onNodeWithText(text(R.string.alarm_hold_stop)).performScrollTo()
        control.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(70f, -140f)) }
        compose.waitUntil(5_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            stopped.isNotEmpty()
        }
        control.performTouchInput { up() }
        assertEquals(listOf("token"), stopped)
    }
    @Test fun `successful challenge closes itself after its acknowledgement`() {
        val state = mutableStateOf<RingingAlarm?>(active)
        var closed = 0
        val completion = mutableStateOf<AlarmCompletion?>(null)
        show { AlarmRingingScreen(state.value?.presented(), true, false, { _, _ ->
            state.value = null; completion.value = AlarmCompletion.ANSWERED
        }, {}, { closed++ }, completion = completion.value) }
        compose.onNodeWithText("Amiga").performClick()
        compose.waitUntil(2500) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            closed > 0
        }
        assertEquals(1, closed)
    }

    @Test fun `acknowledgement text stays at the same position through its final frames`() {
        val state = mutableStateOf<RingingAlarm?>(active)
        val completion = mutableStateOf<AlarmCompletion?>(null)
        show { AlarmRingingScreen(state.value?.presented(), false, false, { _, _ ->
            state.value = null; completion.value = AlarmCompletion.ANSWERED
        }, {}, {}, completion = completion.value) }
        compose.onNodeWithText("Amiga").performClick()
        val before = compose.onNodeWithText(text(R.string.alarm_correct)).fetchSemanticsNode().boundsInRoot
        assertTrue(before.width > 0 && before.height > 0)
        compose.mainClock.advanceTimeBy(850)
        val after = compose.onNodeWithText(text(R.string.alarm_correct)).fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        System.getProperty("protracktor.screenshots")?.let { directory ->
            val view = host.get().window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            java.io.File(directory, "b57-r2-done-en.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun `wrong tap switches the visible topic and correct new answer dismisses the same session`() {
        val described = song.copy(year = "1992", yearAlternatives = listOf("1991", "1993"))
        val state = mutableStateOf(AlarmBook(3, listOf(described), ringing = active.copy(song = described)))
        val rules = AlarmRules(AlarmQuizFactory(listOf("amiga" to "Amiga", "pc" to "PC", "c64" to "C64"))) { true }
        val completion = mutableStateOf<AlarmCompletion?>(null)
        val wrong = mutableStateOf<String?>(null)
        show { AlarmRingingScreen(state.value.ringing?.presented(), false, false, { token, choice ->
            val result = rules.answer(state.value, token, choice, AlarmClockReading(100, 100, 1))
            state.value = result.book
            completion.value = if (result.accepted) AlarmCompletion.ANSWERED else null
            wrong.value = choice.takeUnless { result.accepted }
        }, {}, {}, completion = completion.value, wrongChoice = wrong.value) }
        compose.onNodeWithText("C64").performClick()
        compose.onNodeWithText(text(R.string.alarm_question_year)).assertExists()
        compose.onNodeWithText(text(R.string.alarm_wrong)).assertExists()
        assertEquals(active.token, state.value.ringing!!.token)
        assertEquals(active.deadlineElapsed, state.value.ringing!!.deadlineElapsed)
        compose.onNodeWithText("1992").performClick()
        assertNull(state.value.ringing)
        compose.onNodeWithText(text(R.string.alarm_correct)).assertExists()
    }

}
