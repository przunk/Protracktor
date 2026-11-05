// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.permissions

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], qualifiers = "en-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessRequestCoordinatorTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var host: ActivityController<ComponentActivity>
    private val results = mutableListOf<AccessRequestResult>()
    private var code: Int? = null
    private var launched: Any? = null
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            code = requestCode; launched = input
        }
    }
    @Before fun start() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.CAMERA)
        host = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        host.get().setContent { MaterialTheme {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }) {
                val request = rememberAccessRequestCoordinator { _, result -> results += result }
                Button({ request.request(AppAccess.CAMERA) }) { Text("Request camera") }
            }
        } }
    }
    @After fun stop() { host.get().setContent { }; compose.waitForIdle(); host.close() }
    @Test fun `runtime launcher result cannot invent a grant that Android still denies`() {
        compose.onNodeWithText("Request camera").performClick()
        assertEquals(Manifest.permission.CAMERA, launched)
        compose.runOnIdle { registry.dispatchResult(checkNotNull(code), true) }
        assertEquals(listOf(AccessRequestResult.DENIED), results)
    }
    @Test fun `the actual system grant wins over a canceled runtime result`() {
        compose.onNodeWithText("Request camera").performClick()
        compose.runOnIdle {
            shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.CAMERA)
            registry.dispatchResult(checkNotNull(code), false)
        }
        assertEquals(listOf(AccessRequestResult.GRANTED), results)
    }
    @Test fun `repeated denial routes to app settings instead of an invisible runtime prompt`() {
        compose.onNodeWithText("Request camera").performClick()
        compose.runOnIdle { registry.dispatchResult(checkNotNull(code), false) }
        compose.onNodeWithText("Request camera").performClick()
        val intent = launched as android.content.Intent
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${host.get().packageName}", intent.dataString)
        compose.runOnIdle { registry.dispatchResult(checkNotNull(code), android.app.Activity.RESULT_CANCELED, android.content.Intent()) }
        assertEquals(listOf(AccessRequestResult.DENIED, AccessRequestResult.DENIED), results)
    }
    @Test fun `disposing the request screen retires its pending continuation`() {
        compose.onNodeWithText("Request camera").performClick()
        host.get().setContent { Text("Closed") }; compose.waitForIdle()
        compose.runOnIdle { registry.dispatchResult(checkNotNull(code), true) }
        assertTrue(results.isEmpty())
    }
}
