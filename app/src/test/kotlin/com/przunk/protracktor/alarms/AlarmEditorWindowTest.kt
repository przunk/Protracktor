// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.ComponentName
import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmEditorWindowTest {
    @Test fun `production editor resizes for the keyboard instead of allowing window panning`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getActivityInfo(ComponentName(context, AlarmsActivity::class.java), 0)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            info.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
    }
}
