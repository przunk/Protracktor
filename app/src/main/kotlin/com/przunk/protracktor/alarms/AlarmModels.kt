// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.alarms

import java.time.LocalDate

object AlarmLimits {
    const val STORE_VERSION = 1
    const val MAX_ALARMS = 32
    const val MAX_SONGS = 64
    const val MAX_AUDIO_BYTES = 128L * 1024 * 1024
    const val EXCERPT_SECONDS = 60
    const val SILENCE_SECONDS = 10
    const val DELIVERY_GRACE_MS = 120_000L
    const val RINGING_LIMIT_MS = 600_000L
    private val idPattern = Regex("[A-Za-z0-9_-]{1,80}")
    fun validId(value: String) = idPattern.matches(value)
}

data class AlarmSong(
    val id: String,
    val sourceKey: String,
    val title: String,
    val author: String = "",
    val platformId: String? = null,
    val catalogueId: String? = null,
    val durationMillis: Long,
    val year: String = "",
    val authorAlternatives: List<String> = emptyList(),
    val yearAlternatives: List<String> = emptyList(),
) {
    init {
        require(AlarmLimits.validId(id))
        require(Regex("[0-9a-f]{64}").matches(sourceKey))
        require(title.isNotBlank() && title.length <= 1024 && author.length <= 1024)
        require(durationMillis in 1..61_000)
        require(year.isEmpty() || com.przunk.protracktor.player.ReleaseYear.of(year) == year)
        require(authorAlternatives.size <= 16 && authorAlternatives.all { it.isNotBlank() && it.length <= 160 })
        require(yearAlternatives.size <= 16 && yearAlternatives.all { com.przunk.protracktor.player.ReleaseYear.isSingle(it) })
        require(platformId == null || AlarmLimits.validId(platformId))
        require(catalogueId == null || AlarmLimits.validId(catalogueId))
    }
}

data class MusicAlarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    /** ISO weekdays, Monday = 1. Empty means a one-shot alarm. */
    val weekdays: Set<Int> = emptySet(),
    val songIds: List<String> = emptyList(),
    val enabled: Boolean = false,
    val nextAtMillis: Long = 0,
    val occurrenceDate: String? = null,
    val consumedDate: String? = null,
) {
    init {
        require(AlarmLimits.validId(id) && hour in 0..23 && minute in 0..59)
        require(label.length <= 160 && weekdays.all { it in 1..7 })
        require(songIds.size <= AlarmLimits.MAX_SONGS && songIds.distinct().size == songIds.size)
        require(songIds.all(AlarmLimits::validId) && (!enabled || songIds.isNotEmpty()))
        require(nextAtMillis >= 0)
        occurrenceDate?.let(LocalDate::parse)
        consumedDate?.let(LocalDate::parse)
    }
}

enum class AlarmQuestion { PLATFORM, AUTHOR, TITLE, YEAR }
data class AlarmChoice(val id: String, val value: String)

data class AlarmQuiz(
    val category: AlarmQuestion,
    val choices: List<AlarmChoice>,
    val correctId: String,
) {
    init {
        require(choices.size == 3 && choices.map { it.id }.distinct().size == 3)
        require(choices.all { AlarmLimits.validId(it.id) && it.value.isNotBlank() && it.value.length <= 1024 })
        require(choices.map { AlarmQuizFactory.fold(it.value) }.distinct().size == 3)
        require(choices.count { it.id == correctId } == 1)
    }
    fun accepts(choiceId: String) = correctId == choiceId
}

data class RingingAlarm(
    val token: String,
    val alarmIds: Set<String>,
    val song: AlarmSong,
    val quiz: AlarmQuiz?,
    val startedElapsed: Long,
    val deadlineElapsed: Long,
    val bootCount: Int,
    val preview: Boolean = false,
    val backup: Boolean = false,
) {
    init {
        require(AlarmLimits.validId(token) && alarmIds.isNotEmpty() && alarmIds.all(AlarmLimits::validId))
        require(startedElapsed >= 0 && deadlineElapsed > startedElapsed)
        require(deadlineElapsed - startedElapsed <= AlarmLimits.RINGING_LIMIT_MS)
        require(backup || quiz != null)
    }
    fun expired(elapsed: Long, currentBoot: Int) =
        currentBoot != bootCount || elapsed < startedElapsed || elapsed >= deadlineElapsed
}

enum class AlarmOutcomeKind { ANSWERED, EMERGENCY_STOP, TIMED_OUT, MISSED, INTERRUPTED, FAILED }
data class AlarmOutcome(val alarmIds: Set<String>, val atMillis: Long, val kind: AlarmOutcomeKind)

data class AlarmBook(
    val revision: Long = 0,
    val songs: List<AlarmSong> = emptyList(),
    val alarms: List<MusicAlarm> = emptyList(),
    val ringing: RingingAlarm? = null,
    val lastOutcome: AlarmOutcome? = null,
) {
    /** Copy collection boundaries before publishing a durable/history-bearing snapshot. */
    fun frozen(): AlarmBook {
        require(revision >= 0 && songs.size <= AlarmLimits.MAX_SONGS && alarms.size <= AlarmLimits.MAX_ALARMS)
        require(songs.map { it.id }.distinct().size == songs.size)
        require(songs.map { it.sourceKey }.distinct().size == songs.size)
        require(alarms.map { it.id }.distinct().size == alarms.size)
        val ids = songs.map { it.id }.toSet()
        require(alarms.all { a -> a.songIds.all { it in ids } })
        return copy(
            songs = songs.map { it.frozen() },
            alarms = alarms.map { it.copy(weekdays = it.weekdays.toSet(), songIds = it.songIds.toList()) },
            ringing = ringing?.let { it.copy(song = it.song.frozen(), alarmIds = it.alarmIds.toSet(), quiz = it.quiz?.copy(choices = it.quiz.choices.toList())) },
            lastOutcome = lastOutcome?.let { it.copy(alarmIds = it.alarmIds.toSet()) },
        )
    }
}

private fun AlarmSong.frozen() = copy(authorAlternatives = authorAlternatives.toList(), yearAlternatives = yearAlternatives.toList())

/** Only persistence commits a proposal; rules retain the revision they were computed from. */
internal fun AlarmBook.committedAfter(previous: AlarmBook): AlarmBook {
    require(revision == previous.revision) { "An alarm proposal cannot assign its own revision" }
    return copy(revision = Math.addExact(previous.revision, 1)).frozen()
}
