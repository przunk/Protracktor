// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { catalogue } from './store.js';
import * as archive from './catalogue.js';
import { PLATFORM_FINGERPRINT, countPlatformBucket, platformForCatalogueRow, parseModlandFavourites, storedRandomScope } from './random-scope.js';

const FAVOURITES = 'modland-favourites:';
const FAVOURITES_URL = 'https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/master/songdb/sources/site/modland_favourites.tsv';
let accepted = null;
const checkAbort = (signal) => { if (signal?.aborted) throw new DOMException('Aborted', 'AbortError'); };

export async function favouritesMeta() { return catalogue.get(`${FAVOURITES}meta`); }
export async function clearFavourites() { accepted = null; await catalogue.clear(FAVOURITES); }

export async function downloadFavourites({ signal, onProgress } = {}) {
  onProgress?.({ stage: 'fetching' });
  const response = await fetch(FAVOURITES_URL, { signal });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const bytes = await response.arrayBuffer();
  checkAbort(signal);
  const paths = [...new Set(parseModlandFavourites(new TextDecoder().decode(bytes)))];
  if (!paths.length) throw new Error('The list contains no favourite paths.');
  onProgress?.({ stage: 'storing' });
  await catalogue.replacePrefix(FAVOURITES, [{ key: `${FAVOURITES}paths`, paths },
    { key: `${FAVOURITES}meta`, listed: paths.length, at: Date.now() }], { signal });
  accepted = null;
  onProgress?.({ stage: 'done', listed: paths.length });
  return paths.length;
}

async function resources() {
  const indexes = await Promise.all(archive.sources().map(async (source) => ({ source,
    meta: await archive.meta(source), formats: await archive.formats(source) })));
  const paths = (await catalogue.get(`${FAVOURITES}paths`))?.paths ?? [];
  // Include paths themselves: two quick replacements can have equal counts/timestamps.
  const revisions = [...archive.sources().map((source) => catalogue.revisionOf(`${source}:`)), catalogue.revisionOf(FAVOURITES)];
  return { indexes, paths, key: JSON.stringify([PLATFORM_FINGERPRINT, revisions, indexes, paths]) };
}

async function favouriteTracks(paths, signal) {
  const buckets = new Map();
  for (const path of paths) {
    const first = path.indexOf('/'), last = path.lastIndexOf('/');
    if (first <= 0 || last === path.length - 1) continue;
    const bucket = path.slice(0, last);
    let files = buckets.get(bucket);
    if (!files) { files = new Set(); buckets.set(bucket, files); }
    files.add(path.slice(last + 1));
  }
  const found = [];
  for (const [bucket, files] of buckets) {
    checkAbort(signal);
    const slash = bucket.indexOf('/');
    const format = slash < 0 ? bucket : bucket.slice(0, slash);
    const author = slash < 0 ? '' : bucket.slice(slash + 1);
    const tracks = await archive.tracksIn(format, author, 'modland');
    checkAbort(signal);
    found.push(...tracks.filter((track) => files.has(track.name)));
  }
  return found;
}

/** Cache by actual resources, not the current Browse view or download progress percentage. */
export async function randomAvailability({ signal } = {}) {
  for (let attempt = 0; attempt < 2; attempt++) {
    checkAbort(signal);
    const before = await resources();
    if (accepted?.key === before.key) return accepted.value;
    const platformCounts = {};
    for (const { source, meta } of before.indexes) {
      checkAbort(signal);
      if (meta?.platformFingerprint === PLATFORM_FINGERPRINT && meta.platformCounts) {
        for (const [platform, count] of Object.entries(meta.platformCounts)) platformCounts[platform] = (platformCounts[platform] ?? 0) + count;
      } else await catalogue.visitPrefix(`${source}:tracks:`, (bucket) => countPlatformBucket(platformCounts, source, bucket), { signal });
    }
    const favourites = await favouriteTracks(before.paths, signal);
    const after = await resources();
    if (after.key !== before.key) continue;
    checkAbort(signal);
    const value = Object.freeze({ platformCounts: Object.freeze(platformCounts), favouriteCount: favourites.length,
      favouritesListed: before.paths.length });
    accepted = { key: before.key, value };
    return value;
  }
  throw new Error('The catalogues changed while checking filters. Try again.');
}

/** Apply archive/directory/filename precedence before drawing, so the pool stays uniform. */
export async function scopedRandomTable(scope, { signal } = {}) {
  scope = storedRandomScope(scope);
  checkAbort(signal);
  if (scope === 'everything') return archive.buildRandomTable({ signal });
  if (scope === 'favourites') {
    const paths = (await catalogue.get(`${FAVOURITES}paths`))?.paths ?? [];
    const tracks = await favouriteTracks(paths, signal);
    return { tracks, total: tracks.length };
  }
  const wanted = scope.slice(9), entries = [];
  let total = 0;
  for (const source of archive.sources()) {
    for (const { name: format } of await archive.formats(source)) {
      checkAbort(signal);
      const fixed = platformForCatalogueRow(source, format, '');
      if (fixed && fixed.id !== wanted) continue;
      for (const { name: author, count } of await archive.authors(format, source)) {
        if (!count) continue;
        checkAbort(signal);
        let indices;
        if (!fixed) {
          const tracks = await archive.tracksIn(format, author, source);
          indices = tracks.flatMap((track, at) => platformForCatalogueRow(source, format, track.name)?.id === wanted ? [at] : []);
        }
        const size = indices?.length ?? count;
        if (!size) continue;
        total += size;
        entries.push({ source, format, author, end: total, ...(indices ? { indices } : {}) });
      }
    }
  }
  return { entries, total };
}
