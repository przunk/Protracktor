// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

class ListeningSessionTest {
    private var time = 0L
    private val session = ListeningSession { time }

    @Test fun `five progressing seconds qualify exactly once`() {
        session.begin("a", 7, 0.0, true)
        time = 4999
        assertNull(session.sample(4.999, true))
        time = 5000
        assertEquals(ListeningSession.Qualified("a", 7), session.sample(5.0, true))
        time = 9000
        assertNull(session.sample(9.0, true))
    }

    @Test fun `three second natural completion qualifies exactly once`() {
        session.begin("short", 7, 0.0, true)
        time = 3000
        assertEquals(ListeningSession.Qualified("short", 7), session.sample(3.0, false, completed = true))
        assertNull(session.sample(3.0, false, completed = true))
    }

    @Test fun `completion between polls still qualifies a very short track`() {
        session.begin("short", 0, 0.0, true)
        time = 200
        assertEquals("short", session.sample(0.08, false, completed = true)?.trackId)
    }

    @Test fun `end handler qualifies after the final stopped poll without needing another tick`() {
        session.begin("short", 0, 0.0, true)
        time = 3000
        assertNull(session.sample(3.0, false))
        assertEquals("short", session.sample(3.0, false, completed = true)?.trackId)
    }

    @Test fun `known duration completion qualifies even when the decoder still plays`() {
        session.begin("short", 0, 0.0, true)
        time = 3000
        assertNull(session.sample(3.0, true))
        assertEquals("short", session.sample(3.0, true, completed = true)?.trackId)
    }

    @Test fun `short playback interrupted by pause or skip does not qualify`() {
        session.begin("short", 0, 0.0, true)
        time = 3000
        assertNull(session.sample(3.0, false))
        session.begin("next", 0, 0.0, true)
        time += 100
        assertNull(session.sample(0.1, true))
    }

    @Test fun `seeking to the end cannot qualify but five real seconds still can`() {
        session.begin("seek", 0, 0.0, true)
        time = 1000
        session.sample(1.0, true)
        session.seeking(1.0)
        time += 1000
        session.baseline(99.0, true)
        time += 1000
        assertNull(session.sample(100.0, false, completed = true))
        session.baseline(100.0, true)
        time += 3000
        assertEquals("seek", session.sample(103.0, true)?.trackId)
    }

    @Test fun `restart restores eligibility after a sought occurrence`() {
        session.begin("short", 0, 0.0, true)
        session.seeking(0.0)
        session.begin("short", 0, 0.0, true)
        time = 3000
        assertEquals("short", session.sample(3.0, false, completed = true)?.trackId)
    }

    @Test fun `pause resume preserves eligibility for a short natural completion`() {
        session.begin("short", 0, 0.0, true)
        time = 1000
        session.sample(1.0, false)
        time = 60000
        session.baseline(1.0, true)
        time += 2000
        assertEquals("short", session.sample(3.0, false, completed = true)?.trackId)
    }

    @Test fun `failed start or zero audio completion never qualifies`() {
        session.begin("failed", 0, 0.0, false)
        time = 3000
        assertNull(session.sample(3.0, false, completed = true))
        session.begin("empty", 0, 0.0, true)
        time += 1000
        assertNull(session.sample(0.0, false, completed = true))
    }

    @Test fun `clear cancels short completion until a new occurrence`() {
        session.begin("short", 0, 0.0, true)
        time = 1000
        session.sample(1.0, true)
        session.forget()
        session.baseline(1.0, true)
        time += 2000
        assertNull(session.sample(3.0, false, completed = true))
        session.begin("short", 1, 0.0, true)
        time += 3000
        assertEquals(ListeningSession.Qualified("short", 1), session.sample(3.0, false, completed = true))
    }

    @Test fun `paused time and a seek jump earn nothing`() {
        session.begin("a", 0, 0.0, true)
        time = 2000
        assertNull(session.sample(2.0, false))
        time = 60000
        session.baseline(100.0, true)
        time += 2999
        assertNull(session.sample(102.999, true))
        time++
        assertEquals("a", session.sample(103.0, true)?.trackId)
    }

    @Test fun `short or stalled audio cannot qualify on a delayed poll`() {
        session.begin("short", 0, 0.0, true)
        time = 60000
        assertNull(session.sample(4.9, false))
        session.begin("failed", 0, 0.0, false)
        time += 60000
        assertNull(session.sample(0.0, false))
    }

    @Test fun `moving to another occurrence resets earned time even for the same file`() {
        session.begin("a", 0, 0.0, true)
        time = 4000
        assertNull(session.sample(4.0, true))
        session.begin("a", 0, 0.0, true)
        time += 2000
        assertNull(session.sample(2.0, true))
    }

    @Test fun `clear cancels current occurrence until a new begin`() {
        session.begin("a", 0, 0.0, true)
        time = 4000
        session.sample(4.0, true)
        session.forget()
        session.baseline(4.0, true)
        time += 10000
        assertNull(session.sample(14.0, true))
        session.begin("b", 1, 0.0, true)
        time += 5000
        assertEquals(ListeningSession.Qualified("b", 1), session.sample(5.0, true))
    }

    @Test fun `subsong position reset preserves file level earned time`() {
        session.begin("file", 0, 0.0, true)
        time = 3000
        session.sample(3.0, true)
        session.baseline(0.0, true)
        time += 2000
        assertEquals("file", session.sample(2.0, true)?.trackId)
    }
}
