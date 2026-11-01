// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.net

import com.przunk.protracktor.player.DownloadPlan
import com.przunk.protracktor.player.DownloadSizes
import org.junit.Assert.*
import org.junit.Test

/** Run unchanged against both compiled flag values; no alternate production configuration. */
class AmpBuildGateTest {
    @Test fun `the build gate bounds initial availability and attempts to reenable it`() {
        val access = AmpAccess(initiallyEnabled = true)
        access.setEnabled(true)
        assertEquals(Amp.ENABLED, access.enabled)
        assertEquals(Amp.ENABLED, access.capture() != null)
        val admitted = access.capture()?.let { ticket ->
            access.allows(ticket) && access.attach(ticket) {}
        } ?: false
        assertEquals(Amp.ENABLED, admitted)
    }

    @Test fun `discovery follows the compiled flag while retention always knows AMP`() {
        Amp.access.setEnabled(true)
        assertTrue(Amp in Catalogue.known)
        assertEquals(Amp.ENABLED, Amp in Catalogue.all)
        assertEquals(Amp.ENABLED, Catalogue.byId(Amp.id) != null)
        assertEquals(!Amp.ENABLED, CatalogueRetention.blocksReference("amp://A/Artist/MOD.song.gz"))
    }

    @Test fun `index download choices cannot bypass the build gate`() {
        Amp.access.setEnabled(true)
        assertEquals(Amp.ENABLED, Amp.id in DownloadPlan.choices())
        assertEquals(if (Amp.ENABLED) listOf(Amp.id) else emptyList<String>(),
            DownloadPlan.stepsFor(setOf(Amp.id)))
        assertEquals(if (Amp.ENABLED) DownloadSizes.AMP_MB else 0,
            DownloadPlan.megabytesFor(setOf(Amp.id)))
    }
}
