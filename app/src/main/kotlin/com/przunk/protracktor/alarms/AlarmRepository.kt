// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** All mutations share the same lock and publish only after the atomic write succeeds. */
class AlarmRepository private constructor(val files: AlarmFileStore) {
    private val initial = runCatching { files.read() }
    val readFailure: Throwable? = initial.exceptionOrNull()
    init {
        if (readFailure == null) files.directory.listFiles().orEmpty()
            .filter { it.extension == "preparing" && AlarmLimits.validId(it.nameWithoutExtension) }
            .forEach { it.delete() }
    }
    private val mutable = MutableStateFlow(initial.getOrDefault(AlarmBook()))
    val state = mutable.asStateFlow()
    val book: AlarmBook get() = state.value

    @Synchronized fun change(transform: (AlarmBook) -> AlarmBook): AlarmBook {
        check(readFailure == null) { "Alarm data cannot be opened; it has been preserved" }
        val old = book
        val proposed = transform(old)
        if (proposed == old) return old
        val next = proposed.copy(revision = old.revision + 1).frozen()
        files.write(next)
        mutable.value = next
        return next
    }

    @Synchronized fun add(song: AlarmSong, prepared: File): AlarmSong {
        check(readFailure == null)
        book.songs.firstOrNull { it.sourceKey == song.sourceKey }?.let { prepared.delete(); return it }
        require(book.songs.size < AlarmLimits.MAX_SONGS) { "Alarm music collection is full" }
        val used = files.directory.listFiles().orEmpty().filter { it.extension == "m4a" }.sumOf { it.length() }
        require(used + prepared.length() <= AlarmLimits.MAX_AUDIO_BYTES) { "Alarm music storage is full" }
        check(files.directory.isDirectory || files.directory.mkdirs()) { "Cannot create alarm storage" }
        val target = files.audioFile(song.id)
        check(!target.exists() && prepared.renameTo(target)) { "Cannot save alarm music" }
        try { change { it.copy(songs = it.songs + song) } }
        catch (failure: Throwable) { target.delete(); throw failure }
        return song
    }

    @Synchronized fun cleanUnusedAudio() {
        val keep = book.songs.map { it.id }.toSet() + listOfNotNull(book.ringing?.song?.id)
        files.directory.listFiles().orEmpty().filter { it.extension == "m4a" && it.nameWithoutExtension !in keep }
            .forEach { it.delete() }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, AlarmRepository>()
        fun from(context: Context): AlarmRepository {
            val files = AlarmFileStore.from(context)
            return instances.computeIfAbsent(files.directory.absolutePath) { AlarmRepository(files) }
        }
        fun sourceAllowed(song: AlarmSong): Boolean = when (song.catalogueId) {
            "amp" -> com.przunk.protracktor.net.Amp.access.enabled
            "unexotica" -> com.przunk.protracktor.net.UnExoticA.ENABLED
            else -> true
        }
    }
}
