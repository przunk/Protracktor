// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.screenshot

import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.alarms.*
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "en-w360dp-h800dp-xhdpi")
class AlarmScreensTest {
    private val song = AlarmSong("song", "a".repeat(64), "Battle of honour", "Treks", "pc", durationMillis = 60_000, year = "1995")
    private val quiz = AlarmQuiz(AlarmQuestion.AUTHOR, listOf(AlarmChoice("a", "Jakim"), AlarmChoice("b", "Treks"), AlarmChoice("c", "Space ninja / a deliberately long alternative")), "b")
    private fun pictures(locale: String) {
        android.provider.Settings.Global.putFloat(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        for (dark in listOf(true, false)) {
            val theme = if (dark) AppTheme.DARK else AppTheme.LIGHT
            val suffix = "$locale-${if (dark) "dark" else "light"}"
            render(720, 1600, atMillis = 128) { ProtracktorTheme(theme, false) {
                AlarmRingingScreen(RingingAlarm("token", setOf("alarm"), song, quiz, 0, 600000, 1), false, false, { _, _ -> }, {}, {})
            } }.saveAs("b57-r3-ringing-$suffix")
            render(720, 1600, atMillis = 128) { ProtracktorTheme(theme, false) {
                AlarmManagement(AlarmBook(songs = listOf(song), alarms = listOf(MusicAlarm("alarm", 7, 30, "Morning music", setOf(1,2,3,4,5), listOf(song.id), true, 1793773800000))),
                    1793773800000, false, true, true, true, false, usable = { true }, onBack = {}, onSave = {}, onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {})
            } }.saveAs("b57-r3-management-$suffix")
            val selected = song.copy(id = "lotus", sourceKey = "c".repeat(64), title = "Lotus 3 · Space ninja", author = "Patrick Phelan", platformId = "amiga", year = "1992")
            val music = listOf(selected, song, song.copy(id = "other", sourceKey = "b".repeat(64), title = "Studium Falszu", author = "Jakim", year = "1994"))
            val alarm = MusicAlarm("alarm", 7, 30, "Morning music", setOf(1,2,3,4,5), listOf(selected.id), true)
            render(720, 1600, atMillis = 128) { ProtracktorTheme(theme, false) {
                AlarmEditor(alarm, music, { true }, {}, {})
            } }.saveAs("b57-r3-editor-$suffix")
            render(720, 1600, atMillis = 128, gesture = { view ->
                val start = android.os.SystemClock.uptimeMillis()
                fun touch(action: Int, offset: Int, y: Float) {
                    val event = android.view.MotionEvent.obtain(start, start + offset, action, 350f, y, 0)
                    view.dispatchTouchEvent(event); event.recycle()
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
                }
                touch(android.view.MotionEvent.ACTION_DOWN, 0, 1400f)
                for (i in 1..18) touch(android.view.MotionEvent.ACTION_MOVE, i * 16, 1400f - i * 50)
                touch(android.view.MotionEvent.ACTION_UP, 320, 500f)
            }) { ProtracktorTheme(theme, false) {
                AlarmEditor(alarm, music, { true }, {}, {})
            } }.saveAs("b57-r3-editor-music-$suffix")
        }
    }
    @Test fun english() = pictures("en")
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi") fun polish() = pictures("pl")
    private fun upcomingPictures(locale: String, theme: AppTheme) {
        val date = java.time.Instant.now().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        for (offset in listOf(0L, 1L, 5L)) {
            val at = date.plusDays(offset).atTime(7, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val book = AlarmBook(songs = listOf(song), alarms = listOf(MusicAlarm("alarm", 7, 30,
                songIds = listOf(song.id), enabled = true, nextAtMillis = at)))
            render(720, 1600, atMillis = 128) { ProtracktorTheme(theme, false) {
                AlarmManagement(book, at, false, true, true, true, false, usable = { true }, onBack = {},
                    onSave = {}, onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {})
            } }.saveAs("b57-r4-time-$locale-$offset")
        }
    }
    @Test fun upcomingEnglish() = upcomingPictures("en", AppTheme.DARK)
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi") fun upcomingPolish() = upcomingPictures("pl", AppTheme.LIGHT)

}
