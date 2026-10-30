// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { trimQuery } from './query-text.js';
import { importedPlaylistName, readPlaylistFile, writePlaylistFile, playlistFileName,
  playlistSize, playlistReference, networkPlaylistUrl } from './playlist-file.js';

/** Adapters supply storage/held references; transfer never owns an editor or a playback plan. */
export class PlaylistTransfer {
  constructor({ selection, read, known, held, remote = networkPlaylistUrl, presentRemote = (track) => track, save, newId }) {
    Object.assign(this, { selection, read, known, held, remote, presentRemote, save, newId });
  }
  async export(id, label) {
    const selected = this.selection();
    const name = label ?? (id === selected.id ? selected.name : 'playlist');
    const tracks = id === selected.id ? selected.tracks.map((track) => playlistReference({ ...track,
      size: track.size ?? track.data?.byteLength })) : (await this.read(id))?.tracks ?? [];
    if (!tracks.length) return Object.freeze({ kind: 'empty' });
    return Object.freeze({ kind: 'export', filename: playlistFileName(name), text: writePlaylistFile(name, tracks) });
  }
  async import(text, filename) {
    if (typeof text !== 'string' || !trimQuery(text)) return Object.freeze({ kind: 'unreadable' });
    const entries = readPlaylistFile(text);
    if (!entries.length) return Object.freeze({ kind: 'no-tracks' });
    const known = new Map((await this.known()).map((track) => [track.url, track]));
    const held = this.held().filter((track) => track.url && track.data?.byteLength > 0);
    const bytes = new Map(held.map((track) => [track.url, track.data]));
    const usable = (track) => {
      const data = bytes.get(track.url);
      if (data) return { ...track, data, local: false, size: track.size ?? data.byteLength };
      const url = !track.local && this.remote(track.url);
      return url ? { ...track, url } : null;
    };
    const found = [];
    for (const entry of entries) {
      let track = entry.id && known.has(entry.id) ? usable(known.get(entry.id)) : null;
      if (!track) {
        const url = entry.id && this.remote(entry.id) || this.remote(entry.location);
        if (url) track = this.presentRemote({ url, name: entry.title, author: entry.author, size: entry.size, file: entry.file,
          meta: entry.location.includes('/') ? entry.location.slice(0, entry.location.lastIndexOf('/')) : '' });
      }
      if (!track) {
        const match = held.find((candidate) => (candidate.file || candidate.name) === entry.file
          && (BigInt(entry.size) <= 0 || playlistSize(candidate.size ?? candidate.data.byteLength) === entry.size));
        if (match) track = { ...match, size: match.size ?? match.data.byteLength, local: false };
      }
      if (track) found.push(track);
    }
    const seen = new Set(), tracks = found.filter((track) => {
      if (seen.has(track.url)) return false; seen.add(track.url); return true;
    });
    const name = importedPlaylistName(text, filename), id = this.newId();
    await this.save({ id, name, tracks: tracks.map(playlistReference), index: 0 });
    return Object.freeze({ kind: 'import', id, name, offered: entries.length, found: found.length,
      tracks: Object.freeze(tracks) });
  }
}
