// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** C134: AMP module numbers looked up by path, in place, from the table written with the index. */
class AmpModuleIdsTest {
    @get:Rule val folder = TemporaryFolder()
    private fun table() = AmpModuleIds(File(folder.root, "amp-module-ids.tsv"))

    @Test fun `every written path is found, and nothing else`() {
        val random = Random(1134)
        val pairs = (1..5000).map { n ->
            val letter = ('A' + random.nextInt(26))
            "$letter/Artist ${random.nextInt(800)}/MOD.tune $n ${"é".repeat(n % 3)}.gz" to (n * 7L)
        }
        val ids = table()
        assertTrue(ids.write(pairs.shuffled(random)))
        for ((path, id) in pairs) assertEquals(path, id, ids.idFor(path))
        for (missing in listOf("", "0", "A/", "Z/Zz/zz.gz", "M/Major Tom/MOD.human race gold.gz", "~~~"))
            assertNull(missing, ids.idFor(missing))
    }

    @Test fun `first and last rows, a single row, and an empty table`() {
        val ids = table()
        assertTrue(ids.write(listOf("B/b/b.gz" to 2L, "A/a/a.gz" to 1L, "C/c/c.gz" to 3L)))
        assertEquals(1L, ids.idFor("A/a/a.gz")); assertEquals(3L, ids.idFor("C/c/c.gz"))
        assertTrue(ids.write(listOf("N/Nork/MOD.Nk-SaveTheFunky.gz" to 49993L)))
        assertEquals(49993L, ids.idFor("N/Nork/MOD.Nk-SaveTheFunky.gz"))
        assertNull(ids.idFor("N/Nork/MOD.Nk-SaveTheFunk.gz"))
        assertTrue(ids.write(emptyList()))
        assertNull(ids.idFor("N/Nork/MOD.Nk-SaveTheFunky.gz"))
    }

    @Test fun `no table yet means no number`() {
        assertFalse(File(folder.root, "amp-module-ids.tsv").exists())
        assertNull(table().idFor("A/a/a.gz"))
    }

    @Test fun `a path listed twice keeps its first number`() {
        val ids = table()
        assertTrue(ids.write(listOf("0-9/708/XM.Serenity.gz" to 14L, "0-9/708/XM.Serenity.gz" to 120610L)))
        assertEquals(14L, ids.idFor("0-9/708/XM.Serenity.gz"))
    }
}
