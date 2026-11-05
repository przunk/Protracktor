// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Shared bounded PCM encoding; the caller owns the decoder and decides duration/fade. */
internal object PcmExport {
    interface Sink : AutoCloseable {
        fun write(pcm: ShortArray, frames: Int)
        fun finish()
    }
    private class AndroidSink(file: File, rate: Int, checkpoint: () -> Unit) : Sink {
        private val writer = M4aWriter(file, rate, checkpoint = checkpoint)
        override fun write(pcm: ShortArray, frames: Int) = writer.write(pcm, frames)
        override fun finish() = writer.finish()
        override fun close() = writer.close()
    }
    suspend fun write(file: File, rate: Int, total: Long, fadeFrames: Long = 0,
        mono: Boolean = false, trimFrames: Long = 0,
        sink: (File, Int, () -> Unit) -> Sink = ::AndroidSink, render: (ShortArray) -> Int): Long {
        val coroutine = currentCoroutineContext()
        var done = 0L
        var trimmed = 0L
        var started = trimFrames == 0L
        var audible = false
        val buffer = ShortArray(4096 * 2)
        try {
            sink(file, rate) { coroutine.ensureActive() }.use { writer ->
                while (done < total) {
                    coroutine.ensureActive()
                    val want = minOf(4096L, total - done).toInt()
                    val pcm = if (want == buffer.size / 2) buffer else ShortArray(want * 2)
                    val got = render(pcm)
                    check(got in 0..want) { "Decoder failed" }
                    if (got == 0) break
                    var first = 0
                    if (!started) {
                        while (first < got && pcm[first * 2] == 0.toShort() && pcm[first * 2 + 1] == 0.toShort()) {
                            first++; trimmed++
                            check(trimmed <= trimFrames) { "No audible start in ten seconds" }
                        }
                        started = first < got
                    }
                    if (first > 0) pcm.copyInto(pcm, 0, first * 2, got * 2)
                    val count = got - first
                    for (i in 0 until count) {
                        if (mono) {
                            val mixed = (pcm[2*i].toInt() + pcm[2*i+1].toInt()) / 2
                            pcm[2*i] = mixed.toShort(); pcm[2*i+1] = mixed.toShort()
                        }
                        audible = audible || pcm[2*i] != 0.toShort() || pcm[2*i+1] != 0.toShort()
                        if (fadeFrames > 0 && done + i > total - fadeFrames) {
                            val g = AudioExport.gainAt(done + i, total, fadeFrames)
                            pcm[2*i] = (pcm[2*i] * g).toInt().toShort()
                            pcm[2*i+1] = (pcm[2*i+1] * g).toInt().toShort()
                        }
                    }
                    if (count > 0) writer.write(pcm, count)
                    done += count
                    if (got < want) break
                }
                check(done > 0 && (!mono || audible)) { "The decoded audio is empty or silent" }
                writer.finish()
            }
            return done
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }
}
