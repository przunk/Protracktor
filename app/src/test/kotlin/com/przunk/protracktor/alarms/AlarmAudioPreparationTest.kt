// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import com.przunk.protracktor.player.PcmExport
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AlarmAudioPreparationTest {
    private class Capture : PcmExport.Sink {
        val samples = mutableListOf<Short>()
        var finished = false
        var closed = false
        override fun write(pcm: ShortArray, frames: Int) { samples.addAll(pcm.take(frames * 2)) }
        override fun finish() { finished = true }
        override fun close() { closed = true }
    }
    @Test fun `alarm trims leading zeros mixes both ears and caps output`() = runBlocking {
        val capture = Capture()
        var chunk = 0
        val frames = PcmExport.write(File("unused"), 44100, 4, mono = true, trimFrames = 8,
            sink = { _, _, _ -> capture }) { pcm ->
            for (i in pcm.indices) pcm[i] = if (chunk == 0 && i < 4) 0 else if (i % 2 == 0) 1000 else 3000
            chunk++; pcm.size / 2
        }
        assertEquals(4, frames)
        assertEquals(List(8) { 2000.toShort() }, capture.samples)
        assertTrue(capture.finished && capture.closed)
    }
    @Test fun `empty or silent data never becomes ready`() = runBlocking {
        for (limit in listOf(0, 4)) {
            val capture = Capture()
            val result = runCatching { PcmExport.write(File("unused"), 44100, 4,
                mono = true, trimFrames = limit.toLong(), sink = { _, _, _ -> capture }) { it.size / 2 } }
            assertTrue(result.isFailure)
            assertFalse(capture.finished)
            assertTrue(capture.closed)
        }
    }
    @Test fun `share keeps stereo samples and writes a shorter natural ending`() = runBlocking {
        val capture = Capture()
        val frames = PcmExport.write(File("unused"), 44100, 8, sink = { _, _, _ -> capture }) {
            it[0] = 100; it[1] = -300; 1
        }
        assertEquals(1, frames)
        assertEquals(listOf(100.toShort(), (-300).toShort()), capture.samples)
        assertTrue(capture.finished)
    }
    @Test fun `failed render releases writer and removes partial audio`() = runBlocking {
        val partial = File.createTempFile("alarm", ".partial")
        val capture = Capture()
        val result = runCatching { PcmExport.write(partial, 44100, 4,
            sink = { _, _, _ -> capture }) { -1 } }
        assertTrue(result.isFailure)
        assertFalse(partial.exists())
        assertTrue(capture.closed)
    }
}
