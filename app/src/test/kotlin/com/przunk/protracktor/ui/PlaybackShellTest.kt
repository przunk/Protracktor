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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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

    /** Settings through the navigation bar's More (redesign E1), as a person reaches it. */
    protected fun openSettings() {
        compose.onNodeWithTag("nav-more").performClick()
        frame(500)
        compose.onNode(hasText(application.getString(com.przunk.protracktor.R.string.settings_title)) and
            hasAnyAncestor(hasTestTag("more-sheet"))).performClick()
        frame(600)
    }

    /**
     * Swipes Settings up until [matcher] shows between the top and the dock. Not
     * `performScrollToNode`: it waits for this harness's clock, which moves only when told, and
     * never returns. The swipe starts above the dock, which takes a vertical drag as its own, and
     * moves a fifth of the screen at a time so a row is not swept past.
     */
    protected fun scrollSettingsTo(matcher: SemanticsMatcher) {
        repeat(20) {
            val node = compose.onAllNodes(matcher).fetchSemanticsNodes().firstOrNull()
            val dockTop = compose.onAllNodesWithTag("player-dock").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
                ?: Float.MAX_VALUE
            if (node != null && node.boundsInRoot.top >= 0f && node.boundsInRoot.bottom <= dockTop) return
            compose.onNode(hasScrollToNodeAction()).performTouchInput {
                swipeUp(startY = bottom * 0.55f, endY = bottom * 0.35f, durationMillis = 300)
            }
            frame(600)
        }
        throw AssertionError("Not reached in Settings: ${matcher.description}")
    }
}
