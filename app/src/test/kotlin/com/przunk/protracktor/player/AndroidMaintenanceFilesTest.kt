// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Application
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
class AndroidMaintenanceFilesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val remote = RemoteFiles(context)
    private val files = AndroidMaintenanceFiles(context, remote)

    private fun put(root: File, path: String): File = File(root, path).apply {
        parentFile!!.mkdirs()
        writeBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `cache removal uses only the existing remote cache and includes partial files`() {
        val ready = put(context.cacheDir, "remote/ready")
        val partial = put(context.cacheDir, "remote/fetch.part")
        val shared = put(context.cacheDir, "shared/share.mod")
        val archive = put(context.filesDir, "catalogues/asma.zip")
        val local = put(context.filesDir, "local.mod")
        assertEquals(3L, files.cacheBytes())
        files.clearCache()
        assertEquals(0L, files.cacheBytes())
        assertFalse(ready.exists())
        assertFalse(partial.exists())
        assertTrue(shared.exists())
        assertTrue(archive.exists())
        assertTrue(local.exists())
    }

    @Test
    fun `archive removal deletes only the requested archive and returns the file result`() {
        val archive = put(context.filesDir, "catalogues/asma.zip")
        val other = put(context.filesDir, "catalogues/other.zip")
        val cached = put(context.cacheDir, "remote/ready")
        assertTrue(files.deleteArchive("asma"))
        assertFalse(archive.exists())
        assertFalse(files.deleteArchive("asma"))
        assertTrue(other.exists())
        assertTrue(cached.exists())
    }

    @Test
    fun `replay removals target separate downloaded and adopted stores without touching neighbours`() {
        val sc68 = put(context.filesDir, "sc68-replays/player")
        val uade = put(context.filesDir, "uade-players/players/player")
        val adopted = put(context.filesDir, "uade/players/player")
        val local = put(context.filesDir, "music/local.mod")
        assertTrue(files.deleteSc68())
        assertFalse(sc68.exists())
        assertTrue(uade.exists())
        assertTrue(adopted.exists())
        assertTrue(files.deleteUade())
        assertFalse(uade.exists())
        assertTrue(adopted.exists())
        assertTrue(files.deleteAdoptedUade())
        assertFalse(adopted.exists())
        assertTrue(local.exists())
    }
}
