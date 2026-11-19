// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.ExecutionException

/** RV-B035: a camera that cannot be started is said so on the screen, and the app stays up. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class ScannerCameraTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun `each way the camera fails to start is caught, and anything else is not`() {
        assertFalse(CameraStart.attempt { throw ExecutionException(RuntimeException("no camera service")) })
        assertFalse(CameraStart.attempt { throw IllegalArgumentException("No available camera can be found") })
        assertFalse(CameraStart.attempt { throw IllegalStateException("not initialised") })
        assertTrue(CameraStart.attempt { })
        assertTrue(runCatching { CameraStart.attempt { throw NullPointerException() } }.isFailure)
    }

    @Test fun `the scanner on a device whose camera cannot start says so`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        shadowOf(activity.application).grantPermissions(Manifest.permission.CAMERA)
        activity.setContent {
            MaterialTheme { ScannerScreen(PaddingValues(), onScanned = {}, onSendLink = {}, onCancel = {}) }
        }
        repeat(20) { ShadowLooper.idleMainLooper(); compose.waitForIdle() }
        compose.onNodeWithText(activity.getString(R.string.scan_unavailable)).assertExists()
        compose.onNodeWithText(activity.getString(R.string.action_send_as_link)).assertExists()
    }
}
