// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** B62: what an alarm's card says in a few words. */
class AlarmSummariesTest {
    private fun song(id: String, title: String) = AlarmSong(id, id.padEnd(64, 'a').take(64).map { if (it in '0'..'9' || it in 'a'..'f') it else 'a' }.joinToString(""), title, durationMillis = 1000)

    @Test fun `days have names where they have one`() {
        assertEquals(AlarmSummaries.Days.ONCE, AlarmSummaries.days(emptySet()))
        assertEquals(AlarmSummaries.Days.WEEKDAYS, AlarmSummaries.days(setOf(1, 2, 3, 4, 5)))
        assertEquals(AlarmSummaries.Days.WEEKENDS, AlarmSummaries.days(setOf(6, 7)))
        assertEquals(AlarmSummaries.Days.EVERY_DAY, AlarmSummaries.days((1..7).toSet()))
        assertEquals(AlarmSummaries.Days.LISTED, AlarmSummaries.days(setOf(1, 3)))
        assertEquals(AlarmSummaries.Days.LISTED, AlarmSummaries.days(setOf(1, 2, 3, 4, 5, 6)))
    }

    @Test fun `music is the first chosen tune still held and how many more`() {
        val songs = listOf(song("a1", "Lotus"), song("b2", "Enigma"), song("c3", "Hybris"))
        val alarm = MusicAlarm("x", 7, 0, songIds = listOf("b2", "gone", "c3"))
        assertEquals(AlarmSummaries.Music("Enigma", 1), AlarmSummaries.music(alarm, songs))
        assertEquals(null, AlarmSummaries.music(alarm.copy(songIds = listOf("gone")), songs))
    }

    @Test fun `the wait is hours and minutes, rounded up, within a day`() {
        val now = Instant.parse("2026-11-10T06:00:00Z")
        assertEquals(AlarmSummaries.Wait(0, 1), AlarmSummaries.until(now.plusSeconds(10).toEpochMilli(), now))
        assertEquals(AlarmSummaries.Wait(9, 12), AlarmSummaries.until(now.plusSeconds(9 * 3600 + 11 * 60 + 30).toEpochMilli(), now))
        assertEquals(null, AlarmSummaries.until(now.plusSeconds(24 * 3600).toEpochMilli(), now))
        assertEquals(null, AlarmSummaries.until(now.minusSeconds(60).toEpochMilli(), now))
    }

    @Test fun `a tune's users are the alarms that chose it`() {
        val tune = song("a1", "Lotus")
        val alarms = listOf(MusicAlarm("x", 7, 0, songIds = listOf("a1")), MusicAlarm("y", 8, 0, songIds = listOf("b2")))
        assertEquals(listOf("x"), AlarmSummaries.users(tune, alarms).map { it.id })
    }
}
