// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import org.junit.Assert.*
import org.junit.Test

class AlarmQuizRetryTest {
    private val song = AlarmSong("song", "a".repeat(64), "Tune", "Artist", "amiga", durationMillis = 3000,
        year = "1992", yearAlternatives = listOf("1991", "1993"))
    private val quiz = AlarmQuiz(AlarmQuestion.PLATFORM, listOf(AlarmChoice("a", "Amiga"), AlarmChoice("b", "PC"), AlarmChoice("c", "C64")), "a")
    private val active = RingingAlarm("token", setOf("alarm"), song, quiz, 100, 600100, 1)
    private val book = AlarmBook(3, listOf(song), ringing = active)
    private val rules = AlarmRules(AlarmQuizFactory(listOf("amiga" to "Amiga", "pc" to "PC", "c64" to "C64"))) { true }
    private val now = AlarmClockReading(1000, 200, 1)

    @Test fun `wrong choice changes category without changing music deadline or session`() {
        val change = rules.answer(book, "token", "b", now)
        assertFalse(change.accepted)
        val next = change.book.ringing!!
        val question = checkNotNull(next.quiz)
        assertEquals(AlarmQuestion.YEAR, question.category)
        assertEquals(setOf("1991", "1992", "1993"), question.choices.map { it.value }.toSet())
        assertEquals(active.copy(quiz = next.quiz), next)
        assertEquals(book.revision + 1, change.book.revision)
        assertNull(change.book.lastOutcome)
    }
    @Test fun `retired answer identities cannot solve or reroll the following question`() {
        val changed = rules.answer(book, "token", "b", now).book
        assertEquals(changed, rules.answer(changed, "token", quiz.correctId, now).book)
        assertEquals(changed, rules.answer(changed, "token", "not-a-choice", now).book)
        val current = changed.ringing!!.quiz!!
        assertTrue(quiz.choices.none { old -> current.choices.any { it.id == old.id } })
        assertNull(rules.answer(changed, "token", current.correctId, now).book.ringing)
    }
    @Test fun `retry retains a valid question when no different category is possible`() {
        val onlyPlatform = song.copy(year = "", yearAlternatives = emptyList())
        val previous = book.copy(songs = listOf(onlyPlatform), ringing = active.copy(song = onlyPlatform))
        assertEquals(previous, rules.answer(previous, "token", "b", now).book)
        assertEquals(book, rules.answer(book, "old-token", "b", now).book)
        assertEquals(book, rules.answer(book, "token", "b", now.copy(elapsedMillis = active.deadlineElapsed)).book)
    }
}
