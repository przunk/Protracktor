// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.alarms

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

data class AlarmClockReading(val wallMillis: Long, val elapsedMillis: Long, val bootCount: Int)
data class AlarmChange(val book: AlarmBook, val accepted: Boolean)

/** Durable session transitions. Callers commit the result before changing platform resources. */
class AlarmRules(
    private val quizzes: AlarmQuizFactory,
    private val sourceAllowed: (AlarmSong) -> Boolean,
) {
    fun reconcile(book: AlarmBook, now: AlarmClockReading, zone: ZoneId, resetTime: Boolean,
        usable: (AlarmSong) -> Boolean): AlarmChange {
        val restored = expire(book, now).book
        val withdrawn = restored.ringing?.takeIf { !it.backup && !sourceAllowed(it.song) }
        val previous = withdrawn?.let { backup(restored, it.token).book } ?: restored
        var missed = emptySet<String>()
        val instant = Instant.ofEpochMilli(now.wallMillis)
        val alarms = previous.alarms.map { alarm ->
            when {
                alarm.songIds.none { id -> previous.songs.any { it.id == id && usable(it) } } ->
                    alarm.copy(nextAtMillis = 0, occurrenceDate = null)
                resetTime || alarm.nextAtMillis == 0L || alarm.nextAtMillis < now.wallMillis - AlarmLimits.DELIVERY_GRACE_MS -> {
                    if (alarm.enabled && alarm.nextAtMillis in 1..now.wallMillis) {
                        missed = missed + alarm.id
                        AlarmPlanner.consume(alarm, instant, zone)
                    } else AlarmPlanner.planned(alarm, instant, zone)
                }
                else -> alarm
            }
        }
        val next = previous.copy(alarms = alarms, lastOutcome = if (missed.isEmpty()) previous.lastOutcome else
            AlarmOutcome(missed, now.wallMillis, AlarmOutcomeKind.MISSED))
        return if (next == book) AlarmChange(book, false)
            else AlarmChange(next.frozen(), true)
    }

    fun delivery(book: AlarmBook, revision: Long, scheduledAt: Long, now: AlarmClockReading,
        zone: ZoneId, token: String, random: Random = Random.Default): AlarmChange {
        if (book.revision != revision || scheduledAt != AlarmPlanner.earliest(book.alarms) ||
            scheduledAt > now.wallMillis) return AlarmChange(book, false)
        val due = book.alarms.filter { it.enabled && it.nextAtMillis in 1..now.wallMillis }
        if (due.isEmpty()) return AlarmChange(book, false)
        val fresh = due.filter { now.wallMillis - it.nextAtMillis <= AlarmLimits.DELIVERY_GRACE_MS }
        val ids = fresh.map { it.id }.toSet()
        val previous = expire(book, now).book
        val alarms = previous.alarms.map { a ->
            if (due.any { it.id == a.id }) AlarmPlanner.consume(a, Instant.ofEpochMilli(now.wallMillis), zone) else a
        }
        val missed = due.filterNot { it.id in ids }.map { it.id }.toSet()
        val outcome = if (missed.isNotEmpty()) AlarmOutcome(missed, now.wallMillis, AlarmOutcomeKind.MISSED) else previous.lastOutcome
        val active = previous.ringing
        val ringing = when {
            ids.isEmpty() -> active
            active != null -> active.copy(alarmIds = (if (active.preview) emptySet() else active.alarmIds) + ids, preview = false,
                backup = active.backup || !sourceAllowed(active.song),
                quiz = active.quiz.takeIf { sourceAllowed(active.song) })
            else -> {
                val chosenIds = fresh.flatMap { it.songIds }.toSet()
                val raw = previous.songs.filter { it.id in chosenIds }
                val eligible = raw.filter { sourceAllowed(it) && quizzes.canAsk(it, previous.songs) }
                val pool = eligible.ifEmpty { raw }
                val chosen = pool[random.nextInt(pool.size)]
                val quiz = if (eligible.isEmpty()) null else quizzes.create(chosen, previous.songs, random)
                RingingAlarm(token, ids, chosen, quiz, now.elapsedMillis,
                    now.elapsedMillis + AlarmLimits.RINGING_LIMIT_MS, now.bootCount, backup = quiz == null)
            }
        }
        return AlarmChange(previous.copy(alarms = alarms, ringing = ringing, lastOutcome = outcome).frozen(), true)
    }

    fun preview(book: AlarmBook, alarmId: String, songIds: Set<String>, now: AlarmClockReading,
        token: String, random: Random = Random.Default): AlarmChange {
        val previous = expire(book, now).book
        if (previous.ringing != null) return AlarmChange(book, false)
        val songs = previous.songs.filter { it.id in songIds && sourceAllowed(it) && quizzes.canAsk(it, previous.songs) }
        if (songs.isEmpty()) return AlarmChange(book, false)
        val song = songs[random.nextInt(songs.size)]
        val quiz = quizzes.create(song, previous.songs, random) ?: return AlarmChange(book, false)
        return AlarmChange(previous.copy(
            ringing = RingingAlarm(token, setOf(alarmId), song, quiz, now.elapsedMillis,
                now.elapsedMillis + AlarmLimits.RINGING_LIMIT_MS, now.bootCount, preview = true)).frozen(), true)
    }

    /** Only a correct answer is accepted; a wrong answer may still persist a new question. */
    fun answer(book: AlarmBook, token: String, choiceId: String, now: AlarmClockReading): AlarmChange {
        val active = book.ringing ?: return AlarmChange(book, false)
        val quiz = active.quiz ?: return AlarmChange(book, false)
        if (active.token != token || active.backup || active.expired(now.elapsedMillis, now.bootCount) ||
            quiz.choices.none { it.id == choiceId }) return AlarmChange(book, false)
        if (quiz.accepts(choiceId)) return finish(book, token, AlarmOutcomeKind.ANSWERED, now)
        // A queued tap from the retired question must never match a new answer by row position.
        val next = quizzes.create(active.song, book.songs, excluding = quiz.category,
            choicePrefix = "retry-${book.revision + 1}") ?: return AlarmChange(book, false)
        return AlarmChange(book.copy(
            ringing = active.copy(quiz = next)).frozen(), false)
    }

    fun stop(book: AlarmBook, token: String, now: AlarmClockReading) =
        finish(book, token, AlarmOutcomeKind.EMERGENCY_STOP, now)

    fun failed(book: AlarmBook, token: String, now: AlarmClockReading) =
        finish(book, token, AlarmOutcomeKind.FAILED, now)

    private fun finish(book: AlarmBook, token: String, kind: AlarmOutcomeKind, now: AlarmClockReading): AlarmChange {
        val active = book.ringing ?: return AlarmChange(book, false)
        if (active.token != token) return AlarmChange(book, false)
        val outcome = if (active.preview) book.lastOutcome else AlarmOutcome(active.alarmIds, now.wallMillis, kind)
        return AlarmChange(book.copy(ringing = null, lastOutcome = outcome).frozen(), true)
    }

    fun backup(book: AlarmBook, token: String): AlarmChange {
        val active = book.ringing ?: return AlarmChange(book, false)
        if (active.token != token || active.backup) return AlarmChange(book, false)
        return AlarmChange(book.copy(
            ringing = active.copy(backup = true, quiz = null)).frozen(), true)
    }

    fun expire(book: AlarmBook, now: AlarmClockReading): AlarmChange {
        val active = book.ringing ?: return AlarmChange(book, false)
        if (!active.expired(now.elapsedMillis, now.bootCount)) return AlarmChange(book, false)
        val kind = if (active.bootCount != now.bootCount || now.elapsedMillis < active.startedElapsed)
            AlarmOutcomeKind.INTERRUPTED else AlarmOutcomeKind.TIMED_OUT
        return finish(book, active.token, kind, now)
    }
}
