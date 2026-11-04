// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "en-w360dp-h800dp-xhdpi")
class AlarmRingingActivityTest {
    private class Host : org.junit.rules.ExternalResource() {
        lateinit var activity: org.robolectric.android.controller.ActivityController<AlarmRingingActivity>
        lateinit var coordinator: AlarmCoordinator
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            AlarmNotifications.channel(context)
            coordinator = AlarmCoordinator.from(context)
            val song = AlarmSong("song", "a".repeat(64), "Morning", platformId = "amiga", durationMillis = 3000)
            coordinator.repository.add(song, java.io.File(context.cacheDir, "prepared").apply { writeBytes(byteArrayOf(1, 2, 3)) })
            coordinator.preview("test", setOf(song.id))
            activity = Robolectric.buildActivity(AlarmRingingActivity::class.java).setup().visible()
        }
        override fun after() { activity.close() }
    }
    private val host = Host()
    @get:Rule val compose: androidx.compose.ui.test.junit4.ComposeContentTestRule = androidx.compose.ui.test.junit4.AndroidComposeTestRule(host) { host.activity.get() }
    @Test fun `correct choice persists the answer shows the acknowledgement and finishes the real activity`() {
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithText("Amiga").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Amiga").performClick()
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            host.coordinator.repository.book.ringing == null
        }
        assertNull(AlarmFileStore.from(host.activity.get()).read().ringing)
        compose.waitUntil(3000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            host.activity.get().isFinishing
        }
        assertTrue(host.activity.get().isFinishing)
    }
}
