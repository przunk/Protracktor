// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/** Global playback width; stable preference values do not depend on enum order. */
enum class StereoMix(val stored: String, val widthPercent: Int) {
    ORIGINAL("original", 100),
    NARROW("narrow", 50),
    MONO("mono", 0);

    companion object {
        fun fromStored(value: String?): StereoMix = entries.firstOrNull { it.stored == value } ?: ORIGINAL
    }
}
