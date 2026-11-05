// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.viewModelScope
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
        override fun after() {
            activity.close()
            java.io.File(coordinator.repository.files.directory, "alarms-v1.json").writeText(AlarmCodec.encode(coordinator.repository.book))
        }
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
    @Test fun `a failed answer write cannot acknowledge success or dismiss the active challenge`() {
        val context = host.activity.get()
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithText("Amiga").fetchSemanticsNodes().isNotEmpty()
        }
        val ledger = java.io.File(host.coordinator.repository.files.directory, "alarms-v1.json")
        val future = "{\"version\":99,\"keep\":\"future data\"}".toByteArray()
        ledger.writeBytes(future)
        compose.onNodeWithText("Amiga").performClick()
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(context.getString(com.przunk.protracktor.R.string.alarm_failure)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Amiga").assertExists()
        compose.onAllNodesWithText(context.getString(com.przunk.protracktor.R.string.alarm_correct)).assertCountEquals(0)
        assertNotNull(host.coordinator.repository.book.ringing)
        assertFalse(host.activity.get().isFinishing)
        assertArrayEquals(future, ledger.readBytes())
    }

    @Test fun `a delayed old answer cannot acknowledge or close a replacement session`() {
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithText("Amiga").fetchSemanticsNodes().isNotEmpty()
        }
        val model = androidx.lifecycle.ViewModelProvider(host.activity.get())[AlarmRingingViewModel::class.java]
        val parent = checkNotNull(model.viewModelScope.coroutineContext[kotlinx.coroutines.Job])
        val baselineJobs = parent.children.count()
        val release = java.util.concurrent.CountDownLatch(1)
        val held = java.util.concurrent.CountDownLatch(1)
        val blocker = kotlin.concurrent.thread(name = "answer-commit-fixture", isDaemon = true) {
            synchronized(host.coordinator) { held.countDown(); check(release.await(10, java.util.concurrent.TimeUnit.SECONDS)) }
        }
        try {
            assertTrue(held.await(5, java.util.concurrent.TimeUnit.SECONDS))
            compose.onNodeWithText("Amiga").performClick()
            assertTrue(model.state.value.busy)
            compose.onNodeWithText("Amiga").assertIsNotEnabled()
            compose.onAllNodesWithText(host.activity.get().getString(com.przunk.protracktor.R.string.alarm_correct)).assertCountEquals(0)
            host.coordinator.repository.change { it.copy(ringing = checkNotNull(it.ringing).copy(token = "replacement")) }
            compose.waitUntil(5000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                model.state.value.active?.token == "replacement"
            }
            release.countDown(); blocker.join(5000)
            compose.waitUntil(5000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                parent.children.count() <= baselineJobs
            }
            assertEquals("replacement", model.state.value.active?.token)
            assertNull(model.state.value.completion)
            assertEquals("replacement", host.coordinator.repository.book.ringing?.token)
            assertFalse(host.activity.get().isFinishing)
        } finally { release.countDown(); blocker.join(5000) }
    }

}
