// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

/**
 * When a line of the dock scrolls instead of ending in "…" (`docs/BACKLOG.md` A54, D3).
 *
 * A line scrolls only when it does not fit -- that part is the marquee's own measurement -- and
 * only if two things allow it:
 *
 * - **The system's animations are on.** "Remove animations" sets the animator scale to 0, and
 *   somebody who asked for nothing to move gets the `…` the dock always had. A scrolling line is
 *   exactly the motion that setting exists to stop.
 * - **The line is not a status.** "Loading…" is short-lived and says one thing; setting it moving
 *   would be motion for its own sake, and it is replaced before a pass could finish.
 *
 * Here rather than in the composable so the rule is a plain function a JVM test can call.
 */
internal object DockMarquee {

    /** About the speed of reading a line without chasing it; the marquee's own default. */
    const val VELOCITY_DP = 30

    /** Held still at the start before each pass, so the beginning of a title can be read. */
    const val PAUSE_MS = 2_000

    fun scrolls(animatorScale: Float, isStatus: Boolean): Boolean = animatorScale > 0f && !isStatus
}
