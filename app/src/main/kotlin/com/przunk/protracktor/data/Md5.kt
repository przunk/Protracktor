// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import java.security.MessageDigest

/**
 * The hash two databases key on, computed once.
 *
 * HVSC's song lengths and the songdb metadata table are both looked up by the MD5 of the whole
 * file, and each store used to compute its own -- so opening a track hashed it twice, over bytes
 * that can run to several megabytes. Neither store was wrong; together they were doing the work
 * twice, which is the sort of thing that only shows up once there are two of them.
 */
object Md5 {
    fun of(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
}
