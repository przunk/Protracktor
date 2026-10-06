// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Application
import android.net.Uri
import com.przunk.protracktor.net.RemoteFiles
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, manifest = Config.NONE)
class AndroidTrackFileAccessTest {
    private val context = RuntimeEnvironment.getApplication()
    private val access = AndroidTrackFileAccess(context.contentResolver, RemoteFiles(context))
    private val payload = byteArrayOf(10, 20, 30)

    private class Stream(bytes: ByteArray, private val fail: Boolean = false) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (fail) throw IOException("provider read failed")
            return super.read(buffer, offset, length)
        }
        override fun close() {
            closed = true
            super.close()
        }
    }

    @Test
    fun `a document read closes its stream on both success and failure`() {
        val uri = Uri.parse("content://music/document/42")
        for (fail in listOf(false, true)) {
            val stream = Stream(payload, fail)
            shadowOf(context.contentResolver).registerInputStream(uri, stream)
            val result = access.readDocument(uri.toString())
            if (fail) assertNull(result) else assertArrayEquals(payload, result)
            assertTrue(stream.closed)
        }
    }

    @Test
    fun `a companion uses the same granted tree and closes its stream`() {
        val tree = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/"
        val main = tree + "primary%3AMusic%2FTFMX%2Fmdat.song"
        val companion = Uri.parse(tree + "primary%3AMusic%2FTFMX%2Fsmpl.song")
        val stream = Stream(payload)
        shadowOf(context.contentResolver).registerInputStream(companion, stream)
        val result = access.readDocumentCompanions(main)
        assertEquals("smpl.song", result.single().first)
        assertArrayEquals(payload, result.single().second)
        assertTrue(stream.closed)

        val failing = Stream(payload, fail = true)
        shadowOf(context.contentResolver).registerInputStream(companion, failing)
        assertTrue(access.readDocumentCompanions(main).isEmpty())
        assertTrue(failing.closed)
    }

    @Test
    fun `opaque or non-tree document identifiers have no guessed companion`() {
        assertTrue(access.readDocumentCompanions("content://music/tree/root/document/42").isEmpty())
        assertTrue(access.readDocumentCompanions("content://music/document/mdat.song").isEmpty())
    }

    @Test
    fun `URL filename decoding preserves plus signs while decoding spaces and unicode`() {
        assertEquals("smpl.a+b ż", access.decodeFileName("smpl.a+b%20%C5%BC"))
    }
}
