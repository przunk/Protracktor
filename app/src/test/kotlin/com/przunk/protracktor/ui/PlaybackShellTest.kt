// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.data.ProtracktorDatabase
import com.przunk.protracktor.player.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.util.ReflectionHelpers

/** Real shell/VM/controller/SQLite with preset playback, never a native audio device. */
abstract class PlaybackShellTest {
    @get:Rule val compose = createEmptyComposeRule()
    protected lateinit var activity: ActivityController<ComponentActivity>
    protected lateinit var controller: PlaybackController
    protected val application get() = ApplicationProvider.getApplicationContext<Application>()
    protected val player get() = ReflectionHelpers.getField<MutableStateFlow<PlayerUiState>>(controller, "_state")
    protected val browse get() = ReflectionHelpers.getField<MutableStateFlow<BrowseState>>(controller, "_browse")
    protected val tune = TrackRef("content://navigation.test/current.mod", "Current tune", author = "Known author")

    @Before fun startShell() {
        Appearance.selectCacheAhead(application, CacheAhead.OFF)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        controller = PlaybackController.get(application)
        activity.get().setContent { MaterialTheme { ProtracktorApp(viewModel = PlayerViewModel(application)) } }
        val view = activity.get().window.decorView
        view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 720, 1600)
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            controller.state.value.restored && controller.browse.value.knowsWhatIsHeld
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @After fun stopShell() {
        if (::controller.isInitialized) {
            val scope = ReflectionHelpers.getField<CoroutineScope>(controller, "scope")
            scope.cancel()
            compose.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                scope.coroutineContext[Job]!!.isCompleted
            }
            controller.release()
            ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(controller, "backgroundWork").close()
            ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(controller, "scanWork").close()
            ReflectionHelpers.setStaticField(PlaybackController::class.java, "instance", null)
        }
        if (::activity.isInitialized) activity.close()
        ProtracktorDatabase.of(application).close()
    }

    protected fun frame(milliseconds: Long = 32) {
        shadowOf(Looper.getMainLooper()).idle()
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
    }
}
