/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.engine

/**
 * The Kotlin side of the native player.
 *
 * Only control and metadata cross this boundary. Decoding and audio output both stay in native
 * code, so nothing here runs on the audio thread.
 */
object NativeEngine {

    init {
        System.loadLibrary("protracktor_engine")
    }

    /**
     * Opens a module from its bytes. Returns a handle, or `null` if the bytes are not a module the
     * backend recognises.
     *
     * Bytes rather than a path because the sources this app plays from are not all files: entries
     * inside an archive and files fetched from a remote catalogue have no path of their own.
     */
    fun open(bytes: ByteArray): Track? {
        val handle = nativeOpen(bytes)
        return if (handle == 0L) null else Track(handle)
    }

    /** An open module. Must be [close]d; the native side owns memory that GC does not see. */
    class Track internal constructor(private val handle: Long) : AutoCloseable {
        private var closed = false

        /** Metadata as `key\tvalue` lines. Parsed by [describe]. */
        fun describe(): Map<String, String> = nativeDescribe(handle())
            .lineSequence()
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
            }
            .toMap()

        fun start(): Boolean = nativeStart(handle())

        /**
         * True once the module has played to its end.
         *
         * Polled rather than pushed: signalling from the audio callback would mean attaching a JNI
         * environment on the one thread that must never be late. The caller is polling for position
         * anyway.
         */
        fun isFinished(): Boolean = nativeIsFinished(handle())

        /** Back to the beginning and playing. For repeat-one, and for replaying a finished track. */
        fun restart(): Boolean = nativeRestart(handle())

        fun stop() = nativeStop(handle())

        fun positionSeconds(): Double = nativePositionSeconds(handle())

        fun durationSeconds(): Double = nativeDurationSeconds(handle())

        override fun close() {
            if (closed) return
            closed = true
            nativeClose(handle)
        }

        // Calling into native code with a freed handle is a crash with no useful stack. Failing here
        // instead names the mistake at the point that made it.
        private fun handle(): Long {
            check(!closed) { "Track already closed" }
            return handle
        }
    }

    @JvmStatic private external fun nativeOpen(data: ByteArray): Long
    @JvmStatic private external fun nativeClose(handle: Long)
    @JvmStatic private external fun nativeStart(handle: Long): Boolean
    @JvmStatic private external fun nativeStop(handle: Long)
    @JvmStatic private external fun nativeIsFinished(handle: Long): Boolean
    @JvmStatic private external fun nativeRestart(handle: Long): Boolean
    @JvmStatic private external fun nativeDescribe(handle: Long): String
    @JvmStatic private external fun nativePositionSeconds(handle: Long): Double
    @JvmStatic private external fun nativeDurationSeconds(handle: Long): Double
}
