// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import android.app.Application
import android.content.ContextWrapper
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
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
class RemoteFilesModlandTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var server: HttpServer
    private lateinit var remote: RemoteFiles
    private lateinit var context: ContextWrapper
    private val responses = ConcurrentHashMap<String, Int>()
    private val requested = LinkedBlockingQueue<String>()
    private val agents = LinkedBlockingQueue<String>()
    @Volatile private var gatedPath: String? = null
    @Volatile private var transportFails = false
    private val payload = byteArrayOf(0, 1, 2, 3)
    private val oldYm = Modland.urlFor("YM/- unknown/conflict europe.ym")
    private val currentYm = Modland.urlFor("YM/_unknown/conflict europe.ym")

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            agents.add(exchange.requestHeaders.getFirst("User-Agent").orEmpty())
            val path = exchange.requestURI.path
            val status = responses[path] ?: 404
            val gate = gatedPath == path && exchange.requestHeaders.getFirst("Cookie") == null
            if (status != 200) {
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            } else {
                val body = if (gate) "Verifying your browser".toByteArray() else payload
                exchange.responseHeaders.set("Content-Type", if (gate) "text/html" else "application/octet-stream")
                if (gate) exchange.responseHeaders.set("Set-Cookie", "verified=yes; Path=/; HttpOnly")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        server.start()
        val cache = temporary.newFolder("cache")
        val files = temporary.newFolder("files")
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getCacheDir(): File = cache
            override fun getFilesDir(): File = files
        }
        remote = RemoteFiles(context, connect = { address ->
            requested.add(address)
            if (transportFails) throw IOException("Controlled connection failure")
            // Only the transport is redirected. Production sees real catalogue identities and
            // runs the same URL, status, gate and cache logic against a local HTTP server.
            URL("http://127.0.0.1:${server.address.port}${URL(address).file}")
                .openConnection() as HttpURLConnection
        })
    }

    @After fun stop() { server.stop(0) }

    private fun response(url: String, status: Int) { responses[URL(url).path.replace("%20", " ")] = status }

    private fun assertRequests(vararg urls: String) {
        assertEquals(urls.toList(), requested.toList())
        assertTrue(agents.all { it == CatalogueHttp.USER_AGENT })
    }

    @Test fun `legacy unknown author recovers on 404 and caches under the original identity`() = runBlocking {
        response(currentYm, 200)
        assertArrayEquals(payload, remote.fetch(oldYm))
        assertTrue(remote.isCached(oldYm))
        assertFalse(remote.isCached(currentYm))
        assertArrayEquals(payload, remote.fetch(oldYm))
        assertRequests(oldYm, currentYm)
    }

    @Test fun `nested Same Game SPC path retains spaces parentheses and game folder`() = runBlocking {
        val old = Modland.urlFor("Nintendo SPC/- unknown/Same Game/same game (22).spc")
        val current = Modland.urlFor("Nintendo SPC/_unknown/Same Game/same game (22).spc")
        responses["/pub/modules/Nintendo SPC/_unknown/Same Game/same game (22).spc"] = 200
        assertArrayEquals(payload, remote.fetch(old))
        assertRequests(old, current)
    }

    @Test fun `nested AdLib legacy folder recovers and survives downloader recreation`() = runBlocking {
        val old = Modland.urlFor("Ad Lib/Screamtracker 3 AdLib/- unknown/(opl2) totally nuts.s3m")
        val current = Modland.urlFor("Ad Lib/Screamtracker 3 AdLib/_unknown/(opl2) totally nuts.s3m")
        responses["/pub/modules/Ad Lib/Screamtracker 3 AdLib/_unknown/(opl2) totally nuts.s3m"] = 200
        assertArrayEquals(payload, remote.fetch(old))
        assertTrue(remote.isCached(old))
        assertFalse(remote.isCached(current))

        var attemptedNetwork = false
        val reopened = RemoteFiles(context, connect = {
            attemptedNetwork = true
            throw IOException("The saved original identity must work offline")
        })
        assertArrayEquals(payload, reopened.fetch(old))
        assertFalse(attemptedNetwork)
        assertRequests(old, current)
    }

    @Test fun `nested Sega legacy folder retains the game directory and original cache identity`() = runBlocking {
        val old = Modland.urlFor("Video Game Music/Sega Megadrive/- unknown/World Championship Soccer/06 ending theme.vgz")
        val current = Modland.urlFor("Video Game Music/Sega Megadrive/_unknown/World Championship Soccer/06 ending theme.vgz")
        responses["/pub/modules/Video Game Music/Sega Megadrive/_unknown/World Championship Soccer/06 ending theme.vgz"] = 200
        assertArrayEquals(payload, remote.fetch(old))
        assertTrue(remote.isCached(old))
        assertFalse(remote.isCached(current))
        assertArrayEquals(payload, remote.fetch(old))
        assertRequests(old, current)
    }

    @Test fun `successful original address is never replaced`() = runBlocking {
        response(oldYm, 200)
        response(currentYm, 200)
        assertArrayEquals(payload, remote.fetch(oldYm))
        assertRequests(oldYm)
    }

    @Test fun `only 404 allows the legacy recovery not access or server errors`() = runBlocking {
        response(currentYm, 200)
        for (status in listOf(401, 403, 410, 429, 500, 503)) {
            requested.clear()
            response(oldYm, status)
            assertNull(remote.fetch(oldYm))
            assertRequests(oldYm)
            assertFalse(remote.isCached(oldYm))
        }
    }

    @Test fun `a missing replacement is tried once and failures are not cached`() = runBlocking {
        repeat(2) { assertNull(remote.fetch(oldYm)) }
        assertRequests(oldYm, currentYm, oldYm, currentYm)
        assertFalse(remote.isCached(oldYm))
    }

    @Test fun `other hosts paths author names and query URLs are not rewritten`() = runBlocking {
        val urls = listOf(
            "https://example.invalid/pub/modules/YM/-%20unknown/conflict%20europe.ym",
            "https://modland.com.evil.invalid/pub/modules/YM/-%20unknown/conflict%20europe.ym",
            "https://modland.com/elsewhere/YM/-%20unknown/conflict%20europe.ym",
            Modland.urlFor("YM/Composer/- unknown.ym"),
            Modland.urlFor("YM/- unknown composer/conflict europe.ym"),
            "$oldYm?download=yes",
            "$oldYm#fragment",
            currentYm,
        )
        for (url in urls) {
            requested.clear()
            assertNull(remote.fetch(url))
            assertRequests(url)
        }
    }

    @Test fun `a transport failure is not mistaken for a missing archive path`() = runBlocking {
        transportFails = true
        assertNull(remote.fetch(oldYm))
        assertRequests(oldYm)
    }

    @Test fun `a recovered address keeps cookie retry and app identity`() = runBlocking {
        response(currentYm, 200)
        gatedPath = "/pub/modules/YM/_unknown/conflict europe.ym"
        assertArrayEquals(payload, remote.fetch(oldYm))
        assertRequests(oldYm, currentYm, currentYm)
    }
}
