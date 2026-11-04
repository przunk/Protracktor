// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.Application
import android.media.session.PlaybackState
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.*
import com.przunk.protracktor.ui.PlaybackShellTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class AlarmPlaybackUiTest : PlaybackShellTest() {
    private val tracks = listOf(TrackRef("content://alarm.test/first.mod", "First", fileName = "first.mod"),
        TrackRef("content://alarm.test/second.mod", "Second", fileName = "second.mod"))
    private fun playlist() {
        compose.runOnIdle { player.value = player.value.copy(queue = PlayQueue(tracks).startAt(0), playback = null,
            playing = false, positionSeconds = 12.0) }
        frame(300)
    }
    @After fun removeAlarmFixture() {
        AlarmRepository.from(application).change { it.copy(ringing = null, alarms = emptyList(), songs = emptyList()) }
    }
    @Test fun `real playlist menu reaches preparation and reports an unreadable source without changing playback`() {
        playlist()
        val before = player.value.queue
        compose.onNodeWithContentDescription(application.getString(R.string.a11y_track_actions, tracks[1].title)).performClick()
        frame(250)
        compose.onNodeWithText(application.getString(R.string.alarm_use_music)).performClick()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            player.value.message?.text == application.getString(R.string.alarm_prepare_failed)
        }
        assertEquals(before, player.value.queue)
        assertTrue(AlarmRepository.from(application).book.songs.isEmpty())
    }
    @Test fun `real playlist tap and transport preserve context and position while an alarm owns audio`() {
        playlist()
        val now = AlarmPlatform.clock(application)
        val song = AlarmSong("alarm-song", "a".repeat(64), "Alarm", platformId = "amiga", durationMillis = 3000)
        val quiz = AlarmQuizFactory(Platforms.all.map { it.id to it.name }).create(song, listOf(song))!!
        AlarmRepository.from(application).change { it.copy(songs = listOf(song), ringing = RingingAlarm("active-alarm",
            setOf("alarm"), song, quiz, now.elapsedMillis, now.elapsedMillis + 600_000, now.bootCount)) }
        val before = player.value.queue
        compose.onNodeWithText(tracks[1].title).performClick()
        frame(100)
        compose.runOnIdle { controller.nextFile(); controller.previousFile() }
        assertEquals(before, player.value.queue)
        assertEquals(12.0, player.value.positionSeconds, .001)
        assertEquals(application.getString(R.string.alarm_finish_first), player.value.message?.text)
        assertFalse(player.value.playing)
    }
    @Test fun `normal media session exposes only stop and an explanation during an alarm`() {
        playlist()
        val state = player.value
        val presented = PlaybackPresentation.playbackState(application, state, alarmRinging = true)
        assertEquals(PlaybackState.STATE_PAUSED, presented.state)
        assertEquals(PlaybackState.ACTION_STOP, presented.actions)
        assertEquals(application.getString(R.string.alarm_finish_first), presented.errorMessage.toString())
        assertEquals(state, player.value)
    }
}
