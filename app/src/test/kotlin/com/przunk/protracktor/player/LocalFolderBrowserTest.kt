// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.GrantedFolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFolderBrowserTest {
    @Test(timeout = 5_000)
    fun `obsolete local folder reads cannot publish after closing and reopening the same folder`() = runBlocking {
        val f = Fixture(this)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var first = true
        f.before = { if (it == "tracks:${f.folder.uri}" && first) {
            first = false
            entered.complete(Unit)
            gate.await()
        } }
        f.browser.openFolder(f.folder)
        entered.await()
        f.browser.closeFolder()
        f.browser.openFolder(f.folder)
        kotlinx.coroutines.yield()
        val expected = f.browse.value.copy(tracks = listOf(f.nested), loading = true)
        f.browse.value = expected
        gate.complete(Unit)
        drain()
        assertEquals(expected, f.browse.value)
    }

    private suspend fun CoroutineScope.drain() {
        while (coroutineContext.job.children.any()) coroutineContext.job.children.toList().joinAll()
    }

    private class Fixture(val scope: CoroutineScope) : LocalFolderBrowser.Store, LocalFolderBrowser.Access {
        val folder = GrantedFolder("tree:music", "Music")
        val other = GrantedFolder("tree:other", "Other")
        val intro = TrackRef("intro", "Intro", subtitle = "Music")
        val nested = TrackRef("nested", "Nested", subtitle = "Music/Atari/good")
        val browse = MutableStateFlow(BrowseState(
            domain = BrowseDomain.LOCAL, openFolder = other, query = "kept", loading = true,
            tracks = listOf(intro), folderAll = listOf("Old" to intro), folderRoot = "Old", folderAt = "Old/sub",
            subfolders = listOf(LocalTree.Folder("sub", "Old/sub", 1)), folderUnscanned = true,
            folderStale = true, scanProgress = 2 to 7,
        ))
        val events = mutableListOf<String>()
        val threads = mutableListOf<Thread>()
        var folders = listOf(folder, other)
        val tracks = mutableMapOf(folder.uri to listOf(intro, nested))
        var stale = true
        var before: suspend (String) -> Unit = {}
        var accessAction: (String) -> Unit = {}
        var onScan: (GrantedFolder) -> Unit = {}
        val browser = LocalFolderBrowser(scope, this, this, browse,
            backendFingerprint = { access("fingerprint"); "decoder-set" },
            startScan = { note("scan:${it.uri}"); onScan(it) },
        )
        private fun note(event: String) { events += event; threads += Thread.currentThread() }
        private suspend fun read(event: String) { note(event); before(event) }
        private fun access(event: String) { note(event); accessAction(event) }
        override suspend fun grantedFolders(): List<GrantedFolder> { read("folders"); return folders }
        override suspend fun rememberFolder(folder: GrantedFolder) { read("remember:${folder.uri}"); folders = listOf(folder, other) }
        override suspend fun forgetFolder(uri: String) { read("forget:$uri"); folders = folders.filterNot { it.uri == uri } }
        override suspend fun forgetIndex(uri: String) { read("index-delete:$uri"); tracks.remove(uri) }
        override suspend fun tracksIn(uri: String): List<TrackRef> { read("tracks:$uri"); return tracks[uri].orEmpty() }
        override suspend fun isStale(uri: String, fingerprint: String): Boolean {
            read("stale:$uri:$fingerprint")
            return stale
        }
        override fun grant(uri: String): GrantedFolder { access("grant:$uri"); return GrantedFolder(uri, "Music") }
        override fun rootPath(uri: String): String { access("root:$uri"); return if (uri == folder.uri) "Music" else "Other" }
    }

    private suspend fun CoroutineScope.withFailures(block: suspend (Fixture, MutableList<Throwable>) -> Unit) {
        val failures = mutableListOf<Throwable>()
        val owner = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, e -> failures += e })
        try { block(Fixture(owner), failures) } finally { owner.cancel(); owner.coroutineContext.job.join() }
    }

    @Test
    fun `construction does not read grants files or native state or launch a scan`() = runBlocking {
        val f = Fixture(this)
        assertTrue(f.events.isEmpty())
        assertTrue(coroutineContext.job.children.none())
        assertEquals("kept", f.browse.value.query)
    }

    @Test
    fun `refresh is launched on the existing scope and updates only the folder rows`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value
        f.browser.refreshFolders()
        assertTrue(f.events.isEmpty())
        drain()
        assertEquals(listOf("folders"), f.events)
        assertEquals(before.copy(folders = f.folders), f.browse.value)
        assertTrue(f.threads.all { it === Thread.currentThread() })
    }

    @Test
    fun `granting remembers and refreshes before requesting open and scan on a queued scope`() = runBlocking {
        val f = Fixture(this)
        f.onScan = {
            assertEquals(f.folder, it)
            assertEquals(f.folders, f.browse.value.folders)
            assertEquals(f.folder, f.browse.value.openFolder)
        }
        f.browser.rememberFolder(f.folder.uri)
        assertTrue(f.events.isEmpty())
        drain()
        assertEquals(listOf("grant:tree:music", "remember:tree:music", "folders", "scan:tree:music",
            "tracks:tree:music", "fingerprint", "stale:tree:music:decoder-set", "root:tree:music"), f.events)
        assertEquals(f.folder, f.browse.value.openFolder)
    }

    @Test(timeout = 5_000)
    fun `an unconfined scope preserves nested launch ordering and scans without awaiting index completion`() = runBlocking {
        val f = Fixture(CoroutineScope(coroutineContext + Dispatchers.Unconfined))
        val gate = CompletableDeferred<Unit>()
        f.before = { if (it == "tracks:tree:music") gate.await() }
        f.onScan = {
            assertEquals(f.folder, f.browse.value.openFolder)
            assertTrue(f.browse.value.loading)
        }
        f.browser.rememberFolder(f.folder.uri)
        assertEquals(listOf("grant:tree:music", "remember:tree:music", "folders", "scan:tree:music", "tracks:tree:music"), f.events)
        assertEquals(f.folder, f.browse.value.openFolder)
        assertTrue(f.browse.value.loading)
        gate.complete(Unit)
        drain()
        assertFalse(f.browse.value.loading)
    }

    @Test
    fun `grant or persistence failure stops before opening or scanning without clearing the old view`() = runBlocking {
        for (failedAt in listOf("grant:tree:music", "remember:tree:music", "folders")) withFailures { f, failures ->
            val before = f.browse.value
            val failure = IllegalStateException(failedAt)
            f.before = { if (it == failedAt) throw failure }
            f.accessAction = { if (it == failedAt) throw failure }
            f.browser.rememberFolder(f.folder.uri)
            f.scope.drain()
            assertSame(failure, failures.single())
            val steps = listOf("grant:tree:music", "remember:tree:music", "folders")
            assertEquals(steps.take(steps.indexOf(failedAt) + 1), f.events)
            assertEquals(before, f.browse.value)
        }
    }

    @Test
    fun `forgetting removes grant then index and closes the current view even when another folder is open`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value
        f.browser.forgetFolder(f.folder.uri)
        assertEquals(before, f.browse.value)
        drain()
        assertEquals(listOf("forget:tree:music", "index-delete:tree:music", "folders"), f.events)
        assertEquals(before.copy(folders = listOf(f.other), openFolder = null, tracks = emptyList()), f.browse.value)
        assertFalse(f.tracks.containsKey(f.folder.uri))
    }

    @Test
    fun `forget failure propagates at each step without pretending the displayed rows changed`() = runBlocking {
        val steps = listOf("forget:tree:music", "index-delete:tree:music", "folders")
        for (failedAt in steps) withFailures { f, failures ->
            val before = f.browse.value
            val failure = IllegalStateException(failedAt)
            f.before = { if (it == failedAt) throw failure }
            f.browser.forgetFolder(f.folder.uri)
            f.scope.drain()
            assertSame(failure, failures.single())
            assertEquals(steps.take(steps.indexOf(failedAt) + 1), f.events)
            assertEquals(before, f.browse.value)
        }
    }

    @Test
    fun `refresh remember and forget repeat the folder read inside atomic update retries`() = runBlocking {
        for (operation in listOf("refresh", "remember", "forget")) {
            val f = Fixture(this)
            var reads = 0
            f.before = { if (it == "folders" && ++reads == 1) f.browse.update { state -> state.copy(query = "concurrent") } }
            when (operation) {
                "refresh" -> f.browser.refreshFolders()
                "remember" -> f.browser.rememberFolder(f.folder.uri)
                "forget" -> f.browser.forgetFolder(f.folder.uri)
            }
            drain()
            assertEquals(operation, 2, reads)
            assertEquals("concurrent", f.browse.value.query)
            assertEquals(f.folders, f.browse.value.folders)
        }
    }

    @Test
    fun `an empty index is unscanned without reading the native fingerprint or starting a scan`() = runBlocking {
        val f = Fixture(this)
        f.tracks.clear()
        val before = f.browse.value
        f.browser.openFolder(f.folder)
        assertTrue(f.events.isEmpty())
        drain()
        assertEquals(listOf("tracks:tree:music", "root:tree:music"), f.events)
        assertEquals(before.copy(openFolder = f.folder, loading = false, tracks = emptyList(),
            folderAll = emptyList(), folderRoot = "Music", folderAt = "Music", subfolders = emptyList(),
            folderUnscanned = true, folderStale = false, navigationVisit = before.navigationVisit + 1), f.browse.value)
    }

    @Test(timeout = 5_000)
    fun `opening publishes loading before reading the index and checks staleness only after nonempty results`() = runBlocking {
        val f = Fixture(this)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        f.before = { if (it == "tracks:tree:music") { entered.complete(Unit); gate.await() } }
        val before = f.browse.value
        f.browser.openFolder(f.folder)
        entered.await()
        assertEquals(before.copy(openFolder = f.folder, loading = true, tracks = emptyList(),
            folderUnscanned = false, folderStale = false, navigationVisit = before.navigationVisit + 1), f.browse.value)
        assertEquals(listOf("tracks:tree:music"), f.events)
        f.browse.update { it.copy(query = "new query") }
        gate.complete(Unit)
        drain()
        assertEquals(listOf("tracks:tree:music", "fingerprint", "stale:tree:music:decoder-set", "root:tree:music"), f.events)
        assertEquals(before.copy(openFolder = f.folder, loading = false, tracks = listOf(f.intro),
            folderAll = listOf("Music" to f.intro, "Music/Atari/good" to f.nested), folderRoot = "Music", folderAt = "Music",
            subfolders = listOf(LocalTree.Folder("Atari", "Music/Atari", 1)), folderUnscanned = false,
            folderStale = true, query = "new query", navigationVisit = before.navigationVisit + 1), f.browse.value)
    }

    @Test
    fun `index fingerprint staleness and path failures retain loading and propagate without a scan`() = runBlocking {
        val steps = listOf("tracks:tree:music", "fingerprint", "stale:tree:music:decoder-set", "root:tree:music")
        for (failedAt in steps) withFailures { f, failures ->
            val before = f.browse.value
            val failure = IllegalStateException(failedAt)
            f.before = { if (it == failedAt) throw failure }
            f.accessAction = { if (it == failedAt) throw failure }
            f.browser.openFolder(f.folder)
            f.scope.drain()
            assertSame(failure, failures.single())
            assertEquals(steps.take(steps.indexOf(failedAt) + 1), f.events)
            assertEquals(before.copy(openFolder = f.folder, loading = true, tracks = emptyList(),
                folderUnscanned = false, folderStale = false, navigationVisit = before.navigationVisit + 1), f.browse.value)
        }
    }

    @Test(timeout = 5_000)
    fun `cancellation during list or index reads keeps the last publication without a completion update`() = runBlocking {
        for (open in listOf(false, true)) withFailures { f, failures ->
            val before = f.browse.value
            val entered = CompletableDeferred<Unit>()
            f.before = { entered.complete(Unit); awaitCancellation() }
            if (open) f.browser.openFolder(f.folder) else f.browser.refreshFolders()
            entered.await()
            f.scope.cancel()
            f.scope.coroutineContext.job.join()
            assertTrue(failures.isEmpty())
            assertEquals(listOf(if (open) "tracks:tree:music" else "folders"), f.events)
            assertEquals(if (open) before.copy(openFolder = f.folder, loading = true, tracks = emptyList(),
                folderUnscanned = false, folderStale = false,
                navigationVisit = before.navigationVisit + 1) else before, f.browse.value)
        }
    }

    @Test
    fun `tree snapshots keep a level only inside the same root and otherwise show the top`() = runBlocking {
        val f = Fixture(this)
        for ((root, at, keep) in listOf(Triple("Music", "Music/Atari/good", true),
            Triple("Music", "Music", true), Triple("Music", "Music/Atari/good", false),
            Triple("Old", "Music/Atari/good", true), Triple("Music", "Music2", true))) {
            val before = f.browse.value.copy(folderRoot = root, folderAt = at)
            val next = f.browser.treeOf(before, f.folder, listOf(f.intro, f.nested), keep)
            val retained = keep && root == "Music" && (at == "Music" || at.startsWith("Music/"))
            val level = if (retained) at else "Music"
            assertEquals(level, next.folderAt)
            assertEquals("Music", next.folderRoot)
            assertEquals(listOf("Music" to f.intro, "Music/Atari/good" to f.nested), next.folderAll)
            assertEquals(if (level == "Music") listOf(f.intro) else listOf(f.nested), next.tracks)
            assertEquals(before.query, next.query)
            assertEquals(before.scanProgress, next.scanProgress)
        }
        val moved = f.intro.copy(subtitle = "Opaque/Path")
        assertEquals("Opaque/Path", f.browser.treeOf(f.browse.value, f.folder, listOf(moved)).folderRoot)
    }

    @Test
    fun `opening a subfolder requires an open grant and changes only the displayed level`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value.copy(folderAll = listOf("Music" to f.intro, "Music/Atari/good" to f.nested))
        f.browse.value = before.copy(openFolder = null)
        val closed = f.browse.value
        f.browser.openSubfolder("Music/Atari/good")
        assertSame(closed, f.browse.value)
        f.browse.value = before
        f.browser.openSubfolder("Music/Atari/good")
        assertEquals(before.copy(folderAt = "Music/Atari/good", subfolders = emptyList(), tracks = listOf(f.nested),
            navigationVisit = before.navigationVisit + 1), f.browse.value)
        assertTrue(f.events.isEmpty())
    }

    @Test
    fun `closing a folder clears only its open reference and visible tracks`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value
        f.browser.closeFolder()
        assertEquals(before.copy(openFolder = null, tracks = emptyList(), loading = false,
            navigationVisit = before.navigationVisit + 1), f.browse.value)
        assertTrue(f.events.isEmpty())
        assertTrue(coroutineContext.job.children.none())
    }

    @Test
    fun `tree path derivation remains inside atomic publication retries while native checks run once`() = runBlocking {
        val f = Fixture(this)
        var paths = 0
        f.accessAction = { if (it == "root:tree:music" && ++paths == 1) f.browse.update { state -> state.copy(query = "concurrent") } }
        f.browser.openFolder(f.folder)
        drain()
        assertEquals(2, paths)
        assertEquals(1, f.events.count { it == "fingerprint" })
        assertEquals(1, f.events.count { it.startsWith("stale:") })
        assertEquals("concurrent", f.browse.value.query)
    }

    @Test(timeout = 5_000)
    fun `a late folder read cannot publish its tree into the newer selection`() = runBlocking {
        val f = Fixture(this)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val otherRendered = CompletableDeferred<Unit>()
        f.before = { if (it == "tracks:tree:music") { entered.complete(Unit); gate.await() } }
        f.accessAction = { if (it == "root:tree:other") otherRendered.complete(Unit) }
        f.browser.openFolder(f.folder)
        entered.await()
        f.browser.openFolder(f.other)
        otherRendered.await()
        assertEquals(f.other, f.browse.value.openFolder)
        assertEquals("Other", f.browse.value.folderRoot)
        gate.complete(Unit)
        drain()
        assertEquals(f.other, f.browse.value.openFolder)
        assertEquals("Other", f.browse.value.folderRoot)
        assertTrue(f.browse.value.tracks.isEmpty())
        assertTrue(f.events.none { it.startsWith("scan:") })
    }
}
