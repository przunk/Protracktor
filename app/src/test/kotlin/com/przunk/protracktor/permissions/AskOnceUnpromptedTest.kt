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
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
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

/** The notification question at start-up (RV-B034): asked once, never answered by opening settings. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "en-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AskOnceUnpromptedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var host: ActivityController<ComponentActivity>
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private var code: Int? = null
    private val launched = mutableListOf<Any?>()
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            code = requestCode; launched += input
        }
    }

    @Before fun start() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        host = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }
    @After fun stop() { host.get().setContent { }; compose.waitForIdle(); host.close() }

    /** One start of the app: the shell composed afresh, as after a launch or a recreation. */
    private fun startApp() {
        host.get().setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }) {
                AskOnceUnprompted(AppAccess.NOTIFICATIONS)
                Text("shell")
            }
        }
        compose.waitForIdle()
    }

    @Test fun `only a missing permission never asked before is asked for unprompted`() {
        assertTrue(mayAskUnprompted(AccessState.REQUEST_NEEDED, askedBefore = false))
        assertFalse(mayAskUnprompted(AccessState.REQUEST_NEEDED, askedBefore = true))
        // Blocked in settings with the permission granted: only settings could change it.
        assertFalse(mayAskUnprompted(AccessState.SETTINGS_NEEDED, askedBefore = false))
        AccessState.entries.filter { it.allowed }.forEach { assertFalse(mayAskUnprompted(it, askedBefore = false)) }
        assertFalse(mayAskUnprompted(AccessState.UNAVAILABLE, askedBefore = false))
    }

    @Test fun `the first start shows the system's dialog, and a declined one is not shown again`() {
        startApp()
        assertEquals(listOf<Any?>(Manifest.permission.POST_NOTIFICATIONS), launched)
        compose.runOnIdle { registry.dispatchResult(checkNotNull(code), false) }

        startApp()
        startApp()
        assertEquals("declined once is declined: no dialog and no settings at later starts",
            listOf<Any?>(Manifest.permission.POST_NOTIFICATIONS), launched)
    }

    @Test fun `somebody who already declined is not sent to settings at start`() {
        AccessHistory.markAsked(app, AppAccess.NOTIFICATIONS)
        startApp()
        assertTrue("nothing may open at start-up, least of all settings: $launched", launched.isEmpty())
    }

    @Test fun `nothing is asked when notifications are allowed`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        startApp()
        assertTrue(launched.isEmpty())
    }
}
