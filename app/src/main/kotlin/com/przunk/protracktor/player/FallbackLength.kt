// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * How long to play a tune whose length nothing knows.
 *
 * **The fault this exists for is "a tune that never ends"** (`docs/STATUS.md` C56). A SID is a 6502
 * program with a play routine and nothing in the file says when to stop; the app ends one only
 * because HVSC's database is asked for a length. That database is an optional download, so until it
 * is fetched every SID played for ever — and a `.sndh` that sc68's own database has no entry for
 * does the same, which is why this applies wherever a length is missing rather than to SID alone.
 *
 * Its own file, small as it is, because two places need to agree about it: the poll that ends a
 * tune and the slider that sets it. A number defined next to one of them is a number the other one
 * copies.
 */
object FallbackLength {

    /**
     * The owner's range, 2026-09-15: three to ten minutes.
     *
     * The lower end is not zero and there is no "off". A tune that never ends is the defect, and an
     * off switch would be a supported way back into it — the honest way to play a SID for ever is
     * repeat-one, which already exists and says so on the button.
     */
    val RANGE_SECONDS: IntRange = 3 * 60..10 * 60

    /**
     * Three minutes.
     *
     * **The default matters more than the range**, because most people never open that screen. Three
     * is roughly where a C64 or Atari loop has said what it has to say, and it is the owner's
     * choice rather than a derived one.
     */
    const val DEFAULT_SECONDS: Int = 3 * 60

    /** The step the slider moves in, and the unit the label is written in. */
    const val STEP_SECONDS: Int = 60

    /**
     * What a stored value means.
     *
     * Zero is "never set" — the column's default, and what an upgraded phone reads back — so it
     * becomes the default rather than an instant end of tune. Anything outside the range is clamped
     * rather than rejected: a value written by a newer build must not make an older one unusable.
     */
    fun fromStored(seconds: Int): Int =
        if (seconds <= 0) DEFAULT_SECONDS else snap(seconds)

    /**
     * The nearest whole minute inside the range.
     *
     * **Here rather than in the screen**, so the invariant belongs to the setting and not to one
     * slider. A stepped Compose `Slider` hands back a float that is only nearly its notch --
     * 239.99997 for four minutes -- and truncating that stores 239 seconds while a label dividing
     * by 60 still reads three minutes. Rounding at both ends means the number stored is always the
     * number shown, whichever control is doing the asking.
     */
    fun snap(seconds: Int): Int =
        (Math.round(seconds.toFloat() / STEP_SECONDS) * STEP_SECONDS).coerceIn(RANGE_SECONDS)

    /** How many discrete positions the slider has, ends included. */
    val STEPS: Int = (RANGE_SECONDS.last - RANGE_SECONDS.first) / STEP_SECONDS + 1
}
