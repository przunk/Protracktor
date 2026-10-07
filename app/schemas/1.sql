CREATE TABLE granted_folders (uri TEXT PRIMARY KEY NOT NULL, display_name TEXT NOT NULL);

CREATE TABLE player_state (id INTEGER PRIMARY KEY CHECK (id = 0), active_playlist_id INTEGER, current_track_id TEXT, shuffle INTEGER NOT NULL DEFAULT 0, repeat_mode TEXT NOT NULL DEFAULT 'OFF');

CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE, track_id TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE, position INTEGER NOT NULL, PRIMARY KEY (playlist_id, track_id));

CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, position INTEGER NOT NULL);

CREATE TABLE tracks (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, subtitle TEXT NOT NULL DEFAULT '');

CREATE INDEX idx_playlist_tracks_order ON playlist_tracks(playlist_id, position);
