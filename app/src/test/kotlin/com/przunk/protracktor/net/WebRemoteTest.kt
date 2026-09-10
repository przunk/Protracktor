// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import com.przunk.protracktor.player.TrackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRemoteTest {

    @Test
    fun `a queue becomes the message the page expects`() {
        val json = WebRemote.buildJson(
            listOf(
                TrackRef(id = "https://modland.com/x.mod", title = "hi", subtitle = ""),
                TrackRef(id = "asma://y.sap", title = "there", subtitle = ""),
            ),
            index = 1,
        )
        assertEquals(
            """{"queue":[{"url":"https://modland.com/x.mod","title":"hi","file":"hi"},""" +
                // **`asma://` means something on this phone and nothing anywhere else**, and no
                // bytes came with it — so it is marked, and the page draws it greyed in its own
                // place rather than as a row that fails the moment it is touched
                // (`docs/BACKLOG.md` A28). Modland's own URL needs no such mark: a browser can
                // fetch it itself.
                """{"url":"asma://y.sap","title":"there","file":"there","local":true}],"index":1}""",
            json,
        )
    }

    /** Modland is full of both, and a broken message would look like a broken connection. */
    /**
     * The owner's `Tactic.sap`, refused in the browser and playing on the phone.
     *
     * A local file's title is often its name with the extension taken off, and four backends choose
     * a loader by that extension. `TrackRef` has kept the two apart since the beginning; the message
     * to the browser did not.
     */
    @Test
    fun `the filename travels beside the title`() {
        val json = WebRemote.buildJson(
            listOf(
                TrackRef(
                    id = "content://com.android.externalstorage.documents/document/x",
                    title = "Tactic",
                    subtitle = "",
                    fileName = "Tactic.sap",
                )
            ),
            index = 0,
        )
        assertTrue(json, json.contains(""""title":"Tactic","file":"Tactic.sap""""))
    }

    @Test
    fun `quotes and backslashes in a title do not break the message`() {
        val json = WebRemote.buildJson(
            listOf(TrackRef(id = "u", title = """he said "no" \ then left""", subtitle = "")),
            index = 0,
        )
        assertTrue(json, json.contains("""he said \"no\" \\ then left"""))
    }

    /**
     * The check that stands in for a platform setting that cannot express it.
     *
     * Cleartext is permitted app-wide because a browser on the LAN has no certificate; the
     * narrowing to addresses that do not leave the building happens here.
     */
    @Test
    fun `a cleartext code is only accepted for an address on this network`() {
        val room = "/pair/" + "0123456789abcdef".repeat(2)
        assertTrue(WebRemote.looksLikePairing("http://192.168.50.206:8173$room"))
        assertTrue(WebRemote.looksLikePairing("http://10.0.0.5:8173$room"))
        assertTrue(WebRemote.looksLikePairing("http://172.20.1.1:8173$room"))
        assertTrue(WebRemote.looksLikePairing("http://localhost:8173$room"))
        assertTrue(WebRemote.looksLikePairing("http://desktop.local:8173$room"))
        // Reachable, ours in shape, and a playlist posted there crosses the internet in the clear.
        assertFalse(WebRemote.looksLikePairing("http://example.com:8173$room"))
        assertFalse(WebRemote.looksLikePairing("http://172.32.0.1:8173$room"))
        assertFalse(WebRemote.looksLikePairing("http://8.8.8.8$room"))
        // Over TLS the host does not matter.
        assertTrue(WebRemote.looksLikePairing("https://player.example$room"))
    }

    @Test
    fun `only our own codes are accepted`() {
        assertTrue(WebRemote.looksLikePairing("http://192.168.50.206:8173/pair/" + "a".repeat(32)))
        assertTrue(WebRemote.looksLikePairing("https://player.example/pair/" + "0123456789abcdef".repeat(2)))
        assertFalse(WebRemote.looksLikePairing("https://example.com/"))
        assertFalse(WebRemote.looksLikePairing("http://192.168.50.206:8173/pair/short"))
        assertFalse(WebRemote.looksLikePairing("WIFI:S:home;T:WPA;P:hunter2;;"))
    }
}
