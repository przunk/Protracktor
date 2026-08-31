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
    const val VERSION = 1

    /** What a fresh install gets. Must equal [CREATE] plus every migration applied in order. */
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
    )

    /**
     * Keyed by the version each set of statements produces, so migrating 1 to 3 runs
     * `MIGRATIONS[2]` then `MIGRATIONS[3]`.
     *
     * Empty at version 1. The map exists now so the first migration is written into a harness that
     * already has a test, rather than arriving with one improvised around it.
     */
    val MIGRATIONS: Map<Int, List<String>> = emptyMap()

    /** Statements to run when upgrading from [from] to [to]. Throws if a step is missing. */
    fun migrationsBetween(from: Int, to: Int): List<String> =
        ((from + 1)..to).flatMap { version ->
            MIGRATIONS[version]
                ?: error("No migration to schema version $version. Upgrading $from -> $to is impossible.")
        }
}
