// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.R
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "en-w360dp-h800dp-xhdpi")
class AlarmManagementActivityTest {
    private class Host : org.junit.rules.ExternalResource() {
        lateinit var activity: org.robolectric.android.controller.ActivityController<AlarmsActivity>
        lateinit var coordinator: AlarmCoordinator
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            AlarmNotifications.channel(context)
            coordinator = AlarmCoordinator.from(context)
            val song = AlarmSong("song", "a".repeat(64), "Morning", platformId = "amiga", durationMillis = 3000)
            coordinator.repository.add(song, File(context.cacheDir, "prepared").apply { writeBytes(byteArrayOf(1,2,3)) })
            coordinator.save(MusicAlarm("morning", 7, 30, songIds = listOf(song.id)))
            activity = Robolectric.buildActivity(AlarmsActivity::class.java).setup().visible()
        }
        override fun after() {
            activity.close()
            File(coordinator.repository.files.directory, "alarms-v1.json").writeText(AlarmCodec.encode(coordinator.repository.book))
        }
    }
    private val host = Host()
    @get:Rule val compose: androidx.compose.ui.test.junit4.ComposeContentTestRule =
        androidx.compose.ui.test.junit4.AndroidComposeTestRule(host) { host.activity.get() }
    @Test fun `failed durable Save keeps the actual editor draft and preserves newer data`() {
        val context = host.activity.get()
        compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithTag("alarm-card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("alarm-card").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.alarm_name)).performTextReplacement("Keep this draft")
        val ledger = File(host.coordinator.repository.files.directory, "alarms-v1.json")
        val future = "{\"version\":99,\"keep\":\"future data\"}".toByteArray()
        ledger.writeBytes(future)
        compose.onNodeWithText(context.getString(R.string.alarm_save)).performClick()
        compose.waitUntil(5000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(context.getString(R.string.alarm_failure)).fetchSemanticsNodes().isNotEmpty() ||
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast() == context.getString(R.string.alarm_failure)
        }
        compose.onNodeWithText("Keep this draft").assertExists()
        compose.onNodeWithText(context.getString(R.string.alarm_save)).assertExists()
        assertArrayEquals(future, ledger.readBytes())
        assertFalse(host.coordinator.repository.book.alarms.single().enabled)
        System.getProperty("protracktor.screenshots")?.let { directory ->
            val view = context.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(directory, "b57-failed-save-en.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test @Config(sdk = [31]) fun `Save acknowledges stored settings while exact access leaves the alarm unarmed`() {
        val context = host.activity.get()
        compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithTag("alarm-card").fetchSemanticsNodes().isNotEmpty() }
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val model = androidx.lifecycle.ViewModelProvider(context)[AlarmManagementViewModel::class.java]
        model.refresh()
        compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle(); model.state.value?.readiness?.exact == false }
        compose.onNodeWithTag("alarm-card").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.alarm_name)).performTextReplacement("Stored but unarmed")
        compose.onNodeWithText(context.getString(R.string.alarm_save)).performClick()
        compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(context.getString(R.string.alarm_saved_not_armed)).fetchSemanticsNodes().isNotEmpty() }
        val saved = host.coordinator.repository.files.read().alarms.single()
        assertTrue(saved.enabled)
        assertEquals("Stored but unarmed", saved.label)
        assertTrue(saved.nextAtMillis > 0)
        assertTrue(host.coordinator.scheduling.value is AlarmScheduleResult.Blocked)
        assertNull(shadowOf(context.getSystemService(android.app.AlarmManager::class.java)).peekNextScheduledAlarm())
    }

}
