// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.MainActivity
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.Appearance
import androidx.core.view.WindowCompat
import com.przunk.protracktor.R
import com.przunk.protracktor.data.DatabaseAccess
import com.przunk.protracktor.data.DatabaseAccessState
import com.przunk.protracktor.data.ProtracktorDatabase
import com.przunk.protracktor.data.SchemaSql
import com.przunk.protracktor.player.PlaybackPresentation
import com.przunk.protracktor.player.PlaybackService
import com.przunk.protracktor.player.ScanNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], qualifiers = "en-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DatabaseAccessEntryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private var activity: ActivityController<out ComponentActivity>? = null

    private fun resetHelper() {
        ProtracktorDatabase.of(context).close()
        ProtracktorDatabase::class.java.getDeclaredField("instance").apply {
            isAccessible = true; set(null, null)
        }
    }

    @Before fun seedNewer() {
        resetHelper()
        context.deleteDatabase(SchemaSql.NAME)
        val file = context.getDatabasePath(SchemaSql.NAME)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            SchemaSql.CREATE.forEach(it::execSQL)
            it.version = SchemaSql.VERSION + 1
        }
    }

    @After fun stop() {
        activity?.let { it.get().setContent { }; compose.waitForIdle(); it.close() }
        resetHelper()
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun systemBars(theme: AppTheme, lightAppearance: Boolean) {
        Appearance.selectTheme(context, theme)
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        awaitText(context.getString(R.string.database_unavailable_title))
        val window = activity!!.get().window
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        assertEquals("Status-bar clock/icons follow the app's effective theme", lightAppearance,
            insets.isAppearanceLightStatusBars)
        assertEquals("Navigation icons follow the same app theme", lightAppearance,
            insets.isAppearanceLightNavigationBars)
    }

    @Test @Config(sdk = [29, 30], qualifiers = "en-w360dp-h800dp-night-xhdpi")
    fun `system bars use Light despite system Dark`() = systemBars(AppTheme.LIGHT, true)

    @Test @Config(sdk = [29, 30], qualifiers = "en-w360dp-h800dp-notnight-xhdpi")
    fun `system bars use Dark despite system Light`() = systemBars(AppTheme.DARK, false)

    @Test @Config(sdk = [29, 30], qualifiers = "en-w360dp-h800dp-night-xhdpi")
    fun `system bars follow System at night`() = systemBars(AppTheme.SYSTEM, false)

    @Test @Config(sdk = [29, 30], qualifiers = "en-w360dp-h800dp-notnight-xhdpi")
    fun `system bars follow System in daytime`() = systemBars(AppTheme.SYSTEM, true)

    @Test fun `real activity blocks external open before constructing the player`() {
        activity = Robolectric.buildActivity(MainActivity::class.java,
            Intent(Intent.ACTION_VIEW, Uri.parse("content://test/keep.mod"))).setup().visible()
        awaitText(context.getString(R.string.database_unavailable_title))
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        assertTrue(activity!!.get().isFinishing)
        assertEquals(DatabaseAccessState.Newer(24, 23), DatabaseAccess.check(context))
    }

    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `real activity explains a newer schema in Polish`() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        awaitText("Baza danych niedostępna")
        compose.onNodeWithText("Spróbuj ponownie").assertIsDisplayed()
        compose.onNodeWithText("Zamknij").assertIsDisplayed()
    }

    @Test fun `pending check does not compose content and ready composes it once`() {
        val checked = CompletableDeferred<DatabaseAccessState>()
        var mounted = 0
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity!!.get().setContent {
            MaterialTheme {
                DatabaseGate(check = { checked.await() }, onClose = {}) {
                    androidx.compose.runtime.LaunchedEffect(Unit) { mounted++ }
                    Text("Ready content")
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.database_checking)).assertIsDisplayed()
        compose.onNodeWithText("Ready content").assertDoesNotExist()
        assertEquals(0, mounted)
        compose.runOnIdle { checked.complete(DatabaseAccessState.Ready) }
        awaitText("Ready content")
        assertEquals(1, mounted)
    }

    @Test fun `actual retry rechecks without resetting then allows a compatible file`() {
        var reads = 0
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity!!.get().setContent {
            MaterialTheme {
                DatabaseGate(check = {
                    reads++
                    withContext(Dispatchers.IO) { DatabaseAccess.check(context) }
                }, onClose = {}) { Text("Ready content") }
            }
        }
        awaitText(context.getString(R.string.database_unavailable_title))
        compose.onNodeWithText("Retry").performClick()
        awaitText(context.getString(R.string.database_unavailable_title))
        assertEquals(2, reads)
        compose.onNodeWithText("Ready content").assertDoesNotExist()
        assertEquals(DatabaseAccessState.Newer(24, 23), DatabaseAccess.check(context))
        // Synthetic fixture only: its shape is schema 23, with a deliberately higher user_version.
        SQLiteDatabase.openDatabase(context.getDatabasePath(SchemaSql.NAME).absolutePath, null,
            SQLiteDatabase.OPEN_READWRITE).use { it.version = SchemaSql.VERSION }
        compose.onNodeWithText("Retry").performClick()
        awaitText("Ready content")
        assertEquals(3, reads)
    }

    @Test fun `unreadable check has actionable feedback rather than a blank player`() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        var closed = false
        activity!!.get().setContent {
            MaterialTheme {
                DatabaseGate(check = { DatabaseAccessState.Unreadable }, onClose = { closed = true }) {
                    Text("Ready content")
                }
            }
        }
        awaitText(context.getString(R.string.database_unreadable_detail))
        compose.onNodeWithText("Ready content").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        assertTrue(closed)
    }

    @Test fun `cold transport service refuses newer data and supplies an explanation link`() {
        val service = Robolectric.buildService(PlaybackService::class.java,
            Intent(context, PlaybackService::class.java).setAction(PlaybackPresentation.ACTION_NEXT))
            .create().startCommand(0, 1)
        assertTrue(Shadows.shadowOf(service.get()).isStoppedBySelf)
        val manager = context.getSystemService(NotificationManager::class.java)
        val notice = Shadows.shadowOf(manager).allNotifications.single()
        assertEquals(context.getString(R.string.database_unavailable_title), notice.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notice.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("24"))
        assertNotNull(notice.contentIntent)
        assertTrue(notice.actions.isNullOrEmpty())
        service.destroy()
        assertEquals(DatabaseAccessState.Newer(24, 23), DatabaseAccess.check(context))
    }

    @Test fun `stale scan notification Stop does not initialize a player`() {
        ScanNotification.show(context, "Folder", 1, 2)
        val manager = context.getSystemService(NotificationManager::class.java)
        Shadows.shadowOf(manager).allNotifications.single().actions.single().actionIntent.send()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(Shadows.shadowOf(manager).allNotifications.isEmpty())
        assertEquals(DatabaseAccessState.Newer(24, 23), DatabaseAccess.check(context))
    }
}
