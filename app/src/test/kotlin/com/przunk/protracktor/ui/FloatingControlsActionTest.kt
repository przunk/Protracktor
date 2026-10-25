// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.player.FloatingControls
import com.przunk.protracktor.player.PlaybackService
import com.przunk.protracktor.player.PlayQueue
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.TrackRef
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
    private var playTaps = 0
    @Before fun start() {
        FloatingControls.setVisible(false)
        ShadowSettings.setCanDrawOverlays(false)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }
    @After fun stop() {
        FloatingControls.setVisible(false)
        activity.get().setContent { }; compose.waitForIdle(); activity.close()
    }
    private fun show(available: Boolean = true, playing: Boolean = false, loading: Boolean = false) {
        activity.get().setContent { MaterialTheme {
            val action = rememberFloatingControlsAction(available)
            PlayerDock(
                PlayerUiState(queue = PlayQueue(listOf(TrackRef("song", "Song"))).startAt(0),
                    restored = available, playing = playing, loadingTrack = loading),
                onSeek = {}, onKeep = {}, onExpand = {}, onBrowse = {},
                onPlayPause = { playTaps++ }, onPrevious = {}, onPreviousFile = {},
                onNext = {}, onNextFile = {}, onShuffle = {}, onRepeat = {},
                onLongPlayPause = action.onToggle, longPlayPauseLabel = action.label,
            )
        } }
    }
    private fun requestedService() = shadowOf(activity.get().application).nextStartedService
    private fun holdPlay() = compose.onNodeWithContentDescription("Play")
        .performTouchInput { longClick() }

    @Test fun `ordinary Play tap retains playback and the hold action adds no visible button`() {
        show()
        compose.onNodeWithText("Floating controls").assertDoesNotExist()
        assertEquals("Floating controls", compose.onNodeWithContentDescription("Play")
            .fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
        compose.onNodeWithContentDescription("Play").performTouchInput { click() }
        assertEquals(1, playTaps)
        assertNull(requestedService())
        compose.onNodeWithText("Open settings").assertDoesNotExist()
    }

    @Test fun `Pause hold opens floating controls without pausing and its tap still pauses`() {
        ShadowSettings.setCanDrawOverlays(true)
        show(playing = true)
        compose.onNodeWithContentDescription("Pause").performTouchInput { longClick() }
        assertEquals(FloatingControls.SHOW, requestedService()!!.action)
        assertEquals(0, playTaps)
        compose.onNodeWithContentDescription("Pause").performTouchInput { click() }
        assertEquals(1, playTaps)
        assertNull(requestedService())
    }

    @Test fun `loading cancellation remains a plain Stop tap`() {
        show(loading = true)
        compose.onNodeWithContentDescription("Stop loading").performTouchInput { click() }
        assertEquals(1, playTaps)
        assertNull(requestedService())
        compose.onNodeWithText("Open settings").assertDoesNotExist()
    }

    @Test fun `permission explanation and Cancel never start playback or a service`() {
        show()
        holdPlay()
        compose.onNodeWithText("Open settings").assertIsDisplayed()
        assertNull(requestedService())
        compose.onNodeWithText("Cancel").performClick()
        assertNull(requestedService())
        assertFalse(FloatingControls.visible.value)
    }

    @Test fun `granted permission sends the explicit Show command to the existing playback service`() {
        ShadowSettings.setCanDrawOverlays(true)
        show()
        holdPlay()
        val intent = requestedService()!!
        assertEquals(PlaybackService::class.java.name, intent.component!!.className)
        assertEquals(FloatingControls.SHOW, intent.action)
        assertFalse(FloatingControls.visible.value)
        assertEquals(0, playTaps)
        assertNull(requestedService())
    }

    @Test fun `return from permission settings checks actual grant instead of result code`() {
        show()
        holdPlay()
        compose.onNodeWithText("Open settings").performClick()
        val request = shadowOf(activity.get()).nextStartedActivityForResult
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, request.intent.action)
        assertEquals("package:${activity.get().packageName}", request.intent.dataString)
        ShadowSettings.setCanDrawOverlays(true)
        compose.runOnIdle { activity.get().activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_CANCELED, Intent()) }
        assertEquals(FloatingControls.SHOW, requestedService()!!.action)
    }

    @Test fun `denied grant explains restricted settings without changing playback`() {
        show()
        holdPlay()
        compose.onNodeWithText("Open settings").performClick()
        val request = shadowOf(activity.get()).nextStartedActivityForResult
        compose.runOnIdle { activity.get().activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, Intent()) }
        compose.onNodeWithText("Allow restricted settings", substring = true)
            .assertIsDisplayed()
        assertNull(requestedService())
        assertEquals(0, playTaps)
    }

    @Test fun `hold before restoration explains why floating controls cannot start`() {
        show(available = false)
        holdPlay()
        compose.onNodeWithText("Load a track to enable floating controls.").assertIsDisplayed()
        assertNull(requestedService())
        assertEquals(0, playTaps)
        compose.onNodeWithText("Close").performClick()
    }

    @Test fun `holding Play hides an active panel without pausing`() {
        FloatingControls.setVisible(true)
        show()
        holdPlay()
        assertEquals(FloatingControls.HIDE, requestedService()!!.action)
        assertEquals(0, playTaps)
        assertNull(requestedService())
    }

    @Test fun `pending permission result survives activity recreation`() {
        show()
        holdPlay()
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
