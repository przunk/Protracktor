// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

/**
 * The database schema, as SQL and nothing else.
 *
 * **No Android imports on purpose.** A migration meets a phone holding somebody's data exactly
 * once, and there is no emulator in this environment (AGENTS.md §3, §10). Keeping the statements as
 * plain strings means they can be executed against a real SQLite engine in a unit test that runs
 * here, which is the only way any of this gets tested before it reaches a device.
 *
 * ### Adding a migration
 *
 * 1. Raise [VERSION].
 * 2. Add the statements under the new version in [MIGRATIONS], keyed by the version they produce.
 * 3. Update [CREATE] so a fresh install arrives at the same shape.
 * 4. Add a test that builds the old version, puts data in, migrates, and checks the data survived.
 *
 * Step 3 is the one that gets forgotten, and the symptom is a schema that differs between an
 * upgraded phone and a fresh install. `SchemaSqlTest` checks the two agree.
 */
object SchemaSql {

    const val NAME = "protracktor.db"

    /** Reserve the next number before starting work; two branches must not both claim one. */
    const val VERSION = 16

    /**
     * Online catalogues and their contents, added at version 2.
     *
     * Held as one list so [CREATE] and [MIGRATIONS] cannot describe different tables -- the failure
     * that leaves an upgraded phone and a fresh install with different schemas, discovered weeks
     * later and far from its cause.
     */
    private val CATALOGUES_V2: List<String> = listOf(
        """
        CREATE TABLE catalogues (
            id TEXT PRIMARY KEY NOT NULL,
            display_name TEXT NOT NULL,
            indexed_at INTEGER,
            track_count INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),

        // Half a million rows for Modland alone, so the columns are the ones the UI browses by and
        // nothing else. The path is the identity: it is what the download URL is built from.
        """
        CREATE TABLE catalogue_tracks (
            catalogue_id TEXT NOT NULL REFERENCES catalogues(id) ON DELETE CASCADE,
            path TEXT NOT NULL,
            format TEXT NOT NULL,
            author TEXT NOT NULL,
            title TEXT NOT NULL,
            size INTEGER NOT NULL,
            PRIMARY KEY (catalogue_id, path)
        )
        """.trimIndent(),

        // **Version 1's indexes, and they do not stay this shape.** `playable` arrives at version
        // 15 and these are rebuilt as partial indexes over it there -- which they cannot be here,
        // because `CREATE` is version 1 plus every migration in order and the column does not
        // exist yet. A fresh install therefore builds these, drops them a moment later and builds
        // the partial ones, which costs nothing on an empty table and keeps one description of the
        // schema rather than two.
        "CREATE INDEX idx_catalogue_browse ON catalogue_tracks(catalogue_id, format, author, title)",
        "CREATE INDEX idx_catalogue_title ON catalogue_tracks(catalogue_id, title)",
    )

    /**
     * Track size, added at version 3.
     *
     * The storage access framework hands out different document URIs for the same file depending on
     * how it was reached, so the id alone cannot tell a duplicate from a new track. Name and size
     * together can, and the size has to survive a restart or the check stops working the moment the
     * app is reopened.
     */
    private val TRACK_SIZE_V3: List<String> = listOf(
        "ALTER TABLE tracks ADD COLUMN size INTEGER NOT NULL DEFAULT 0",
    )

    /**
     * The file's own name, added at version 4.
     *
     * A track's displayed title becomes the tune's real name once it has been opened and found to
     * have one. The filename has to survive that, both so the metadata view can say where a track
     * came from and so identity does not shift under a playlist when a title is rewritten.
     */
    private val TRACK_FILE_NAME_V4: List<String> = listOf(
        "ALTER TABLE tracks ADD COLUMN file_name TEXT NOT NULL DEFAULT ''",
    )

    /**
     * The author, added at version 5.
     *
     * Read from the tune's metadata when it is played, the same way the title is. Stored rather
     * than re-read because re-reading means opening every file, which is the wait R9 exists to
     * remove.
     */
    private val TRACK_AUTHOR_V5: List<String> = listOf(
        "ALTER TABLE tracks ADD COLUMN author TEXT NOT NULL DEFAULT ''",
    )

    /**
     * HVSC's SID song lengths, added at version 6.
     *
     * Keyed by the MD5 of the SID file, which is what the published database is keyed by
     * ([SongLengths]). One row per tune and about 61,000 of them, so this is the largest table
     * after the Modland index -- kept in the database rather than the 5 MB text file it came from
     * because the question asked of it is "this one hash", sixty thousand times per session, and a
     * file is the wrong shape for that.
     *
     * `seconds` holds every subsong's length, not just the first, so the table does not have to be
     * rebuilt when subsongs become selectable (docs/BACKLOG.md A2).
     */
    private val SONG_LENGTHS_V6: List<String> = listOf(
        """
        CREATE TABLE song_lengths (
            md5 TEXT PRIMARY KEY NOT NULL,
            seconds TEXT NOT NULL
        )
        """.trimIndent(),
    )

    /**
     * What has been played, added at version 7.
     *
     * **Self-contained on purpose.** It cannot reference `tracks`: a tune played from Random or
     * from a search result is never added to a playlist, so it has no row there, and those are
     * exactly the tunes this list exists to answer questions about — "that thing yesterday, what
     * was it". A foreign key would have meant history only for music you had already decided to
     * keep, which is the opposite of the point.
     *
     * **One row per track, not one per play.** `played_at` moves and `play_count` rises. A true log
     * would fill with a repeat-one track fifty times over and bury the tune from two days ago,
     * which is the thing being looked for.
     */
    private val PLAY_HISTORY_V7: List<String> = listOf(
        """
        CREATE TABLE play_history (
            track_id TEXT PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            subtitle TEXT NOT NULL DEFAULT '',
            file_name TEXT NOT NULL DEFAULT '',
            author TEXT NOT NULL DEFAULT '',
            size INTEGER NOT NULL DEFAULT 0,
            played_at INTEGER NOT NULL,
            play_count INTEGER NOT NULL DEFAULT 1
        )
        """.trimIndent(),

        "CREATE INDEX idx_play_history_recent ON play_history(played_at DESC)",
    )

    /**
     * The scanned local library, added at version 8.
     *
     * **What it is for.** Scanning a folder means opening every file with a real backend to find out
     * what it is -- which is the only way to stop trusting a filename ({@code docs/BACKLOG.md} A6,
     * {@code docs/STATUS.md} C4) and is far too expensive to repeat. This is where the answer is
     * kept so a later launch, or re-entering the folder, reads instead of re-probing.
     *
     * **Identity is the document URI**, the same thing a track reference uses. Nothing here
     * references `tracks`: a file can be indexed without ever being added to a playlist, which is
     * the normal case.
     *
     * **`backends` is the invalidation rule.** Every row records which decoder set produced it. When
     * the app ships a different set -- as it just did, replacing sc68 2.2.1 with 3.0.0b, which took
     * `.sndh` from 14/30 to 30/30 -- rows produced by the old one are stale, and files that were
     * unplayable may now be playable. Without this the index would quietly outlive the reason its
     * verdicts were true. `docs/BACKLOG.md` A7 has the same problem for catalogue indexes and
     * solves it with a note to a human; this does better.
     *
     * `folder_uri` is what a granted tree being forgotten deletes, and what tells us a row's source
     * may no longer be reachable.
     */
    private val LIBRARY_INDEX_V8: List<String> = listOf(
        """
        CREATE TABLE library_index (
            uri TEXT PRIMARY KEY NOT NULL,
            folder_uri TEXT NOT NULL,
            path TEXT NOT NULL DEFAULT '',
            file_name TEXT NOT NULL,
            size INTEGER NOT NULL DEFAULT 0,
            backend TEXT NOT NULL DEFAULT '',
            format TEXT NOT NULL DEFAULT '',
            title TEXT NOT NULL DEFAULT '',
            author TEXT NOT NULL DEFAULT '',
            duration_ms INTEGER NOT NULL DEFAULT 0,
            subsongs INTEGER NOT NULL DEFAULT 1,
            indexed_at INTEGER NOT NULL,
            backends TEXT NOT NULL DEFAULT ''
        )
        """.trimIndent(),

        // Browsing a granted tree reads by folder and then by path; searching reads by title.
        "CREATE INDEX idx_library_folder ON library_index(folder_uri, path, file_name)",
        "CREATE INDEX idx_library_title ON library_index(title)",
    )

    /**
     * Which decoders built a catalogue index, added at version 9.
     *
     * A catalogue index is filtered **at index time** to the formats a backend can play, so an
     * index built before a backend existed is permanently missing that backend's formats — and it
     * looks empty rather than stale. The owner met this on 2026-09-03: his Modland index predated
     * libsidplayfp, so 60,572 C64 tunes were simply absent and nothing said why.
     *
     * `docs/BACKLOG.md` A7 had been carrying this as a note asking a human to remember, while the
     * local library index (version 8) already recorded its decoder set and offered a rescan. This
     * closes that asymmetry.
     */
    private val CATALOGUE_BACKENDS_V9: List<String> = listOf(
        "ALTER TABLE catalogues ADD COLUMN backends TEXT NOT NULL DEFAULT ''",
    )

    /**
     * Whether to play every tune inside a file, added at version 10.
     *
     * A setting rather than a property of a track, and stored with the other playback modes for the
     * same reason shuffle and repeat are: it applies to whatever plays next, not to one row. Off by
     * default, which the owner chose — a file reporting 256 subsongs would otherwise take over a
     * listening session the first time one appeared.
     */
    /**
     * Author, publisher, album and year by file hash, added at version 11.
     *
     * The second database of facts about files we did not write, after HVSC's song lengths, and it
     * exists for the same reason: **the formats cannot carry what people want to know.** A plain
     * `.mod` or `.xm` has nowhere to put a release year, which is why `docs/WISHLIST.md` B20 could
     * show one for a SNDH and nothing for the 80,000 ProTracker files in Modland.
     *
     * **Keyed by the first twelve hex characters of the MD5, not the whole of it.** That is what
     * the published database uses -- a deliberate 48-bit prefix -- and storing the full hash here
     * would mean every lookup missed. `song_lengths` next door is keyed by the whole thing, because
     * HVSC publishes the whole thing; two tables, two conventions, and the difference is the
     * publisher's rather than ours.
     */
    private val TRACK_METADATA_V11: List<String> = listOf(
        """
        CREATE TABLE track_metadata (
            md5 TEXT PRIMARY KEY NOT NULL,
            author TEXT NOT NULL,
            publisher TEXT NOT NULL,
            album TEXT NOT NULL,
            year TEXT NOT NULL
        )
        """.trimIndent(),
    )

    /**
     * Modland's own favourites, added at version 12.
     *
     * **Paths, not hashes** -- the odd one out among the tables of facts about other people's
     * files, and deliberately. `track_metadata` and `song_lengths` answer questions about a file
     * the user already has, so they key on its digest; this one answers "what should I play", which
     * is a question about the *catalogue*, and the catalogue's identity is the path. Keying on the
     * hash would mean downloading a tune before knowing whether it was worth downloading.
     *
     * One column and nothing else. Everything else about the tune -- format, author, title, size --
     * is in `catalogue_tracks` already, and a second copy would be a second thing to keep current.
     * The join is what turns the list into music, and it is also what quietly drops the favourites
     * this build cannot play: measured 2026-09-08, 991 favourites, of which 891 are still at the
     * path Modland publishes today and 835 survive the index filter.
     */
    private val MODLAND_FAVOURITES_V12: List<String> = listOf(
        """
        CREATE TABLE modland_favourites (
            path TEXT PRIMARY KEY NOT NULL
        )
        """.trimIndent(),
    )

    private val PLAY_ALL_SUBSONGS_V10: List<String> = listOf(
        "ALTER TABLE player_state ADD COLUMN play_all_subsongs INTEGER NOT NULL DEFAULT 0",
    )

    /**
     * What Random picks from, added at version 13.
     *
     * A word rather than a set of columns: the scope is one choice of three shapes, and two of them
     * carry nothing. `RandomScope.stored()` owns the vocabulary. Empty means "never set", which
     * reads back as Everything -- the same answer an upgraded phone gives.
     */
    private val RANDOM_SCOPE_V13: List<String> = listOf(
        "ALTER TABLE player_state ADD COLUMN random_scope TEXT NOT NULL DEFAULT ''",
    )

    /**
     * How long to play a tune nothing knows the length of, added at version 14.
     *
     * **Seconds, and zero means "never set"** -- which reads back as the default, the same answer an
     * upgraded phone gives. Stored rather than derived because it is a preference: `docs/STATUS.md`
     * C56 is the fault it exists for, where a SID with no HVSC entry played for ever because
     * nothing in the file, and nothing in the app, ever said to stop.
     */
    private val FALLBACK_LENGTH_V14: List<String> = listOf(
        "ALTER TABLE player_state ADD COLUMN fallback_length_seconds INTEGER NOT NULL DEFAULT 0",
    )

    /**
     * What a catalogue row is filed under, and whether this build can play it, added at version 15.
     *
     * **The index stops being a function of the decoder set** (`docs/ROADMAP_FORMATS.md` step 0).
     * It used to hold only rows `SupportedFormats` accepted, so adding a format changed what an
     * index should contain and every user downloaded Modland's 40 MB again — a toll charged once
     * per format, and the roadmap has four items that would each have charged it.
     *
     * `ext` and `pre` are the two halves of a filename `SupportedFormats` judges, stored as they
     * are: they depend on the name and not on the list. `playable` is the judgement, and it is
     * re-decided for every row by one `UPDATE` when the list changes — 228ms over 516,107 rows,
     * measured — with no network at all.
     *
     * **This migration cannot backfill the rows that were dropped**, because they were never
     * downloaded. Every existing index is one download short of complete and is marked for a final
     * refresh; after that there is not another.
     */
    private val CATALOGUE_PLAYABLE_V15: List<String> = listOf(
        "ALTER TABLE catalogue_tracks ADD COLUMN ext TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE catalogue_tracks ADD COLUMN pre TEXT NOT NULL DEFAULT ''",
        // 1, not 0. An index written before this migration holds **only** playable rows, so every
        // row in it is playable -- and defaulting to 0 would empty Browse for anybody who did not
        // re-index immediately.
        "ALTER TABLE catalogue_tracks ADD COLUMN playable INTEGER NOT NULL DEFAULT 1",
        // **Whether this index holds the whole archive or only what an older build accepted.**
        //
        // The distinction is the one thing a recompute cannot paper over: an index written before
        // version 15 contains exactly the rows the format list of the day let through, so a format
        // added afterwards has no rows here to be re-decided and a download is genuinely needed. An
        // index written since holds everything, and never needs one again.
        //
        // 0 for everything that already exists, which is the truth about all of it.
        "ALTER TABLE catalogues ADD COLUMN complete INTEGER NOT NULL DEFAULT 0",
        // Rebuilt over the playable rows only, now that the table holds every row. Measured at
        // Modland's size: 129.0 MB with full indexes, 112.8 MB with these, against 83.3 MB for the
        // old shape that held 344,071 rows instead of 516,107. The recompute pays for it — 228ms
        // becomes 1.7s with these to maintain — and it is rare and offline, which the download it
        // replaces was neither.
        "DROP INDEX IF EXISTS idx_catalogue_browse",
        "DROP INDEX IF EXISTS idx_catalogue_title",
        "CREATE INDEX idx_catalogue_browse ON catalogue_tracks(catalogue_id, format, author, title) " +
            "WHERE playable = 1",
        "CREATE INDEX idx_catalogue_title ON catalogue_tracks(catalogue_id, title) WHERE playable = 1",
    )

    /**
     * How many rows a catalogue holds, beside how many of them play, added at version 16.
     *
     * **Because version 15 made those two different numbers and nothing said so.** The owner met it
     * the morning after: a snackbar reporting 500,000-odd tracks indexed over a row reporting
     * 341,842, with nothing to connect them. `track_count` is what this build can open; this is
     * what the archive has.
     *
     * Backfilled by counting, so an index already stored gets its number without being downloaded
     * again — which is the whole point of the version before this one.
     */
    private val CATALOGUE_ARCHIVE_COUNT_V16: List<String> = listOf(
        "ALTER TABLE catalogues ADD COLUMN archive_count INTEGER NOT NULL DEFAULT 0",
        "UPDATE catalogues SET archive_count = " +
            "(SELECT COUNT(*) FROM catalogue_tracks t WHERE t.catalogue_id = catalogues.id)",
    )

    /** What a fresh install gets: version 1's tables plus every migration since. */
    val CREATE: List<String> = listOf(
        """
        CREATE TABLE playlists (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            position INTEGER NOT NULL
        )
        """.trimIndent(),

        // The id is the document URI. Identity has to carry everything that identifies the row
        // (AGENTS.md §10), and for a file reached through the storage access framework the URI is
        // the only thing that does.
        """
        CREATE TABLE tracks (
            id TEXT PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            subtitle TEXT NOT NULL DEFAULT ''
        )
        """.trimIndent(),

        """
        CREATE TABLE playlist_tracks (
            playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
            track_id TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
            position INTEGER NOT NULL,
            PRIMARY KEY (playlist_id, track_id)
        )
        """.trimIndent(),

        "CREATE INDEX idx_playlist_tracks_order ON playlist_tracks(playlist_id, position)",

        // Folders the user granted through SAF. Kept so browsing can resume where it was without
        // sending the user back through a file picker every session.
        """
        CREATE TABLE granted_folders (
            uri TEXT PRIMARY KEY NOT NULL,
            display_name TEXT NOT NULL
        )
        """.trimIndent(),

        // One row, enforced by the CHECK. Settings that come as a set are stored as a set; five
        // key-value rows would let them be half-written.
        """
        CREATE TABLE player_state (
            id INTEGER PRIMARY KEY CHECK (id = 0),
            active_playlist_id INTEGER,
            current_track_id TEXT,
            shuffle INTEGER NOT NULL DEFAULT 0,
            repeat_mode TEXT NOT NULL DEFAULT 'OFF'
        )
        """.trimIndent(),

        "INSERT INTO player_state (id) VALUES (0)",
    ) + CATALOGUES_V2 + TRACK_SIZE_V3 + TRACK_FILE_NAME_V4 + TRACK_AUTHOR_V5 + SONG_LENGTHS_V6 +
        PLAY_HISTORY_V7 + LIBRARY_INDEX_V8 +
        CATALOGUE_BACKENDS_V9 + PLAY_ALL_SUBSONGS_V10 + TRACK_METADATA_V11 +
        MODLAND_FAVOURITES_V12 + RANDOM_SCOPE_V13 + FALLBACK_LENGTH_V14 +
        CATALOGUE_PLAYABLE_V15 + CATALOGUE_ARCHIVE_COUNT_V16



    /**
     * Keyed by the version each set of statements produces, so migrating 1 to 3 runs
     * `MIGRATIONS[2]` then `MIGRATIONS[3]`.
     *
     * The statements are shared with [CREATE] rather than copied, which is what makes the two
     * physically unable to disagree.
     */
    val MIGRATIONS: Map<Int, List<String>> = mapOf(
        2 to CATALOGUES_V2,
        3 to TRACK_SIZE_V3,
        4 to TRACK_FILE_NAME_V4,
        5 to TRACK_AUTHOR_V5,
        6 to SONG_LENGTHS_V6,
        7 to PLAY_HISTORY_V7,
        8 to LIBRARY_INDEX_V8,
        9 to CATALOGUE_BACKENDS_V9,
        10 to PLAY_ALL_SUBSONGS_V10,
        11 to TRACK_METADATA_V11,
        12 to MODLAND_FAVOURITES_V12,
        13 to RANDOM_SCOPE_V13,
        14 to FALLBACK_LENGTH_V14,
        15 to CATALOGUE_PLAYABLE_V15,
        16 to CATALOGUE_ARCHIVE_COUNT_V16,
    )

    /**
     * Records a play, or moves an existing one up and counts it.
     *
     * Here rather than in [HistoryStore] because it is the one statement in the app with real logic
     * in it, and this file is the part that a JVM test can run against a real SQLite engine.
     *
     * **Not** `ON CONFLICT ... DO UPDATE`, which is the obvious way to write an upsert. That needs
     * SQLite 3.24; API 29 ships 3.22 and `minSdk` is 29, so the obvious way crashes on the oldest
     * device supported — and nothing here would catch it, because the tests run against a current
     * SQLite through `sqlite-jdbc` and this machine has no emulator.
     *
     * Parameters: track_id, title, subtitle, file_name, author, size, played_at, **track_id again**
     * for the count lookup.
     */
    val PLAY_HISTORY_RECORD: String = """
        INSERT OR REPLACE INTO play_history
            (track_id, title, subtitle, file_name, author, size, played_at, play_count)
        VALUES (
            ?, ?, ?, ?, ?, ?, ?,
            COALESCE((SELECT play_count FROM play_history WHERE track_id = ?), 0) + 1
        )
    """.trimIndent()

    /** How many tracks history remembers. Past this the oldest are forgotten. */
    const val PLAY_HISTORY_LIMIT = 500

    /** Forgets the oldest. Run in the same transaction as [PLAY_HISTORY_RECORD]. */
    val PLAY_HISTORY_PRUNE: String =
        "DELETE FROM play_history WHERE track_id NOT IN " +
            "(SELECT track_id FROM play_history ORDER BY played_at DESC LIMIT $PLAY_HISTORY_LIMIT)"

    /**
     * Every table in the file, asked of the file rather than listed.
     *
     * `onDowngrade` recreates the database, and to do that it must first remove what is there. The
     * list it used to carry was written at version 1 and named five tables; [CREATE] makes twelve.
     * Seven migrations added tables that nothing removed, so the recreate ran `CREATE TABLE
     * catalogues` against a `catalogues` that still existed and threw -- **on every start**, which
     * is the state the whole method exists to prevent. Verified against a real SQLite on
     * 2026-09-08: `[SQLITE_ERROR] table catalogues already exists`.
     *
     * Asking the file is the fix, and it is the fix rather than a longer list because a longer list
     * would go stale the same way, quietly, and only on somebody's phone.
     *
     * `android_metadata` is left alone: Android creates it when it opens the file, and it holds the
     * locale rather than anything of ours.
     */
    const val TABLE_NAMES: String =
        "SELECT name FROM sqlite_master WHERE type = 'table' " +
            "AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'"

    /**
     * Removes the named tables, and with them their indexes.
     *
     * **In reverse, and that is load-bearing.** `DROP TABLE` runs an implicit delete of the table's
     * rows, and that delete resolves foreign keys -- so dropping `playlists` before
     * `playlist_tracks`, which references it, fails with `no such table: main.playlists`. The app
     * turns foreign keys on, so this is not theoretical; the test found it on the first run.
     *
     * `sqlite_master` lists tables in creation order and a child is always created after the parent
     * it references, so walking it backwards is dependency order. Quoted names, because a table
     * name is whatever somebody wrote in a migration.
     */
    fun dropStatements(tables: List<String>): List<String> =
        tables.reversed().map { "DROP TABLE IF EXISTS \"$it\"" }

    /** Statements to run when upgrading from [from] to [to]. Throws if a step is missing. */
    fun migrationsBetween(from: Int, to: Int): List<String> =
        ((from + 1)..to).flatMap { version ->
            MIGRATIONS[version]
                ?: error("No migration to schema version $version. Upgrading $from -> $to is impossible.")
        }
}
