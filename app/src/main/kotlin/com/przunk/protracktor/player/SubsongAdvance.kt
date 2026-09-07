// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
 * the next random pick, repeating what just played, stopping — belongs to the queue and stays
 * there.
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
     * **Repeat-one repeats the tune you are hearing. Always.** It outranks "play all": while it is
     * on, nothing advances, and the subsong that just ended plays again. The owner put it in three
     * words — *"one to one"* — and it does not matter whether the thing being repeated is a whole
     * file or the fifth tune inside one.
     *
     * This is the second reading of that control, and the first was mine and wrong. It said "one"
     * meant one row of the playlist, so a multi-tune file would loop from its first tune. That is
     * defensible on paper and it fails the only test that counts: with "play all" on, pressing
     * repeat-one still moved you off the tune you were listening to. A repeat that goes somewhere
     * else is not a repeat.
     *
     * Reported as [Next.FileFinished] rather than `Subsong(subsong)` because repeating what is
     * playing is the caller's `restart()` — the same path that already serves repeat-one for an
     * ordinary track, and, since `Sc68Backend` learned to remember its subsong, the one that comes
     * back to the right tune.
     */
    fun after(playAll: Boolean, subsong: Int, subsongCount: Int, repeatOne: Boolean): Next {
        if (repeatOne) return Next.FileFinished
        if (!playAll || subsongCount <= 1) return Next.FileFinished
        if (subsong + 1 < subsongCount) return Next.Subsong(subsong + 1)
        return Next.FileFinished
    }
}
