// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.engine

enum class OutputRecovery {
    UNCHANGED, RECOVERED, FAILED;

    companion object {
        fun fromNative(value: Int): OutputRecovery = when (value) {
            0 -> UNCHANGED
            1 -> RECOVERED
            else -> FAILED
        }
    }
}
