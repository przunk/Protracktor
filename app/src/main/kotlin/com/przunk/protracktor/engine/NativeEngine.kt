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
     * Tells the native side where sc68's replay binaries were unpacked.
     *
     * Must be called before anything is opened. sc68 wraps SNDH and its own container format in
     * small replay routines that live on disk rather than inside the tune, so without this every
     * Atari ST file loads and then refuses to play.
     */
    fun setDataPath(path: String) = nativeSetDataPath(path)

    /**
     * Why the last [open] returned null.
     *
     * Empty when nothing failed. Worth showing: a file no backend claims and a file a backend
     * claimed and then choked on are different problems, and they look identical from outside.
     */
    /**
     * What [open] produced: the track, or nothing and the reason.
     *
     * The reason is worth carrying. A file no backend claims and a file a backend claimed and then
     * choked on are different problems, and they look identical from outside -- it took a host
     * probe to find that sc68 2.2.1 loaded some SNDH files and failed validation on others.
     */
    data class Opened(val track: Track?, val error: String)

    /**
     * Which decoders this build has, and at which versions.
     *
     * Stored with every row of the local index, because an index records verdicts -- what a file is,
     * and whether anything can play it -- and those are only true of the decoders that produced
     * them. Replacing sc68 2.2.1 with 3.0.0b took `.sndh` from 14 of 30 to 30 of 30 on one morning:
     * every "nothing can play this" the old set had written down became wrong. An index that cannot
     * notice that is an index that quietly outlives its own reasoning.
     */
    fun backendsFingerprint(): String = nativeBackendsFingerprint()

    /**
     * Opens a module from its bytes. Returns a handle, or `null` if the bytes are not a module the
     * backend recognises.
     *
     * Bytes rather than a path because the sources this app plays from are not all files: entries
     * inside an archive and files fetched from a remote catalogue have no path of their own.
     *
     * The name comes along separately because some backends need it. Several of the Atari 8-bit
     * formats ASAP handles are told apart by extension rather than by any header, and one of them
     * shares `.fc` with an Amiga format libopenmpt claims.
     */
    fun open(bytes: ByteArray, fileName: String): Opened {
        // The reason comes back with the call. It used to sit in a process-wide string that the
        // caller collected afterwards, which was fine while one thread opened files at a time and
        // became a data race -- confirmed under ThreadSanitizer -- the moment library scanning was
        // made concurrent with playback (`docs/review.md` R2).
        val reason = arrayOfNulls<String>(1)
        val handle = nativeOpen(bytes, fileName, reason)
        return Opened(
            track = if (handle == 0L) null else Track(handle),
            error = reason[0].orEmpty(),
        )
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

        /**
         * Moves the playing position.
         *
         * Takes effect on the audio thread's next pass rather than immediately, so the position
         * read straight afterwards may still be the old one.
         */
        fun seekTo(seconds: Double) = nativeSeek(handle(), seconds)

        /**
         * Scales the output. 1.0 is untouched.
         *
         * Applied inside the render callback, so ducking under a notification lowers the music
         * without a gap where stopping the stream would leave one.
         */
        fun setGain(gain: Float) = nativeSetGain(handle(), gain)

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

    @JvmStatic private external fun nativeOpen(
        data: ByteArray,
        fileName: String,
        errorOut: Array<String?>,
    ): Long
    @JvmStatic private external fun nativeClose(handle: Long)
    @JvmStatic private external fun nativeStart(handle: Long): Boolean
    @JvmStatic private external fun nativeStop(handle: Long)
    @JvmStatic private external fun nativeIsFinished(handle: Long): Boolean
    @JvmStatic private external fun nativeRestart(handle: Long): Boolean
    @JvmStatic private external fun nativeSeek(handle: Long, seconds: Double)
    @JvmStatic private external fun nativeSetDataPath(path: String)
    @JvmStatic private external fun nativeBackendsFingerprint(): String
    @JvmStatic private external fun nativeSetGain(handle: Long, gain: Float)
    @JvmStatic private external fun nativeDescribe(handle: Long): String
    @JvmStatic private external fun nativePositionSeconds(handle: Long): Double
    @JvmStatic private external fun nativeDurationSeconds(handle: Long): Double
}
