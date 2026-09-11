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

    /**
     * How many bytes of local files one message may carry.
     *
     * **Chiptunes are why this is possible at all.** Modland's median module is 20 KB and its mean
     * is 188 KB; a queue of local tracker files is kilobytes, not megabytes, so the phone can simply
     * hand them over. The budget exists for the file that is not typical -- the archive's largest is
     * 71 MB -- and what does not fit is reported rather than dropped.
     */
    const val LOCAL_BYTES_BUDGET = 8 * 1024 * 1024

    suspend fun send(
        endpoint: String,
        tracks: List<TrackRef>,
        index: Int,
        /** Bytes for tracks a browser cannot fetch, by track id. Absent ones are simply not sent. */
        localFiles: Map<String, ByteArray> = emptyMap(),
    ): Outcome =
        withContext(Dispatchers.IO) {
            val body = buildJson(tracks, index, localFiles)
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
    internal fun buildJson(
        tracks: List<TrackRef>,
        index: Int,
        localFiles: Map<String, ByteArray> = emptyMap(),
    ): String {
        val rows = tracks.joinToString(",") { track ->
            val bytes = localFiles[track.id]
            val data = if (bytes == null) "" else {
                // Base64 costs a third more than the bytes, which for a 20 KB module is seven
                // kilobytes and not worth a second channel to avoid.
                ""","data":"${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}""""
            }
            // **The filename travels beside the title, and they are not the same thing.** Four
            // backends choose a loader by extension, and a local file's *title* is often the name
            // with the extension taken off -- so "Tactic.sap" arrived as "Tactic", ASAP did not
            // claim it, and it fell through to game-music-emu which answered "wrong file type".
            // `TrackRef` has kept these apart since the beginning; the message did not.
            val file = track.fileNameOrTitle
            // **A row that could not bring its bytes says so.** Anything whose id is not an address
            // a browser could open -- a storage grant, an `asma://` or `unexotica://` reference --
            // and which did not fit the byte budget would otherwise arrive as a row that fails the
            // moment it is touched. Marked instead, and the page draws it greyed in its own place,
            // exactly as a link's `phone:` line arrives (`docs/BACKLOG.md` A28).
            // **And an MP3 whatever its address is.** `localBytesFor` never packs one, so this
            // would mark it anyway for a local file -- but the rule the owner gave is "always", and
            // a rule that happens to hold is not the same as one that is written down.
            // **The address a browser can fetch**, where the catalogue has one: ASMA's rows go as
            // their own files on asma.atari.org rather than as the `asma://` this phone reads.
            val url = Catalogue.owning(track.id)?.let { c -> c.pathFrom(track.id)?.let(c::fileUrlFor) }
                ?: track.id
            val stranded =
                if (com.przunk.protracktor.player.QueueLink.isMp3(track) ||
                    (bytes == null && !url.startsWith("http"))
                ) ""","local":true""" else ""
            """{"url":"${escape(url)}","title":"${escape(track.title)}",""" +
                """"file":"${escape(file)}"$stranded$data}"""
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
     * Whether a scanned code is one of ours, **and safe to send a playlist to**.
     *
     * A camera sees whatever is on the screen, and most QR codes in the world are not this, so the
     * shape is checked first: `/pair/` and thirty-two hex characters.
     *
     * **Then the address.** Android forbids cleartext by default and this app turns that off for
     * one reason — a browser on the same network has no certificate and no name one could be issued
     * for. The platform can only say yes or no to the whole app, so the narrowing happens here:
     * plain `http` is accepted only for loopback, the private ranges and link-local. A code
     * pointing at `http://example.com/pair/…` is refused, because a playlist posted there would
     * cross the internet in the clear to somebody else's machine.
     */
    fun looksLikePairing(text: String): Boolean {
        val trimmed = text.trim()
        if (!Regex("^https?://[^/]+/pair/[0-9a-f]{32}$").matches(trimmed)) return false
        if (trimmed.startsWith("https://")) return true
        val host = trimmed.removePrefix("http://").substringBefore('/').substringBefore(':')
        return isLocal(host)
    }

    /** Loopback, RFC 1918, link-local, or a `.local` name. Nothing that leaves the building. */
    internal fun isLocal(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".local")) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return when {
            octets[0] == 127 -> true
            octets[0] == 10 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 169 && octets[1] == 254 -> true
            else -> false
        }
    }
}
