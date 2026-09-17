// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.ContentValues
import android.content.Context
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.CatalogueEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
) {
    val indexed: Boolean get() = trackCount > 0 || isOnlineOnly
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
        !isOnlineOnly && trackCount > 0 && backends != current
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
            .rawQuery("SELECT id, track_count, indexed_at, backends FROM catalogues", null)
            .use { row ->
                buildMap {
                    while (row.moveToNext()) {
                        put(
                            row.getString(0),
                            Triple(
                                row.getInt(1),
                                if (row.isNull(2)) null else row.getLong(2),
                                row.getString(3).orEmpty(),
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
            val (count, at, backends) = stored[catalogue.id] ?: Triple(0, null, "")
            CatalogueSummary(
                id = catalogue.id,
                displayName = catalogue.displayName,
                trackCount = count,
                indexedAt = at,
                isOnlineOnly = catalogue.isOnlineOnly,
                backends = backends,
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
        helper.writableDatabase.transaction {
            delete("catalogue_tracks", "catalogue_id NOT IN ($placeholders)", ids)
            delete("catalogues", "id NOT IN ($placeholders)", ids)
        }
    }

    suspend fun replaceIndex(catalogue: Catalogue, entries: List<CatalogueEntry>, backends: String) =
        withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.transaction {
                insertWithOnConflict(
                    "catalogues", null,
                    ContentValues().apply {
                        put("id", catalogue.id)
                        put("display_name", catalogue.displayName)
                        put("indexed_at", System.currentTimeMillis())
                        put("track_count", entries.size)
                        // What produced this index. An index is filtered to the formats a backend
                        // can play, so it is only as good as the decoders that built it.
                        put("backends", backends)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                )
                delete("catalogue_tracks", "catalogue_id = ?", arrayOf(catalogue.id))

                val insert = compileStatement(
                    "INSERT OR REPLACE INTO catalogue_tracks " +
                        "(catalogue_id, path, format, author, title, size) VALUES (?, ?, ?, ?, ?, ?)"
                )
                insert.use { statement ->
                    entries.forEach { entry ->
                        statement.clearBindings()
                        statement.bindString(1, catalogue.id)
                        statement.bindString(2, entry.path)
                        statement.bindString(3, entry.format)
                        statement.bindString(4, entry.author)
                        statement.bindString(5, entry.title)
                        statement.bindLong(6, entry.size)
                        statement.executeInsert()
                    }
                }
            }
        }

    suspend fun formats(catalogueId: String): List<CatalogueGroup> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT format, COUNT(*) FROM catalogue_tracks WHERE catalogue_id = ? GROUP BY format ORDER BY format",
            arrayOf(catalogueId),
        ).use { it.toGroups() }
    }

    suspend fun authors(catalogueId: String, format: String): List<CatalogueGroup> =
        withContext(Dispatchers.IO) {
            helper.readableDatabase.rawQuery(
                "SELECT author, COUNT(*) FROM catalogue_tracks WHERE catalogue_id = ? AND format = ? " +
                    "GROUP BY author ORDER BY author",
                arrayOf(catalogueId, format),
            ).use { it.toGroups() }
        }

    suspend fun tracks(catalogueId: String, format: String, author: String): List<CatalogueTrack> =
        withContext(Dispatchers.IO) {
            helper.readableDatabase.rawQuery(
                "SELECT catalogue_id, path, format, author, title, size FROM catalogue_tracks " +
                    "WHERE catalogue_id = ? AND format = ? AND author = ? ORDER BY title",
                arrayOf(catalogueId, format, author),
            ).use { it.toTracks() }
        }

    /** How many rows [search] would return without its limit. Same `WHERE`, no `ORDER BY`. */
    suspend fun countMatches(
        query: String,
        catalogueIds: Set<String>,
        formats: Set<String> = emptySet(),
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
            val words = SearchTerms.sqlFor(query, "title", "author")
            val byFormat = if (formats.isEmpty()) {
                "" to emptyArray<String>()
            } else {
                val placeholders = formats.joinToString(",") { "?" }
                " AND format COLLATE NOCASE IN ($placeholders)" to formats.toTypedArray()
            }
            helper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM catalogue_tracks " +
                    "WHERE (${words.first})${scope.first}${byFormat.first}",
                words.second + scope.second + byFormat.second,
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }

    /**
     * How many indexed rows each format directory holds.
     *
     * What makes the platform chips honest. **A catalogue index only ever contains files this build
     * claims** — `Catalogue.parseIndex` is handed a `keep` predicate and drops the rest at index
     * time — so a count here is a count of tunes that will actually open, and a platform with none
     * has nothing to offer rather than merely nothing indexed. That is the difference between
     * greying a chip out for a reason and greying it out on a hunch.
     *
     * One grouped scan, and only when the filter is drawn.
     */
    suspend fun formatCounts(): Map<String, Int> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT format, COUNT(*) FROM catalogue_tracks GROUP BY format", emptyArray(),
        ).use { row ->
            buildMap { while (row.moveToNext()) put(row.getString(0), row.getInt(1)) }
        }
    }

    /**
     * Title and author search.
     *
     * @param catalogueIds empty means every indexed catalogue.
     * @param formats empty means every format; otherwise Modland directory names, from `Platforms`.
     * @param query may be blank, which matches everything the other two allow.
     */
    suspend fun search(
        query: String,
        catalogueIds: Set<String>,
        limit: Int,
        formats: Set<String> = emptySet(),
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
            val words = SearchTerms.sqlFor(query, "title", "author")
            // Modland's directory name is what the `format` column holds, so narrowing to a platform
            // is one `IN (…)` over a column that already exists. `COLLATE NOCASE` because the table
            // stores the archive's own capitalisation and `Platforms` states everything lower-cased.
            val byFormat = if (formats.isEmpty()) {
                "" to emptyArray<String>()
            } else {
                val placeholders = formats.joinToString(",") { "?" }
                " AND format COLLATE NOCASE IN ($placeholders)" to formats.toTypedArray()
            }
            helper.readableDatabase.rawQuery(
                "SELECT catalogue_id, path, format, author, title, size FROM catalogue_tracks " +
                    "WHERE (${words.first})${scope.first}${byFormat.first} ORDER BY title LIMIT ?",
                words.second + scope.second + byFormat.second + arrayOf(limit.toString()),
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
        formats: Set<String> = emptySet(),
        favouritesOnly: Boolean = false,
    ): List<CatalogueTrack> =
        withContext(Dispatchers.IO) {
            if (count <= 0) return@withContext emptyList()
            val clauses = mutableListOf<String>()
            val arguments = mutableListOf<String>()
            if (catalogueIds.isNotEmpty()) {
                clauses += "catalogue_id IN (${catalogueIds.joinToString(",") { "?" }})"
                arguments += catalogueIds
            }
            if (formats.isNotEmpty()) {
                clauses += "format COLLATE NOCASE IN (${formats.joinToString(",") { "?" }})"
                arguments += formats
            }
            if (favouritesOnly) {
                clauses += "catalogue_id = ?"
                arguments += FavouriteStore.MODLAND_ID
                clauses += "path IN (SELECT path FROM modland_favourites)"
            }
            val where = if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
            helper.readableDatabase.rawQuery(
                "SELECT catalogue_id, path, format, author, title, size FROM catalogue_tracks" +
                    "$where ORDER BY RANDOM() LIMIT $count",
                arguments.toTypedArray(),
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
