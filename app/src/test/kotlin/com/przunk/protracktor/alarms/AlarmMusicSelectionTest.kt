// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import com.przunk.protracktor.player.PlayQueue
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.TrackRef
import org.junit.Assert.*
import org.junit.Test

class AlarmMusicSelectionTest {
    @Test fun `current tune selection uses its known title and subsong rather than the row filename`() {
        val current = TrackRef("content://song.sid", "Third tune", author = "Composer")
        val metadata = mutableMapOf("title" to "Third tune")
        val selected = AlarmMusicSelection.capture(current.copy(title = "song.sid"),
            PlayerUiState(queue = PlayQueue(listOf(current)).startAt(0), subsong = 2, metadata = metadata))
        assertEquals(2, selected.subsong)
        assertEquals("Third tune", selected.ref.title)
    }
    @Test fun `an unplayed row uses decoder default without borrowing current credits`() {
        val ref = TrackRef("content://other.mod", "Other")
        val selected = AlarmMusicSelection.capture(ref, PlayerUiState(queue = PlayQueue(listOf(TrackRef("content://song.sid", "Current"))).startAt(0), subsong = 5, metadata = mapOf("title" to "Current")))
        assertEquals(ref, selected.ref)
        assertNull(selected.subsong)
    }
    @Test fun `a loading file has no confirmed current tune and uses its decoder default`() {
        val ref = TrackRef("content://loading.sap", "Loading")
        val selected = AlarmMusicSelection.capture(ref, PlayerUiState(queue = PlayQueue(listOf(ref)).startAt(0),
            loadingTrack = true, subsong = 0))
        assertNull(selected.subsong)
    }

}
