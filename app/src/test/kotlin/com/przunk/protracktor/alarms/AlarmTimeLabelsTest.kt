// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmTimeLabelsTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val now = Instant.parse("2026-11-04T10:00:00Z")
    private fun label(target: String, reference: Instant = now, locale: Locale = Locale.UK,
        use24Hours: Boolean = true, displayZone: ZoneId = zone): String = AlarmTimeLabels.upcoming(
            Instant.parse(target).toEpochMilli(), reference, displayZone, locale, use24Hours,
            if (locale.language == "pl") "Dzisiaj" else "Today",
            if (locale.language == "pl") "Jutro" else "Tomorrow")

    @Test fun `same calendar day shows Today and the selected clock format`() {
        assertEquals("Today · 17:30", label("2026-11-04T16:30:00Z"))
        assertEquals("Today · 5:30 PM", label("2026-11-04T16:30:00Z", locale = Locale.US, use24Hours = false))
    }
    @Test fun `the following date shows Tomorrow across month and year boundaries`() {
        assertEquals("Tomorrow · 07:30", label("2027-01-01T06:30:00Z", Instant.parse("2026-12-31T12:00:00Z")))
    }
    @Test fun `later occurrences show a short weekday without a numeric date`() {
        assertEquals("Mon · 07:30", label("2026-11-09T06:30:00Z"))
        assertEquals("Wed · 07:30", label("2026-11-11T06:30:00Z"))
    }
    @Test fun `Polish labels and weekdays follow the app locale`() {
        val pl = Locale.forLanguageTag("pl")
        assertEquals("Dzisiaj · 17:30", label("2026-11-04T16:30:00Z", locale = pl))
        assertEquals("Jutro · 07:30", label("2026-11-05T06:30:00Z", locale = pl))
        assertEquals("pon. · 07:30", label("2026-11-09T06:30:00Z", locale = pl))
    }
    @Test fun `Tomorrow uses calendar days even when the DST interval exceeds twenty four hours`() {
        assertEquals("Tomorrow · 23:15", label("2026-10-25T22:15:00Z", Instant.parse("2026-10-24T21:30:00Z")))
    }
    @Test fun `the displayed timezone decides whether a UTC date is today`() {
        assertEquals("Today · 19:30", label("2026-11-05T03:30:00Z", Instant.parse("2026-11-04T23:00:00Z"),
            displayZone = ZoneId.of("America/Los_Angeles")))
    }
    @Test fun `refresh wakes at midnight and is bounded for system clock and zone changes`() {
        assertEquals(20_000L, AlarmTimeLabels.refreshDelayMillis(Instant.parse("2026-11-04T22:59:40Z"), zone))
        assertEquals(60_000L, AlarmTimeLabels.refreshDelayMillis(now, zone))
        assertEquals("Tomorrow · 07:30", label("2026-11-05T06:30:00Z", Instant.parse("2026-11-04T22:59:59Z")))
        assertEquals("Today · 07:30", label("2026-11-05T06:30:00Z", Instant.parse("2026-11-04T23:00:00Z")))
    }
}
