// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.KeyguardManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.R
import com.przunk.protracktor.AppLanguage
import com.przunk.protracktor.AppLocale
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowLog
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29, 33], qualifiers = "en-w360dp-h800dp-xhdpi")
class FloatingControlsWindowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val calls = mutableListOf<String>()
    private lateinit var host: FloatingControlsWindow
    private val state = FloatingControlsState(true, true, false, false, true, null)
    @Before fun start() {
        // Core declares this signature permission in the merged manifest; Robolectric needs its grant.
        val receiverPermission = "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        assertEquals(context.packageName, context.packageManager.getPermissionInfo(receiverPermission, 0).packageName)
        shadowOf(context as Application).grantPermissions(receiverPermission)
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        host = FloatingControlsWindow(context, { calls += "previous" }, { calls += "play" },
            { calls += "next" }, { calls += "heart" })
        host.update(state)
    }
    @After fun stop() { host.close() }
    private fun frame() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
    private fun assertShown() = assertTrue(ShadowLog.getLogsForTag("Protracktor").joinToString("\n") {
        it.msg + "\n" + it.throwable?.stackTraceToString()
    }, FloatingControls.visible.value)

    @Test fun `production host attaches one bounded non-focusable overlay and close never pauses`() {
        val before = WindowInspector.getGlobalWindowViews().toSet()
        host.show(); frame()
        assertShown()
        val roots = WindowInspector.getGlobalWindowViews().filter { it !in before }
        assertEquals(1, roots.size)
        val root = roots.single()
        val layout = root.layoutParams as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, layout.type)
        assertTrue(layout.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertEquals(0, layout.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        assertTrue(layout.width in 1 until context.resources.displayMetrics.widthPixels)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, layout.height)
        host.show(); frame()
        assertEquals(roots, WindowInspector.getGlobalWindowViews().filter { it !in before })
        host.update(state.copy(playing = false)); frame()
        assertShown()
        host.close(); frame()
        assertFalse(FloatingControls.visible.value)
        assertFalse(root in WindowInspector.getGlobalWindowViews())
        assertTrue(calls.isEmpty())
    }

    @Test fun `denied permission never attaches and revocation removes an existing window`() {
        val before = WindowInspector.getGlobalWindowViews().toSet()
        ShadowSettings.setCanDrawOverlays(false)
        host.show(); frame()
        assertFalse(FloatingControls.visible.value)
        assertEquals(context.getString(R.string.floating_unavailable), ShadowToast.getTextOfLatestToast())
        assertEquals(before, WindowInspector.getGlobalWindowViews().toSet())
        ShadowSettings.setCanDrawOverlays(true)
        host.show(); frame()
        assertShown()
        ShadowSettings.setCanDrawOverlays(false)
        host.refreshConfiguration(); frame()
        assertFalse(FloatingControls.visible.value)
        assertEquals(before, WindowInspector.getGlobalWindowViews().toSet())
        assertTrue(calls.isEmpty())
    }

    @Test fun `screen lock temporarily hides a requested window and unlock restores it`() {
        val before = WindowInspector.getGlobalWindowViews().toSet()
        host.show(); frame()
        val root = WindowInspector.getGlobalWindowViews().single { it !in before }
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF)); frame()
        assertEquals(View.GONE, root.visibility)
        assertTrue(FloatingControls.visible.value)
        context.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT)); frame()
        assertEquals(View.VISIBLE, root.visibility)
        host.close()
        context.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT)); frame()
        assertFalse(FloatingControls.visible.value)
        assertEquals(before, WindowInspector.getGlobalWindowViews().toSet())
    }

    @Test fun `temporary loading retains controls while an empty stopped session removes them`() {
        host.show(); frame()
        host.update(state.copy(hasTrack = false, loading = true)); frame()
        assertTrue(FloatingControls.visible.value)
        host.update(state.copy(hasTrack = false, playing = true)); frame()
        assertTrue(FloatingControls.visible.value)
        host.update(state.copy(hasTrack = false, playing = false)); frame()
        assertFalse(FloatingControls.visible.value)
        assertTrue(calls.isEmpty())
    }

    @Test fun `app language changes relabel the active window without replacing playback`() {
        val before = WindowInspector.getGlobalWindowViews().toSet()
        host.show(); frame()
        val root = WindowInspector.getGlobalWindowViews().single { it !in before }
        AppLocale.select(context, AppLanguage.POLISH); frame()
        assertEquals("Pływające przyciski", (root.layoutParams as WindowManager.LayoutParams).title)
        AppLocale.select(context, AppLanguage.ENGLISH); frame()
        assertEquals("Floating controls", (root.layoutParams as WindowManager.LayoutParams).title)
        assertShown()
        assertTrue(calls.isEmpty())
    }
}
