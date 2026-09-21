// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.engine

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three build settings without which UADE cannot run at all.
 *
 * **None of them is visible in any code path.** UADE is a second process: libuade forks and execs
 * `uadecore`, and an executable has to be a file on disk for `exec` to reach it. Three things have
 * to be true at once for that file to exist, and each one is a line in a build script that any
 * tidy-up would remove without a symptom until a phone says "the Amiga decoder is not set up".
 *
 * 1. The backend is built — `-DPROTRACKTOR_WITH_UADE=ON`, since `native/CMakeLists.txt` defaults
 *    it off for the browser, which has no `fork`.
 * 2. The emulator is named `libuadecore.so`, because Android packages `lib/<abi>/lib*.so` and
 *    nothing else.
 * 3. The native libraries are **extracted at install** — `useLegacyPackaging = true`. With the
 *    modern default they are mapped straight out of the APK and `nativeLibraryDir`, the one
 *    directory an app may execute from since Android 10, holds nothing.
 *
 * A test rather than a comment because the failure is silent, remote and three files apart.
 */
class UadecoreCanBeExecutedTest {

    private val root: File by lazy {
        var here: File? = File(".").absoluteFile
        while (here != null && !File(here, "settings.gradle.kts").isFile) here = here.parentFile
        assertTrue("the project root was not found from ${File(".").absolutePath}", here != null)
        here!!
    }

    private fun read(path: String): String {
        val file = File(root, path)
        assertTrue("${file.path} is missing", file.isFile)
        return file.readText()
    }

    @Test
    fun `the Gradle build asks for the UADE backend`() {
        assertTrue(
            "app/build.gradle.kts must pass -DPROTRACKTOR_WITH_UADE=ON; native/CMakeLists.txt " +
                "defaults it off because WebAssembly has no fork",
            read("app/build.gradle.kts").contains("-DPROTRACKTOR_WITH_UADE=ON"),
        )
    }

    @Test
    fun `the emulator is named as a library so Android packages it`() {
        val cmake = read("native/backends/uade/CMakeLists.txt")
        assertTrue(
            "uadecore must be built with PREFIX \"lib\" and SUFFIX \".so\", or it is not packaged",
            cmake.contains("PREFIX \"lib\"") && cmake.contains("SUFFIX \".so\""),
        )
    }

    @Test
    fun `the native libraries are extracted at install`() {
        val gradle = read("app/build.gradle.kts")
        val stripped = gradle.lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
        assertTrue(
            "useLegacyPackaging must be true, or nativeLibraryDir holds no libuadecore.so to exec",
            Regex("""useLegacyPackaging\s*=\s*true""").containsMatchIn(stripped),
        )
    }
}
