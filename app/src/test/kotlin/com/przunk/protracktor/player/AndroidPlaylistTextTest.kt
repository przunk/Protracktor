// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.przunk.protracktor.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class AndroidPlaylistTextTest {
    private fun context(language: String): Context {
        val app = RuntimeEnvironment.getApplication()
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
    }

    @Test
    fun `session notices retain their PL and EN resources and fresh message identity`() {
        val cases = listOf(
            PlaylistSessionState.Notice.CHANGES_DISCARDED to R.string.notice_changes_discarded,
            PlaylistSessionState.Notice.PLAYLIST_EMPTIED to R.string.notice_playlist_emptied,
        )
        for (language in listOf("en", "pl")) {
            val context = context(language)
            val text = AndroidPlaylistText(context)
            for ((notice, resource) in cases) {
                val message = text.message(notice)
                assertEquals(context.getString(resource), message.text)
                assertNull(message.actionLabel)
                assertNotEquals(message.id, text.message(notice).id)
            }
        }
    }

    @Test
    fun `failed exports and invalid imports keep their distinct PL and EN messages and identity`() {
        val cases = listOf(
            PlaylistTransfer.Notice.EmptyPlaylist to R.string.notice_playlist_empty,
            PlaylistTransfer.Notice.ExportFailed to R.string.notice_playlist_failed,
            PlaylistTransfer.Notice.UnreadableFile to R.string.notice_file_unreadable,
            PlaylistTransfer.Notice.NoTracks to R.string.notice_file_no_tracks,
        )
        for (language in listOf("en", "pl")) {
            val context = context(language)
            val text = AndroidPlaylistText(context)
            for ((notice, resource) in cases) {
                val message = text.message(notice)
                assertEquals(context.getString(resource), message.text)
                assertNull(message.actionLabel)
                assertNotEquals(message.id, text.message(notice).id)
            }
        }
    }

    @Test
    fun `full imports use existing plurals and partial imports report both counts in both languages`() {
        for (language in listOf("en", "pl")) {
            val context = context(language)
            val text = AndroidPlaylistText(context)
            for (found in listOf(0, 1, 2, 5, 1_234)) {
                val name = "Żółć 🎵"
                assertEquals(context.resources.getQuantityString(R.plurals.notice_imported, found, found, name),
                    text.message(PlaylistTransfer.Notice.Imported(found, found, name)).text)
                assertEquals(context.getString(R.string.notice_imported_partial, found, found + 1, name),
                    text.message(PlaylistTransfer.Notice.Imported(found, found + 1, name)).text)
            }
        }
    }
}
