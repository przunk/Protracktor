// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.engine

/**
 * The engine's `describe` block, read into fields.
 *
 * `key<TAB>value` lines, with one exception the engine arranges on purpose: **`message` is written
 * last because it is the one value with line breaks in it** (`native/engine/engine.cpp`) -- a
 * module's greetings, laid out for a tracker's fixed-width screen. Read line by line, as this used
 * to be, its first line survived and the rest were dropped, or became keys of their own where one
 * happened to hold a tab (`docs/STATUS.md` C40). So everything after `message<TAB>` at the start of
 * a line is the message, whole.
 *
 * Kept apart from [NativeEngine], which loads the native library the moment it is touched, so the
 * rule can be tested on the JVM. The page reads the block by the same rule (`describeFields`).
 */
object DescribeBlock {

    private const val MESSAGE = "message\t"

    fun parse(text: String): Map<String, String> {
        val at = when {
            text.startsWith(MESSAGE) -> 0
            else -> text.indexOf("\n$MESSAGE").let { if (it < 0) -1 else it + 1 }
        }
        val head = if (at < 0) text else text.substring(0, at)
        val fields = head.lineSequence()
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
            }
            .toMap(LinkedHashMap())
        if (at >= 0) fields["message"] = text.substring(at + MESSAGE.length).trimEnd()
        return fields
    }
}
