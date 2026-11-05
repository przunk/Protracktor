// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

/**
 * 16-bit stereo PCM in, an `.m4a` out -- Android's own AAC encoder and MP4 muxer, so no library is
 * added to share a tune as audio (`docs/BACKLOG.md` A62). 128 kbit/s, AAC-LC: what every chat app
 * and player takes.
 */
class M4aWriter(file: File, private val sampleRate: Int, bitRate: Int = 128_000,
    private val checkpoint: () -> Unit = {},
) : AutoCloseable {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer = try { MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) }
        catch (failure: Throwable) { codec.release(); throw failure }
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var framesIn = 0L
    private var finished = false

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (failure: Throwable) { close(); throw failure }
    }

    /** Encodes [frames] stereo frames of [pcm]. */
    fun write(pcm: ShortArray, frames: Int) {
        var at = 0
        val total = frames * 2
        var progress = android.os.SystemClock.elapsedRealtime()
        while (at < total) {
            poll(progress)
            val index = codec.dequeueInputBuffer(10_000)
            if (index < 0) { drain(false); continue }
            val buffer = codec.getInputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
            buffer.clear()
            val count = minOf(buffer.remaining() / 2, total - at) and 1.inv()
            check(count > 0) { "AAC encoder provided an empty input buffer" }
            buffer.asShortBuffer().put(pcm, at, count)
            codec.queueInputBuffer(index, 0, count * 2, framesIn * 1_000_000L / sampleRate, 0)
            framesIn += count / 2
            at += count
            progress = android.os.SystemClock.elapsedRealtime()
            drain(false)
        }
    }

    /** Ends the stream and writes the file out. */
    fun finish() {
        if (finished) return
        var index: Int
        val since = android.os.SystemClock.elapsedRealtime()
        do { poll(since); index = codec.dequeueInputBuffer(10_000); if (index < 0) drain(false) } while (index < 0)
        codec.queueInputBuffer(index, 0, 0, framesIn * 1_000_000L / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(true)
        finished = true
        muxer.stop()
    }

    private fun drain(endOfStream: Boolean) {
        var progress = android.os.SystemClock.elapsedRealtime()
        while (true) {
            poll(progress)
            val index = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                index >= 0 -> {
                    progress = android.os.SystemClock.elapsedRealtime()
                    val out = codec.getOutputBuffer(index)!!
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!config && info.size > 0 && track >= 0) muxer.writeSampleData(track, out, info)
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun poll(since: Long) {
        checkpoint()
        check(android.os.SystemClock.elapsedRealtime() - since < 10_000) { "AAC encoder stalled" }
    }

    override fun close() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { muxer.release() }
    }
}
