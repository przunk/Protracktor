// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
 * The three kinds of interruption are deliberately not treated alike, because they are not alike
 * to listen to:
 *
 * - **A notification** asks to duck. The music drops in volume for a moment and comes back. Pausing
 *   for a message arriving is worse than the message.
 * - **A phone call** takes focus transiently. Playback stops and **does not resume** when the call
 *   ends: music restarting by itself as you put the phone down is startling.
 * - **Another player starting** takes focus permanently. Playback stops and stays stopped.
 */
class AudioFocus(
    private val context: Context,
    private val onPause: () -> Unit,
    private val onDuck: (Float) -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var request: AudioFocusRequest? = null
    private var noisyReceiver: BroadcastReceiver? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            // A notification, a navigation prompt: quieter, not silent, and back afterwards.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(DUCKED_GAIN)
            AudioManager.AUDIOFOCUS_GAIN -> onDuck(1f)

            // A call, or another player. Both stop us; neither brings us back -- music must not
            // restart by itself when a call ends.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS -> onPause()
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
            // False, so the system tells us to duck instead of doing the pausing for us. Handling it
            // ourselves is the only way a notification can lower the music rather than stop it.
            .setWillPauseWhenDucked(false)
            .build()
        request = built
        return audioManager.requestAudioFocus(built) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun release() {
        request?.let(audioManager::abandonAudioFocusRequest)
        unregisterNoisyReceiver()
    }

    private companion object {
        /** About a quarter of the level. Audible underneath, and clearly out of the way. */
        const val DUCKED_GAIN = 0.25f
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onPause()
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
