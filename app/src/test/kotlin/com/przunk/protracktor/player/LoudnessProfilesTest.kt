// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LoudnessProfilesTest {
    @get:Rule val temporary=TemporaryFolder()
    private val profile=LoudnessProfile(20.0,-23.0,.4,false)
    private fun base(bytes: ByteArray=byteArrayOf(1), name: String="a.mod", companions: List<Pair<String,ByteArray>> = emptyList(),
        revision: String="decoder1") = LoudnessProfiles.contentKey(bytes,name,companions,revision)

    @Test fun `content identity includes decoder filename companions subsong and stereo mode`() {
        val b=base()
        assertNotEquals(b,base(bytes=byteArrayOf(2)))
        assertNotEquals(b,base(name="a.sid"))
        assertNotEquals(b,base(revision="decoder2"))
        assertNotEquals(base(companions=listOf("sample" to byteArrayOf(1))),
            base(companions=listOf("sample" to byteArrayOf(2))))
        assertNotEquals(LoudnessProfiles.key(b,0,100),LoudnessProfiles.key(b,1,100))
        assertNotEquals(LoudnessProfiles.key(b,0,100),LoudnessProfiles.key(b,0,0))
    }
    @Test fun `cache survives a new reader and prefers a complete or longer measurement`() {
        val dir=temporary.newFolder(); val key=LoudnessProfiles.key(base(),0,100)
        val cache=LoudnessProfiles(dir); cache.write(key,profile)
        assertEquals(profile,LoudnessProfiles(dir).read(key))
        cache.write(key,profile.copy(seconds=2.0,lufs=-10.0))
        assertEquals(profile,cache.read(key))
        val complete=profile.copy(seconds=10.0,complete=true)
        cache.write(key,complete); cache.write(key,profile.copy(seconds=100.0))
        assertEquals(complete,cache.read(key))
    }
    @Test fun `invalid and corrupt cache entries are misses not playback failures`() {
        val dir=temporary.newFolder(); val key=LoudnessProfiles.key(base(),0,100)
        val cache=LoudnessProfiles(dir)
        assertNull(cache.read(key)); cache.write(key,profile.copy(lufs=Double.NaN)); assertNull(cache.read(key))
        File(dir,"$key.profile").writeText("truncated")
        assertNull(cache.read(key)); cache.write(key,profile); assertEquals(profile,cache.read(key))
        val record=File(dir,"$key.profile"); val damaged=record.readBytes()
        damaged[19]=(damaged[19].toInt() xor 1).toByte(); record.writeBytes(damaged)
        assertNull("A finite-looking bit flip must not become an excessive boost",cache.read(key))
        assertNull(cache.read("../escape")); cache.write("../escape",profile)
        assertFalse(File(dir.parentFile,"escape.profile").exists())
    }
    @Test fun `silent short or malformed native results cannot become boost profiles`() {
        for (values in listOf(null,doubleArrayOf(1.0),doubleArrayOf(.2,-20.0,.2,0.0),
            doubleArrayOf(20.0,-100.0,0.0,1.0),doubleArrayOf(Double.NaN,-20.0,.2,0.0)))
            assertNull(LoudnessProfile.fromNative(values))
        assertEquals(profile,LoudnessProfile.fromNative(doubleArrayOf(20.0,-23.0,.4,0.0)))
    }
}
