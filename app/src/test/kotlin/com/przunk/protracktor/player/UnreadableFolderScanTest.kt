// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** RV-B008: a folder whose provider refuses to list it gives no list -- it does not throw. */
@RunWith(AndroidJUnit4::class)
class UnreadableFolderScanTest {
    /** A documents provider whose grant was withdrawn: every query is refused. */
    class Revoked : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? =
            throw SecurityException("Permission Denial: reading $uri requires that you obtain access")
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    /** A provider whose storage is gone. */
    class Gone : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? =
            throw IllegalStateException("volume unmounted")
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    @Test fun `a folder whose access was withdrawn, or whose storage is gone, is reported unreadable`() = runBlocking {
        Robolectric.setupContentProvider(Revoked::class.java, "test.revoked")
        Robolectric.setupContentProvider(Gone::class.java, "test.gone")
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        for (authority in listOf("test.revoked", "test.gone")) {
            val tree = DocumentsContract.buildTreeDocumentUri(authority, "primary:Music")
            assertNull(authority, MediaScanner.listFiles(context, tree))
        }
    }
}
