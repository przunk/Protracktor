// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.przunk.protracktor.net.RemoteFiles
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class AndroidPlaylistFilesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val remote = RemoteFiles(context)
    private val files = AndroidPlaylistFiles(AndroidTrackFileAccess(context.contentResolver, remote), remote)

    @Test
    fun `playlist reads decode UTF-8 on IO and close the provider stream including on failure`() = runBlocking {
        val text = "#PLAYLIST:Żółć 🎵\none.mod\n"
        val uri = Uri.parse("content://playlist/documents/42")
        for (fail in listOf(false, true)) {
            var closed = false
            var thread: Thread? = null
            val stream = object : ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    thread = Thread.currentThread()
                    if (fail) throw IOException("synthetic provider error")
                    return super.read(buffer, offset, length)
                }
                override fun close() { closed = true; super.close() }
            }
            shadowOf(context.contentResolver).registerInputStream(uri, stream)
            val result = files.read(uri.toString())
            if (fail) assertNull(result) else assertEquals(text, result)
            assertTrue(closed)
            assertTrue(thread != null)
            assertNotSame(Thread.currentThread(), thread)
        }
    }

    @Test
    fun `document labels preserve the existing tree and document URI rules`() {
        assertEquals("Music", files.labelOf("content://com.android.externalstorage.documents/tree/primary%3AMusic"))
        assertEquals("primary:Download/Favourites.m3u8", files.labelOf("content://com.android.externalstorage.documents/document/primary%3ADownload%2FFavourites.m3u8"))
    }

    @Test
    fun `a shared copy uses the manifest FileProvider and the M3U send contract`() {
        val bytes = "#EXTM3U\none.mod\n".toByteArray()
        val shared = files.shareableCopy("transfer-test.m3u8", bytes)!!
        val uri = Uri.parse(shared)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.shares", uri.authority)
        assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        val intent = AndroidPlaylistFiles.shareIntent(shared, "Playlist subject")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("audio/x-mpegurl", intent.type)
        assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("Playlist subject", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
    }
}
