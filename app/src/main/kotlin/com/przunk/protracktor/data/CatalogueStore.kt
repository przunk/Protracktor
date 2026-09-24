// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.ContentValues
import android.content.Context
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.CatalogueEntry
import com.przunk.protracktor.player.SupportedFormats
import com.przunk.protracktor.player.Platforms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One `catalogues` row as stored, which outgrew being a `Triple` when `complete` arrived. */
private data class StoredIndex(
    val trackCount: Int = 0,
    val indexedAt: Long? = null,
    val backends: String = "",
    val complete: Boolean = false,
    /**
     * How many rows the archive has, against [trackCount]'s "how many of them this build opens".
     *
     * Two numbers since 2026-09-17, because the index keeps everything (`docs/ROADMAP_FORMATS.md`
     * step 0). Saying only one of them is how the owner met a snackbar reporting 500,000-odd over a
     * row reporting 341,842.
     */
    val archiveCount: Int = 0,
)

data class CatalogueSummary(
    val id: String,
    val displayName: String,
    val trackCount: Int,
    val indexedAt: Long?,
    val isOnlineOnly: Boolean = false,
    /**
     * Which decoders built this index, or empty when it was built before that was recorded.
     *
     * An index is filtered at index time to what a backend can play, so one built by a different
     * set is missing whatever arrived since — and looks empty rather than out of date.
     */
    val backends: String = "",
    /**
     * Whether this index holds the whole archive rather than one build's idea of it.
     *
     * **False is what every index written before 2026-09-17 is**, and it is the reason [isStale]
     * still exists. Those hold only the rows the format list of the day accepted, so a format added
     * afterwards has no row here to be re-decided and the archive's index has to be fetched again.
     * An index written since holds everything and is never stale for that reason —
     * `refreshPlayable` answers the question locally, in 228ms, with no network.
     */
    val complete: Boolean = false,
    val archiveCount: Int = 0,
) {
    /**
     * Whether this phone holds this catalogue's index at all.
     *
     * **Rows of any kind, not playable rows.** Since the index keeps everything the archive lists
     * (`docs/ROADMAP_FORMATS.md` step 0), [trackCount] is how much of it *this build opens* and
     * [archiveCount] is how much there is. Asking only the first would report a fully downloaded
     * catalogue as missing whenever this build happens to play none of it -- and then offer to
     * download it again, which would change nothing.
     */
    val indexed: Boolean get() = trackCount > 0 || archiveCount > 0 || isOnlineOnly
    /** A downloaded catalogue with no index cannot be browsed until the user fetches it again. */
    val requiresIndex: Boolean get() = !isOnlineOnly && !indexed

    /**
     * Whether this index was built by decoders this build no longer has — or by an unknown set.
     *
     * An empty `backends` is an index written before this column existed, and such an index can be
     * missing a decoder's whole catalogue while looking current — 60,572 C64 tunes, when
     * libsidplayfp arrived. Unknown counts as stale for that reason.
     */
    fun isStale(current: String): Boolean =
        !isOnlineOnly && trackCount > 0 && !complete && backends != current
}

/** A row of an online catalogue, ready to become a playable reference. */
data class CatalogueTrack(
    val catalogueId: String,
    val path: String,
    val format: String,
    val author: String,
    val title: String,
    val size: Long,
)

/** A browsing level: a format or an author, with how much is inside. */
data class CatalogueGroup(val name: String, val count: Int)

/**
 * The downloaded catalogue indexes.
 *
 * Kept in the same database as everything else so browsing an online archive works with no network
 * at all — the index is fetched once, and the network is next needed only when a track is played.
 */
class CatalogueStore(context: Context) {

    private val helper = ProtracktorDatabase.of(context)

