// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class AlarmReconciliationTest {
    private val song = AlarmSong("song", "a".repeat(64), "Music", platformId = "amiga", durationMillis = 3000)
    private val at = Instant.parse("2026-11-03T07:00:00Z").toEpochMilli()
    private val alarm = MusicAlarm("alarm", 7, 0, songIds = listOf(song.id), enabled = true,
        nextAtMillis = at, occurrenceDate = "2026-11-03")
    private val book = AlarmBook(songs = listOf(song), alarms = listOf(alarm))
    private val rules = AlarmRules(AlarmQuizFactory(emptyList())) { true }
    private fun reconcile(late: Long, reset: Boolean = false, source: Boolean = true) = rules.reconcile(book,
        AlarmClockReading(at + late, 2000, 1), ZoneId.of("UTC"), reset) { source }.book
    @Test fun `opening management cannot skip a recently due receiver occurrence`() {
        assertEquals(book, reconcile(30_000))
    }
    @Test fun `missed one shot is consumed instead of silently rearmed tomorrow`() {
        val missed = reconcile(180_000)
        assertFalse(missed.alarms.single().enabled)
        assertEquals(0L, missed.alarms.single().nextAtMillis)
        assertEquals(AlarmOutcomeKind.MISSED, missed.lastOutcome!!.kind)
    }
    @Test fun `boot or wall clock reset retires a past occurrence without a late burst`() {
        val missed = reconcile(1000, reset = true)
        assertFalse(missed.alarms.single().enabled)
        assertNull(missed.ringing)
    }
    @Test fun `unavailable music keeps requested enablement without an armed instant`() {
        val pending = reconcile(-60_000, source = false)
        assertTrue(pending.alarms.single().enabled)
        assertEquals(0L, pending.alarms.single().nextAtMillis)
        assertNull(pending.lastOutcome)
    }
    @Test fun `missed weekly occurrence advances once to a future selected day`() {
        val weekly = book.copy(alarms = listOf(alarm.copy(weekdays = setOf(2))))
        val next = rules.reconcile(weekly, AlarmClockReading(at + 180_000, 2000, 1), ZoneId.of("UTC"), false) { true }.book
        assertTrue(next.alarms.single().enabled)
        assertEquals("2026-11-10", next.alarms.single().occurrenceDate)
        assertEquals("2026-11-03", next.alarms.single().consumedDate)
    }
    @Test fun `a restored challenge cannot play derived audio from a withdrawn catalogue`() {
        val quiz = AlarmQuiz(AlarmQuestion.TITLE, listOf(AlarmChoice("a", "Music"), AlarmChoice("b", "Other"), AlarmChoice("c", "Third")), "a")
        val active = RingingAlarm("token", setOf("alarm"), song, quiz, 100, 600100, 1)
        val unavailable = AlarmRules(AlarmQuizFactory(emptyList())) { false }
        val restored = unavailable.reconcile(book.copy(ringing = active), AlarmClockReading(at-1000, 1000, 1), ZoneId.of("UTC"), false) { false }.book
        assertTrue(restored.ringing!!.backup)
        assertNull(restored.ringing!!.quiz)
        assertEquals(active.token, restored.ringing!!.token)
        assertEquals(active.deadlineElapsed, restored.ringing!!.deadlineElapsed)
    }

}
