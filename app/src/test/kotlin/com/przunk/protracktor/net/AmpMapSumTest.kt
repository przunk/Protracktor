// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** AMP's published map sum, read and computed (B67). */
class AmpMapSumTest {
    @Test fun `AMP's sum file is read as its first word, in lower case`() {
        assertEquals("000cf3878165ca3d6d805e774dfcd0b0",
            AmpMapSum.parse("000cf3878165ca3d6d805e774dfcd0b0  amp_modules.json\n".toByteArray()))
        assertEquals("000cf3878165ca3d6d805e774dfcd0b0", AmpMapSum.parse("000CF3878165CA3D6D805E774DFCD0B0".toByteArray()))
    }

    @Test fun `anything that is not a sum reads as none`() {
        for (text in listOf("", "   ", "not a sum", "000cf3878165ca3d6d805e774dfcd0b", "<html>error</html>")) {
            assertNull(text, AmpMapSum.parse(text.toByteArray()))
        }
    }

    @Test fun `the sum of the bytes is MD5 in AMP's form`() {
        // md5("abc"), the standard check value.
        assertEquals("900150983cd24fb0d6963f7d28e17f72", AmpMapSum.of("abc".toByteArray()))
    }
}
