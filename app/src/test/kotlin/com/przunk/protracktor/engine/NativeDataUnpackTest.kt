// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** RV-B017: decoder data is marked unpacked only when every file was copied. */
class NativeDataUnpackTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `a copy that failed leaves no marker, so the next start tries again`() {
        val target = File(folder.root, "sc68")
        NativeData.unpack(target, "v2") { File(target, "Replay").mkdirs(); false }
        assertFalse(File(target, ".unpacked").exists())
    }

    @Test fun `a complete copy is marked with its version`() {
        val target = File(folder.root, "uade")
        NativeData.unpack(target, "v2") { true }
        assertEquals("v2", File(target, ".unpacked").readText())
    }

    @Test fun `what was there before is replaced, not added to`() {
        val target = File(folder.root, "sc68").apply { mkdirs() }
        File(target, "stale.bin").writeText("old")
        NativeData.unpack(target, "v3") { true }
        assertFalse(File(target, "stale.bin").exists())
    }
}
