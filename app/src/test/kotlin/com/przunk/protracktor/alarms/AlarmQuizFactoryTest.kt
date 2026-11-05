// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.alarms

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AlarmQuizFactoryTest {
    private fun song(id: String, title: String = "Tune", author: String = "", platform: String? = null) =
        AlarmSong(id, id.first().code.toString(16).padStart(64, '0'), title, author, platform, durationMillis = 1000)
    private val platforms = listOf("amiga" to "Amiga", "pc" to "PC", "c64" to "Commodore 64")

    @Test fun `one known platform is enough and every quiz has one correct answer`() {
        val tune = song("a", platform = "amiga")
        repeat(40) { seed ->
            val quiz = AlarmQuizFactory(platforms).create(tune, listOf(tune), Random(seed))!!
            assertEquals(AlarmQuestion.PLATFORM, quiz.category)
            assertEquals(3, quiz.choices.size)
            assertEquals(1, quiz.choices.count { quiz.accepts(it.id) })
            assertEquals("Amiga", quiz.choices.single { quiz.accepts(it.id) }.value)
            assertFalse(quiz.accepts("retired-choice"))
        }
    }

    @Test fun `artist and title alternatives are real pool values not generated placeholders`() {
        val pool = listOf(song("a", author = "Jakim"), song("b", author = "Jester"), song("c", author = "4mat"))
        val quiz = AlarmQuizFactory(emptyList()).create(pool[0], pool, Random(7))!!
        assertEquals(AlarmQuestion.AUTHOR, quiz.category)
        assertEquals(pool.map { it.author }.toSet(), quiz.choices.map { it.value }.toSet())
        val titles = pool.mapIndexed { i, s -> s.copy(author = "", title = "Tune $i") }
        assertEquals(AlarmQuestion.TITLE, AlarmQuizFactory(emptyList()).create(titles[0], titles)!!.category)
    }

    @Test fun `unknown credits and repeated folded titles cannot form three false alternatives`() {
        val pool = listOf(song("a", "Żółw", "unknown"), song("b", "ZOLW", "- unknown"), song("c", "  żółw  ", ""))
        assertFalse(AlarmQuizFactory(emptyList()).canAsk(pool[0], pool))
        assertNull(AlarmQuizFactory(emptyList()).create(pool[0], pool))
        assertEquals("zolw", AlarmQuizFactory.fold(" Żółw "))
    }

    @Test fun `compound artist credits do not become wrong alternatives for a contributor`() {
        val pool = listOf(song("a", author = "Jakim"), song("b", author = "Jakim & Jester"), song("c", author = "4mat"))
        assertNull(AlarmQuizFactory(emptyList()).create(pool[0], pool))
        assertNull(AlarmQuizFactory(emptyList()).create(pool[1], pool))
    }

    @Test fun `published state copies mutable collection boundaries`() {
        val songs = mutableListOf(song("a"))
        val ids = mutableListOf("a")
        val days = mutableSetOf(1)
        val alarms = mutableListOf(MusicAlarm("alarm", 7, 0, weekdays = days, songIds = ids))
        val snapshot = AlarmBook(songs = songs, alarms = alarms).frozen()
        songs.clear(); ids.clear(); days.clear(); alarms.clear()
        assertEquals(1, snapshot.songs.size)
        assertEquals(listOf("a"), snapshot.alarms.single().songIds)
        assertEquals(setOf(1), snapshot.alarms.single().weekdays)
    }
}
