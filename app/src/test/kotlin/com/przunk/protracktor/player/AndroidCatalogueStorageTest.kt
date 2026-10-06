// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Application
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.data.SchemaSql
import com.przunk.protracktor.net.RemoteFiles
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class AndroidCatalogueStorageTest {
    private val context = RuntimeEnvironment.getApplication()
    private val remote = RemoteFiles(context)
    private val files = AndroidCatalogueStorage(context, remote)

    private fun put(root: File, path: String, size: Int): File = File(root, path).apply {
        parentFile!!.mkdirs()
        writeBytes(ByteArray(size))
    }

    @Test
    fun `replay and player counters read their existing stores without installing anything`() {
        assertEquals(0, files.replayCount())
        assertEquals(0L, files.replayBytes())
        assertFalse(files.playersPresent())
        assertEquals(0, files.playerCount())
        assertEquals(0L, files.playerBytes())
        assertFalse(File(context.filesDir, "sc68-replays").exists())
        assertFalse(File(context.filesDir, "uade-players").exists())
        put(context.filesDir, "sc68-replays/one", 3)
        put(context.filesDir, "sc68-replays/two", 5)
        put(context.filesDir, "uade-players/players/one", 7)
        put(context.filesDir, "uade-players/players/two", 11)
        put(context.filesDir, "uade-players/players/nested/extra", 13)
        put(context.filesDir, "uade-players/song.conf", 17)
        assertEquals(2, files.replayCount())
        assertEquals(8L, files.replayBytes())
        assertTrue(files.playersPresent())
        assertEquals(2, files.playerCount())
        assertEquals(48L, files.playerBytes())
    }

    @Test
    fun `cache and archive counts use the supplied remote store and exclude partial cache writes`() {
        assertEquals(0L, files.cacheBytes())
        assertEquals(0L, files.permanentBytes())
        assertEquals(0L, files.archiveBytes("asma"))
        put(context.cacheDir, "remote/ready", 19)
        put(context.cacheDir, "remote/pending.part", 23)
        put(context.cacheDir, "shared/export", 29)
        remote.archiveFile("asma").writeBytes(ByteArray(31))
        remote.archiveFile("another").writeBytes(ByteArray(37))
        assertEquals(19L, files.cacheBytes())
        assertEquals(68L, files.permanentBytes())
        assertEquals(31L, files.archiveBytes("asma"))
        assertEquals(37L, files.archiveBytes("another"))
        assertEquals(0L, files.archiveBytes("missing"))
    }

    @Test
    fun `database size is the database file only and does not open or create it`() {
        val database = context.getDatabasePath(SchemaSql.NAME)
        assertEquals(0L, files.databaseBytes())
        assertFalse(database.exists())
        database.parentFile!!.mkdirs()
        database.writeBytes(ByteArray(41))
        File(database.parentFile, "${database.name}-wal").writeBytes(ByteArray(43))
        assertEquals(41L, files.databaseBytes())
        assertTrue(database.delete())
        assertTrue(database.mkdir())
        assertEquals(0L, files.databaseBytes())
    }

    @Test
    fun `paired status reads current pairing preferences on each call`() {
        assertFalse(files.pairedBrowser())
        Appearance.rememberPairing(context, "http://192.0.2.1:8080")
        assertTrue(files.pairedBrowser())
        Appearance.rememberPairing(context, null)
        assertFalse(files.pairedBrowser())
    }
}
