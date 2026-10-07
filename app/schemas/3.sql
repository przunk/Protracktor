CREATE TABLE catalogue_tracks (
    catalogue_id TEXT NOT NULL REFERENCES catalogues(id) ON DELETE CASCADE,
    path TEXT NOT NULL,
    format TEXT NOT NULL,
    author TEXT NOT NULL,
    title TEXT NOT NULL,
    size INTEGER NOT NULL,
    PRIMARY KEY (catalogue_id, path)
);

CREATE TABLE catalogues (
    id TEXT PRIMARY KEY NOT NULL,
    display_name TEXT NOT NULL,
    indexed_at INTEGER,
    track_count INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE granted_folders (uri TEXT PRIMARY KEY NOT NULL, display_name TEXT NOT NULL);

CREATE TABLE player_state (id INTEGER PRIMARY KEY CHECK (id = 0), active_playlist_id INTEGER, current_track_id TEXT, shuffle INTEGER NOT NULL DEFAULT 0, repeat_mode TEXT NOT NULL DEFAULT 'OFF');

CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE, track_id TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE, position INTEGER NOT NULL, PRIMARY KEY (playlist_id, track_id));

CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, position INTEGER NOT NULL);

CREATE TABLE tracks (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, subtitle TEXT NOT NULL DEFAULT '', size INTEGER NOT NULL DEFAULT 0);

CREATE INDEX idx_catalogue_browse ON catalogue_tracks(catalogue_id, format, author, title);

CREATE INDEX idx_catalogue_title ON catalogue_tracks(catalogue_id, title);

CREATE INDEX idx_playlist_tracks_order ON playlist_tracks(playlist_id, position);
