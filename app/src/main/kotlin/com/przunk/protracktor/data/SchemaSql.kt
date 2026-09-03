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
    const val VERSION = 9

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
        CATALOGUE_BACKENDS_V9



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

    /** Statements to run when upgrading from [from] to [to]. Throws if a step is missing. */
    fun migrationsBetween(from: Int, to: Int): List<String> =
        ((from + 1)..to).flatMap { version ->
            MIGRATIONS[version]
                ?: error("No migration to schema version $version. Upgrading $from -> $to is impossible.")
        }
}
