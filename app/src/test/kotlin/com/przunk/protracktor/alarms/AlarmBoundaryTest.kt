// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AlarmBoundaryTest {
    @Test fun `ordinary application code reaches alarms only through its API`() {
        val root = File("src/main/kotlin/com/przunk/protracktor")
        assertTrue("Production sources must be available to the boundary check", root.isDirectory)
        val sources = root.walkTopDown().filter { it.extension == "kt" && !it.relativeTo(root).invariantSeparatorsPath.startsWith("alarms/") }.toList()
        assertTrue(sources.isNotEmpty())
        val implementation = Regex("\\bcom\\.przunk\\.protracktor\\.alarms\\.(?!api\\b)")
        val violations = sources.filter { implementation.containsMatchIn(it.readText()) }.map { it.relativeTo(root).path }
        assertEquals("Alarm implementation references cross the API boundary", emptyList<String>(), violations)
    }
    @Test fun `retained compatibility cleanup cannot link a removed component type`() {
        val source = File("src/main/kotlin/com/przunk/protracktor/alarms/AlarmFeatureLifecycle.kt")
        assertTrue(source.isFile)
        // Without a manifest entry, R8 may rename the type before Intent reads Class.getName.
        val omittedType = Regex("\\b(?:AlarmService|AlarmsActivity|AlarmRingingActivity)\\s*::\\s*class")
        assertFalse("Compatibility must use stable installed component identities", omittedType.containsMatchIn(source.readText()))
    }

}
