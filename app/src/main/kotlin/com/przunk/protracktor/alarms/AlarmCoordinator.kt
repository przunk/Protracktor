// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import com.przunk.protracktor.player.Platforms
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Worker-thread facade shared by management, receiver and ringing service. */
internal class AlarmCoordinator private constructor(private val context: Context) {
    val repository = AlarmRepository.from(context)
    private val quizzes = AlarmQuizFactory(Platforms.all.map { it.id to it.name })
    private val rules = AlarmRules(quizzes, AlarmRepository::sourceAllowed)
    private val armed = MutableStateFlow<Long?>(null)
    val armedAt = armed.asStateFlow()
    fun usable(song: AlarmSong) = AlarmRepository.sourceAllowed(song) &&
        repository.files.audioFile(song.id).length() > 0 && quizzes.canAsk(song, repository.book.songs)

    @Synchronized fun reconcile(resetTime: Boolean = false) {
        if (repository.readFailure != null) { armed.value = AlarmPlatform.schedule(context, AlarmBook()); return }
        val now = AlarmPlatform.clock(context)
        val zone = ZoneId.systemDefault()
        repository.change {
            val next = rules.reconcile(it, now, zone, resetTime, ::usable).book
            if (AlarmPlatform.notifications(context) || next.ringing == null) next else next.copy(ringing = null,
                lastOutcome = AlarmOutcome(next.ringing.alarmIds, now.wallMillis, AlarmOutcomeKind.MISSED))
        }
        schedule()
        repository.cleanUnusedAudio()
    }

    @Synchronized fun save(alarm: MusicAlarm) {
        repository.change { book ->
            require(book.alarms.any { it.id == alarm.id } || book.alarms.size < AlarmLimits.MAX_ALARMS)
            require(!alarm.enabled || alarm.songIds.any { id -> book.songs.any { it.id == id && usable(it) } })
            val planned = AlarmPlanner.planned(alarm.copy(consumedDate = null), Instant.now(), ZoneId.systemDefault())
            book.copy(alarms = book.alarms.filterNot { it.id == alarm.id } + planned)
        }
        schedule()
    }
    @Synchronized fun deleteAlarm(id: String) {
        repository.change { it.copy(alarms = it.alarms.filterNot { a -> a.id == id }) }; schedule()
    }
    @Synchronized fun restoreAlarm(alarm: MusicAlarm) {
        repository.change { book ->
            require(book.alarms.none { it.id == alarm.id }) { "This alarm has changed" }
            require(book.alarms.size < AlarmLimits.MAX_ALARMS)
            require(alarm.songIds.all { id -> book.songs.any { it.id == id } }) { "Alarm music was removed" }
            book.copy(alarms = book.alarms + AlarmPlanner.planned(alarm, Instant.now(), ZoneId.systemDefault()))
        }
        schedule()
    }
    @Synchronized fun deleteSong(id: String) {
        repository.change { book ->
            book.copy(songs = book.songs.filterNot { it.id == id }, alarms = book.alarms.map { a ->
                val songs = a.songIds.filterNot { it == id }
                a.copy(songIds = songs, enabled = a.enabled && songs.isNotEmpty(),
                    nextAtMillis = if (songs.isEmpty()) 0 else a.nextAtMillis)
            })
        }
        reconcile()
    }
    @Synchronized fun deliver(revision: Long, at: Long) {
        val now = AlarmPlatform.clock(context)
        repository.change { book ->
            val next = rules.delivery(book, revision, at, now, ZoneId.systemDefault(), UUID.randomUUID().toString()).book
            if (AlarmPlatform.notifications(context) && AlarmPlatform.fullScreen(context)) next else next.copy(ringing = null,
                lastOutcome = next.ringing?.let { AlarmOutcome(it.alarmIds, now.wallMillis, AlarmOutcomeKind.MISSED) } ?: next.lastOutcome)
        }
        schedule()
    }
    @Synchronized fun preview(id: String, songIds: Set<String>) {
        check(AlarmPlatform.notifications(context))
        repository.change { rules.preview(it, id, songIds, AlarmPlatform.clock(context), UUID.randomUUID().toString()).book }
        schedule()
    }
    @Synchronized fun answer(token: String, choice: String): Boolean {
        var accepted = false
        repository.change { rules.answer(it, token, choice, AlarmPlatform.clock(context)).also { result -> accepted = result.accepted }.book }
        schedule(); return accepted
    }
    @Synchronized fun stop(token: String) {
        repository.change { rules.stop(it, token, AlarmPlatform.clock(context)).book }; schedule()
    }
    @Synchronized fun backup(token: String) {
        repository.change { rules.backup(it, token).book }; schedule()
    }
    @Synchronized fun failed(token: String) {
        repository.change { rules.failed(it, token, AlarmPlatform.clock(context)).book }; schedule()
    }
    private fun schedule() { armed.value = AlarmPlatform.schedule(context, repository.book) }
    companion object {
        private val instances = ConcurrentHashMap<String, AlarmCoordinator>()
        fun from(context: Context): AlarmCoordinator {
            val app = context.applicationContext
            val key = AlarmFileStore.from(app).directory.absolutePath
            return instances.computeIfAbsent(key) { AlarmCoordinator(app) }
        }
        fun isRinging(context: Context): Boolean = AlarmRepository.from(context).book.ringing?.let {
            val now = AlarmPlatform.clock(context); !it.expired(now.elapsedMillis, now.bootCount)
        } == true
    }
}
