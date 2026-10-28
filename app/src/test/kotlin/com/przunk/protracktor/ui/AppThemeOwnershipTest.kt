// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.content.pm.PackageManager
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.przunk.protracktor.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checks packaged opt-outs; Robolectric does not implement MIUI's rendering algorithm. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 30])
class AppThemeOwnershipTest {
    @Test fun `MIUI is told to preserve the app managed colours`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        assertTrue("Xiaomi's documented true value disables its automatic recolouring",
            info.metaData?.getBoolean("force_dark_google", false) == true)
    }

    @Test fun `the production window theme explicitly disables platform Force Dark`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val theme = ContextThemeWrapper(context, R.style.Theme_Protracktor)
        val attributes = theme.obtainStyledAttributes(intArrayOf(android.R.attr.forceDarkAllowed))
        try {
            assertTrue("The theme must declare its colour ownership", attributes.hasValue(0))
            assertFalse(attributes.getBoolean(0, true))
        } finally {
            attributes.recycle()
        }
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(R.style.Theme_Protracktor, info.theme)
    }
}
