// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { catalogue } from './store.js';
import * as archive from './catalogue.js';
import { SearchSelection } from './library-search.js';
import { searchMatches } from './rules.js';
import { platformForCatalogueRow, platformForFileName, countPlatformBucket, PLATFORM_FINGERPRINT } from './random-scope.js';

/** One catalogue row as a search result. */
function catalogueResult(source, format, author, title) {
  return Object.freeze({ url: archive.urlFor(format, author, title, source),
    name: title, file: title, meta: [archive.sourceName(source), format, author].filter(Boolean).join('/') });
}

/** Only approved browser catalogues; all matching words may cross title/author fields. */
export async function searchCatalogueTracks(query, ids, platforms, limit, signal) {
  const wanted = archive.sources().filter((source) => !ids.length || ids.includes(source));
  const selection = new SearchSelection(limit);
  const held = await catalogue.visitTitleShards(wanted, (source, shard) => {
    if (!Array.isArray(shard.entries)) throw new Error('Unreadable catalogue title shard');
    for (const [title, format, author] of shard.entries) {
      if (platforms.length && !platforms.includes(platformForCatalogueRow(source, format, title)?.id)) continue;
      if (!searchMatches(query, title, author)) continue;
      selection.accept(catalogueResult(source, format, author, title));
    }
  }, signal);
  return Object.freeze({ rows: selection.rows(), total: selection.total, held: Object.freeze(held) });
}

/**
 * Browse's Search, below the folders it narrows (2026-11-09): the tunes in one catalogue, or one
 * format of it, that match by Search's own rule. Nothing typed asks nothing.
 */
export async function searchCatalogueTracksWithin(query, source, format, limit, signal) {
  if (!String(query ?? '').trim()) return Object.freeze([]);
  const selection = new SearchSelection(limit);
  await catalogue.visitTitleShards([source], (from, shard) => {
    if (!Array.isArray(shard.entries)) throw new Error('Unreadable catalogue title shard');
    for (const [title, group, author] of shard.entries) {
      if (format != null && group !== format) continue;
      if (!searchMatches(query, title, author)) continue;
      selection.accept(catalogueResult(from, group, author, title));
    }
  }, signal);
  return Object.freeze(selection.rows());
}

/** Availability comes from held data only; a pending read cannot claim that an index is missing. */
export async function searchScopeAvailability(savedTracks, signal) {
  const check = () => { if (signal?.aborted) throw new DOMException('Aborted', 'AbortError'); };
  for (let attempt = 0; attempt < 2; attempt++) {
    const revisions = archive.sources().map((source) => catalogue.revisionOf(`${source}:`));
    const catalogues = [], platformCounts = {};
    for (const source of archive.sources()) {
      check();
      const meta = await archive.meta(source);
      if (!meta) continue;
      catalogues.push(source);
      if (meta.platformFingerprint === PLATFORM_FINGERPRINT && meta.platformCounts) {
        for (const [id, count] of Object.entries(meta.platformCounts)) platformCounts[id] = (platformCounts[id] ?? 0) + count;
      } else await catalogue.visitPrefix(`${source}:tracks:`, (bucket) => countPlatformBucket(platformCounts, source, bucket), { signal });
    }
    for (const track of savedTracks) {
      const id = platformForFileName(track.file ?? '')?.id;
      if (id) platformCounts[id] = (platformCounts[id] ?? 0) + 1;
    }
    check();
    if (archive.sources().some((source, i) => catalogue.revisionOf(`${source}:`) !== revisions[i])) continue;
    return Object.freeze({ catalogues: Object.freeze(catalogues), platformCounts: Object.freeze(platformCounts) });
  }
  throw new Error('The catalogues changed while checking search scopes. Try again.');
}
