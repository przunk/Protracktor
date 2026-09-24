// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.net.UnExoticA
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which way each archive's tune is asked whether it is on the phone (the owner, 2026-09-24). */
class OnPhoneTest {

    @Test
    fun `a file fetched on its own is asked of the cache by its address`() {
        val modland = "https://modland.com/pub/modules/Protracker/4-Mat/elysium.mod"
        assertEquals(OnPhone.Check.Cached(modland), OnPhone.checkFor(modland))
        val modArchive = "https://api.modarchive.org/downloads.php?moduleid=42#lotus.mod"
        assertEquals(OnPhone.Check.Cached(modArchive), OnPhone.checkFor(modArchive))
    }

    @Test
    fun `an ASMA tune is on the phone when ASMA is`() {
        assertEquals(OnPhone.Check.Archive("asma"), OnPhone.checkFor("asma://asma/Composers/przunk/Bonio.sap"))
    }

    @Test
    fun `an UnExoticA tune is on the phone when its game's archive is cached`() {
        val path = "Game/Composer/Title.lha/Title/mod.name"
        assertEquals(
            OnPhone.Check.Cached(UnExoticA.archiveUrlFor(path)!!),
            OnPhone.checkFor("unexotica://$path"),
        )
    }

    @Test
    fun `a file of the phone's own library is on the phone`() {
        assertEquals(OnPhone.Check.Local, OnPhone.checkFor("content://com.android.externalstorage.documents/document/primary%3AMusic%2Fx.mod"))
        assertEquals(OnPhone.Check.Local, OnPhone.checkFor("file:///sdcard/x.mod"))
        assertEquals(OnPhone.Check.Never, OnPhone.checkFor("something-else"))
    }
}
