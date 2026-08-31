/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat

/**
 * Being a good citizen about the one speaker everybody shares.
 *
 * Two separate things, both of which a player is expected to honour and neither of which Android
 * does for you:
 *
 * - **Focus.** Another app starting audio, or a phone call arriving, has to be able to take the
 *   speaker. Without this we simply play over them.
 * - **Becoming noisy.** Headphones pulled out, or Bluetooth disconnecting, must pause rather than
 *   suddenly play the user's music out loud to a room. This one is a courtesy people only notice
 *   once, memorably.
 *
 * Ducking is treated as a pause rather than a volume dip. These formats are dense and quiet
 * chip-music; halving the volume under a navigation prompt leaves neither audible.
 */
class AudioFocus(
    private val context: Context,
    private val onPause: () -> Unit,
    private val onResume: () -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Only resume what we paused. A user's own pause must not be undone by a phone call ending. */
    private var pausedByLoss = false
    private var request: AudioFocusRequest? = null
    private var noisyReceiver: BroadcastReceiver? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByLoss = false // permanent: do not come back on our own
                onPause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pausedByLoss = true
                onPause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedByLoss) {
                pausedByLoss = false
                onResume()
            }
        }
    }

    /** Returns false when the system refused, in which case playback should not start. */
    fun acquire(): Boolean {
        registerNoisyReceiver()

        val existing = request
        if (existing != null) {
            return audioManager.requestAudioFocus(existing) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        val built = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(listener)
            .setWillPauseWhenDucked(true)
            .build()
        request = built
        return audioManager.requestAudioFocus(built) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun release() {
        request?.let(audioManager::abandonAudioFocusRequest)
        pausedByLoss = false
        unregisterNoisyReceiver()
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    pausedByLoss = false
                    onPause()
                }
            }
        }
        // The export flag is mandatory from API 34 for anything but a protected system broadcast.
        // This one is protected, so it would be accepted either way -- stating it means the next
        // person adding a filter here does not have to find that out from a crash.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyReceiver = receiver
    }

    private fun unregisterNoisyReceiver() {
        noisyReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        noisyReceiver = null
    }
}
