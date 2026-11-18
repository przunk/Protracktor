// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import org.junit.Assume.assumeTrue
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.net.Amp
import com.przunk.protracktor.data.ProtracktorDatabase
import com.przunk.protracktor.data.HistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers
import java.util.Base64
import java.util.zip.Deflater

/** RV-B001 through the real controller: a link naming an unknown server waits for an answer. */
@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en")
class LinkControllerTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var controller: PlaybackController

    private fun link(vararg rows: String): String {
        val raw = rows.joinToString("\n").toByteArray()
        val deflater = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(raw); finish() }
        val buffer = ByteArray(raw.size + 1024)
        var total = 0
        while (!deflater.finished()) total += deflater.deflate(buffer, total, buffer.size - total)
        deflater.end()
        return "https://przunk.github.io/Protracktor/src/#" + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.copyOf(total))
    }

    @Before fun start() {
        app.getSharedPreferences("protracktor_preferences", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("amp_enabled", false).commit()
        Amp.access.setEnabled(true)
        Appearance.selectCacheAhead(app, CacheAhead.OFF)
        controller = PlaybackController.get(app)
        await { controller.state.value.restored && controller.browse.value.knowsWhatIsHeld }
    }

    @After fun stop() {
        Amp.access.setEnabled(true)
        if (::controller.isInitialized) {
            val scope = ReflectionHelpers.getField<CoroutineScope>(controller, "scope")
            scope.cancel()
            await { scope.coroutineContext[Job]!!.isCompleted }
            controller.release()
            ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(controller, "backgroundWork").close()
            ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(controller, "scanWork").close()
            ReflectionHelpers.setStaticField(PlaybackController::class.java, "instance", null)
        }
        ProtracktorDatabase.of(app).close()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        fail("Controller did not reach the expected host state")
    }

    @Test fun `a link naming an unknown server plays nothing until it is answered`() {
        val queueBefore = controller.state.value.activePlayback
        // `.invalid` never resolves, so even a mistake here fetches nothing.
        controller.openLink(link("Protracker/4-Mat/elysium.mod", "https://server.invalid/x.mod"))
        shadowOf(Looper.getMainLooper()).idle()
        val asked = controller.state.value.pendingLink
        assertEquals(listOf("https://server.invalid/x.mod"), asked?.foreign)
        assertEquals(queueBefore, controller.state.value.activePlayback)

        controller.cancelLink()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(controller.state.value.pendingLink)
        assertEquals(queueBefore, controller.state.value.activePlayback)
    }

    @Test fun `answered yes, the link's tunes become what plays`() {
        controller.openLink(link("https://server.invalid/x.mod", "https://server.invalid/y.mod"))
        shadowOf(Looper.getMainLooper()).idle()
        controller.confirmLink()
        await { controller.state.value.activePlayback.order.tracks.map { it.id } == listOf("https://server.invalid/x.mod", "https://server.invalid/y.mod") }
        assertNull(controller.state.value.pendingLink)
    }
}
