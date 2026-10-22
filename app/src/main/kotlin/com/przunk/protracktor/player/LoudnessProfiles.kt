// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.CRC32

data class LoudnessProfile(val seconds: Double, val lufs: Double, val peak: Double, val complete: Boolean) {
    val usable get() = seconds.isFinite() && seconds >= 0.4 && lufs.isFinite() && lufs in -70.0..30.0 &&
        peak.isFinite() && peak > 0.0
    companion object {
        fun fromNative(values: DoubleArray?): LoudnessProfile? = values?.takeIf { it.size >= 4 }
            ?.let { LoudnessProfile(it[0], it[1], it[2], it[3] == 1.0) }?.takeIf { it.usable }
    }
}

/** Disposable content-addressed statistics, independent of user data and the History lifecycle. */
class LoudnessProfiles(private val directory: File) {
    @Synchronized fun read(key: String): LoudnessProfile? = runCatching {
        require(key.matches(Regex("[a-f0-9]{64}")))
        DataInputStream(File(directory,"$key.profile").inputStream().buffered()).use {
            val payload=ByteArray(29); it.readFully(payload)
            check(it.readLong()==CRC32().apply { update(payload) }.value && it.read()==-1)
            DataInputStream(ByteArrayInputStream(payload)).use { data ->
                check(data.readInt()==1)
                LoudnessProfile(data.readDouble(),data.readDouble(),data.readDouble(),data.readBoolean()).takeIf { p -> p.usable }
            }
        }
    }.getOrNull()

    @Synchronized fun write(key: String, profile: LoudnessProfile) {
        if (!profile.usable || !key.matches(Regex("[a-f0-9]{64}"))) return
        val previous=read(key)
        if (previous != null && (previous.complete && !profile.complete ||
                previous.complete == profile.complete && previous.seconds >= profile.seconds)) return
        runCatching {
            directory.mkdirs()
            val target=File(directory,"$key.profile")
            val temporary=File.createTempFile("profile-",".tmp",directory)
            try {
                DataOutputStream(temporary.outputStream().buffered()).use {
                    val bytes=ByteArrayOutputStream()
                    DataOutputStream(bytes).use { data ->
                        data.writeInt(1); data.writeDouble(profile.seconds); data.writeDouble(profile.lufs)
                        data.writeDouble(profile.peak); data.writeBoolean(profile.complete)
                    }
                    val payload=bytes.toByteArray(); it.write(payload)
                    it.writeLong(CRC32().apply { update(payload) }.value)
                }
                Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
            directory.listFiles { f -> f.name.matches(Regex("[a-f0-9]{64}\\.profile")) }
                ?.sortedBy { it.lastModified() }?.dropLast(512)?.forEach { it.delete() }
        } // An evicted/unwritable measurement cache never prevents playback.
    }

    companion object {
        fun contentKey(bytes: ByteArray, name: String, companions: List<Pair<String,ByteArray>>, revision: String): String {
            val digest=MessageDigest.getInstance("SHA-256")
            fun part(data: ByteArray) {
                val size=data.size
                digest.update(byteArrayOf((size ushr 24).toByte(),(size ushr 16).toByte(),(size ushr 8).toByte(),size.toByte()))
                digest.update(data)
            }
            part("loudness-v1:$revision".toByteArray(Charsets.UTF_8))
            part(name.toByteArray(Charsets.UTF_8)); part(bytes)
            companions.sortedBy { it.first }.forEach { (n,b) -> part(n.toByteArray(Charsets.UTF_8)); part(b) }
            return digest.digest().joinToString("") { it.toUByte().toString(16).padStart(2,'0') }
        }
        fun key(base: String, subsong: Int, width: Int): String = MessageDigest.getInstance("SHA-256")
            .digest("$base:$subsong:$width".toByteArray(Charsets.UTF_8)).joinToString("") { it.toUByte().toString(16).padStart(2,'0') }
    }
}