    suspend fun summaries(): List<CatalogueSummary> = withContext(Dispatchers.IO) {
        val stored = helper.readableDatabase
            .rawQuery(
                "SELECT id, track_count, indexed_at, backends, complete, archive_count FROM catalogues",
                null,
            )
            .use { row ->
                buildMap {
                    while (row.moveToNext()) {
                        put(
                            row.getString(0),
                            StoredIndex(
                                trackCount = row.getInt(1),
                                indexedAt = if (row.isNull(2)) null else row.getLong(2),
                                backends = row.getString(3).orEmpty(),
                                complete = row.getInt(4) != 0,
                                archiveCount = row.getInt(5),
                            ),
                        )
                    }
                }
            }

        // Driven by the catalogues the app knows about, not by what happens to be in the table.
        // A catalogue nobody has indexed yet still has to appear, or there is no way to index it.
        //
        // Search-only ones last: they are the only rows with no download arrow, and a gap in the
        // middle of a column of buttons reads as something missing. Sorted by the property rather
        // than by naming The Mod Archive, so the next one lands right without anybody remembering.
        // `sortedBy` is stable, so the rest keep the order `Catalogue.all` declares.
        Catalogue.all.sortedBy { it.isOnlineOnly }.map { catalogue ->
            val held = stored[catalogue.id] ?: StoredIndex()
            CatalogueSummary(
                id = catalogue.id,
                displayName = catalogue.displayName,
                trackCount = held.trackCount,
                indexedAt = held.indexedAt,
                isOnlineOnly = catalogue.isOnlineOnly,
                backends = held.backends,
                complete = held.complete,
                archiveCount = held.archiveCount,
            )
        }
    }

    /**
     * Replaces a catalogue's contents wholesale.
     *
     * One transaction with a compiled statement: half a million rows inserted one autocommit at a
     * time would take minutes rather than seconds, and a half-written index is worse than none.
     */
    /**
     * Throws one catalogue's index away, rows and all.
     *
     * The `catalogues` row goes too rather than being zeroed. A row with `track_count = 0` and a
     * recorded `backends` is how an index that *failed* looks; a catalogue that was never indexed
     * has no row at all, and that is the state being returned to. Leaving the row would make a
     * deliberate delete indistinguishable from a broken download.
     *
     * Re-fetchable: the index is a file on the catalogue's own server and downloading it again is
     * the button that is already there.
     */
    suspend fun clearIndex(catalogueId: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.transaction {
            delete("catalogue_tracks", "catalogue_id = ?", arrayOf(catalogueId))
            delete("catalogues", "id = ?", arrayOf(catalogueId))
        }
    }

    /**
     * Throws away rows belonging to catalogues this build no longer offers.
     *
     * **This is what makes turning a catalogue off mean something.** `Catalogue.all` decides what is
     * browsable, so removing an entry from it hides a catalogue immediately — and leaves its rows in
     * the database, taking space and appearing in a global search, which is not "off" but "hidden".
     * UnExoticA is behind a switch for a reason (`docs/PLAN_UNEXOTICA.md`), and the reason is that
     * the switch might actually be thrown.
     *
     * Run once at start-up. Cheap when there is nothing to do: a `DELETE ... NOT IN` over an indexed
     * column that matches no rows.
     */
    suspend fun pruneUnknownCatalogues() = withContext(Dispatchers.IO) {
        val known = Catalogue.all.map { it.id }
        val placeholders = known.joinToString(",") { "?" }
        val ids = known.toTypedArray()

        // **Asked before it is done, because the asking is free and the doing is not.**
        //
        // This runs at every launch, and `catalogue_id NOT IN (...)` over `catalogue_tracks` is a
        // full scan: nothing indexes a negation, and since v15 the two browse indexes are partial
        // (`WHERE playable = 1`) so they could not serve it even if it were positive. Measured on
        // 516,000 rows: **55.6 ms on a desktop**, in a write transaction, on the one connection
        // every other read at launch is queueing behind — and a phone's storage is several times
        // slower than that (`docs/BACKLOG.md` A48).
        //
        // The `catalogues` table has one row per catalogue and answers the same question in
        // **0.0 ms**. A catalogue that has rows but no row of its own is not reachable by anything
        // this app does: `replaceIndex` writes both in one transaction and this deletes both in
        // one. If that is ever untrue, it is a defect in whatever broke the pair, and scanning half
        // a million rows at every launch is not the way to find out.
        val stale = helper.readableDatabase
            .rawQuery("SELECT EXISTS(SELECT 1 FROM catalogues WHERE id NOT IN ($placeholders))", ids)
            .use { row -> row.moveToFirst() && row.getInt(0) == 1 }
        if (!stale) return@withContext

        helper.writableDatabase.transaction {
            delete("catalogue_tracks", "catalogue_id NOT IN ($placeholders)", ids)
            delete("catalogues", "id NOT IN ($placeholders)", ids)
        }
    }

