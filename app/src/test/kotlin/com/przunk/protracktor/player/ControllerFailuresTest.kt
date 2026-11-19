// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import android.database.sqlite.SQLiteFullException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/** RV-B014: a failure in the controller's work is reported, and the work after it still runs. */
@RunWith(AndroidJUnit4::class)
class ControllerFailuresTest {
    @Test fun `a failing block is reported and the scope carries on`() {
        val reported = mutableListOf<Exception>()
        val scope = ControllerFailures.scope { reported += it }
        scope.launch { throw SQLiteFullException("database or disk is full") }
        scope.launch { withContext(Dispatchers.IO) { throw IllegalStateException("off the main thread") } }
        ShadowLooper.idleMainLooper()
        repeat(50) { if (reported.size < 2) { Thread.sleep(10); ShadowLooper.idleMainLooper() } }
        assertEquals(listOf("SQLiteFullException", "IllegalStateException"), reported.map { it.javaClass.simpleName }.sorted().reversed())
        assertTrue(scope.isActive)
        var ran = false
        scope.launch { ran = true }
        ShadowLooper.idleMainLooper()
        assertTrue(ran)
    }

    @Test fun `storage failures are told apart`() {
        assertTrue(ControllerFailures.isStorage(SQLiteFullException()))
        assertTrue(ControllerFailures.isStorage(java.io.IOException("ENOSPC")))
        assertFalse(ControllerFailures.isStorage(IllegalStateException()))
    }
}
