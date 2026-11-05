// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.alarms

import com.przunk.protracktor.data.SearchTerms
import kotlin.random.Random

/** Three alternatives in one category; no invented credits or unknown placeholders. */
class AlarmQuizFactory(private val platforms: List<Pair<String, String>>) {
    private data class Candidate(val category: AlarmQuestion, val correct: String, val others: List<String>)

    fun canAsk(song: AlarmSong, pool: List<AlarmSong>) = candidates(song, pool).isNotEmpty()

    fun create(song: AlarmSong, pool: List<AlarmSong>, random: Random = Random.Default,
        excluding: AlarmQuestion? = null, choicePrefix: String = "choice"): AlarmQuiz? {
        val possible = candidates(song, pool).filter { it.category != excluding }
        if (possible.isEmpty()) return null
        val chosen = possible[random.nextInt(possible.size)]
        val values = (listOf(chosen.correct) + chosen.others.shuffled(random).take(2)).shuffled(random)
        val choices = values.mapIndexed { index, value -> AlarmChoice("$choicePrefix-$index", value) }
        return AlarmQuiz(chosen.category, choices, choices.single { fold(it.value) == fold(chosen.correct) }.id)
    }

    private fun candidates(song: AlarmSong, pool: List<AlarmSong>): List<Candidate> = buildList {
        fun addKnown(category: AlarmQuestion, correct: String?, values: List<String>) {
            val actual = correct?.trim()?.takeIf(::known) ?: return
            val others = values.map(String::trim).filter(::known).distinctBy(::fold)
                .filter { fold(it) != fold(actual) }
            if (others.size >= 2) add(Candidate(category, actual, others))
        }
        addKnown(AlarmQuestion.PLATFORM, platforms.firstOrNull { it.first == song.platformId }?.second,
            platforms.map { it.second })
        if (singleAuthor(song.author)) {
            addKnown(AlarmQuestion.AUTHOR, song.author, (pool.map { it.author } + song.authorAlternatives).filter(::singleAuthor))
        }
        addKnown(AlarmQuestion.TITLE, song.title, pool.map { it.title })
        if (com.przunk.protracktor.player.ReleaseYear.isSingle(song.year))
            addKnown(AlarmQuestion.YEAR, song.year, (pool.map { it.year } + song.yearAlternatives)
                .filter { com.przunk.protracktor.player.ReleaseYear.isSingle(it) })
    }

    companion object {
        private val spaces = Regex("\\s+")
        private val multipleAuthors = Regex("[,;&/]|\\b(?:feat\\.?|ft\\.?|and|with)\\b", RegexOption.IGNORE_CASE)
        fun fold(value: String): String = SearchTerms.fold(value).trim().replace(spaces, " ")
        internal fun known(value: String) = fold(value) !in setOf("", "-", "unknown", "- unknown", "(unknown)", "unknown author", "unknown artist", "unknown title", "untitled", "(untitled)", "no title", "?", "n/a")
        internal fun authorKnown(value: String) = known(value) && fold(value) !in setOf("mod", "xm", "s3m", "it", "modland", "files", "local", "protracker", "soundtracker", "fasttracker", "impulsetracker")
        private fun singleAuthor(value: String) = authorKnown(value) && !multipleAuthors.containsMatchIn(value)
    }
}