    /** @return how many of [entries] this build can open, which is what the catalogue then reports. */
    suspend fun replaceIndex(catalogue: Catalogue, entries: List<CatalogueEntry>, backends: String): Int =
        withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            var playableCount = 0
            db.transaction {
                insertWithOnConflict(
                    "catalogues", null,
                    ContentValues().apply {
                        put("id", catalogue.id)
                        put("display_name", catalogue.displayName)
                        put("indexed_at", System.currentTimeMillis())
                        // **What can be played, not what was stored.** Since the index keeps
                        // every row, `entries.size` is the archive's size and would tell the owner
                        // he has half a million tunes he cannot open. Filled in below, once the
                        // rows have been counted.
                        put("track_count", 0)
                        // What produced this index. It no longer decides what the index *holds*
                        // -- every row is kept -- but it still records which format list decided
                        // the `playable` flags, so a change to that list can be noticed and the
                        // flags re-decided without a download (`refreshPlayable`).
                        put("backends", backends)
                        // Written by a build that keeps every row, so no format added later can
                        // find this index short of anything.
                        put("complete", 1)
                        put("archive_count", entries.size)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                )
                delete("catalogue_tracks", "catalogue_id = ?", arrayOf(catalogue.id))

                val insert = compileStatement(
                    "INSERT OR REPLACE INTO catalogue_tracks " +
                        "(catalogue_id, path, format, author, title, size, ext, pre, playable, folded, platform) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )
                insert.use { statement ->
                    entries.forEach { entry ->
                        // **Every row, whether this build can play it or not.** The two halves of
                        // the name go in beside it, and the verdict is `playable` -- which one
                        // `UPDATE` re-decides when the format list changes, instead of a download
                        // (`docs/ROADMAP_FORMATS.md` step 0).
                        val ext = SupportedFormats.extensionOf(entry.title)
                        val pre = SupportedFormats.prefixOf(entry.title)
                        statement.clearBindings()
                        statement.bindString(1, catalogue.id)
                        statement.bindString(2, entry.path)
                        statement.bindString(3, entry.format)
                        statement.bindString(4, entry.author)
                        statement.bindString(5, entry.title)
                        statement.bindLong(6, entry.size)
                        statement.bindString(7, ext)
                        statement.bindString(8, pre)
                        val playable = SupportedFormats.offeredInCatalogue(entry.format, entry.title)
                        if (playable) playableCount++
                        statement.bindLong(9, if (playable) 1L else 0L)
                        // Search's folded copy, for the few rows with accents (A53).
                        SearchTerms.foldedOrNull(entry.title, entry.author)
                            ?.let { statement.bindString(10, it) } ?: statement.bindNull(10)
                        // Its machine, by the one rule (C89): the archive, the directory, the name.
                        statement.bindString(
                            11,
                            Platforms.forCatalogueRow(catalogue.id, entry.format, entry.title)?.id.orEmpty(),
                        )
                        statement.executeInsert()
                    }
                }
                update(
                    "catalogues",
                    ContentValues().apply { put("track_count", playableCount) },
                    "id = ?", arrayOf(catalogue.id),
                )
            }
            playableCount
        }

    /**
     * Records that these indexes were judged by the current format list.
     *
     * **Only the whole ones.** A partial index — written before the index stopped being a function
     * of that list — is short of rows for any format added since, and nothing local can supply
     * them; leaving its stamp alone is what keeps it saying so.
     */
    suspend fun restampComplete(backends: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update(
            "catalogues",
            ContentValues().apply { put("backends", backends) },
            "complete = 1", null,
        )
        Unit
    }

    /**
     * Re-decides `playable` for every stored row, for nothing but the cost of one statement.
     *
     * **This is what item 0 of `docs/ROADMAP_FORMATS.md` bought.** Adding a format used to mean
     * every user downloading Modland's 40 MB again, because the index held only what the old list
     * accepted. It holds everything now, so a new format is a question already answered by rows
     * that are already here: 228ms over 516,107 of them, measured, and no network.
     *
     * `ext` and `pre` were written from the filename and never from the list, which is why this can
     * be a single `UPDATE` rather than half a million round trips through Kotlin.
     *
     * @return how many rows are playable afterwards, so the caller can say what changed.
     */
    suspend fun refreshPlayable(): Int = withContext(Dispatchers.IO) {
        val (sql, bound) = playableUpdate(
            SupportedFormats.extensions, SupportedFormats.prefixes, SupportedFormats.refusedDirectories,
        )
        helper.writableDatabase.transaction {
            execSQL(sql, bound)
            // And every row's platform, by the same rule a new row is written with (C89).
            val (platform, platformBound) = platformUpdate()
            execSQL(platform, platformBound)
            // The count every catalogue shows follows the flags, or the number on screen is the
            // answer to a question the app stopped asking.
            execSQL(
                "UPDATE catalogues SET track_count = (" +
                    "SELECT COUNT(*) FROM catalogue_tracks t WHERE t.catalogue_id = catalogues.id " +
                    "AND t.playable = 1)"
            )
        }
        helper.readableDatabase
            .rawQuery("SELECT COUNT(*) FROM catalogue_tracks WHERE playable = 1", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    suspend fun formats(catalogueId: String): List<CatalogueGroup> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT format, COUNT(*) FROM catalogue_tracks WHERE catalogue_id = ? AND playable = 1 " +
                "GROUP BY format ORDER BY format",
            arrayOf(catalogueId),
        ).use { it.toGroups() }
    }

    suspend fun authors(catalogueId: String, format: String): List<CatalogueGroup> =
        withContext(Dispatchers.IO) {
            helper.readableDatabase.rawQuery(
                "SELECT author, COUNT(*) FROM catalogue_tracks WHERE catalogue_id = ? AND format = ? AND playable = 1 " +
                    "GROUP BY author ORDER BY author",
                arrayOf(catalogueId, format),
            ).use { it.toGroups() }
        }

    suspend fun tracks(catalogueId: String, format: String, author: String): List<CatalogueTrack> =
        withContext(Dispatchers.IO) {
            helper.readableDatabase.rawQuery(
                "SELECT catalogue_id, path, format, author, title, size FROM catalogue_tracks " +
                    "WHERE catalogue_id = ? AND format = ? AND author = ? AND playable = 1 ORDER BY title",
                arrayOf(catalogueId, format, author),
            ).use { it.toTracks() }
        }

    /** How many rows [search] would return without its limit. Same `WHERE`, no `ORDER BY`. */
    suspend fun countMatches(
        query: String,
        catalogueIds: Set<String>,
        platforms: Set<String> = emptySet(),
    ): Int =
        withContext(Dispatchers.IO) {
            // Blank is allowed here too; see `search`. A count that refused would report zero
            // matches beside a screen full of them.
            val scope = if (catalogueIds.isEmpty()) {
                "" to emptyArray<String>()
            } else {
                val placeholders = catalogueIds.joinToString(",") { "?" }
                " AND catalogue_id IN ($placeholders)" to catalogueIds.toTypedArray()
            }
            val words = SearchTerms.sqlFor(query, "title", "author", sparse = "folded")
            val byPlatform = platformClause(platforms)
            helper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM catalogue_tracks " +
                    "WHERE playable = 1 AND (${words.first})${scope.first}${byPlatform.first}",
                words.second + scope.second + byPlatform.second,
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }

    /**
     * How many playable rows each platform holds, by the `platform` column (C89).
     *
     * What makes the platform chips honest: a count of tunes that will actually open, so a platform
     * with none has nothing to offer rather than merely nothing indexed. It used to be counted by
     * directory, which left ASMA out of Atari 8-bit here as well as in the filter.
     *
     * One grouped scan, and only when the filter is drawn.
     */
    suspend fun platformCounts(): Map<String, Int> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT platform, COUNT(*) FROM catalogue_tracks WHERE playable = 1 AND platform <> '' GROUP BY platform", emptyArray(),
        ).use { row ->
            buildMap { while (row.moveToNext()) put(row.getString(0), row.getInt(1)) }
        }
    }

    /**
     * Title and author search.
     *
     * @param catalogueIds empty means every indexed catalogue.
     * @param platforms empty means every platform; otherwise `Platforms` ids, matched against the
     *   `platform` column each row is written with (C89).
     * @param query may be blank, which matches everything the other two allow.
     */
    suspend fun search(
        query: String,
        catalogueIds: Set<String>,
        limit: Int,
        platforms: Set<String> = emptySet(),
    ): List<CatalogueTrack> =
        withContext(Dispatchers.IO) {
            // No blank guard. It made sense while typing was the only way to narrow a search --
            // an empty box asked nothing -- and stopped making sense when a scope became a question
            // of its own. `%%` matches every row, so "everything on the Commodore 64" is a platform
            // and an empty field, and the caller's per-source cap is what bounds it.
            //
            // The controller was changed to allow this and this was not, so the search returned the
            // one track that came from a playlist and looked like a broken filter.
            val scope = if (catalogueIds.isEmpty()) {
                "" to emptyArray<String>()
            } else {
                val placeholders = catalogueIds.joinToString(",") { "?" }
                " AND catalogue_id IN ($placeholders)" to catalogueIds.toTypedArray()
            }
            // **Every word, anywhere, in any order** (`SearchTerms`), which is also where the
            // escaping lives: a user typing % or _ searches for those characters rather than
            // matching everything.
            val words = SearchTerms.sqlFor(query, "title", "author", sparse = "folded")
            val byPlatform = platformClause(platforms)
            helper.readableDatabase.rawQuery(
                "SELECT catalogue_id, path, format, author, title, size FROM catalogue_tracks " +
                    "WHERE playable = 1 AND (${words.first})${scope.first}${byPlatform.first} ORDER BY title LIMIT ?",
                words.second + scope.second + byPlatform.second + arrayOf(limit.toString()),
            ).use { it.toTracks() }
        }

    /** One track at random from the indexed catalogues, or null when nothing is indexed. */
    /**
     * Where a track sits in its catalogue.
     *
     * Asked of the database rather than worked out from the path, so that jumping to a track's
     * neighbours lands exactly where browsing to them lands: the format and author here are the
     * ones the index parser produced, whatever it decided about a path with too few or too many
     * segments.
     */
    suspend fun locate(catalogueId: String, path: String): CatalogueTrack? =
        withContext(Dispatchers.IO) {
            helper.readableDatabase.rawQuery(
                // **Not filtered by `playable`**, deliberately: this answers "where does this path
                // sit", which is a fact about the archive, and it is asked about a track already
                // playing. A row that stopped being playable should still be locatable.
                "SELECT catalogue_id, path, format, author, title, size FROM catalogue_tracks " +
                    "WHERE catalogue_id = ? AND path = ? LIMIT 1",
                arrayOf(catalogueId, path),
            ).use { it.toTracks().firstOrNull() }
        }

    /**
     * Several at once, for reading ahead.
     *
     * One query rather than [count] of them, and it cannot hand back the same track twice inside a
     * batch. `ORDER BY RANDOM()` scans the table, so asking once for three costs what asking once
     * for one does -- and a third of what three separate calls would.
     */
    /**
     * @param formats when non-empty, Modland directory names — the same `format` column the search
     * filter narrows by, and the same set `Platforms` produces. Narrowing the dice to one machine
     * is this clause and nothing else.
     * @param favouritesOnly draws from Modland's published favourites instead of the whole index.
     * A join rather than a fourth kind of table: the list is 991 paths, everything else about those
     * tunes is already here, and the join is also what drops the favourites this build cannot play.
     * Deliberately **not** combined with [formats]: the list is Amiga tracker music almost
     * entirely, so narrowing it by machine would offer a chip that mostly returns nothing.
     */
    suspend fun randomSample(
        count: Int,
        catalogueIds: Set<String> = emptySet(),
        platforms: Set<String> = emptySet(),
        favouritesOnly: Boolean = false,
    ): List<CatalogueTrack> =
        withContext(Dispatchers.IO) {
            if (count <= 0) return@withContext emptyList()
            val (where, arguments) = randomWhere(catalogueIds, platforms, favouritesOnly)
            helper.readableDatabase.rawQuery(
                "SELECT catalogue_id, path, format, author, title, size FROM catalogue_tracks" +
                    "$where ORDER BY RANDOM() LIMIT $count",
                arguments,
            ).use { it.toTracks() }
        }

    private fun android.database.Cursor.toGroups(): List<CatalogueGroup> =
        buildList { while (moveToNext()) add(CatalogueGroup(getString(0), getInt(1))) }

    private fun android.database.Cursor.toTracks(): List<CatalogueTrack> = buildList {
        while (moveToNext()) {
            add(
                CatalogueTrack(
                    catalogueId = getString(0),
                    path = getString(1),
                    format = getString(2),
                    author = getString(3),
                    title = getString(4),
                    size = getLong(5),
                )
            )
        }
    }
}

