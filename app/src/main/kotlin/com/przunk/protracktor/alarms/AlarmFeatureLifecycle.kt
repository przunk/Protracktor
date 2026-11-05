// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.NotificationManager
import android.content.Context
import android.util.AtomicFile
import java.io.File

/** Compatibility shutdown is deliberately independent of opening or rewriting the alarm ledger. */
internal object AlarmFeatureLifecycle {
    private const val MARKER = "suspended-by-build-v1"
    private fun marker(directory: File) = AtomicFile(File(directory, MARKER))

    @Synchronized fun deactivate(context: Context) {
        // Attempt every shutdown boundary even if another platform or storage operation fails.
        var failure: Exception? = null
        fun attempt(block: () -> Unit) { try { block() } catch (error: Exception) { if (failure == null) failure = error } }
        attempt { AndroidAlarmScheduler.cancel(context, retire = true) }
        attempt { context.stopService(AlarmPlatform.serviceIntent(context)) }
        attempt { context.getSystemService(NotificationManager::class.java).cancel(AlarmPlatform.STARTUP_NOTIFICATION) }
        attempt { context.getSystemService(NotificationManager::class.java).cancel(AlarmPlatform.NOTIFICATION) }
        attempt {
            val directory = AlarmFileStore.directory(context)
            if (File(directory, "alarms-v1.json").exists() || File(directory, "alarms-v1.json.bak").exists()) {
                val state = marker(directory)
                val output = state.startWrite()
                try { output.write("suspended-v1".toByteArray()); state.finishWrite(output) }
                catch (error: Throwable) { state.failWrite(output); throw error }
            }
        }
        failure?.let { throw it }
    }

    @Synchronized fun requireExplicitRearm(repository: AlarmRepository) {
        val state = marker(repository.files.directory)
        if (!state.baseFile.exists() && !File(state.baseFile.path + ".bak").exists()) return
        val expected = "suspended-v1".toByteArray()
        state.openRead().use { input ->
            val actual = ByteArray(expected.size)
            java.io.DataInputStream(input).readFully(actual)
            check(actual.contentEquals(expected) && input.read() == -1) { "Alarm feature state cannot be opened; it has been preserved" }
        }
        check(repository.readFailure == null) { "Alarm data cannot be opened; it has been preserved" }
        repository.change { book ->
            book.copy(alarms = book.alarms.map { it.copy(enabled = false, nextAtMillis = 0, occurrenceDate = null) },
                ringing = null,
                lastOutcome = book.ringing?.takeUnless { it.preview }?.let {
                    AlarmOutcome(it.alarmIds, System.currentTimeMillis(), AlarmOutcomeKind.INTERRUPTED)
                } ?: book.lastOutcome)
        }
        state.delete()
        check(!state.baseFile.exists() && !File(state.baseFile.path + ".bak").exists()) { "Alarm feature state could not be updated" }
    }
}
