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
class AndroidDownloadTextTest {
    private fun context(language: String): Context {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return app.createConfigurationContext(config)
    }

    @Test
    fun `group labels use the existing resources in both languages`() {
        for (language in listOf("en", "pl")) {
            val context = context(language)
            val text = AndroidDownloadText(context)
            assertEquals(context.getString(R.string.song_metadata_title), text.label(DownloadText.Label.SONG_METADATA))
            assertEquals(context.getString(R.string.download_all_running), text.label(DownloadText.Label.ALL_RUNNING))
            assertEquals(context.getString(R.string.replay_routines_title), text.label(DownloadText.Label.REPLAY_ROUTINES))
        }
    }

    @Test
    fun `failure and group messages retain their distinct resources and message identity`() {
        val notices = listOf(
            DownloadNotice.SongLengthsFailed to R.string.notice_song_lengths_failed,
            DownloadNotice.SongLengthsEmpty to R.string.notice_song_lengths_empty,
            DownloadNotice.TrackMetadataFailed to R.string.notice_track_metadata_failed,
            DownloadNotice.TrackMetadataEmpty to R.string.notice_track_metadata_empty,
            DownloadNotice.SongDbLengthsFailed to R.string.notice_songdb_lengths_failed,
            DownloadNotice.FavouritesFailed to R.string.notice_favourites_failed,
            DownloadNotice.FavouritesEmpty to R.string.notice_favourites_empty,
            DownloadNotice.SongMetadataDone to R.string.notice_song_metadata_done,
            DownloadNotice.AllDownloaded(4, 0) to R.string.notice_all_downloaded,
        )
        for (language in listOf("en", "pl")) {
            val context = context(language)
            val text = AndroidDownloadText(context)
            for ((notice, resource) in notices) {
                val message = text.message(notice)
                assertEquals("$language: $notice", context.getString(resource), message.text)
                assertNull(message.actionLabel)
                assertNotEquals(message.id, text.message(notice).id)
            }
        }
    }

    @Test
    fun `replay results distinguish combined partial and failed downloads in both languages`() {
        for (language in listOf("en", "pl")) {
            val context = context(language)
            val text = AndroidDownloadText(context)
            for ((sc68, uade) in listOf(1 to 1, 3 to 5, null to 5, 3 to null, null to null)) {
                val expected = if (sc68 != null && uade != null) {
                    context.resources.getQuantityString(R.plurals.notice_replay_routines_done, sc68 + uade, sc68 + uade)
                } else if (sc68 == null && uade == null) {
                    context.getString(R.string.notice_replay_routines_failed)
                } else {
                    context.getString(R.string.notice_replay_routines_partly)
                }
                assertEquals(expected, text.message(DownloadNotice.ReplaysDownloaded(sc68, uade)).text)
            }
        }
    }

    @Test
    fun `parameterized messages preserve plural quantities counts and partial-success branches`() {
        for (language in listOf("en", "pl")) {
            val context = context(language)
            val text = AndroidDownloadText(context)
            val resources = context.resources
            fun check(notice: DownloadNotice, expected: String) {
                assertEquals("$language: $notice", expected, text.message(notice).text)
            }
            check(DownloadNotice.IndexDownloadFailed("ASMA"), context.getString(R.string.notice_index_download_failed, "ASMA"))
            check(DownloadNotice.ArchiveStoreFailed("ASMA"), context.getString(R.string.notice_archive_store_failed, "ASMA"))
            for (count in listOf(0, 1, 2, 5, 1_234)) {
                for (playable in listOf(count, count + 1)) {
                    check(DownloadNotice.Indexed("ASMA", count, playable), resources.getQuantityString(
                        R.plurals.notice_indexed_all, count, count, "ASMA",
                    ))
                }
                check(DownloadNotice.Indexed("ASMA", count + 1, count), resources.getQuantityString(
                    R.plurals.notice_indexed_partly, count + 1, count + 1, "ASMA", count,
                ))
                check(DownloadNotice.SongLengthsDone(count), resources.getQuantityString(
                    R.plurals.notice_song_lengths_done, count, count,
                ))
                check(DownloadNotice.TrackMetadataDone(count), resources.getQuantityString(
                    R.plurals.notice_track_metadata_done, count, count,
                ))
                check(DownloadNotice.FavouritesDone(0, count), context.getString(R.string.notice_favourites_need_index, count))
                check(DownloadNotice.FavouritesDone(1, count), context.getString(R.string.notice_favourites_playable, 1, count))
                check(DownloadNotice.AllDownloaded(count, 2), context.getString(R.string.notice_all_downloaded_partly, count, 2))
                check(DownloadNotice.DownloadsStopped(count), context.getString(R.string.notice_downloads_stopped, count))
            }
        }
    }
}