/**
 * The `WHERE` a random draw uses, and its arguments.
 *
 * **Lifted out of [randomSample] so it can be proved.** The dice is the one reader where an
 * unplayable row would not merely look wrong but waste the listener's time — it would open a
 * file the app cannot decode and move on — and it is also the only one whose filter is built
 * from a list rather than written into the SQL, where a reader scanning for `playable` does not
 * see it. `CataloguePlayableTest` runs this against a real SQLite.
 *
 * `playable = 1` is first and never optional. The index holds every row the archive has
 * (`docs/ROADMAP_FORMATS.md` step 0), so a draw without it draws from the whole archive.
 */
internal fun randomWhere(
    catalogueIds: Set<String>,
    platforms: Set<String>,
    favouritesOnly: Boolean,
): Pair<String, Array<String>> {
    val clauses = mutableListOf("playable = 1")
    val arguments = mutableListOf<String>()
    if (catalogueIds.isNotEmpty()) {
        clauses += "catalogue_id IN (${catalogueIds.joinToString(",") { "?" }})"
        arguments += catalogueIds
    }
    if (platforms.isNotEmpty()) {
        clauses += "platform IN (${platforms.joinToString(",") { "?" }})"
        arguments += platforms
    }
    if (favouritesOnly) {
        clauses += "catalogue_id = ?"
        arguments += FavouriteStore.MODLAND_ID
        clauses += "path IN (SELECT path FROM modland_favourites)"
    }
    return " WHERE " + clauses.joinToString(" AND ") to arguments.toTypedArray()
}

