// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import com.przunk.protracktor.permissions.AccessState
import org.junit.Assert.*
import org.junit.Test

class AlarmReadinessTest {
    private val ready = AlarmReadiness(AccessState.GRANTED, AccessState.GRANTED, AccessState.GRANTED, true, false)
    @Test fun `system alarm volume is a warning rather than a hidden scheduling permission`() {
        assertTrue(ready.copy(muted = true).canSchedule)
        assertTrue(ready.copy(muted = true).canPresent)
    }
    @Test fun `denied exact access prevents scheduling while a visible preview remains possible`() {
        val missing = ready.copy(exactAccess = AccessState.SETTINGS_NEEDED)
        assertFalse(missing.canSchedule)
        assertTrue(missing.notifications)
        assertEquals(listOf(AlarmAccess.EXACT), missing.blockers)
    }
    @Test fun `a muted low importance channel cannot be mistaken for a full screen ready alarm`() {
        val missing = ready.copy(channelReady = false)
        assertFalse(missing.canSchedule)
        assertFalse(missing.canPresent)
        assertEquals(listOf(AlarmAccess.NOTIFICATIONS), missing.blockers)
    }
    @Test fun `old platform not required grants preserve normal alarm readiness`() {
        assertTrue(ready.copy(exactAccess = AccessState.NOT_REQUIRED, fullScreenAccess = AccessState.NOT_REQUIRED).canSchedule)
    }
}
