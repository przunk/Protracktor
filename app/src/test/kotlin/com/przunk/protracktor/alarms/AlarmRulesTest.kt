// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.alarms

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AlarmRulesTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val now = AlarmClockReading(Instant.parse("2026-11-03T06:00:00Z").toEpochMilli(), 1000, 7)
    private val song = AlarmSong("song", "a".repeat(64), "Tune", "Jakim", "amiga", durationMillis = 1000)
    private val quizzes = AlarmQuizFactory(listOf("amiga" to "Amiga", "pc" to "PC", "c64" to "C64"))
    private val rules = AlarmRules(quizzes) { true }
    private fun alarm(id: String = "alarm", at: Long = now.wallMillis) = MusicAlarm(id, 7, 0, songIds = listOf(song.id), enabled = true,
        nextAtMillis = at, occurrenceDate = "2026-11-03")
    private fun book() = AlarmBook(3, listOf(song), listOf(alarm()))
    private fun ring(book: AlarmBook = book(), token: String = "session") =
        rules.delivery(book, book.revision, AlarmPlanner.earliest(book.alarms)!!, now, zone, token, Random(1)).book

    @Test fun `delivery consumes a one shot before exposing one stable challenge`() {
        val state = ring()
        assertFalse(state.alarms.single().enabled)
        assertEquals(0L, state.alarms.single().nextAtMillis)
        assertEquals("2026-11-03", state.alarms.single().consumedDate)
        assertNotNull(state.ringing?.quiz)
        assertEquals(now.elapsedMillis + AlarmLimits.RINGING_LIMIT_MS, state.ringing!!.deadlineElapsed)
    }

    @Test fun `duplicate early and old revision deliveries cannot start or reroll sound`() {
        assertFalse(rules.delivery(book(), 2, now.wallMillis, now, zone, "old").accepted)
        assertFalse(rules.delivery(book(), 3, now.wallMillis, now.copy(wallMillis = now.wallMillis - 1), zone, "early").accepted)
        val state = ring()
        assertFalse(rules.delivery(state, 3, now.wallMillis, now, zone, "again").accepted)
        assertEquals("session", state.ringing!!.token)
    }

    @Test fun `expired delivery is recorded as missed without a late burst`() {
        val previous = book().copy(alarms = listOf(alarm(at = now.wallMillis - AlarmLimits.DELIVERY_GRACE_MS - 1)))
        val state = ring(previous)
        assertNull(state.ringing)
        assertEquals(AlarmOutcomeKind.MISSED, state.lastOutcome?.kind)
        assertFalse(state.alarms.single().enabled)
    }

    @Test fun `overlapping deliveries preserve the current song choices token and deadline`() {
        val first = ring()
        val before = first.copy(alarms = first.alarms + alarm("second"))
        val state = ring(before, "replacement")
        assertEquals(first.ringing!!.quiz, state.ringing!!.quiz)
        assertEquals(first.ringing!!.token, state.ringing!!.token)
        assertEquals(first.ringing!!.deadlineElapsed, state.ringing!!.deadlineElapsed)
        assertEquals(setOf("alarm", "second"), state.ringing!!.alarmIds)
    }

    @Test fun `wrong and stale answers cannot dismiss while the correct answer persists its result`() {
        val state = ring()
        val quiz = state.ringing!!.quiz!!
        val wrong = quiz.choices.first { it.id != quiz.correctId }.id
        assertFalse(rules.answer(state, "session", wrong, now).accepted)
        assertFalse(rules.answer(state, "older-session", quiz.correctId, now).accepted)
        val answered = rules.answer(state, "session", quiz.correctId, now)
        assertTrue(answered.accepted)
        assertNull(answered.book.ringing)
        assertEquals(AlarmOutcomeKind.ANSWERED, answered.book.lastOutcome?.kind)
    }

    @Test fun `backup mode removes an inaudible question and still permits deliberate stop`() {
        val state = rules.backup(ring(), "session").book
        assertTrue(state.ringing!!.backup)
        assertNull(state.ringing!!.quiz)
        assertFalse(rules.answer(state, "session", "choice-0", now).accepted)
        assertNull(rules.stop(state, "session", now).book.ringing)
    }

    @Test fun `a withdrawn source uses backup rather than playing its prepared clip`() {
        val closed = AlarmRules(quizzes) { false }
        val state = closed.delivery(book(), 3, now.wallMillis, now, zone, "session").book
        assertTrue(state.ringing!!.backup)
        assertNull(state.ringing!!.quiz)
    }

    @Test fun `preview does not consume schedules and never replaces an active alarm`() {
        val state = rules.preview(book(), "alarm", setOf("song"), now, "preview").book
        assertEquals(book().alarms, state.alarms)
        assertTrue(state.ringing!!.preview)
        assertFalse(rules.preview(state, "alarm", setOf("song"), now, "another").accepted)
        val stopped = rules.stop(state, "preview", now).book
        assertEquals(book().alarms, stopped.alarms)
        assertNull(stopped.lastOutcome)
    }

    @Test fun `timeout and reboot retire durable ringing without resetting its lifetime`() {
        val state = ring()
        val end = rules.expire(state, now.copy(elapsedMillis = state.ringing!!.deadlineElapsed))
        assertNull(end.book.ringing)
        assertEquals(AlarmOutcomeKind.TIMED_OUT, end.book.lastOutcome?.kind)
        assertEquals(AlarmOutcomeKind.INTERRUPTED, rules.expire(state, now.copy(bootCount = 8, elapsedMillis = 0)).book.lastOutcome?.kind)
    }
    @Test fun `a real occurrence joining a preview records only real schedule IDs`() {
        val preview = rules.preview(book(), "preview-song", setOf(song.id), now, "preview-token", Random(1)).book
        val state = ring(preview)
        assertFalse(state.ringing!!.preview)
        assertEquals(setOf("alarm"), state.ringing!!.alarmIds)
        assertEquals("preview-token", state.ringing!!.token)
        assertEquals(preview.ringing!!.deadlineElapsed, state.ringing!!.deadlineElapsed)
    }

}
