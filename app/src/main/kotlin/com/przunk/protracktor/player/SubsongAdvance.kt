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

/**
 * What a file with several tunes in it does when one of them ends.
 *
 * **Extracted because this decision has been wrong twice.** C11: auto-advance raced through every
 * subsong in silence, because a finished tune has no audio callback left to apply a switch. C13:
 * repeat-one was never consulted at all under "play all", so the last subsong looped for ever on
 * three backends and jumped to the first on the fourth — three behaviours, none of them chosen.
 * Both were reasoned about in a method that needs a phone to run. This part needs nothing.
 *
 * It answers only the subsong question. What happens when the *file* is finished — the next track,
 * the next random pick, stopping — belongs to the queue and stays there.
 */
object SubsongAdvance {

    sealed interface Next {
        /** Play this subsong, zero-based. */
        data class Subsong(val index: Int) : Next
        /** Nothing more in this file; whatever owns the queue decides. */
        data object FileFinished : Next
    }

    /**
     * @param playAll whether the user asked for every tune in the file
     * @param subsong which one just ended, zero-based
     * @param subsongCount how many the file has
     * @param repeatOne whether repeat-one is on
     *
     * **"One" means one row of the playlist, and a multi-tune file is one row.** So with "play all"
     * on, repeat-one loops the whole file from its first tune rather than leaving the last tune
     * looping on its own. That is a judgement, not the only possible reading — and the alternative
     * was rejected for the reason the owner gave about the transport buttons: a control should not
     * mean a different thing depending on which file happens to be open.
     *
     * With "play all" off, a multi-tune file behaves like any other track: it does not walk, and
     * repeat-one repeats the tune that was playing. That is the caller's `restart()`, not a subsong
     * decision, so it reports [Next.FileFinished] here.
     */
    fun after(playAll: Boolean, subsong: Int, subsongCount: Int, repeatOne: Boolean): Next {
        if (!playAll || subsongCount <= 1) return Next.FileFinished
        if (subsong + 1 < subsongCount) return Next.Subsong(subsong + 1)
        return if (repeatOne) Next.Subsong(0) else Next.FileFinished
    }
}
