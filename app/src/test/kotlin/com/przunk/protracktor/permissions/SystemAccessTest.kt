// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.permissions

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SystemAccessTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun `old platforms do not ask for exact alarm or full screen special access`() {
        assertEquals(AccessState.NOT_REQUIRED, SystemAccess.inspect(context, AppAccess.EXACT_ALARMS))
        assertEquals(AccessState.NOT_REQUIRED, SystemAccess.inspect(context, AppAccess.FULL_SCREEN_ALARMS))
        assertNull(SystemAccess.runtimePermission(AppAccess.NOTIFICATIONS))
    }
    @Test fun `camera denial and grant are inspected from the actual application permission`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        assertEquals(AccessState.REQUEST_NEEDED, SystemAccess.inspect(context, AppAccess.CAMERA))
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        assertEquals(AccessState.GRANTED, SystemAccess.inspect(context, AppAccess.CAMERA))
    }
    @Test fun `notification settings stay required when old platform app notifications are blocked`() {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        assertEquals(AccessState.SETTINGS_NEEDED, SystemAccess.inspect(context, AppAccess.NOTIFICATIONS))
        assertFalse(SystemAccess.allowed(context, AppAccess.NOTIFICATIONS))
    }
    @Test @Config(sdk = [31]) fun `exact access observes revocation without cached grant state`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(SystemAccess.allowed(context, AppAccess.EXACT_ALARMS))
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val declared = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        val expected = if (Manifest.permission.SCHEDULE_EXACT_ALARM in declared) AccessState.SETTINGS_NEEDED else AccessState.UNAVAILABLE
        assertEquals(expected, SystemAccess.inspect(context, AppAccess.EXACT_ALARMS))
    }
    @Test @Config(sdk = [33]) fun `notification permission is separate from a notification manager enabled flag`() {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(AccessState.REQUEST_NEEDED, SystemAccess.inspect(context, AppAccess.NOTIFICATIONS))
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(SystemAccess.allowed(context, AppAccess.NOTIFICATIONS))
    }
    @Test fun `settings routes retain package and channel identity`() {
        assertEquals("android.settings.action.MANAGE_OVERLAY_PERMISSION", SystemAccess.settings(context, AppAccess.OVERLAY).action)
        assertEquals("package:${context.packageName}", SystemAccess.settings(context, AppAccess.OVERLAY).dataString)
        assertEquals("alarm-channel", SystemAccess.notificationChannel(context, "alarm-channel").getStringExtra(android.provider.Settings.EXTRA_CHANNEL_ID))
    }
}
