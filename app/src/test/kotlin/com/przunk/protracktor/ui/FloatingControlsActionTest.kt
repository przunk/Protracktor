// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.player.FloatingControls
import com.przunk.protracktor.player.PlaybackService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class FloatingControlsActionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    @Before fun start() {
        FloatingControls.setVisible(false)
        ShadowSettings.setCanDrawOverlays(false)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }
    @After fun stop() {
        FloatingControls.setVisible(false)
        activity.get().setContent { }; compose.waitForIdle(); activity.close()
    }
    private fun show(available: Boolean = true) {
        activity.get().setContent { MaterialTheme { FloatingControlsAction(available) } }
    }
    private fun requestedService() = shadowOf(activity.get().application).nextStartedService

    @Test fun `permission explanation and Cancel never start playback or a service`() {
        show()
        compose.onNodeWithText("Floating controls").performClick()
        compose.onNodeWithText("Open settings").assertIsDisplayed()
        assertNull(requestedService())
        compose.onNodeWithText("Cancel").performClick()
        assertNull(requestedService())
        assertFalse(FloatingControls.visible.value)
    }

    @Test fun `granted permission sends the explicit Show command to the existing playback service`() {
        ShadowSettings.setCanDrawOverlays(true)
        show()
        compose.onNodeWithText("Floating controls").performTouchInput { click() }
        val intent = requestedService()!!
        assertEquals(PlaybackService::class.java.name, intent.component!!.className)
        assertEquals(FloatingControls.SHOW, intent.action)
        assertFalse(FloatingControls.visible.value)
    }

    @Test fun `return from permission settings checks actual grant instead of result code`() {
        show()
        compose.onNodeWithText("Floating controls").performClick()
        compose.onNodeWithText("Open settings").performClick()
        val request = shadowOf(activity.get()).nextStartedActivityForResult
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, request.intent.action)
        assertEquals("package:${activity.get().packageName}", request.intent.dataString)
        ShadowSettings.setCanDrawOverlays(true)
        compose.runOnIdle { activity.get().activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_CANCELED, Intent()) }
        assertEquals(FloatingControls.SHOW, requestedService()!!.action)
    }

    @Test fun `denied grant explains the refusal and missing track is visibly unavailable`() {
        show()
        compose.onNodeWithText("Floating controls").performClick()
        compose.onNodeWithText("Open settings").performClick()
        val request = shadowOf(activity.get()).nextStartedActivityForResult
        compose.runOnIdle { activity.get().activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, Intent()) }
        compose.onNodeWithText("Floating controls need permission to display over other apps. Playback is unchanged. You can try again from Now Playing.")
            .assertIsDisplayed()
        assertNull(requestedService())
        compose.onNodeWithText("Close").performClick()
        show(available = false)
        compose.onNodeWithText("Floating controls").assertIsNotEnabled()
        compose.onNodeWithText("Load a track to enable floating controls.").assertIsDisplayed()
    }

    @Test fun `pending permission result survives activity recreation`() {
        show()
        compose.onNodeWithText("Floating controls").performClick()
        compose.onNodeWithText("Open settings").performClick()
        val request = shadowOf(activity.get()).nextStartedActivityForResult
        compose.runOnIdle { activity.recreate().visible() }
        show()
        compose.waitForIdle()
        ShadowSettings.setCanDrawOverlays(true)
        compose.runOnIdle { activity.get().activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_CANCELED, Intent()) }
        assertEquals(FloatingControls.SHOW, requestedService()!!.action)
    }
}
