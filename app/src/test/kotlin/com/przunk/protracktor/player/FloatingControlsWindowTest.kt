// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.KeyguardManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.R
import com.przunk.protracktor.AppLanguage
import com.przunk.protracktor.AppLocale
import com.przunk.protracktor.ui.FloatingDragContainer
import com.przunk.protracktor.ui.FloatingDismissTarget
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
    private val state = FloatingControlsState(true, true, false, false, true, null, false, true)
    @Before fun start() {
        // Core declares this signature permission in the merged manifest; Robolectric needs its grant.
        val receiverPermission = "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        assertEquals(context.packageName, context.packageManager.getPermissionInfo(receiverPermission, 0).packageName)
        shadowOf(context as Application).grantPermissions(receiverPermission)
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        host = FloatingControlsWindow(context, { calls += "previous" }, { calls += "play" },
            { calls += "next" }, { calls += "heart" }, { calls += "previousFile" }, { calls += "nextFile" })
        host.update(state)
    }
    @After fun stop() { host.close() }
    private fun frame() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
    private fun send(root: View, action: Int, rawX: Float, rawY: Float) {
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, rawX, rawY, 0)
        event.offsetLocation(-location[0].toFloat(), -location[1].toFloat())
        try { root.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun beginDrag(root: View): Pair<Float, Float> {
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val x = location[0] + root.width / 2f
        val y = location[1] + root.height / 2f
        send(root, MotionEvent.ACTION_DOWN, x, y)
        send(root, MotionEvent.ACTION_MOVE, x - 50f, y)
        frame()
        return x - 50f to y
    }
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

    @Test fun `drag adds only a bounded non-touchable dismissal target and close removes both windows`() {
        val before = WindowInspector.getGlobalWindowViews().toSet()
        host.show(); frame()
        val root = WindowInspector.getGlobalWindowViews().single { it !in before }
        assertTrue(root is FloatingDragContainer)
        beginDrag(root)
        val indicator = WindowInspector.getGlobalWindowViews().filterIsInstance<FloatingDismissTarget>().single()
        val layout = indicator.layoutParams as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, layout.type)
        assertTrue(layout.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(layout.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(layout.alpha > 0f && layout.alpha <= .8f)
        assertTrue(layout.width in 1 until context.resources.displayMetrics.widthPixels)
        host.close(); frame()
        assertEquals(before, WindowInspector.getGlobalWindowViews().toSet())
        assertTrue(calls.isEmpty())
    }

    @Test fun `dropping on the target hides without playback but cancellation preserves the panel`() {
        val before = WindowInspector.getGlobalWindowViews().toSet()
        for ((cancel, outside) in listOf(true to false, false to true, false to false)) {
            host.show(); frame()
            val root = WindowInspector.getGlobalWindowViews().single { it !in before }
            beginDrag(root)
            val indicator = WindowInspector.getGlobalWindowViews().filterIsInstance<FloatingDismissTarget>().single()
            val location = IntArray(2)
            indicator.getLocationOnScreen(location)
            val badge = indicator.getChildAt(0)
            val x = location[0] + badge.left + badge.width / 2f
            val y = location[1] + badge.top + badge.height / 2f
            send(root, MotionEvent.ACTION_MOVE, x, y); frame()
            assertTrue(indicator.armed)
            assertEquals(context.getString(R.string.floating_release_hide),
                (indicator.getChildAt(1) as TextView).text)
            send(root, if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,
                x + if (outside) 180f else 0f, y); frame()
            assertTrue(WindowInspector.getGlobalWindowViews().none { it is FloatingDismissTarget })
            assertEquals(cancel || outside, FloatingControls.visible.value)
            assertTrue(calls.isEmpty())
        }
        assertEquals(before, WindowInspector.getGlobalWindowViews().toSet())
    }

    @Test fun `ordinary drag release snaps to an edge and locking clears an in-progress target`() {
        val before = WindowInspector.getGlobalWindowViews().toSet()
        host.show(); frame()
        val root = WindowInspector.getGlobalWindowViews().single { it !in before }
        val (x, y) = beginDrag(root)
        send(root, MotionEvent.ACTION_UP, x, y)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        val saved = context.getSharedPreferences("protracktor_floating_controls", Context.MODE_PRIVATE)
        assertTrue(saved.getFloat("x", -.5f) in listOf(0f, 1f))
        assertTrue(WindowInspector.getGlobalWindowViews().none { it is FloatingDismissTarget })
        beginDrag(root)
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF)); frame()
        assertEquals(View.GONE, root.visibility)
        assertTrue(WindowInspector.getGlobalWindowViews().none { it is FloatingDismissTarget })
        assertTrue(FloatingControls.visible.value)
        assertTrue(calls.isEmpty())
    }

    @Test fun `a tap interrupting the edge animation resumes docking after release`() {
        val before = WindowInspector.getGlobalWindowViews().toSet()
        context.getSharedPreferences("protracktor_floating_controls", Context.MODE_PRIVATE)
            .edit().clear().commit()
        host.show(); frame()
        val root = WindowInspector.getGlobalWindowViews().single { it !in before }
        val restingX = (root.layoutParams as WindowManager.LayoutParams).x
        val (x, y) = beginDrag(root)
        assertNotEquals(restingX, (root.layoutParams as WindowManager.LayoutParams).x)
        send(root, MotionEvent.ACTION_UP, x, y)
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        // The panel's padding avoids executing any transport operation in this window test.
        send(root, MotionEvent.ACTION_DOWN, location[0] + 2f, location[1] + 2f)
        send(root, MotionEvent.ACTION_UP, location[0] + 2f, location[1] + 2f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        val saved = context.getSharedPreferences("protracktor_floating_controls", Context.MODE_PRIVATE)
        val side = saved.getFloat("x", -.5f)
        assertTrue(side in listOf(0f, 1f))
        assertTrue(calls.isEmpty())
    }
}
