// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRecordingTest {

    @Test
    fun `a play History started leaves History alone`() {
        assertFalse(HistoryRecording.records(fromHistory = true))
    }

    @Test
    fun `a search result or a folder is recorded as before`() {
        assertTrue(HistoryRecording.records(fromHistory = false))
    }

    @Test
    fun `manual origin overrides parked History source in either direction`() {
        val track = TrackRef("file", "Tune")
        for (fromHistory in listOf(false, true)) {
            val context = ActiveQueue(QueueSource.Collection(SessionSource.History), PlayQueue(listOf(track)).startAt(0))
            val manual = PlaybackSchedule().enqueue(track, fromHistory).next(context) as ScheduledStep.Select
            val state = PlayerUiState(playback = context, schedule = manual.schedule)
            assertEquals(!fromHistory, HistoryRecording.records(state.resultsFromHistory))
        }
    }
}
