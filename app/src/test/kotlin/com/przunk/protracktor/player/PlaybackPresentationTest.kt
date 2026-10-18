// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.media.MediaMetadata
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.AppLanguage
import com.przunk.protracktor.AppLocale
import com.przunk.protracktor.R
import com.przunk.protracktor.data.SavedPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaybackPresentationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val track = TrackRef("song", "Song title", "Archive · path/Author", fileName = "song.mod", author = "Author")
    private fun state() = PlayerUiState(restored = true, activePlaylistId = 7,
        playlists = listOf(SavedPlaylist(7, "List")),
        queue = PlayQueue(listOf(TrackRef("selected", "Selected"))).startAt(0),
        playback = collectionPlayback(PlayQueue(listOf(track, TrackRef("next", "Next"))).startAt(0)),
        metadata = mapOf("year" to "1992", "artist" to "Author"),
        playing = true, positionSeconds = 12.0, durationSeconds = 90.0, subsong = 2)
    private fun assertBold(value: CharSequence) {
        assertEquals("Song title", value.toString())
        val spans = (value as Spanned).getSpans(0, value.length, StyleSpan::class.java)
        assertTrue(spans.any { it.style == Typeface.BOLD && value.getSpanStart(it) == 0 && value.getSpanEnd(it) == value.length })
    }

    @Test fun `production notification and session use the shared subtitle and bold title`() {
        val session = PlaybackMediaSession.create(context)
        try {
            val state = state()
            val content = PlaybackPresentation.contentOf(context, state)
            val notification = PlaybackPresentation.notification(context, "playback", session.sessionToken, content)
            val metadata = PlaybackPresentation.metadata(context, state)
            assertEquals("Author · Amiga · 1992", content.subtitle)
            assertEquals(content.subtitle, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals(content.subtitle, metadata.description.subtitle.toString())
            assertEquals(content.subtitle, metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))
            assertBold(notification.extras.getCharSequence(Notification.EXTRA_TITLE)!!)
            assertBold(metadata.getText(MediaMetadata.METADATA_KEY_TITLE))
            assertBold(metadata.description.title!!)
            assertEquals(90000L, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION))
            assertEquals(R.drawable.ic_notification_protracktor, notification.smallIcon.resId)
            assertEquals("Author", state.metadata["artist"])
        } finally { session.release() }
    }

    @Test fun `heart stays after Next throughout delayed writes success failure and double taps`() {
        val session = PlaybackMediaSession.create(context)
        try {
            for (success in listOf(true, false)) {
                val player = MutableStateFlow(state())
                var writes = 0
                var completion: ((Boolean) -> Unit)? = null
                val notices = mutableListOf<PlaylistHeartSave.Outcome>()
                val saver = PlaylistHeartSave(player, { writes++; completion = it }, {}, { result, _ -> notices += result })
                val request = PlaylistHeart.of(player.value)!!.request
                val expectedActions = listOf(PlaybackPresentation.ACTION_PREVIOUS, PlaybackPresentation.ACTION_PLAY_PAUSE,
                    PlaybackPresentation.ACTION_NEXT, PlaylistHeartActions.ACTION, PlaybackPresentation.ACTION_STOP)
                val transport = PlaybackPresentation.playbackState(context, player.value).actions
                fun checkAndGetHeart(): PlaylistHeart.Request {
                    val current = player.value
                    val notification = PlaybackPresentation.notification(context, "playback", session.sessionToken,
                        PlaybackPresentation.contentOf(context, current))
                    assertEquals(expectedActions, notification.actions.map { shadowOf(it.actionIntent).savedIntent.action })
                    val heartIntent = shadowOf(notification.actions[3].actionIntent).savedIntent
                    val state = PlaybackPresentation.playbackState(context, current)
                    assertEquals(transport, state.actions)
                    assertEquals(request, PlaylistHeartActions.decode(state.customActions.single().action))
                    assertEquals(12.0, current.positionSeconds, 0.0)
                    assertEquals(2, current.subsong)
                    assertEquals(track, current.current)
                    assertTrue(current.playing)
                    return PlaylistHeartActions.decode(heartIntent.dataString)!!
                }
                saver.add(checkAndGetHeart())
                assertTrue(player.value.notificationPlaylistSaving)
                saver.add(checkAndGetHeart())
                assertEquals(1, writes)
                assertEquals(listOf(PlaylistHeartSave.Outcome.SAVING), notices)
                completion!!.invoke(success)
                assertFalse(player.value.notificationPlaylistSaving)
                assertEquals(request, checkAndGetHeart())
                assertEquals(if (success) PlaylistHeartSave.Outcome.ADDED_AND_SAVED else PlaylistHeartSave.Outcome.FAILED,
                    notices.last())
            }
        } finally { session.release() }
    }

    @Test fun `raw service context resolves shared loading and idle statuses in the selected app language`() {
        AppLocale.select(context, AppLanguage.POLISH)
        val local = AppLocale.wrap(context)
        val loading = PlaybackPresentation.contentOf(context, state().copy(loadingTrack = true))
        assertEquals(local.getString(R.string.dock_loading), loading.subtitle)
        assertEquals(loading.subtitle, PlaybackPresentation.metadata(context, state().copy(loadingTrack = true)).description.subtitle.toString())
        val idle = PlaybackPresentation.contentOf(context, PlayerUiState())
        assertNull(idle.title)
        assertEquals(local.getString(R.string.dock_idle_subtitle), idle.subtitle)
    }

    @Test fun `notification glyph is a flat white silhouette with transparent corners`() {
        val drawable = context.getDrawable(R.drawable.ic_notification_protracktor)!!
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 96, 96)
        drawable.draw(Canvas(bitmap))
        assertEquals(0, bitmap.getPixel(0, 0))
        assertEquals(0, bitmap.getPixel(95, 95))
        var visible = 0
        for (y in 0 until 96) for (x in 0 until 96) {
            val pixel = bitmap.getPixel(x, y)
            if (pixel ushr 24 != 0) { visible++; assertEquals(0xFFFFFF, pixel and 0xFFFFFF) }
        }
        assertTrue("Glyph must be readable, not an empty or opaque square", visible in 1000..7000)
    }
}
