// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

fun randomPlayback(track: TrackRef, shuffle: Boolean = false, repeat: RepeatMode = RepeatMode.OFF) =
    ActiveQueue(QueueSource.Random(1), PlayQueue(listOf(track), shuffle = shuffle, repeat = repeat).startAt(0))

fun collectionPlayback(order: PlayQueue, origin: SessionSource = SessionSource.Search("")) =
    ActiveQueue(QueueSource.Collection(origin), order)

fun externalPlayback(track: TrackRef) = ActiveQueue(QueueSource.External, PlayQueue(listOf(track)).startAt(0))
