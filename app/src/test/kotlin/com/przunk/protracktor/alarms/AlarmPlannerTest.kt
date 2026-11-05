// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.alarms

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class AlarmPlannerTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private fun alarm(hour: Int = 7, minute: Int = 0, days: Set<Int> = emptySet()) =
        MusicAlarm("alarm", hour, minute, weekdays = days, songIds = listOf("song"), enabled = true)
    private fun next(a: MusicAlarm, now: String) = AlarmPlanner.next(a, Instant.parse(now), warsaw)!!

    @Test fun `disabled schedules do not acquire a future occurrence`() {
        assertNull(AlarmPlanner.next(alarm().copy(enabled = false), Instant.EPOCH, warsaw))
    }

    @Test fun `one shot uses a strict future minute and crosses midnight`() {
        assertEquals(Instant.parse("2026-11-03T06:00:00Z").toEpochMilli(), next(alarm(), "2026-11-03T05:59:59Z").atMillis)
        assertEquals(Instant.parse("2026-11-04T06:00:00Z").toEpochMilli(), next(alarm(), "2026-11-03T06:00:00Z").atMillis)
        assertEquals("2027-01-01", next(alarm(0), "2026-12-31T22:59:59Z").date.toString())
    }

    @Test fun `weekly alarm selects the next configured weekday without a seven day truncation`() {
        val monday = alarm(days = setOf(1))
        assertEquals("2026-11-09", next(monday, "2026-11-02T07:00:00Z").date.toString())
        assertEquals("2026-11-09", next(monday.copy(consumedDate = "2026-11-02"), "2026-11-02T05:00:00Z").date.toString())
    }

    @Test fun `DST missing time moves to the first real local time after the gap`() {
        val occurrence = next(alarm(2, 30), "2026-03-28T23:00:00Z")
        assertEquals(Instant.parse("2026-03-29T01:00:00Z").toEpochMilli(), occurrence.atMillis)
    }

    @Test fun `DST overlap chooses an eligible instant and consumed date cannot ring twice`() {
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), next(alarm(2, 30), "2026-10-24T23:00:00Z").atMillis)
        assertEquals(Instant.parse("2026-10-25T01:30:00Z").toEpochMilli(), next(alarm(2, 30), "2026-10-25T00:45:00Z").atMillis)
        assertEquals("2026-10-26", next(alarm(2, 30).copy(consumedDate = "2026-10-25"), "2026-10-25T00:45:00Z").date.toString())
    }

    @Test fun `consuming a one shot retains its row disabled while weekly plans the next date`() {
        val now = Instant.parse("2026-11-03T06:00:00Z")
        val once = AlarmPlanner.planned(alarm(), now.minusSeconds(1), warsaw)
        val stopped = AlarmPlanner.consume(once, now, warsaw)
        assertFalse(stopped.enabled)
        assertEquals(0L, stopped.nextAtMillis)
        assertEquals("2026-11-03", stopped.consumedDate)
        val daily = AlarmPlanner.planned(alarm(days = (1..7).toSet()), now.minusSeconds(1), warsaw)
        assertEquals(Instant.parse("2026-11-04T06:00:00Z").toEpochMilli(), AlarmPlanner.consume(daily, now, warsaw).nextAtMillis)
    }

    @Test fun `earliest broker time ignores disabled or unplanned schedules`() {
        assertEquals(200L, AlarmPlanner.earliest(listOf(alarm().copy(nextAtMillis = 500), alarm().copy(nextAtMillis = 100, enabled = false), alarm().copy(nextAtMillis = 200))))
        assertNull(AlarmPlanner.earliest(listOf(alarm())))
    }

    @Test fun `elapsed timeout cannot be extended by wall clock or a reboot`() {
        val song = AlarmSong("song", "a".repeat(64), "Tune", platformId = "amiga", durationMillis = 1000)
        val quiz = AlarmQuizFactory(listOf("amiga" to "Amiga", "pc" to "PC", "c64" to "C64")).create(song, listOf(song))!!
        val active = RingingAlarm("session", setOf("alarm"), song, quiz, 100, 600_100, 4)
        assertFalse(active.expired(600_099, 4))
        assertTrue(active.expired(600_100, 4))
        assertTrue(active.expired(99, 4))
        assertTrue(active.expired(200, 5))
    }
}
