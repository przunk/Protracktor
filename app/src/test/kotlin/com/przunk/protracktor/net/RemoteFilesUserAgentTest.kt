// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import android.app.Application
import android.content.ContextWrapper
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class RemoteFilesUserAgentTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var server: HttpServer
    private lateinit var remote: RemoteFiles
    private val requests = LinkedBlockingQueue<Pair<String?, String?>>()
    private val payload = byteArrayOf(0, 1, 2, 3)
    private val expectedAgent = "Protracktor (Android; +https://github.com/przunk/protracktor)"

    @Before
    fun setUp() {
        val cache = temporary.newFolder("cache")
        val files = temporary.newFolder("files")
        remote = RemoteFiles(object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getCacheDir(): File = cache
            override fun getFilesDir(): File = files
        })
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val cookie = exchange.requestHeaders.getFirst("Cookie")
            requests.add(exchange.requestHeaders.getFirst("User-Agent") to cookie)
            val gate = exchange.requestURI.path == "/gate" && cookie == null
            val body = if (gate) "Verifying your browser".toByteArray() else payload
            when (exchange.requestURI.path) {
                "/redirect" -> {
                    exchange.responseHeaders.set("Location", "/archive")
                    exchange.sendResponseHeaders(302, -1)
                    exchange.close()
                }
                "/missing" -> {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                }
                else -> {
                    exchange.responseHeaders.set("Content-Type", if (gate) "text/html" else "application/octet-stream")
                    if (gate) exchange.responseHeaders.set("Set-Cookie", "verified=yes; Path=/; HttpOnly")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}/$path"

    private fun assertRequest(cookie: String? = null) {
        assertEquals(expectedAgent to cookie, requests.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun `archive download identifies the app and a cache hit sends no request`() = runBlocking {
        assertArrayEquals(payload, remote.fetch(url("archive")))
        assertRequest()
        assertArrayEquals(payload, remote.fetch(url("archive")))
        assertEquals(0, requests.size)
    }

    @Test
    fun `index refresh identifies the app on each uncached request`() = runBlocking {
        repeat(2) {
            assertArrayEquals(payload, remote.fetchIndex(url("index")))
            assertRequest()
        }
    }

    @Test
    fun `verification retry keeps the app identity and sends only the cookie pair`() = runBlocking {
        assertArrayEquals(payload, remote.fetch(url("gate")))
        assertRequest()
        assertRequest("verified=yes")
        assertEquals(0, requests.size)
    }

    @Test
    fun `redirect keeps the app identity`() = runBlocking {
        assertArrayEquals(payload, remote.fetch(url("redirect")))
        repeat(2) { assertRequest() }
    }

    @Test
    fun `failed download identifies the app without caching the error`() = runBlocking {
        repeat(2) {
            assertNull(remote.fetch(url("missing")))
            assertRequest()
        }
    }
}