/**
 * The statement that re-decides every stored row's `playable`, and its arguments --
 * [SupportedFormats.offeredInCatalogue] in SQL: a name claimed by extension or prefix, in a directory
 * not refused (C88). **One place**, so the test runs the statement the app runs rather than a copy
 * of it that could drift.
 */
internal fun playableUpdate(
    extensions: Collection<String>,
    prefixes: Collection<String>,
    refusedDirectories: Collection<String>,
): Pair<String, Array<String>> {
    val ext = extensions.joinToString(",") { "?" }
    val pre = prefixes.joinToString(",") { "?" }
    // `NOT IN ()` is a syntax error in SQLite, so an empty list is a condition that always holds.
    val refused = if (refusedDirectories.isEmpty()) "1" else "format NOT IN (${refusedDirectories.joinToString(",") { "?" }})"
    return "UPDATE catalogue_tracks SET playable = " +
        "(CASE WHEN (ext IN ($ext) OR pre IN ($pre)) AND $refused THEN 1 ELSE 0 END)" to
        (extensions + prefixes + refusedDirectories).toTypedArray()
}

/**
 * Narrowing to platforms: ` AND platform IN (…)`, or nothing for none -- the search's and the count's
 * one clause, over the column every row is written with (C89).
 */
internal fun platformClause(platforms: Set<String>): Pair<String, Array<String>> =
    if (platforms.isEmpty()) "" to emptyArray()
    else " AND platform IN (${platforms.joinToString(",") { "?" }})" to platforms.toTypedArray()

/** Re-decides every stored row's `platform`: [Platforms.sqlCase] as an `UPDATE`, and its arguments. */
internal fun platformUpdate(): Pair<String, Array<String>> {
    val (case, arguments) = Platforms.sqlCase()
    return "UPDATE catalogue_tracks SET platform = $case" to arguments
}
