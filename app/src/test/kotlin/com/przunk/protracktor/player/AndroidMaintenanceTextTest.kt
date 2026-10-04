// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.przunk.protracktor.R
import com.przunk.protracktor.player.LibraryMaintenance.Notice
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
class AndroidMaintenanceTextTest {
    private fun context(language: String): Context {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return app.createConfigurationContext(config)
    }

    @Test
    fun `deleting label and immediate notices retain PL EN resources and fresh message identity`() {
        for (language in listOf("pl", "en")) {
            val context = context(language)
            val text = AndroidMaintenanceText(context)
            assertEquals(context.getString(R.string.browse_deleting_short), text.deleting())
            for ((notice, resource) in listOf(Notice.IndexDeleted to R.string.notice_index_deleted,
                Notice.MetadataDeleted to R.string.notice_song_metadata_deleted)) {
                val message = text.message(notice)
                assertEquals(context.getString(resource), message.text)
                assertNull(message.actionLabel)
                assertNotEquals(message.id, text.message(notice).id)
            }
        }
    }

    @Test
    fun `failure notices preserve the catalogue name or localized data-set title`() {
        for (language in listOf("pl", "en")) {
            val context = context(language)
            val text = AndroidMaintenanceText(context)
            for ((notice, name) in listOf(
                Notice.DeleteFailed("Archive title") to "Archive title",
                Notice.MetadataDeleteFailed to context.getString(R.string.song_metadata_title),
                Notice.ReplaysDeleteFailed to context.getString(R.string.replay_routines_title),
            )) {
                assertEquals(context.getString(R.string.notice_delete_failed, name), text.message(notice).text)
            }
        }
    }

    @Test
    fun `freed sizes retain nothing sub-megabyte and rounded-down grouped counts in both languages`() {
        val mb = 1024L * 1024L
        for (language in listOf("pl", "en")) {
            val context = context(language)
            val text = AndroidMaintenanceText(context)
            for (bytes in listOf(-1L, 0L)) assertEquals(context.getString(R.string.notice_freed_nothing), text.message(Notice.Freed(bytes)).text)
            for (bytes in listOf(1L, mb - 1L)) assertEquals(context.getString(R.string.notice_freed_under_a_megabyte), text.message(Notice.Freed(bytes)).text)
            for ((bytes, count) in listOf(mb to 1L, (mb + mb / 2L) to 1L, (1234L * mb) to 1234L)) {
                assertEquals(context.getString(R.string.notice_freed_megabytes, count), text.message(Notice.Freed(bytes)).text)
            }
        }
    }
}
