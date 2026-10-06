// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Application
import android.content.ContentResolver
import android.content.ContextWrapper
import android.net.Uri
import com.przunk.protracktor.data.GrantedFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, manifest = Config.NONE)
class AndroidFolderAccessTest {
    private val context = RuntimeEnvironment.getApplication()
    private val tree = "content://com.android.externalstorage.documents/tree/primary%3AAndrzej%2FMusic%2FAmiga"

    @Test
    fun `grant retains the exact tree URI and read permission with its existing display label`() {
        val access = AndroidFolderAccess(context)
        assertTrue(context.contentResolver.persistedUriPermissions.isEmpty())
        assertEquals(GrantedFolder(tree, "Amiga"), access.grant(tree))
        val permission = context.contentResolver.persistedUriPermissions.single()
        assertEquals(Uri.parse(tree), permission.uri)
        assertTrue(permission.isReadPermission)
        assertFalse(permission.isWritePermission)
    }

    @Test
    fun `root paths preserve nested paths and fallback labels without requesting permission`() {
        val access = AndroidFolderAccess(context)
        assertEquals("Andrzej/Music/Amiga", access.rootPath(tree))
        assertEquals("opaque", access.rootPath("content://provider/document/opaque"))
        assertEquals("primary:", access.rootPath("content://provider/tree/primary%3A"))
        assertTrue(context.contentResolver.persistedUriPermissions.isEmpty())
    }

    @Test
    fun `a rejected persistent permission still produces the original folder label`() {
        var attempts = 0
        val denied = object : ContextWrapper(context) {
            override fun getContentResolver(): ContentResolver { attempts++; throw SecurityException("permission rejected") }
        }
        assertEquals(GrantedFolder(tree, "Amiga"), AndroidFolderAccess(denied).grant(tree))
        assertEquals(1, attempts)
    }
}
