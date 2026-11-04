// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.alarms

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/** The small alarm ledger is independent of the credential-protected music database. */
class AlarmFileStore internal constructor(val directory: File) {
    private val ledger = AtomicFile(File(directory, "alarms-v1.json"))

    @Synchronized fun read(): AlarmBook {
        val input = try { ledger.openRead() } catch (missing: FileNotFoundException) {
            if (ledger.baseFile.exists() || File(ledger.baseFile.path + ".bak").exists()) throw missing
            return AlarmBook()
        }
        val bytes = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(out.size() + count <= MAX_LEDGER_BYTES) { "Alarm data exceeds its size limit" }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
        return AlarmCodec.decode(bytes.toString(Charsets.UTF_8))
    }

    @Synchronized fun write(book: AlarmBook) {
        // Guard the lowest write boundary too: an old build must not replace newer/corrupt data.
        val existing = if (ledger.baseFile.exists() || File(ledger.baseFile.path + ".bak").exists()) read() else null
        require(existing == null || book.revision > existing.revision) { "Stale alarm data replacement" }
        val bytes = AlarmCodec.encode(book).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_LEDGER_BYTES)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create alarm storage" }
        val output = ledger.startWrite()
        try {
            output.write(bytes)
            ledger.finishWrite(output)
        } catch (failure: Throwable) {
            ledger.failWrite(output)
            throw failure
        }
    }

    fun audioFile(songId: String): File {
        require(AlarmLimits.validId(songId))
        return File(directory, "$songId.m4a")
    }

    companion object {
        const val MAX_LEDGER_BYTES = 1024 * 1024
        private val stores = ConcurrentHashMap<String, AlarmFileStore>()
        fun directory(context: Context) = File(context.createDeviceProtectedStorageContext().noBackupFilesDir, "music-alarms")
        fun from(context: Context): AlarmFileStore {
            val root = directory(context)
            return stores.computeIfAbsent(root.absolutePath) { AlarmFileStore(root) }
        }
    }
}
