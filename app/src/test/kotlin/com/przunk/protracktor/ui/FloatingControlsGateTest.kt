// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.player.FloatingControls
import com.przunk.protracktor.Appearance
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
class FloatingControlsGateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>

    @Before fun start() {
        FloatingControls.setVisible(false)
        ShadowSettings.setCanDrawOverlays(false)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        Appearance.selectFloatingControlsEnabled(activity.get(), false)
    }

    @After fun stop() {
        FloatingControls.setVisible(false)
        activity.get().setContent { }; compose.waitForIdle(); activity.close()
    }

    @Test fun `disabled preference cannot start a service even with overlay permission granted`() {
        ShadowSettings.setCanDrawOverlays(true)
        FloatingControls.request(activity.get(), show = true)
        assertNull(shadowOf(activity.get().application).nextStartedService)
        assertFalse(FloatingControls.visible.value)
    }

    @Test fun `disabled preference offers no hold action and ordinary Play remains usable`() {
        var taps = 0
        activity.get().setContent { MaterialTheme {
            val action = rememberFloatingControlsAction(available = true,
                enabled = Appearance.floatingControlsEnabled(activity.get()))
            PlayerDock(
                PlayerUiState(queue = PlayQueue(listOf(TrackRef("song", "Song"))).startAt(0), restored = true),
                onSeek = {}, onKeep = {}, onExpand = {}, onBrowse = {}, onPlayPause = { taps++ },
                onPrevious = {}, onPreviousFile = {}, onNext = {}, onNextFile = {},
                onShuffle = {}, onRepeat = {},
                onLongPlayPause = action?.onToggle, longPlayPauseLabel = action?.label,
            )
        } }
        compose.onNodeWithContentDescription("Play").performTouchInput { longClick() }
        compose.onNodeWithText("Open settings").assertDoesNotExist()
        assertFalse(compose.onNodeWithContentDescription("Play").fetchSemanticsNode()
            .config.contains(SemanticsActions.OnLongClick))
        assertNull(shadowOf(activity.get()).nextStartedActivity)
        assertNull(shadowOf(activity.get().application).nextStartedService)
        val before = taps
        compose.onNodeWithContentDescription("Play").performTouchInput { click() }
        assertEquals(before + 1, taps)
    }

    @Test fun `Hide remains available after disabling the preference`() {
        FloatingControls.setVisible(true)
        FloatingControls.request(activity.get(), show = false)
        assertEquals(FloatingControls.HIDE, shadowOf(activity.get().application).nextStartedService?.action)
    }

    @Test fun `opt in persists without activating a panel and an unchanged selection is not written again`() {
        assertFalse(Appearance.floatingControlsEnabled(activity.get()))
        assertTrue(Appearance.selectFloatingControlsEnabled(activity.get(), true))
        assertFalse(Appearance.selectFloatingControlsEnabled(activity.get(), true))
        assertTrue(Appearance.floatingControlsEnabled(android.content.ContextWrapper(activity.get())))
        assertFalse(FloatingControls.visible.value)
        assertNull(shadowOf(activity.get().application).nextStartedService)
    }

    @Test fun `enabled preference allows an explicit Show request without restoring panel visibility`() {
        Appearance.selectFloatingControlsEnabled(activity.get(), true)
        FloatingControls.request(activity.get(), true)
        assertEquals(FloatingControls.SHOW, shadowOf(activity.get().application).nextStartedService?.action)
        assertFalse(FloatingControls.visible.value)
    }
}
