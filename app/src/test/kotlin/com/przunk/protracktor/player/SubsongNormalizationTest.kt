// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A subsong switch with normalization on: silent, measured, then switched at its level. */
class SubsongNormalizationTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val events = mutableListOf<String>()
    private val measurements = mutableMapOf<Int, CompletableDeferred<LoudnessProfile?>>()
    private var width = 100
    private val profile = LoudnessProfile(seconds = 20.0, lufs = -24.0, peak = 0.5, complete = false)

    private val switcher = SubsongNormalization(
        scope = scope,
        worker = Dispatchers.Unconfined,
        silence = { events += "silence" },
        prepare = { index, measured ->
            events += "measure $index@$measured"
            measurements.getOrPut(index) { CompletableDeferred() }.await()
        },
        width = { width },
        switch = { index, chosen -> events += "switch $index ${if (chosen == null) "fallback" else "profile"}" },
    )

    @After fun stop() = scope.cancel()

    private fun measured(index: Int, result: LoudnessProfile? = profile) {
        measurements.getOrPut(index) { CompletableDeferred() }.complete(result)
    }

    @Test fun `the new tune is switched in only once its profile is ready`() {
        switcher.request(3)
        assertTrue(switcher.pending)
        // Before the fix the decoder switched here and played at the fallback gain.
        assertEquals(listOf("silence", "measure 3@100"), events)
        measured(3)
        assertEquals(listOf("silence", "measure 3@100", "switch 3 profile"), events)
        assertFalse(switcher.pending)
    }

    @Test fun `pause pressed while measuring holds the new tune once it is switched`() {
        switcher.request(1)
        switcher.intend(false)
        measured(1)
        assertEquals(listOf("silence", "measure 1@100", "switch 1 profile", "silence"), events)
    }

    @Test fun `pause then play while measuring starts the new tune normally`() {
        switcher.request(1)
        switcher.intend(false)
        switcher.intend(true)
        measured(1)
        assertEquals("switch 1 profile", events.last())
    }

    @Test fun `a newer choice abandons the older measurement`() {
        switcher.request(1)
        switcher.request(2)
        measured(1)
        measured(2)
        assertEquals(listOf("switch 2 profile"), events.filter { it.startsWith("switch") })
    }

    @Test fun `a mix changed during the measurement does not apply another mix's profile`() {
        switcher.request(4)
        width = 50
        measured(4)
        assertEquals("switch 4 fallback", events.last())
    }

    @Test fun `a tune that cannot be measured still plays, from the fallback`() {
        switcher.request(5)
        measured(5, null)
        assertEquals("switch 5 fallback", events.last())
    }

    @Test fun `a closed track never switches`() {
        switcher.request(6)
        switcher.cancel()
        measured(6)
        assertFalse(events.any { it.startsWith("switch") })
        assertFalse(switcher.pending)
    }
}
