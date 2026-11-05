// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.przunk.protracktor.R
import java.io.File

internal enum class AlarmAudioStatus { PREPARING, PLAYING, WAITING_FOCUS, STOPPED }

/** Main-thread player with a bounded async preparation and explicit alarm-stream focus. */
internal class AlarmSound(private val context: Context,
    private val status: (AlarmAudioStatus) -> Unit = {}, private val failed: () -> Unit) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var closed = false
    private var level = 0f
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(true).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener({ change ->
            if (!closed && prepared) {
                try {
                    when (change) {
                        AudioManager.AUDIOFOCUS_GAIN -> playFocused()
                        AudioManager.AUDIOFOCUS_LOSS -> fail()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { if (player?.isPlaying == true) player?.pause(); status(AlarmAudioStatus.WAITING_FOCUS) }
                        // Keep the alarm audible over notification tones; real focus loss still pauses it.
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Unit
                    }
                } catch (_: RuntimeException) { fail() }
            }
        }, handler).build()
    private val timeout = Runnable { fail() }
    private val ramp = object : Runnable {
        override fun run() {
            if (closed || !prepared) return
            level = (level + .05f).coerceAtMost(1f)
            runCatching { player?.setVolume(level, level) }.onFailure { fail() }
            if (level < 1f) handler.postDelayed(this, 150)
        }
    }
    fun start(file: File?, backup: Boolean) {
        status(AlarmAudioStatus.PREPARING)
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setOnErrorListener { _, _, _ -> fail(); true }
            mp.setOnPreparedListener {
                if (closed) return@setOnPreparedListener
                handler.removeCallbacks(timeout)
                prepared = true
                when (audio.requestAudioFocus(focus)) {
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> try { playFocused() } catch (_: RuntimeException) { fail() }
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> status(AlarmAudioStatus.WAITING_FOCUS)
                    else -> fail()
                }
            }
            if (backup) context.resources.openRawResourceFd(R.raw.alarm_backup).use {
                mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            } else mp.setDataSource(checkNotNull(file).absolutePath)
            mp.setAudioAttributes(attributes)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            handler.postDelayed(timeout, 10_000)
            mp.prepareAsync()
        } catch (_: Exception) { fail() }
    }
    private fun playFocused() {
        player?.start()
        status(AlarmAudioStatus.PLAYING)
        player?.setVolume(level, level)
        handler.removeCallbacks(ramp)
        if (level < 1f) handler.post(ramp)
    }
    private fun fail() {
        if (closed) return
        close(); failed()
    }
    override fun close() {
        if (closed) return
        closed = true
        status(AlarmAudioStatus.STOPPED)
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.release() }; player = null
        audio.abandonAudioFocusRequest(focus)
    }
}
