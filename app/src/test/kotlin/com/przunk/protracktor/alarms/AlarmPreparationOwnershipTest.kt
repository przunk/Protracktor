// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.player.TrackRef
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmPreparationOwnershipTest {
    @Test fun `stale Cancel cannot retire a later preparation request`() = runBlocking {
        withTimeout(5000) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val firstEntered = CompletableDeferred<Unit>()
            val first = launch { AlarmMusicPreparer.add(context, TrackRef("content://first.mod", "First"), null) {
                firstEntered.complete(Unit); awaitCancellation()
            } }
            firstEntered.await()
            val oldId = AlarmMusicPreparer.preparing.first { it?.title == "First" }!!.id
            assertEquals(AlarmPreparationCancellation.CANCELLED, AlarmMusicPreparer.cancel(oldId))
            first.join()
            val secondEntered = CompletableDeferred<Unit>()
            val second = launch { AlarmMusicPreparer.add(context, TrackRef("content://second.mod", "Second"), null) {
                secondEntered.complete(Unit); awaitCancellation()
            } }
            try {
                secondEntered.await()
                val current = AlarmMusicPreparer.preparing.value!!
                assertNotEquals(oldId, current.id)
                assertEquals(AlarmPreparationCancellation.CHANGED, AlarmMusicPreparer.cancel(oldId))
                assertTrue(second.isActive)
                assertEquals(current, AlarmMusicPreparer.preparing.value)
                assertEquals(AlarmPreparationCancellation.CANCELLED, AlarmMusicPreparer.cancel(current.id))
                second.join()
                assertNull(AlarmMusicPreparer.preparing.value)
                assertEquals(AlarmPreparationCancellation.FINISHED, AlarmMusicPreparer.cancel(current.id))
                assertTrue(AlarmRepository.from(context).book.songs.isEmpty())
            } finally { second.cancel(); second.join() }
        }
    }
}
