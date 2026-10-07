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
, backends TEXT NOT NULL DEFAULT '');

CREATE TABLE granted_folders (uri TEXT PRIMARY KEY NOT NULL, display_name TEXT NOT NULL);

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
);

CREATE TABLE modland_favourites (
    path TEXT PRIMARY KEY NOT NULL
);

CREATE TABLE play_history (
    track_id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    subtitle TEXT NOT NULL DEFAULT '',
    file_name TEXT NOT NULL DEFAULT '',
    author TEXT NOT NULL DEFAULT '',
    size INTEGER NOT NULL DEFAULT 0,
    played_at INTEGER NOT NULL,
    play_count INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE player_state (id INTEGER PRIMARY KEY CHECK (id = 0), active_playlist_id INTEGER, current_track_id TEXT, shuffle INTEGER NOT NULL DEFAULT 0, repeat_mode TEXT NOT NULL DEFAULT 'OFF', play_all_subsongs INTEGER NOT NULL DEFAULT 0);

CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE, track_id TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE, position INTEGER NOT NULL, PRIMARY KEY (playlist_id, track_id));

CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, position INTEGER NOT NULL);

CREATE TABLE song_lengths (
    md5 TEXT PRIMARY KEY NOT NULL,
    seconds TEXT NOT NULL
);

CREATE TABLE track_metadata (
    md5 TEXT PRIMARY KEY NOT NULL,
    author TEXT NOT NULL,
    publisher TEXT NOT NULL,
    album TEXT NOT NULL,
    year TEXT NOT NULL
);

CREATE TABLE tracks (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, subtitle TEXT NOT NULL DEFAULT '', size INTEGER NOT NULL DEFAULT 0, file_name TEXT NOT NULL DEFAULT '', author TEXT NOT NULL DEFAULT '');

CREATE INDEX idx_catalogue_browse ON catalogue_tracks(catalogue_id, format, author, title);

CREATE INDEX idx_catalogue_title ON catalogue_tracks(catalogue_id, title);

CREATE INDEX idx_library_folder ON library_index(folder_uri, path, file_name);

CREATE INDEX idx_library_title ON library_index(title);

CREATE INDEX idx_play_history_recent ON play_history(played_at DESC);

CREATE INDEX idx_playlist_tracks_order ON playlist_tracks(playlist_id, position);
