/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
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
     * An empty `backends` means the index predates this column, which is exactly the case that
     * prompted it: the owner's Modland index predated libsidplayfp and its 60,572 C64 tunes were
     * simply absent. Treating "unknown" as stale is right, because unknown is how the problem
     * looked.
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
        Catalogue.all.map { catalogue ->
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
            val pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
            val byFormat = if (formats.isEmpty()) {
                "" to emptyArray<String>()
            } else {
                val placeholders = formats.joinToString(",") { "?" }
                " AND format COLLATE NOCASE IN ($placeholders)" to formats.toTypedArray()
            }
            helper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM catalogue_tracks " +
                    "WHERE (title LIKE ? ESCAPE '!' OR author LIKE ? ESCAPE '!')" +
                    "${scope.first}${byFormat.first}",
                arrayOf(pattern, pattern) + scope.second + byFormat.second,
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
            // Escaped so a user typing % or _ searches for those characters instead of matching
            // everything -- a search box that silently means something else is worse than no search.
            val pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
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
                    "WHERE (title LIKE ? ESCAPE '!' OR author LIKE ? ESCAPE '!')" +
                    "${scope.first}${byFormat.first} ORDER BY title LIMIT ?",
                arrayOf(pattern, pattern) + scope.second + byFormat.second + arrayOf(limit.toString()),
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
    suspend fun randomSample(count: Int, catalogueIds: Set<String> = emptySet()): List<CatalogueTrack> =
        withContext(Dispatchers.IO) {
            if (count <= 0) return@withContext emptyList()
            val scope = if (catalogueIds.isEmpty()) {
                "" to emptyArray<String>()
            } else {
                val placeholders = catalogueIds.joinToString(",") { "?" }
                " WHERE catalogue_id IN ($placeholders)" to catalogueIds.toTypedArray()
            }
            helper.readableDatabase.rawQuery(
                "SELECT catalogue_id, path, format, author, title, size FROM catalogue_tracks" +
                    "${scope.first} ORDER BY RANDOM() LIMIT $count",
                scope.second,
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
