// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { trimQuery } from './query-text.js';

/** Decimal metadata avoids rounding Android's signed 64-bit sizes through a JS Number. */
export function playlistSize(value) {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return '0';
  const text = String(value ?? '0');
  if (!/^[+-]?\d+$/.test(text)) return '0';
  const size = BigInt(text);
  return size >= -9223372036854775808n && size <= 9223372036854775807n ? String(size) : '0';
}
const singleLine = (value) => String(value ?? '').replace(/\r\n|[\r\n]/g, ' ');
const nonblank = (text) => !!trimQuery(text);
export const networkPlaylistUrl = (value) => {
  if (typeof value !== 'string' || !/^https?:\/\//i.test(value)) return null;
  try { return ['http:', 'https:'].includes(new URL(value).protocol) ? value : null; } catch { return null; }
};
const storedAvailability = Symbol('stored-playlist-availability');

/** Availability belongs to this tab; derived missing/held flags must not rewrite a saved row. */
export function hydratePlaylistReference(track, data = track.data) {
  const available = data?.byteLength > 0;
  const { data: _previousData, ...reference } = track;
  return { ...reference, ...(available ? { data } : {}),
    local: available ? false : !!track.local || !networkPlaylistUrl(track.url),
    [storedAvailability]: track[storedAvailability] ?? Object.freeze({ local: track.local }) };
}
export const playlistFileName = (label) => `${label.replace(/[^\w -]/gu, '_')}.m3u8`;

export function playlistNameIn(text) {
  const line = text.split(/\r\n|[\r\n]/).map(trimQuery).find((line) => line.startsWith('#PLAYLIST:'));
  const name = line === undefined ? '' : trimQuery(line.slice('#PLAYLIST:'.length));
  return name || null;
}
export function importedPlaylistName(text, filename) {
  const name = playlistNameIn(text);
  if (name !== null) return name;
  const last = filename.slice(filename.lastIndexOf('/') + 1).split(':').at(-1);
  const dot = last.lastIndexOf('.');
  return (dot < 0 ? last : last.slice(0, dot)) || 'Imported';
}

/** Android PlaylistFile.read, including per-entry metadata and first artist separator. */
export function readPlaylistFile(text) {
  const entries = [];
  let title = '', author = '', id = null, size = '0';
  for (const raw of text.split(/\r\n|[\r\n]/)) {
    const line = trimQuery(raw);
    if (!line) continue;
    if (line.startsWith('#EXTINF:')) {
      const comma = line.indexOf(','), label = comma < 0 ? '' : line.slice(comma + 1), dash = label.indexOf(' - ');
      // Native's writer also emits this separator for an empty artist; retain the actual title.
      if (dash >= 0) { author = trimQuery(label.slice(0, dash)); title = trimQuery(label.slice(dash + 3)); }
      else title = trimQuery(label);
    } else if (line.startsWith('#PROTRACKTOR:id=')) {
      const value = line.slice('#PROTRACKTOR:id='.length); id = nonblank(value) ? value : null;
    } else if (line.startsWith('#PROTRACKTOR:size=')) size = playlistSize(line.slice('#PROTRACKTOR:size='.length));
    else if (!line.startsWith('#')) {
      const file = line.slice(line.lastIndexOf('/') + 1);
      entries.push(Object.freeze({ id, title: nonblank(title) ? title : file, author, size, location: line, file }));
      title = ''; author = ''; id = null; size = '0';
    }
  }
  return Object.freeze(entries);
}

/** One logical metadata field per line; embedded newlines cannot inject another M3U entry. */
export function writePlaylistFile(name, tracks) {
  const lines = ['#EXTM3U'];
  if (nonblank(name)) lines.push(`#PLAYLIST:${singleLine(name)}`);
  for (const track of tracks) {
    const title = track.name ?? '', file = nonblank(track.file ?? '') ? track.file : title, meta = track.meta ?? '';
    const author = nonblank(track.author ?? '') ? track.author : meta.slice(meta.lastIndexOf('/') + 1).split(' · ').at(-1);
    lines.push(`#EXTINF:-1,${singleLine(author)} - ${singleLine(title)}`, `#PROTRACKTOR:id=${singleLine(track.url)}`);
    const size = playlistSize(track.size);
    if (BigInt(size) > 0) lines.push(`#PROTRACKTOR:size=${size}`);
    const location = networkPlaylistUrl(track.url) ? track.url
      : nonblank(meta) ? `${meta.replace(/^\/+|\/+$/g, '')}/${file}` : file;
    lines.push(singleLine(location));
  }
  return lines.join('\n') + '\n';
}

/** Only portable references cross storage/share boundaries; held audio never does. */
export function playlistReference(track) {
  const { url, name, meta, file, author, size } = track;
  const local = track[storedAvailability] ? track[storedAvailability].local : track.local;
  return { url, name, meta, local, file,
    ...(author !== undefined ? { author } : {}), ...(size !== undefined ? { size: playlistSize(size) } : {}) };
}
