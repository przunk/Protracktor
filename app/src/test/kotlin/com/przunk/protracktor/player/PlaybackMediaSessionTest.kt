// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Context
import android.media.session.MediaSession
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowMediaSession

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33], shadows = [RecordingMediaSession::class])
class PlaybackMediaSessionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `production session reserves both transport slots before any playback state`() {
        val session = PlaybackMediaSession.create(context)
        try {
            val extras = Shadow.extract<RecordingMediaSession>(session).extras
            assertTrue("Previous slot must not be filled by the heart",
                extras?.getBoolean("android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS") == true)
            assertTrue("Next slot must not be filled by the heart",
                extras?.getBoolean("android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT") == true)
        } finally { session.release() }
    }

    @Test fun `each production session owns an independent reservation bundle`() {
        val first = PlaybackMediaSession.create(context)
        val second = PlaybackMediaSession.create(context)
        try {
            val firstExtras = Shadow.extract<RecordingMediaSession>(first).extras
            val secondExtras = Shadow.extract<RecordingMediaSession>(second).extras
            assertNotNull(firstExtras)
            assertNotNull(secondExtras)
            assertNotSame(firstExtras, secondExtras)
            firstExtras!!.clear()
            assertTrue(secondExtras!!.getBoolean("android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS"))
            assertTrue(secondExtras.getBoolean("android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT"))
        } finally { first.release(); second.release() }
    }
}

// Robolectric's framework session does not deliver extras to MediaController. Record the actual
// setExtras call, rather than duplicating the reservation policy in a simulated controller.
@Implements(MediaSession::class)
class RecordingMediaSession : ShadowMediaSession() {
    var extras: Bundle? = null
        private set

    @Implementation fun setExtras(value: Bundle?) { extras = value }
}
