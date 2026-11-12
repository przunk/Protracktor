// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import android.app.KeyguardManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.player.CatalogueBrowser
import com.przunk.protracktor.player.FloatingControls
import com.przunk.protracktor.player.PlayQueue
import com.przunk.protracktor.player.PlaybackService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class FloatingControlsSettingsTest : PlaybackShellTest() {
    private fun openFixtureSettings() {
        val catalogues = ReflectionHelpers.getField<CatalogueBrowser>(controller, "catalogueBrowser")
        ReflectionHelpers.setField(catalogues, "backends", { "host-fixture" })
        openSettings()
        frame(250)
    }

    private fun setting() = compose.onNodeWithContentDescription(application.getString(R.string.floating_show))

    @Test fun `settings exposes an off by default opt in without starting an overlay`() {
        compose.runOnIdle { player.value = player.value.copy(queue = PlayQueue(listOf(tune)).startAt(0)) }
        frame(250)
        openFixtureSettings()
        scrollSettingsTo(hasContentDescription(application.getString(R.string.floating_show)))
        val setting = setting()
        setting.assertIsOff()
        setting.performClick()
        frame(250)
        setting.assertIsOn()
        assertTrue(Appearance.floatingControlsEnabled(application))
        assertFalse(FloatingControls.visible.value)
        assertNull(shadowOf(application).nextStartedService)
        assertNull(shadowOf(activity.get()).nextStartedActivity)
    }

    @Test fun `opting in after service startup permits manual activation and opting out closes the panel`() {
        val receiverPermission = "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        shadowOf(application).grantPermissions(receiverPermission)
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(application.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        compose.runOnIdle { player.value = player.value.copy(queue = PlayQueue(listOf(tune)).startAt(0)) }
        frame(250)
        val before = player.value
        val service = Robolectric.buildService(PlaybackService::class.java).create()
        val windowsBefore = WindowInspector.getGlobalWindowViews().toSet()
        try {
            assertFalse(FloatingControls.visible.value)
            openFixtureSettings()
            scrollSettingsTo(hasContentDescription(application.getString(R.string.floating_show)))
            setting().performClick()
            frame(250)
            assertTrue(Appearance.floatingControlsEnabled(application))
            compose.onNodeWithContentDescription(application.getString(R.string.action_back)).performClick()
            frame(250)
            compose.onNodeWithContentDescription("Play").performTouchInput { longClick() }
            val show = shadowOf(application).nextStartedService
            assertEquals(FloatingControls.SHOW, show?.action)
            compose.runOnIdle { service.get().onStartCommand(show, 0, 1) }
            frame(250)
            assertTrue("An already running service must create the host after opt-in", FloatingControls.visible.value)
            assertNotEquals(windowsBefore, WindowInspector.getGlobalWindowViews().toSet())
            openFixtureSettings()
            scrollSettingsTo(hasContentDescription(application.getString(R.string.floating_show)))
            setting().performClick()
            frame(250)
            assertFalse(Appearance.floatingControlsEnabled(application))
            assertFalse(FloatingControls.visible.value)
            assertEquals(windowsBefore, WindowInspector.getGlobalWindowViews().toSet())
            assertEquals(before.activePlayback, player.value.activePlayback)
            assertEquals(before.playing, player.value.playing)
        } finally {
            service.destroy()
            FloatingControls.setVisible(false)
        }
    }
}
