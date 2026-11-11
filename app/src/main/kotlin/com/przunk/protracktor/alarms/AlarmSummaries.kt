// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import java.time.Duration
import java.time.Instant

/**
 * What an alarm's card says in a few words (B62): the days as a name where they have one, the
 * music by its first tune, how long until it rings. Pure, so the JVM holds the rules.
 */
internal object AlarmSummaries {
    enum class Days { ONCE, WEEKDAYS, WEEKENDS, EVERY_DAY, LISTED }

    fun days(weekdays: Set<Int>): Days = when (weekdays) {
        emptySet<Int>() -> Days.ONCE
        (1..7).toSet() -> Days.EVERY_DAY
        (1..5).toSet() -> Days.WEEKDAYS
        setOf(6, 7) -> Days.WEEKENDS
        else -> Days.LISTED
    }

    /** The first chosen tune the library still holds, and how many more there are. */
    data class Music(val first: String, val more: Int)

    fun music(alarm: MusicAlarm, songs: List<AlarmSong>): Music? {
        val chosen = alarm.songIds.mapNotNull { id -> songs.firstOrNull { it.id == id } }
        return chosen.firstOrNull()?.let { Music(it.title, chosen.size - 1) }
    }

    /** Hours and minutes until [at], rounded up to the minute; null past a day, when the day says it. */
    data class Wait(val hours: Long, val minutes: Long)

    fun until(at: Long, now: Instant): Wait? {
        val left = Duration.ofMillis(at - now.toEpochMilli())
        if (left.isNegative || left >= Duration.ofDays(1)) return null
        val minutes = (left.seconds + 59) / 60  // `seconds`, not `toSeconds()`: that one is API 31
        return Wait(minutes / 60, minutes % 60)
    }

    /** How many alarms use [song], for the Music tab and for deleting it. */
    fun users(song: AlarmSong, alarms: List<MusicAlarm>): List<MusicAlarm> = alarms.filter { song.id in it.songIds }
}
