/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Removing tracks and putting them back.
 *
 * The one operation in this app that can lose somebody's playlist, so it is a separate object with
 * a test rather than a few lines inside the controller. A bulk delete is one edit, not twenty
 * (`docs/BACKLOG.md` A4): undoing it has to bring the whole selection back, to the positions it
 * came from.
 */
class TrackEditingTest {

    private fun list(n: Int) = (0 until n).map { TrackRef(id = "id$it", title = "t$it") }

    @Test
    fun `removing then restoring gives back exactly what was there`() {
        val original = list(7)
        val (kept, removed) = TrackEditing.remove(original, listOf(1, 3, 5))
        assertEquals(listOf("id0", "id2", "id4", "id6"), kept.map { it.id })
        assertEquals(original, TrackEditing.restore(kept, removed))
    }

    @Test
    fun `it holds for any selection, not just a convenient one`() {
        // The failure this guards against is off by one and only for some shapes, so the shapes are
        // generated rather than chosen. A fixed seed keeps a failure reproducible.
        val random = Random(4)
        repeat(200) {
            val original = list(random.nextInt(1, 12))
            val indices = original.indices.filter { random.nextBoolean() }
            val (kept, removed) = TrackEditing.remove(original, indices)
            assertEquals(original.size - indices.size, kept.size)
            assertEquals(original, TrackEditing.restore(kept, removed))
        }
    }

    @Test
    fun `restoring puts the lowest position back first`() {
        // Order is the whole trick. Inserting the lowest first makes room for the next; the other
        // way round each lands a place too early and the list comes back subtly wrong rather than
        // obviously broken.
        val original = list(5)
        val (kept, removed) = TrackEditing.remove(original, listOf(4, 0, 2))
        assertEquals(original, TrackEditing.restore(kept, removed.shuffled(Random(1))))
    }

    @Test
    fun `positions out of range are ignored rather than throwing`() {
        val original = list(3)
        val (kept, removed) = TrackEditing.remove(original, listOf(-1, 1, 99))
        assertEquals(listOf("id0", "id2"), kept.map { it.id })
        assertEquals(1, removed.size)
    }

    @Test
    fun `the same position twice removes one track`() {
        val (kept, removed) = TrackEditing.remove(list(3), listOf(1, 1, 1))
        assertEquals(2, kept.size)
        assertEquals(1, removed.size)
    }

    @Test
    fun `restoring into a list that shrank underneath clamps instead of throwing`() {
        val (_, removed) = TrackEditing.remove(list(6), listOf(5))
        // The playlist was saved, reloaded, and is shorter now. Better a track at the end than a
        // crash while undoing.
        val restored = TrackEditing.restore(list(2), removed)
        assertEquals(3, restored.size)
        assertTrue(restored.last().id == "id5")
    }

    @Test
    fun `removing nothing changes nothing`() {
        val original = list(4)
        val (kept, removed) = TrackEditing.remove(original, emptyList())
        assertEquals(original, kept)
        assertTrue(removed.isEmpty())
    }
}
