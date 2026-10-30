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

    private fun cases(name: String, header: String): List<List<String>> {
        val root=generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it,"settings.gradle.kts").isFile }
        val text=File(root,"docs/rules/$name.tsv").readText()
        val rows=text.lineSequence().filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        assertEquals(header,rows.first())
        val cases=rows.drop(1)
        assertEquals(Regex("(?m)^# cases: (\\d+)$").find(text)!!.groupValues[1].toInt(),cases.size)
        assertTrue(cases.isNotEmpty())
        return cases.map { it.split('\t') }
    }
    private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun `shared WEB profile validation uses native usability boundaries`() {
        for (row in cases("loudness-profile-cases","case\tseconds\tlufs\tpeak\tcomplete\tusable")) {
            val values=row.subList(1,5).map(String::toDouble).toDoubleArray()
            assertEquals(row[0],row[5]=="1",LoudnessProfile.fromNative(values)!=null)
        }
    }
    @Test fun `shared WEB binary records match native payload checksum and corruption refusal`() {
        val dir=temporary.newFolder(); val cache=LoudnessProfiles(dir)
        for (row in cases("loudness-encoding-cases","case\tseconds\tlufs\tpeak\tcomplete\tpayload")) {
            val profile=LoudnessProfile(row[1].toDouble(),row[2].toDouble(),row[3].toDouble(),row[4]=="1")
            val key=LoudnessProfiles.key(base(name=row[0]),0,100)
            cache.write(key,profile)
            val file=File(dir,"$key.profile")
            val expected=bytes(row[5])
            assertArrayEquals(row[0],expected,file.readBytes())
            assertEquals(profile,cache.read(key))
            expected[19]=(expected[19].toInt() xor 1).toByte(); file.writeBytes(expected)
            assertNull(row[0],cache.read(key))
        }
    }
    @Test fun `shared WEB identities preserve native byte framing UTF8 ordering and variants`() {
        for (row in cases("loudness-identity-cases","case\tname\tbytes\trevision\tcompanions\tsubsong\twidth\tbase\tkey")) {
            val companions=if (row[4]=="-") emptyList() else row[4].split(';').map {
                val (name,hex)=it.split('='); name to bytes(hex)
            }
            val base=LoudnessProfiles.contentKey(bytes(row[2]),row[1],companions,row[3])
            assertEquals(row[0],row[7],base)
            assertEquals(row[0],row[8],LoudnessProfiles.key(base,row[5].toInt(),row[6].toInt()))
        }
    }

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
