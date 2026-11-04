// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Duration
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer

/** Framework simulation only; these assertions do not establish real-device audibility. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmSoundTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var sound: AlarmSound? = null
    @After fun close() { sound?.close(); ShadowMediaPlayer.resetStaticState() }
    @Test fun `alarm audio loops uses alarm focus ramps and releases every resource`() {
        var media: MediaPlayer? = null
        ShadowMediaPlayer.setCreateListener { player, _ -> media = player }
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(3000, 1) }
        sound = AlarmSound(context) { fail("Valid prepared audio failed") }.also { it.start(File("prepared.m4a"), false) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        val player = checkNotNull(media)
        assertTrue(player.isPlaying && player.isLooping)
        assertEquals(AudioAttributes.USAGE_ALARM, shadowOf(player).audioAttributes.usage)
        val audio = context.getSystemService(AudioManager::class.java)
        val captured = shadowOf(audio).lastAudioFocusRequest
        val request = captured.audioFocusRequest
        assertEquals(AudioAttributes.USAGE_ALARM, request.audioAttributes.usage)
        captured.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertFalse(player.isPlaying)
        captured.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertTrue(player.isPlaying)
        assertTrue(request.willPauseWhenDucked())
        captured.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertTrue("A notification must not pause the alarm", player.isPlaying)
        captured.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertTrue(player.isPlaying)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3500))
        assertEquals(1f, shadowOf(player).leftVolume, .001f)
        sound!!.close()
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(player).state)
        assertSame(request, shadowOf(audio).lastAbandonedAudioFocusRequest)
    }
    @Test fun `delayed focus waits without backup then starts and ramps on gain`() {
        var media: MediaPlayer? = null
        var failed = 0
        val statuses = mutableListOf<AlarmAudioStatus>()
        val audio = context.getSystemService(AudioManager::class.java)
        shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
        ShadowMediaPlayer.setCreateListener { player, _ -> media = player }
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(3000, 1) }
        sound = AlarmSound(context, status = statuses::add) { failed++ }.also { it.start(File("prepared.m4a"), false) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        val player = checkNotNull(media)
        assertFalse(player.isPlaying)
        assertEquals(0, failed)
        assertEquals(AlarmAudioStatus.WAITING_FOCUS, statuses.last())
        assertTrue(shadowOf(audio).lastAudioFocusRequest.audioFocusRequest.acceptsDelayedFocusGain())
        shadowOf(audio).lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3500))
        assertTrue(player.isPlaying)
        assertEquals(AlarmAudioStatus.PLAYING, statuses.last())
        assertEquals(1f, shadowOf(player).leftVolume, .001f)
    }
    @Test fun `slow preparation fails once at the bounded deadline`() {
        var failed = 0
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(3000, 100_000) }
        sound = AlarmSound(context) { failed++ }.also { it.start(File("slow.m4a"), false) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertEquals(1, failed)
        sound!!.close()
        assertEquals(1, failed)
    }
    @Test fun `foreground notification exists before repository decoding or player construction`() {
        val service = Robolectric.buildService(AlarmService::class.java).create()
        try {
            assertEquals(AlarmPlatform.STARTUP_NOTIFICATION, shadowOf(service.get()).lastForegroundNotificationId)
            assertEquals(android.app.Notification.CATEGORY_ALARM, shadowOf(service.get()).lastForegroundNotification.category)
            assertNull(shadowOf(service.get()).lastForegroundNotification.fullScreenIntent)
        } finally { service.destroy() }
    }
}
