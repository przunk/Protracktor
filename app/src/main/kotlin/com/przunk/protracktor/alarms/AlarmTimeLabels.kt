// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Calendar-relative labels for upcoming occurrences; historical outcomes keep their dates. */
internal object AlarmTimeLabels {
    fun upcoming(millis: Long, now: Instant, zone: ZoneId, locale: Locale, use24Hours: Boolean,
        today: String, tomorrow: String): String {
        val target = Instant.ofEpochMilli(millis).atZone(zone)
        val localToday = now.atZone(zone).toLocalDate()
        val day = when (target.toLocalDate()) {
            localToday -> today
            localToday.plusDays(1) -> tomorrow
            else -> target.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        }
        return "$day · ${time(target.toLocalTime(), locale, use24Hours)}"
    }

    fun time(value: LocalTime, locale: Locale, use24Hours: Boolean): String =
        DateTimeFormatter.ofPattern(if (use24Hours) "HH:mm" else "h:mm a", locale).format(value)

    fun refreshDelayMillis(now: Instant, zone: ZoneId): Long {
        val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        return Duration.between(now, midnight).toMillis().coerceIn(1L, 60_000L)
    }
}
