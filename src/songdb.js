// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * `audacious-uade-tools`' metadata table, as parsing and nothing else -- the phone's
 * `SongDbMetadata`, rule for rule (`docs/PLAN_WEB_PARITY.md` W5).
 *
 * Tab-separated, five columns: **hash, author, publisher, album, year**. The hash is the first 48
 * bits of the file's MD5 -- twelve hex characters -- because that is what the publisher keys on;
 * looking up the whole hash would miss every time, silently. A row with an implausible key is
 * dropped rather than repaired, and a row that says nothing about its tune is not kept.
 */

/** How many hex characters of the MD5 the published database keys on. */
export const SONGDB_KEY_LENGTH = 12;

/** The key for a file's MD5: the publisher's twelve characters. */
export const songDbKey = (md5) => String(md5).toLowerCase().slice(0, SONGDB_KEY_LENGTH);

const HEX = /^[0-9a-f]{12}$/;

export function parseSongDbMetadata(text) {
  const entries = [];
  for (const line of String(text ?? '').split('\n')) {
    if (!line.trim()) continue;
    const fields = line.split('\t');
    const key = fields[0].trim().toLowerCase();
    if (!HEX.test(key)) continue;
    const [author, publisher, album, year] = [1, 2, 3, 4].map((i) => (fields[i] ?? '').trim());
    if (!author && !publisher && !album && !year) continue;
    entries.push({ key, author, publisher, album, year });
  }
  return entries;
}
