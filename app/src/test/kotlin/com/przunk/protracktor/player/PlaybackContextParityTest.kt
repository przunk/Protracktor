// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same explicit-transport cases run against the WEB production domain model. */
class PlaybackContextParityTest {
    @Test fun `source and finite transport match the shared WEB cases`() {
        val relative = "docs/rules/playback-context-cases.tsv"
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, relative).isFile }
        requireNotNull(root) { "$relative is missing" }
        val lines = File(root, relative).readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("case\tsource\tat\trepeat\taction\tstep\tcurrent", lines.first())
        val rows = lines.drop(1)
        assertTrue("No parity cases executed", rows.isNotEmpty())
        val tracks = listOf("a", "b", "c").map { TrackRef(it, it.uppercase()) }
        rows.forEach { line ->
            val cells = line.split('\t')
            assertEquals(line, 7, cells.size)
            val (name, sourceName, at, repeatName, action) = cells
            val source = when (sourceName) {
                "playlist" -> QueueSource.Playlist(1)
                "collection" -> QueueSource.Collection(SessionSource.Search("test"))
                "random" -> QueueSource.Random(7)
                "external" -> QueueSource.External
                "none" -> QueueSource.None
                else -> error("Unknown source in $name: $sourceName")
            }
            val repeatMode = when (repeatName) {
                "off" -> RepeatMode.OFF
                "all" -> RepeatMode.PLAYLIST
                "one" -> RepeatMode.ONE
                else -> error("Unknown repeat mode in $name: $repeatName")
            }
            val order = PlayQueue(tracks, repeat = repeatMode).let { queue ->
                at.toIntOrNull()?.let(queue::startAt) ?: queue
            }
            val context = ActiveQueue(source, order)
            val result = when (action) {
                "next" -> context.next()
                "previous" -> context.previous()
                "ended" -> context.ended()
                else -> error("Unknown action in $name: $action")
            }
            val kind = when (result) {
                is QueueStep.Play -> "play"
                QueueStep.RandomNext -> "random-next"
                QueueStep.RandomPrevious -> "random-previous"
                QueueStep.Replay -> "replay"
                QueueStep.Stop -> "stop"
                QueueStep.Unchanged -> "unchanged"
            }
            assertEquals(name, cells[5], kind)
            assertEquals(name, cells[6].toIntOrNull(), (result as? QueueStep.Play)?.queue?.order?.currentIndex)
            if (result is QueueStep.Play) assertEquals(name, source, result.queue.source)
        }
    }
}
