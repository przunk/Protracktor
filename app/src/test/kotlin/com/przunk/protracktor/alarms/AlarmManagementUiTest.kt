// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.przunk.protracktor.R
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
class AlarmManagementUiTest {
    private class Host : org.junit.rules.ExternalResource() {
        lateinit var controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
        override fun before() { controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
        override fun after() { controller.close() }
    }
    private val hostRule = Host()
    private val host get() = hostRule.controller
    @get:Rule val compose: androidx.compose.ui.test.junit4.ComposeContentTestRule =
        androidx.compose.ui.test.junit4.AndroidComposeTestRule(hostRule) { hostRule.controller.get() }
    private val song = AlarmSong("song", "a".repeat(64), "Morning tune", "Artist", "amiga", durationMillis = 3000)
    private val quiz = AlarmQuiz(AlarmQuestion.PLATFORM, listOf(AlarmChoice("a", "Amiga"), AlarmChoice("b", "C64"), AlarmChoice("c", "Atari ST")), "a")
    private val active = RingingAlarm("token", setOf("alarm"), song, quiz, 0, 600000, 1)
    private fun text(id: Int) = host.get().getString(id)
    private fun layout() = compose.runOnUiThread {
        val views = listOfNotNull(host.get().window.decorView,
            org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)
        views.forEach { view ->
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 720, 1600)
        }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
    }
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MaterialTheme { content() } }
        layout()
        compose.waitForIdle()
    }
    private fun frame() {
        layout()
        compose.mainClock.advanceTimeBy(100)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
        layout()
        compose.waitForIdle()
    }

    @Test fun `Back occupies the same rectangle in management and editor`() {
        show { AlarmManagement(AlarmBook(songs = listOf(song)), null, false, true, true, true, false,
            usable = { true }, onBack = {}, onSave = {}, onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {}) }
        val before = compose.onNodeWithText(text(R.string.alarm_back)).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(text(R.string.alarm_new)).performClick()
        frame()
        val after = compose.onNodeWithText(text(R.string.alarm_back)).fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
    }

    @Test fun `delete offers Undo and restores the same alarm settings`() {
        val alarm = MusicAlarm("morning", 7, 30, "Morning music", setOf(1, 2, 3), listOf(song.id), true)
        val book = mutableStateOf(AlarmBook(songs = listOf(song), alarms = listOf(alarm)))
        val restored = mutableListOf<MusicAlarm>()
        show { AlarmManagement(book.value, 1793773800000, false, true, true, true, false,
            usable = { true }, onBack = {}, onSave = {}, onDelete = { id ->
                book.value = book.value.copy(alarms = book.value.alarms.filterNot { it.id == id }); true
            }, onRemoveMusic = {}, onTest = {}, onAccess = {}, onRestore = {
                restored += it; book.value = book.value.copy(alarms = book.value.alarms + it)
            }) }
        compose.onNodeWithText(text(R.string.alarm_delete)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.alarm_deleted)).assertExists()
        assertTrue(book.value.alarms.isEmpty())
        compose.onNodeWithText(text(R.string.action_undo)).performClick()
        assertEquals(listOf(alarm), restored)
        assertEquals(listOf(alarm), book.value.alarms)
    }
    @Test fun `failed deletion keeps the row and does not offer a false Undo`() {
        val alarm = MusicAlarm("morning", 7, 30, songIds = listOf(song.id), enabled = true)
        show { AlarmManagement(AlarmBook(songs = listOf(song), alarms = listOf(alarm)), 1793773800000,
            false, true, true, true, false, usable = { true }, onBack = {}, onSave = {},
            onDelete = { false }, onRemoveMusic = {}, onTest = {}, onAccess = {}) }
        compose.onNodeWithText(text(R.string.alarm_delete)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.alarm_deleted)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_undo)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.alarm_edit)).assertExists()
    }

    @Test fun `weekday captions remain on one line and selection is saved`() {
        val saved = mutableListOf<MusicAlarm>()
        show { AlarmManagement(AlarmBook(songs = listOf(song)), null, false, true, true, true, false,
            usable = { true }, onBack = {}, onSave = saved::add, onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {}) }
        compose.onNodeWithText(text(R.string.alarm_new)).performClick()
        frame()
        val monday = compose.onNodeWithContentDescription("Monday").performScrollTo()
        monday.performClick()
        frame()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNode(hasText("Mon") and hasAnyAncestor(hasContentDescription("Monday")), useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        val caption = layouts.single()
        assertTrue("Weekday text must fit its actual drawing width", caption.getLineRight(0) <= caption.size.width)
        assertFalse(caption.isLineEllipsized(0))
        compose.onNodeWithText(text(R.string.alarm_save)).performClick()
        assertEquals(setOf(1), saved.single().weekdays)
    }
    @Test fun `revoked full screen access hides stale armed summaries immediately`() {
        val alarm = MusicAlarm("morning", 7, 30, songIds = listOf(song.id), enabled = true, nextAtMillis = 1793773800000)
        show { AlarmManagement(AlarmBook(songs = listOf(song), alarms = listOf(alarm)), alarm.nextAtMillis,
            false, true, true, false, false, usable = { true }, onBack = {}, onSave = {},
            onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {}) }
        compose.onNodeWithText(text(R.string.alarm_not_armed), substring = true).performScrollTo().assertExists()
        compose.onAllNodesWithText("Next alarm ·", substring = true).assertCountEquals(0)
    }

    @Test fun `editing a disabled alarm enables it when Save is pressed`() {
        val alarm = MusicAlarm("morning", 7, 30, songIds = listOf(song.id), enabled = false)
        val saved = mutableListOf<MusicAlarm>()
        show { AlarmManagement(AlarmBook(songs = listOf(song), alarms = listOf(alarm)), null,
            false, true, true, true, false, usable = { true }, onBack = {}, onSave = saved::add,
            onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {}) }
        compose.onNodeWithText(text(R.string.alarm_edit)).performScrollTo().performClick()
        frame()
        compose.onNodeWithText(text(R.string.alarm_name)).performTextReplacement("A new name")
        compose.onNodeWithText(text(R.string.alarm_save)).performClick()
        assertTrue(saved.single().enabled)
        assertEquals("A new name", saved.single().label)
    }
    @Test fun `Info opens general guidance without cluttering the alarm list`() {
        show { AlarmManagement(AlarmBook(songs = listOf(song)), null, false, true, true, true, false,
            usable = { true }, onBack = {}, onSave = {}, onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {}) }
        compose.onNodeWithText(text(R.string.alarm_disclaimer)).assertDoesNotExist()
        compose.onNodeWithText("Info").performClick()
        frame()
        compose.onNodeWithText(text(R.string.alarm_disclaimer), substring = true).assertExists()
        compose.onNodeWithText(text(R.string.action_close)).performClick()
        compose.onNodeWithText(text(R.string.alarm_disclaimer), substring = true).assertDoesNotExist()
    }

    @Test @Config(sdk = [30]) fun `editor keeps the header and name visible with keyboard insets`() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(host.get().window, false)
        var observedIme = 0
        var composeView: android.view.View? = null
        show {
            val view = androidx.compose.ui.platform.LocalView.current
            val bottom = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
            androidx.compose.runtime.SideEffect { observedIme = bottom; composeView = view }
            AlarmEditor(MusicAlarm("morning", 7, 30, songIds = listOf(song.id)), listOf(song), { true }, {}, {})
        }
        val before = compose.onNodeWithText(text(R.string.alarm_back)).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(text(R.string.alarm_name)).performClick()
        compose.runOnUiThread {
            val insets = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, 760))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), true).build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(checkNotNull(composeView), insets)
        }
        frame()
        assertEquals("The fixture must deliver the keyboard inset to Compose", 760, observedIme)
        compose.onNodeWithText(text(R.string.alarm_back)).assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText(text(R.string.alarm_back)).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText(text(R.string.alarm_name)).assertIsDisplayed()
        System.getProperty("protracktor.screenshots")?.let { directory ->
            val view = host.get().window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            java.io.File(directory, "b57-r3-editor-ime-en.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

}
