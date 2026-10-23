// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/** Keep an opened decoder caller-private until its cancellable preparation can be adopted. */
internal suspend fun <T> AutoCloseable.prepareOrClose(prepare: suspend () -> T): T = try {
    prepare().also { coroutineContext.ensureActive() }
} catch (failure: Throwable) {
    close()
    throw failure
}

/** One analysis decoder at a time; stale work can populate cache but never controls playback. */
internal class LoudnessPreparation(
    private val cache: LoudnessProfiles,
    private val revision: () -> String,
    private val measure: (ByteArray,String,List<Pair<String,ByteArray>>,Int,Int) -> DoubleArray?,
) {
    private val mutex=Mutex()
    data class Prepared(val base: String, val profile: LoudnessProfile?)
    /** Worker thread only. A negative subsong means the decoder's actual initial audible tune. */
    suspend fun prepare(bytes: ByteArray, name: String, companions: List<Pair<String,ByteArray>>,
        subsong: Int, width: Int): Prepared {
        val base=LoudnessProfiles.contentKey(bytes,name,companions,revision())
        return mutex.withLock {
            coroutineContext.ensureActive()
            // The -1 alias only skips repeated warming. Audible playback always asks its actual index.
            val known=cache.read(LoudnessProfiles.key(base,subsong,width))
            if (known != null) return@withLock Prepared(base,known)
            val values=try { measure(bytes,name,companions,subsong,width) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            coroutineContext.ensureActive()
            val profile=LoudnessProfile.fromNative(values)
            val actual=values?.getOrNull(4)?.takeIf { it.isFinite() && it >= 0 && it <= 65535 && it % 1.0 == 0.0 }?.toInt()
            if (profile != null && actual != null) {
                cache.write(LoudnessProfiles.key(base,actual,width),profile)
                if (subsong<0) cache.write(LoudnessProfiles.key(base,-1,width),profile)
            }
            Prepared(base,profile.takeIf { subsong<0 || actual==subsong })
        }
    }
}
