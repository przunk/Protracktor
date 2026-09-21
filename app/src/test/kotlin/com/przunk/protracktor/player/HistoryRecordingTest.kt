// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecordingTest {

    @Test
    fun `a play History started leaves History alone`() {
        assertFalse(HistoryRecording.records(walkingResults = true, resultsFromHistory = true))
    }

    @Test
    fun `a search result or a folder is recorded as before`() {
        assertTrue(HistoryRecording.records(walkingResults = true, resultsFromHistory = false))
    }

    @Test
    fun `the playlist is recorded, whatever list was walked before it`() {
        // resultsFromHistory is left set once the results list is gone; it must not outlive it.
        assertTrue(HistoryRecording.records(walkingResults = false, resultsFromHistory = true))
        assertTrue(HistoryRecording.records(walkingResults = false, resultsFromHistory = false))
    }
}
