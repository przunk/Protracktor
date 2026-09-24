// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.engine

import com.przunk.protracktor.player.SupportedFormats

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
     * Where UADE's emulator, its data and a scratch directory are.
     *
     * Three paths because none of them is a build-time constant: `uadecore` is an executable
     * shipped as `lib/<abi>/libuadecore.so` and found through `applicationInfo.nativeLibraryDir`,
     * the data directory is under `filesDir`, and the scratch directory is where a tune is written
     * so that it has a path — which is the only way a multifile song can find its other half.
     *
     * Called again after the replay routines are downloaded, because until they are there the
     * backend answers "not set up" and the Amiga formats are simply absent.
     */
    fun setUadePaths(coreFile: String, baseDir: String, scratchDir: String) =
        nativeSetUadePaths(coreFile, baseDir, scratchDir)

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
    /**
     * What this build would put in an index: the decoders, and the names they are offered.
     *
     * Both halves matter and only one used to be recorded. See `SupportedFormats.fingerprint`.
     */
    fun backendsFingerprint(): String =
        nativeBackendsFingerprint() + ";" + SupportedFormats.fingerprint + ";" +
            com.przunk.protracktor.player.Platforms.fingerprint

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
    /**
     * [companions] are the other files of a multifile song — `smpl.name` beside `mdat.name` for
     * TFMX — as (file name, bytes). Only UADE reads them, and only because the emulated program
     * asks for them by name while it plays; every other decoder is handed one file.
     */
    fun open(
        bytes: ByteArray,
        fileName: String,
        companions: List<Pair<String, ByteArray>> = emptyList(),
    ): Opened {
        // The reason comes back with the call. It used to sit in a process-wide string that the
        // caller collected afterwards, which was fine while one thread opened files at a time and
        // became a data race -- confirmed under ThreadSanitizer -- the moment library scanning was
        // made concurrent with playback (`docs/review.md` R2).
        val reason = arrayOfNulls<String>(1)
        val handle = nativeOpen(
            bytes,
            fileName,
            companions.map { it.first }.toTypedArray(),
            companions.map { it.second }.toTypedArray(),
            reason,
        )
        return Opened(
            track = if (handle == 0L) null else Track(handle),
            error = reason[0].orEmpty(),
        )
    }

    /**
     * A file opened to be rendered into another file rather than played (`docs/BACKLOG.md` A62):
     * the same decoders the player opens it with, and no audio stream. Null with [error] when no
     * decoder took it.
     */
    fun openRendering(
        bytes: ByteArray,
        fileName: String,
        companions: List<Pair<String, ByteArray>> = emptyList(),
    ): Pair<Rendering?, String> {
        val reason = arrayOfNulls<String>(1)
        val handle = nativeRenderOpen(
            bytes, fileName,
            companions.map { it.first }.toTypedArray(),
            companions.map { it.second }.toTypedArray(),
            reason,
        )
        return (if (handle == 0L) null else Rendering(handle)) to reason[0].orEmpty()
    }

    /** A decoder rendering to PCM on demand. Must be [close]d. One thread at a time. */
    class Rendering internal constructor(private var handle: Long) : AutoCloseable {
        fun selectSubsong(index: Int): Boolean = nativeRenderSelect(live(), index)
        fun currentSubsong(): Int = nativeRenderSubsong(live())
        fun durationSeconds(): Double = nativeRenderDuration(live())
        /** 0 when the decoder has no preference. */
        fun preferredSampleRate(): Int = nativeRenderRate(live())
        /** Stereo frames into [out] (two shorts each); fewer than asked is the end, -1 a failure. */
        fun render(sampleRate: Int, out: ShortArray): Int = nativeRender(live(), sampleRate, out)
        override fun close() {
            if (handle != 0L) nativeRenderClose(handle)
            handle = 0L
        }
        private fun live(): Long = handle.also { check(it != 0L) { "rendering used after close" } }
    }

    /** An open module. Must be [close]d; the native side owns memory that GC does not see. */
    class Track internal constructor(private val handle: Long) : AutoCloseable {
        private var closed = false

        /** Metadata as `key\tvalue` lines, the module's message whole ([DescribeBlock]). */
        fun describe(): Map<String, String> = DescribeBlock.parse(nativeDescribe(handle()))

        fun start(): Boolean = nativeStart(handle())

        /**
         * Each subsong's length where a database knows it, zero-based, zero where it does not.
         * Before [start]. Only UADE listens: it works out only what is missing (A52).
         */
        fun knownLengths(lengths: List<Double>) = nativeKnownLengths(handle(), lengths.toDoubleArray())

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
         * Moves the playing position. **Not from the main thread.**
         *
         * It used to be handed to the audio callback and applied there, which returned at once and
         * was the wrong trade: a seek is unbounded work for every emulator behind this — they
         * reach a position by running forward to it — and doing it inside a callback with a
         * millisecond budget starved the stream and froze the app. It is now done on the calling
         * thread while the callback plays silence, so this call blocks for as long as the decoder
         * needs, which for a long tune is seconds.
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

        /** How many tunes are inside this file. One for a format that holds one. */
        fun subsongCount(): Int = nativeSubsongCount(handle())

        /**
         * Plays tune [index], counted from zero.
         *
         * Handed to the audio callback rather than applied here, the same way a seek is: the
         * callback is the only thread that touches the decoder, and swapping one underneath a read
         * in progress is how a player crashes.
         */
        fun selectSubsong(index: Int) = nativeSelectSubsong(handle(), index)

        /**
         * What Oboe gave us, if it is not what the decoder asked for. Empty when all is well.
         *
         * Only meaningful after [start]: the stream does not exist until then. Read once, right
         * after starting.
         */
        fun sampleRateNote(): String = nativeSampleRateNote(handle())

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
        companionNames: Array<String>,
        companionData: Array<ByteArray>,
        errorOut: Array<String?>,
    ): Long
    @JvmStatic private external fun nativeClose(handle: Long)
    @JvmStatic private external fun nativeRenderOpen(
        data: ByteArray,
        fileName: String,
        companionNames: Array<String>,
        companionData: Array<ByteArray>,
        errorOut: Array<String?>,
    ): Long
    @JvmStatic private external fun nativeRenderClose(handle: Long)
    @JvmStatic private external fun nativeRenderSelect(handle: Long, index: Int): Boolean
    @JvmStatic private external fun nativeRenderSubsong(handle: Long): Int
    @JvmStatic private external fun nativeRenderDuration(handle: Long): Double
    @JvmStatic private external fun nativeRenderRate(handle: Long): Int
    @JvmStatic private external fun nativeRender(handle: Long, sampleRate: Int, out: ShortArray): Int
    @JvmStatic private external fun nativeStart(handle: Long): Boolean
    @JvmStatic private external fun nativeKnownLengths(handle: Long, lengths: DoubleArray)
    @JvmStatic private external fun nativeStop(handle: Long)
    @JvmStatic private external fun nativeIsFinished(handle: Long): Boolean
    @JvmStatic private external fun nativeRestart(handle: Long): Boolean
    @JvmStatic private external fun nativeSeek(handle: Long, seconds: Double)
    @JvmStatic private external fun nativeSubsongCount(handle: Long): Int
    @JvmStatic private external fun nativeSelectSubsong(handle: Long, index: Int)
    @JvmStatic private external fun nativeSetDataPath(path: String)
    @JvmStatic private external fun nativeSetUadePaths(
        coreFile: String,
        baseDir: String,
        scratchDir: String,
    )
    @JvmStatic private external fun nativeBackendsFingerprint(): String
    @JvmStatic private external fun nativeSetGain(handle: Long, gain: Float)
    @JvmStatic private external fun nativeDescribe(handle: Long): String
    @JvmStatic private external fun nativePositionSeconds(handle: Long): Double
    @JvmStatic private external fun nativeDurationSeconds(handle: Long): Double
    @JvmStatic private external fun nativeSampleRateNote(handle: Long): String
}
