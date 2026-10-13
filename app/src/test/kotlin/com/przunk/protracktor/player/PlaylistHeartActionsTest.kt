// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Context
import android.content.res.Configuration
import android.media.session.PlaybackState
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.R
import com.przunk.protracktor.AppLanguage
import com.przunk.protracktor.AppLocale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33])
class PlaylistHeartActionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun heart(track: String = "https://example.org/path/a%20b.mod?q=1#sub") = PlaylistHeart(
        PlaylistHeart.Request(track, 7), TrackRef(track, "A song"), "My list", false, false)

    @Test fun `both platform surfaces carry identical encoded identity without extras`() {
        val wanted = heart()
        val custom = PlaylistHeartActions.customAction(context, wanted)!!
        val notification = PlaylistHeartActions.notificationAction(context, wanted)!!
        val intent = shadowOf(notification.actionIntent).savedIntent
        assertEquals(PlaylistHeartActions.ACTION, intent.action)
        assertEquals(wanted.request, PlaylistHeartActions.decode(intent.dataString))
        assertEquals(wanted.request, PlaylistHeartActions.decode(custom.action))
        assertTrue(PlaylistHeartActions.isHeartAction(custom.action))
        assertEquals(R.drawable.ic_playlist_heart_outline, custom.icon)
        assertEquals("Add to My list", custom.name.toString())
        assertEquals(custom.name, notification.title)
        assertEquals(listOf(custom), PlaybackState.Builder().addCustomAction(custom).build().customActions)
    }

    @Test fun `creating a new notification cannot retarget an older pending button`() {
        val old = PlaylistHeartActions.notificationAction(context, heart("old"))!!
        val newer = PlaylistHeartActions.notificationAction(context, heart("new"))!!
        val anotherList = PlaylistHeartActions.notificationAction(context,
            heart("old").copy(request = PlaylistHeart.Request("old", 8)))!!
        assertNotEquals(old.actionIntent, newer.actionIntent)
        assertNotEquals(old.actionIntent, anotherList.actionIntent)
        assertEquals(PlaylistHeart.Request("old", 7),
            PlaylistHeartActions.decode(shadowOf(old.actionIntent).savedIntent.dataString))
        assertEquals(PlaylistHeart.Request("new", 7),
            PlaylistHeartActions.decode(shadowOf(newer.actionIntent).savedIntent.dataString))
    }

    @Test fun `filled membership is labelled without offering removal`() {
        val member = heart().copy(member = true)
        val custom = PlaylistHeartActions.customAction(context, member)!!
        val notification = PlaylistHeartActions.notificationAction(context, member)!!
        assertEquals(R.drawable.ic_playlist_heart_filled, custom.icon)
        assertEquals("On My list", custom.name.toString())
        assertEquals(custom.name, notification.title)
        assertEquals(member.request, PlaylistHeartActions.decode(custom.action))
    }

    @Test fun `pending save disables the legacy command and removes the custom command`() {
        val pending = heart().copy(saving = true)
        assertNull(PlaylistHeartActions.customAction(context, pending))
        val action = PlaylistHeartActions.notificationAction(context, pending)!!
        assertNull(action.actionIntent)
        assertEquals("Saving My list…", action.title.toString())
        assertNull(PlaylistHeartActions.notificationAction(context, null))
        assertNull(PlaylistHeartActions.customAction(context, null))
    }

    @Test fun `malformed or unrelated requests are never accepted`() {
        listOf(null, "", "https://save/7/track", "protracktor-playlist-heart://other/7/track",
            "protracktor-playlist-heart://save/0/track", "protracktor-playlist-heart://save/no/track",
            "protracktor-playlist-heart://save/7", "protracktor-playlist-heart://save/7/a/b").forEach {
            assertNull(it, PlaylistHeartActions.decode(it))
        }
        assertFalse(PlaylistHeartActions.isHeartAction("play"))
    }

    @Test fun `polish labels preserve the playlist name`() {
        val polish = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("pl"))
        })
        assertEquals("Dodaj do My list", PlaylistHeartActions.customAction(polish, heart())!!.name.toString())
        assertEquals("Na liście My list", PlaylistHeartActions.customAction(polish,
            heart().copy(member = true))!!.name.toString())
    }

    @Test fun `notification and session labels follow the app selection instead of system language`() {
        AppLocale.select(context, AppLanguage.POLISH)
        assertEquals("Dodaj do My list", PlaylistHeartActions.customAction(context, heart())!!.name.toString())
        assertEquals("Na liście My list", PlaylistHeartActions.notificationAction(context,
            heart().copy(member = true))!!.title.toString())
        AppLocale.select(context, AppLanguage.ENGLISH)
        assertEquals("Add to My list", PlaylistHeartActions.customAction(context, heart())!!.name.toString())
    }

    @Test fun `background feedback follows language changes without restarting the service`() {
        AppLocale.select(context, AppLanguage.POLISH)
        assertEquals("Dodano do My list. Playlista zapisana razem z wcześniejszymi zmianami.",
            PlaylistHeartActions.feedback(context, PlaylistHeartSave.Outcome.ADDED_AND_SAVED, heart()))
        AppLocale.select(context, AppLanguage.ENGLISH)
        assertEquals(context.getString(R.string.notification_playlist_already_saved, "My list"),
            PlaylistHeartActions.feedback(context, PlaylistHeartSave.Outcome.ALREADY_AND_SAVED, heart()))
    }
}
