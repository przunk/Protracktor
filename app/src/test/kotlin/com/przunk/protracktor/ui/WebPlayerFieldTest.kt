// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** RV-B040: the web player's address can be emptied, and a pairing still shows its address. */
class WebPlayerFieldTest {
    private val fallback = "http://localhost:8173/src/"

    @Test fun `deleting the last character leaves the field empty, though the stored value reads as the fallback`() {
        assertEquals("", WebPlayerField.shown("", fallback))
    }

    @Test fun `what is being typed stays`() {
        assertEquals("http://192.168", WebPlayerField.shown("http://192.168", "http://192.168"))
        assertEquals("http://host/ ", WebPlayerField.shown("http://host/ ", "http://host/"))
    }

    @Test fun `an address from a pairing replaces an old one`() {
        assertEquals("http://192.168.1.5:8173/src/", WebPlayerField.shown("http://old/", "http://192.168.1.5:8173/src/"))
    }
}
