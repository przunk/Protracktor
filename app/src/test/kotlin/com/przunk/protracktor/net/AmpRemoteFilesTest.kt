// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import org.junit.Assume.assumeTrue
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class AmpRemoteFilesTest {
    private val url = Amp.downloadUrl(49993)!!
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val payload = "Synthetic module".toByteArray()

    private class Reply(address: String, val bytes: ByteArray) : HttpURLConnection(URL(address)) {
        var status = 200
        var mime = "application/octet-stream"
        var beforeBody: () -> Unit = {}
        var onDisconnect: () -> Unit = {}
        override fun connect() = Unit
        override fun disconnect() { onDisconnect() }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = mime
        override fun getHeaderFields() = mapOf("Set-Cookie" to listOf("gate=1"))
        override fun getInputStream(): java.io.InputStream {
            beforeBody()
            return bytes.inputStream()
        }
    }

    @Before fun reset() { Amp.access.setEnabled(true); RemoteFiles(context).clearCache() }
    @After fun restore() { Amp.access.setEnabled(true) }

    @Test fun `compiled availability controls actual transfers and leaves other sources usable`() = runBlocking {
        var calls = 0
        val files = RemoteFiles(context) { calls++; Reply(it, payload) }
        Amp.access.setEnabled(true)
        assertEquals(Amp.ENABLED, files.fetch(url) != null)
        assertEquals(Amp.ENABLED, files.fetchIndex(Amp.indexUrl) != null)
        assertArrayEquals(payload, files.fetch("https://example.org/unrelated.mod"))
        assertEquals(if (Amp.ENABLED) 3 else 1, calls)
    }

    @Test fun `off blocks cached bytes and indexes but not an unrelated source`() = runBlocking {
        assumeTrue("an AMP-enabled build; listed in app/src/test/amp-enabled-only.txt", Amp.ENABLED)
        var calls = 0
        val files = RemoteFiles(context) { calls++; Reply(it, payload) }
        assertArrayEquals(payload, files.fetch(url))
        assertTrue(files.isCached(url))
        Amp.access.setEnabled(false)
        assertNull(files.fetch(url))
        assertNull(files.fetchIndex(Amp.indexUrl))
        assertEquals(1, calls)
        assertArrayEquals(payload, files.fetch("https://example.org/MOD.other"))
        assertEquals(2, calls)
        Amp.access.setEnabled(true)
        assertArrayEquals(payload, files.fetch(url))
        assertEquals(2, calls)
    }

    @Test fun `AMP protection HTML is not retried with a cookie or stored as music`() = runBlocking {
        assumeTrue("an AMP-enabled build; listed in app/src/test/amp-enabled-only.txt", Amp.ENABLED)
        var calls = 0
        val files = RemoteFiles(context) { address ->
            calls++
            Reply(address, "<html>Access denied</html>".toByteArray()).apply { mime = "text/html" }
        }
        assertNull(files.fetch(url))
        assertEquals(1, calls)
        assertFalse(files.isCached(url))
    }

    @Test fun `turning off disconnects a pending transfer and off-on cannot adopt its late bytes`() = runBlocking {
        assumeTrue("an AMP-enabled build; listed in app/src/test/amp-enabled-only.txt", Amp.ENABLED)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var disconnected = false
        val files = RemoteFiles(context) { address ->
            Reply(address, payload).apply {
                beforeBody = {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                onDisconnect = { disconnected = true; release.countDown() }
            }
        }
        val pending = async(kotlinx.coroutines.Dispatchers.IO) { files.fetch(url) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        Amp.access.setEnabled(false)
        Amp.access.setEnabled(true)
        assertNull(pending.await())
        assertTrue(disconnected)
        assertFalse(files.isCached(url))
    }

    @Test fun `AMP request uses the stable User-Agent and no Referer`() = runBlocking {
        assumeTrue("an AMP-enabled build; listed in app/src/test/amp-enabled-only.txt", Amp.ENABLED)
        lateinit var connection: Reply
        val files = RemoteFiles(context) { Reply(it, payload).also { reply -> connection = reply } }
        assertArrayEquals(payload, files.fetch(url))
        assertEquals(CatalogueHttp.USER_AGENT, connection.getRequestProperty("User-Agent"))
        assertNull(connection.getRequestProperty("Referer"))
        assertNull(connection.getRequestProperty("Cookie"))
    }

    @Test fun `AMP refusal is not retried and HTML with a misleading MIME type is not cached`() = runBlocking {
        assumeTrue("an AMP-enabled build; listed in app/src/test/amp-enabled-only.txt", Amp.ENABLED)
        for (status in listOf(403, 200)) {
            var calls = 0
            val files = RemoteFiles(context) { address ->
                calls++
                Reply(address, "<!doctype html>Refused".toByteArray()).apply { this.status = status }
            }
            assertNull(files.fetch(url))
            assertEquals(1, calls)
            assertFalse(files.isCached(url))
        }
    }
}
