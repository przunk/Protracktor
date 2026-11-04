// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmWriterTest {
    @Test fun `each committed command advances once and retries retire every previous answer identity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AlarmNotifications.channel(context)
        val control = AlarmCoordinator.from(context)
        val song = AlarmSong("song", "a".repeat(64), "Morning", platformId = "amiga", durationMillis = 3000,
            year = "1992", yearAlternatives = listOf("1991", "1993"))
        val saved = control.commitMusic(song, java.io.File(context.cacheDir, "prepared").apply { writeBytes(byteArrayOf(1,2,3)) })
        assertEquals(song, saved)
        assertEquals(1L, control.repository.book.revision)
        control.save(MusicAlarm("alarm", 7, 30, songIds = listOf(song.id)))
        assertEquals(2L, control.repository.book.revision)
        control.preview("alarm", setOf(song.id))
        assertEquals(3L, control.repository.book.revision)
        val retired = mutableSetOf<String>()
        repeat(3) { index ->
            val active = control.repository.book.ringing!!
            val quiz = active.quiz!!
            assertTrue(quiz.choices.none { it.id in retired })
            retired += quiz.choices.map { it.id }
            assertEquals(AlarmAnswerResult.INCORRECT, control.answer(active.token, quiz.choices.first { it.id != quiz.correctId }.id))
            assertEquals(4L + index, control.repository.book.revision)
        }
        val active = control.repository.book.ringing!!
        val before = control.repository.book
        assertEquals(AlarmAnswerResult.STALE, control.answer(active.token, retired.first()))
        assertSame(before, control.repository.book)
        assertEquals(AlarmAnswerResult.CORRECT, control.answer(active.token, active.quiz!!.correctId))
        assertEquals(7L, control.repository.book.revision)
        assertNull(control.repository.book.ringing)
        assertEquals(control.repository.book, control.repository.files.read())
    }
}
