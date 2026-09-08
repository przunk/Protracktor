// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import android.util.Log
import com.przunk.protracktor.player.TrackRef
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sending the playlist to a browser that is showing a pairing code.
 *
 * **The page cannot be posted to; the server it listens to can.** A browser page has no address of
 * its own — it can open a connection but never accept one — so the code on screen carries the
 * address of the server serving that page, and this posts there. Today that is the small node
 * server in `scripts/serve-web.mjs`; the same three routes move to a Worker unchanged when the page
 * is hosted somewhere reachable from outside a LAN (`docs/PLAN_HANDOFF.md` §3).
 *
 * **The whole queue, not a stream.** This is the owner's first shape: scan, and the browser has the
 * list and the place in it. Keeping the connection open so the phone can drive playback is the next
 * one and uses this same channel.
 */
object WebRemote {

    private const val TAG = "WebRemote"

    /** What happened, in the three shapes the caller has to say something different about. */
    sealed interface Outcome {
        data class Delivered(val listeners: Int) : Outcome
        /** Reached, and nobody was listening: the page is closed, or the code is from an old one. */
        data object NoOneListening : Outcome
        data class Unreachable(val reason: String) : Outcome
    }

    suspend fun send(endpoint: String, tracks: List<TrackRef>, index: Int): Outcome =
        withContext(Dispatchers.IO) {
            val body = buildJson(tracks, index)
            try {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5_000
                    readTimeout = 10_000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                if (status !in 200..299) return@withContext Outcome.Unreachable("HTTP $status")
                // {"delivered":n} -- zero means the address was right and the page has gone.
                val delivered = Regex("\"delivered\"\\s*:\\s*(\\d+)").find(text)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (delivered > 0) Outcome.Delivered(delivered) else Outcome.NoOneListening
            } catch (e: Exception) {
                Log.w(TAG, "could not reach $endpoint", e)
                Outcome.Unreachable(e.message ?: e.javaClass.simpleName)
            }
        }

    /**
     * The message, written by hand.
     *
     * Four fields and no library: `org.json` would do, and adding a serialisation framework to send
     * one object would be the tail wagging the dog. The escaping is the part worth getting right —
     * Modland is full of quotes and backslashes in titles.
     */
    internal fun buildJson(tracks: List<TrackRef>, index: Int): String {
        val rows = tracks.joinToString(",") { track ->
            """{"url":"${escape(track.id)}","title":"${escape(track.title)}"}"""
        }
        return """{"queue":[$rows],"index":$index}"""
    }

    private fun escape(text: String): String = buildString {
        for (c in text) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }

    /**
     * Whether a scanned code is one of ours.
     *
     * A camera sees whatever is on the screen, and most QR codes in the world are not this. Checked
     * by shape rather than by host: the host is whatever machine is running the page.
     */
    fun looksLikePairing(text: String): Boolean =
        Regex("^https?://[^/]+/pair/[0-9a-f]{32}$").matches(text.trim())
}
