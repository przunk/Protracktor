// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.alarms

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Wall-clock occurrence selection, separate from Android delivery and elapsed ringing time. */
object AlarmPlanner {
    data class Occurrence(val atMillis: Long, val date: LocalDate)

    fun next(alarm: MusicAlarm, now: Instant, zone: ZoneId): Occurrence? {
        if (!alarm.enabled || alarm.songIds.isEmpty()) return null
        val today = now.atZone(zone).toLocalDate()
        for (day in 0L..7L) {
            val date = today.plusDays(day)
            if (date.toString() == alarm.consumedDate) continue
            if (alarm.weekdays.isNotEmpty() && date.dayOfWeek.value !in alarm.weekdays) continue
            val local = date.atTime(alarm.hour, alarm.minute)
            val offsets = zone.rules.getValidOffsets(local)
            val candidates = if (offsets.isEmpty()) {
                listOf(zone.rules.getTransition(local)!!.dateTimeAfter.atZone(zone).toInstant())
            } else offsets.map { local.atOffset(it).toInstant() }
            val instant = candidates.filter { it > now }.minOrNull() ?: continue
            return Occurrence(instant.toEpochMilli(), date)
        }
        return null
    }

    fun planned(alarm: MusicAlarm, now: Instant, zone: ZoneId): MusicAlarm {
        val occurrence = next(alarm, now, zone)
        return alarm.copy(nextAtMillis = occurrence?.atMillis ?: 0, occurrenceDate = occurrence?.date?.toString())
    }

    fun earliest(alarms: List<MusicAlarm>): Long? =
        alarms.asSequence().filter { it.enabled && it.nextAtMillis > 0 }.map { it.nextAtMillis }.minOrNull()

    fun consume(alarm: MusicAlarm, now: Instant, zone: ZoneId): MusicAlarm {
        val consumed = alarm.copy(
            consumedDate = alarm.occurrenceDate ?: Instant.ofEpochMilli(alarm.nextAtMillis).atZone(zone).toLocalDate().toString(),
            enabled = alarm.enabled && alarm.weekdays.isNotEmpty(),
            nextAtMillis = 0,
            occurrenceDate = null,
        )
        return planned(consumed, now, zone)
    }
}
