// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputRecoveryTest {
    @Test fun `zero is an unchanged output`() {
        assertEquals(OutputRecovery.UNCHANGED, OutputRecovery.fromNative(0))
    }
    @Test fun `one means the output was recovered`() {
        assertEquals(OutputRecovery.RECOVERED, OutputRecovery.fromNative(1))
    }
    @Test fun `minus one reports failure`() {
        assertEquals(OutputRecovery.FAILED, OutputRecovery.fromNative(-1))
    }
    @Test fun `unexpected native results fail closed`() {
        for (value in listOf(Int.MIN_VALUE, -2, 2, Int.MAX_VALUE)) {
            assertEquals(OutputRecovery.FAILED, OutputRecovery.fromNative(value))
        }
    }
}
