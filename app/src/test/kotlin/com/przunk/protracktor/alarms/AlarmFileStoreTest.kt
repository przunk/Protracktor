// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.alarms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.random.Random
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class AlarmFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun song() = AlarmSong("song", "a".repeat(64), "Tune", "Jakim", "amiga", "modland", 1000)
    private fun book(): AlarmBook {
        val song = song()
        val quiz = AlarmQuizFactory(listOf("amiga" to "Amiga", "pc" to "PC", "c64" to "C64")).create(song, listOf(song), Random(17))!!
        return AlarmBook(7, listOf(song), listOf(MusicAlarm("alarm", 7, 30, "Morning", setOf(1, 5), listOf(song.id), true, 1000, "2026-11-03")),
            RingingAlarm("session", setOf("alarm"), song, quiz, 100, 600_100, 3),
            AlarmOutcome(setOf("older"), 100, AlarmOutcomeKind.ANSWERED))
    }

    @Test fun `production factory stores alarm data in device protected no backup storage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(File(context.createDeviceProtectedStorageContext().noBackupFilesDir, "music-alarms"), AlarmFileStore.from(context).directory)
        assertSame(AlarmFileStore.from(context), AlarmFileStore.from(context.createDeviceProtectedStorageContext()))
    }

    @Test fun `real atomic file round trip retains schedules metadata choices and occurrence state`() {
        val store = AlarmFileStore(temporary.newFolder())
        assertEquals(AlarmBook(), store.read())
        store.write(book())
        assertEquals(book(), AlarmFileStore(store.directory).read())
        store.write(book().copy(revision = 8, ringing = null))
        assertEquals(8L, store.read().revision)
        assertNull(store.read().ringing)
    }

    @Test fun `backup state persists without inventing a recognition question`() {
        val store = AlarmFileStore(temporary.newFolder())
        val backup = book().copy(ringing = book().ringing!!.copy(backup = true, quiz = null))
        store.write(backup)
        assertTrue(store.read().ringing!!.backup)
        assertNull(store.read().ringing!!.quiz)
    }

    @Test fun `unknown newer format is refused without clearing or rewriting its bytes`() {
        val dir = temporary.newFolder()
        val file = File(dir, "alarms-v1.json").apply { writeText("{\"version\":99,\"keep\":\"future\"}") }
        val bytes = file.readBytes()
        try { AlarmFileStore(dir).read(); fail("newer format accepted") } catch (e: AlarmDataVersionException) { assertEquals(99, e.version) }
        assertThrows(AlarmDataVersionException::class.java) { AlarmFileStore(dir).write(book()) }
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun `malformed storage and traversal IDs cannot become a reset or an audio path`() {
        val dir = temporary.newFolder()
        val store = AlarmFileStore(dir)
        val file = File(dir, "alarms-v1.json").apply { writeText("interrupted and corrupt") }
        val bytes = file.readBytes()
        assertThrows(Exception::class.java) { store.read() }
        assertThrows(Exception::class.java) { store.write(book()) }
        assertArrayEquals(bytes, file.readBytes())
        assertThrows(IllegalArgumentException::class.java) { store.audioFile("../other") }
        assertThrows(IllegalArgumentException::class.java) { store.audioFile("/absolute") }
    }

    @Test fun `invalid replacement is rejected before the previous file is touched`() {
        val store = AlarmFileStore(temporary.newFolder())
        store.write(book())
        val bytes = File(store.directory, "alarms-v1.json").readBytes()
        assertThrows(IllegalArgumentException::class.java) { store.write(book().copy(songs = emptyList())) }
        assertArrayEquals(bytes, File(store.directory, "alarms-v1.json").readBytes())
    }

    @Test fun `a stale writer cannot replace a newer persisted revision`() {
        val store = AlarmFileStore(temporary.newFolder())
        store.write(book())
        store.write(book().copy(revision = 8, ringing = null))
        assertThrows(IllegalArgumentException::class.java) { store.write(book()) }
        assertEquals(8L, store.read().revision)
        assertNull(store.read().ringing)
    }

    @Test fun `codec rejects ambiguous choices unknown enum values and oversized data`() {
        val root = JSONObject(AlarmCodec.encode(book()))
        root.getJSONObject("ringing").getJSONObject("quiz").put("category", "FUTURE")
        assertThrows(IllegalArgumentException::class.java) { AlarmCodec.decode(root.toString()) }
        val duplicate = JSONObject(AlarmCodec.encode(book()))
        duplicate.getJSONObject("ringing").getJSONObject("quiz").getJSONArray("choices").getJSONObject(1).put("value", "AMIGA")
        duplicate.getJSONObject("ringing").getJSONObject("quiz").getJSONArray("choices").getJSONObject(0).put("value", "Amiga")
        assertThrows(IllegalArgumentException::class.java) { AlarmCodec.decode(duplicate.toString()) }
        val dir = temporary.newFolder()
        File(dir, "alarms-v1.json").writeBytes(ByteArray(AlarmFileStore.MAX_LEDGER_BYTES + 1))
        assertThrows(IllegalArgumentException::class.java) { AlarmFileStore(dir).read() }
    }
    @Test fun `old alarm records remain readable and new offline quiz facts round trip`() {
        val song = AlarmSong("song", "a".repeat(64), "Lotus", durationMillis = 3000)
        val legacy = org.json.JSONObject(AlarmCodec.encode(AlarmBook(songs = listOf(song))))
        legacy.getJSONArray("songs").getJSONObject(0).apply {
            remove("year"); remove("authorAlternatives"); remove("yearAlternatives")
        }
        assertEquals(song, AlarmCodec.decode(legacy.toString()).songs.single())
        val enriched = song.copy(author = "Patrick Phelan", year = "1992",
            authorAlternatives = listOf("Jakim", "Jester"), yearAlternatives = listOf("1990", "1991"))
        assertEquals(enriched, AlarmCodec.decode(AlarmCodec.encode(AlarmBook(songs = listOf(enriched)))).songs.single())
    }

    @Test fun `a retry question and its new identities survive a real atomic file reload`() {
        val previous = book()
        val song = previous.songs.single().copy(year = "1992", yearAlternatives = listOf("1991", "1993"))
        val ready = previous.copy(songs = listOf(song), ringing = previous.ringing!!.copy(song = song))
        val quiz = ready.ringing!!.quiz!!
        val rules = AlarmRules(AlarmQuizFactory(listOf("amiga" to "Amiga", "pc" to "PC", "c64" to "C64"))) { true }
        val now = AlarmClockReading(1000, 200, 3)
        val changed = rules.answer(ready, "session", quiz.choices.first { it.id != quiz.correctId }.id, now).book
        assertNotEquals(quiz.category, changed.ringing!!.quiz!!.category)
        val store = AlarmFileStore(temporary.newFolder())
        store.write(changed)
        val restored = AlarmFileStore(store.directory).read()
        assertEquals(changed, restored)
        assertEquals(restored, rules.answer(restored, "session", quiz.correctId, now).book)
    }

}
