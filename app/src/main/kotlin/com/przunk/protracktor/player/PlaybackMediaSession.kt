// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Context
import android.media.session.MediaSession
import android.os.Bundle

internal object PlaybackMediaSession {
    fun create(context: Context): MediaSession {
        val session = MediaSession(context, "Protracktor")
        // Android 13+ otherwise puts custom actions into missing Previous/Next slots.
        // These documented session wire keys avoid adding a media compatibility dependency.
        session.setExtras(Bundle().apply {
            putBoolean("android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS", true)
            putBoolean("android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT", true)
        })
        return session
    }
}
