// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.screenshot

import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.alarms.*
import com.przunk.protracktor.data.SavedPlaylist
import com.przunk.protracktor.player.TrackRef
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** B62: the alarm list, editor, Add music and Music tab, to look at before an APK goes out. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "en-w360dp-h800dp-xhdpi")
class AlarmUxScreensTest {
    private val lotus = AlarmSong("lotus", "c".repeat(64), "Lotus 3 · Space ninja", "Patrick Phelan", "amiga", durationMillis = 60_000, year = "1992")
    private val battle = AlarmSong("battle", "a".repeat(64), "Battle of honour", "Treks", "pc", durationMillis = 60_000, year = "1995")
    private val refs = listOf(
        TrackRef("content://m/1", "Lotus 3 · Space ninja", author = "Patrick Phelan"),
        TrackRef("content://m/2", "Enigma", author = "Phenomena"),
        TrackRef("content://m/3", "Hybris", author = "Mr. Man"),
        TrackRef("content://m/4", "Stardust Memories", author = "Jester"),
    )
    private val sources = object : AlarmMusicSources {
        override suspend fun playlists() = listOf(SavedPlaylist(1, "Wake-up", trackCount = 4))
        override suspend fun tracksIn(playlist: Long) = refs.map(AlarmCandidates::of)
        override suspend fun history() = refs.map(AlarmCandidates::of)
        override suspend fun search(query: String) = emptyList<AlarmCandidate>()
    }
    private fun pictures(locale: String) {
        android.provider.Settings.Global.putFloat(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val next = java.time.Instant.now().plusSeconds(9 * 3600 + 12 * 60).toEpochMilli()
        val book = AlarmBook(songs = listOf(lotus, battle), alarms = listOf(
            MusicAlarm("a", 7, 30, "Morning music", setOf(1, 2, 3, 4, 5), listOf(lotus.id, battle.id), true, next),
            MusicAlarm("b", 9, 0, "", setOf(6, 7), listOf(battle.id), false)))
        val picks = listOf(AlarmPick(refs[0], AlarmPickState.Added("x")), AlarmPick(refs[1], AlarmPickState.Preparing),
            AlarmPick(refs[2], AlarmPickState.Waiting))
        for (dark in listOf(true, false)) {
            val theme = if (dark) AppTheme.DARK else AppTheme.LIGHT
            val suffix = "$locale-${if (dark) "dark" else "light"}"
            render(720, 1600, atMillis = 400) { ProtracktorTheme(theme, false) {
                AlarmManagement(book, next, false, true, true, true, false, usable = { true }, onBack = {},
                    onSave = { AlarmSaveResult.Saved(AlarmScheduleResult.Idle) }, onDelete = { true }, onRemoveMusic = {}, onTest = {}, onAccess = {},
                    sources = sources)
            } }.saveAs("b62-list-$suffix")
            render(720, 1600, atMillis = 400) { ProtracktorTheme(theme, false) {
                AlarmEditor(MusicAlarm("n", 7, 30), listOf(lotus, battle), { true }, {}, onSave = { AlarmSaveResult.Saved(AlarmScheduleResult.Idle) },
                    isNew = true, onAddMusic = {})
            } }.saveAs("b62-editor-new-$suffix")
            render(720, 1600, atMillis = 400) { ProtracktorTheme(theme, false) {
                AlarmMusicPicker(sources, picks, {}, {})
            } }.saveAs("b62-picker-$suffix")
        }
    }
    @Test fun english() = pictures("en")
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi") fun polish() = pictures("pl")
}
