// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.KeyguardManager
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.przunk.protracktor.AppLocale
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.R
import com.przunk.protracktor.ui.FloatingControlsPanel
import com.przunk.protracktor.ui.FloatingDismissTarget
import com.przunk.protracktor.ui.FloatingDragContainer
import com.przunk.protracktor.ui.Haptics
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import kotlin.math.min
import kotlin.math.roundToInt

/** Bounded panel and transient drop target, owned by PlaybackService, without a second player. */
internal class FloatingControlsWindow(
    private val context: Context,
    private val previous: () -> Unit,
    private val playPause: () -> Unit,
    private val next: () -> Unit,
    private val heart: (PlaylistHeart.Request) -> Unit,
    private val previousFile: () -> Unit,
    private val nextFile: () -> Unit,
) {
    private var model by mutableStateOf(FloatingControlsState(false, false, false, false, false, null, false, false))
    private var local by mutableStateOf(AppLocale.wrap(context))
    private var view: FloatingDragContainer? = null
    private var composition: ComposeView? = null
    private var dismissTarget: FloatingDismissTarget? = null
    private var snapAnimator: ValueAnimator? = null
    private var owner: WindowOwner? = null
    private var manager: WindowManager? = null
    private var windowContext: Context? = null
    private var params: WindowManager.LayoutParams? = null
    private val preferences = context.getSharedPreferences("protracktor_floating_controls", Context.MODE_PRIVATE)
    private val language = context.getSharedPreferences("protracktor_preferences", Context.MODE_PRIVATE)
    private var position = storedPosition()
    private var areaWidth = 0
    private var areaHeight = 0
    private var registered = false
    private val localeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "app_language") refreshConfiguration()
    }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!Settings.canDrawOverlays(context)) close()
            else {
                val hidden = intent.action == Intent.ACTION_SCREEN_OFF || locked()
                if (hidden) {
                    view?.cancelGesture()
                    cancelSnap()
                    hideDismissTarget()
                    position = position.snapped()
                    place()
                    rememberPosition()
                }
                view?.visibility = if (hidden) View.GONE else View.VISIBLE
            }
        }
    }

    fun update(state: FloatingControlsState) {
        model = state
        if (view != null && (!Settings.canDrawOverlays(context) || (!state.hasTrack && !state.loading && !state.playing))) close()
    }

    fun show() {
        if (view != null) return
        position = storedPosition()
        if (!Settings.canDrawOverlays(context) || (!model.hasTrack && !model.loading)) {
            FloatingControls.unavailable(context)
            return
        }
        try {
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            val displayContext = context.createDisplayContext(display)
            val target = if (Build.VERSION.SDK_INT >= 30)
                displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            else displayContext
            windowContext = target
            local = AppLocale.wrap(target)
            val wm = target.getSystemService(WindowManager::class.java)
            manager = wm
            val lifecycle = WindowOwner()
            owner = lifecycle
            val root = FloatingDragContainer(target, ::beginTouch, ::snapToEdge, ::showDismissTarget,
                ::dragBy, ::finishDrag).apply {
                setViewTreeLifecycleOwner(lifecycle)
                setViewTreeSavedStateRegistryOwner(lifecycle)
                alpha = 0f
            }
            val content = ComposeView(target).apply {
                setContent {
                    CompositionLocalProvider(LocalContext provides local,
                        LocalConfiguration provides Configuration(local.resources.configuration)) {
                        ProtracktorTheme(theme = AppTheme.DARK, dynamicColour = false) {
                            FloatingControlsPanel(model, previous, playPause, next, heart, ::close,
                                ::moveAccessible, ::snapToEdge, previousFile, nextFile)
                        }
                    }
                }
            }
            composition = content
            root.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT))
            view = root
            updateArea()
            val layout = WindowManager.LayoutParams(panelWidth(), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                if (Build.VERSION.SDK_INT >= 30) {
                    setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    setFitInsetsIgnoringVisibility(true)
                }
                title = local.getString(R.string.floating_show)
            }
            params = layout
            root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) place()
                root.alpha = 1f
            }
            wm.addView(root, layout)
            lifecycle.start()
            ContextCompat.registerReceiver(context, screen, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            language.registerOnSharedPreferenceChangeListener(localeListener)
            root.visibility = if (locked()) View.GONE else View.VISIBLE
            FloatingControls.setVisible(true)
        } catch (failure: RuntimeException) {
            // Permission and display validity can change between the check and WindowManager.
            Log.w("Protracktor", "Could not attach floating controls", failure)
            close()
            FloatingControls.unavailable(context)
        }
    }

    fun refreshConfiguration() {
        if (view == null) return
        if (!Settings.canDrawOverlays(context)) { close(); return }
        view?.cancelGesture()
        cancelSnap()
        hideDismissTarget()
        position = position.snapped()
        local = AppLocale.wrap(windowContext ?: context)
        params?.title = local.getString(R.string.floating_show)
        updateArea()
        params?.width = panelWidth()
        place()
    }

    private fun updateArea() {
        val wm = manager ?: return
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            areaWidth = metrics.bounds.width() - insets.left - insets.right
            areaHeight = metrics.bounds.height() - insets.top - insets.bottom
        } else {
            val config = (windowContext ?: context).resources.configuration
            areaWidth = dp(config.screenWidthDp)
            areaHeight = dp(config.screenHeightDp)
        }
    }

    private fun panelWidth() = min(dp(288), (areaWidth - dp(16)).coerceAtLeast(1))
    private fun dp(value: Int) = (value * (windowContext ?: context).resources.displayMetrics.density).roundToInt()
    private fun locked() = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun place() {
        val root = view ?: return
        val layout = params ?: return
        val offset = position.place(areaWidth, areaHeight, layout.width, root.height, dp(8))
        layout.x = offset.x
        layout.y = offset.y
        try { manager?.updateViewLayout(root, layout) }
        catch (failure: RuntimeException) {
            Log.w("Protracktor", "Could not position floating controls", failure)
            close()
            FloatingControls.unavailable(context)
        }
    }

    private fun move(dx: Int, dy: Int) {
        val root = view ?: return
        val layout = params ?: return
        position = FloatingPanelPosition.from(PanelOffset(layout.x + dx, layout.y + dy),
            areaWidth, areaHeight, layout.width, root.height, dp(8))
        place()
    }

    private fun dragBy(dx: Int, dy: Int, rawX: Float, rawY: Float) {
        move(dx, dy)
        if (dismissTarget?.hover(rawX, rawY) == true) view?.let { Haptics(it).tick() }
    }

    private fun beginTouch() {
        cancelSnap()
        hideDismissTarget()
    }

    private fun moveAccessible(dx: Int, dy: Int) {
        cancelSnap()
        if (dx != 0) { position = position.copy(x = if (dx < 0) 0f else 1f); place() }
        else move(0, dy)
    }

    private fun showDismissTarget() {
        if (view == null || dismissTarget != null) return
        val wm = manager ?: return
        try {
            val indicator = FloatingDismissTarget(local)
            dismissTarget = indicator
            val layout = WindowManager.LayoutParams(dp(136), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dp(24)
                // Non-touchable SAW windows must stay below Android 12's obscuring-opacity limit.
                alpha = if (Build.VERSION.SDK_INT >= 31)
                    min(.8f, context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch)
                    else .8f
                if (Build.VERSION.SDK_INT >= 30) {
                    setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    setFitInsetsIgnoringVisibility(true)
                }
                title = local.getString(R.string.floating_hide)
            }
            wm.addView(indicator, layout)
        } catch (failure: RuntimeException) {
            Log.w("Protracktor", "Could not show floating dismissal target", failure)
            close()
            FloatingControls.unavailable(context)
        }
    }

    private fun hideDismissTarget() {
        val indicator = dismissTarget ?: return
        dismissTarget = null
        try { manager?.removeViewImmediate(indicator) }
        catch (gone: IllegalArgumentException) { Log.d("Protracktor", "Floating dismissal target already removed") }
    }

    private fun finishDrag(cancelled: Boolean) {
        val dismiss = !cancelled && dismissTarget?.armed == true
        hideDismissTarget()
        if (dismiss) close() else snapToEdge()
    }

    private fun cancelSnap() {
        val running = snapAnimator
        snapAnimator = null // Cancel also calls onAnimationEnd; it must not persist an old target.
        running?.cancel()
    }

    private fun snapToEdge() {
        val root = view ?: return
        cancelSnap()
        val target = position.snapped()
        if (position.x == target.x || !ValueAnimator.areAnimatorsEnabled()) {
            position = target; place(); rememberPosition()
            return
        }
        val start = position
        val animator = ValueAnimator.ofFloat(start.x, target.x)
        snapAnimator = animator
        animator.duration = 180L
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            if (view === root) { position = start.copy(x = it.animatedValue as Float); place() }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (snapAnimator === animation && view === root) {
                    snapAnimator = null
                    position = target; place(); rememberPosition()
                }
            }
        })
        animator.start()
    }

    private fun rememberPosition() {
        preferences.edit().putFloat("x", position.x).putFloat("y", position.y).apply()
    }

    private fun storedPosition() = FloatingPanelPosition(
        preferences.getFloat("x", 1f), preferences.getFloat("y", .55f)).snapped()

    fun close() {
        cancelSnap()
        hideDismissTarget()
        val root = view
        view = null
        root?.cancelGesture(notify = false)
        val content = composition
        composition = null
        if (registered) { context.unregisterReceiver(screen); registered = false }
        language.unregisterOnSharedPreferenceChangeListener(localeListener)
        if (root != null) {
            try { manager?.removeViewImmediate(root) }
            catch (gone: IllegalArgumentException) { Log.d("Protracktor", "Floating window already removed") }
        }
        content?.disposeComposition()
        owner?.close()
        owner = null
        params = null
        manager = null
        windowContext = null
        local = AppLocale.wrap(context)
        FloatingControls.setVisible(false)
    }

    private class WindowOwner : LifecycleOwner, SavedStateRegistryOwner {
        override val lifecycle = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val savedStateRegistry get() = saved.savedStateRegistry
        init { saved.performAttach(); saved.performRestore(null); lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE) }
        fun start() { lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START); lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        fun close() { lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY) }
    }
}
