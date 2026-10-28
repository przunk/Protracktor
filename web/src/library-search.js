// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { searchMatches } from './rules.js';
import { platformForFileName } from './random-scope.js';

export const SEARCH_SOURCE_LIMIT = 2000;

export function searchScope(kind = 'everywhere', ids = []) {
  switch (kind) {
    case 'everywhere': case 'playlists': return Object.freeze({ kind, ids: Object.freeze([]) });
    case 'online': case 'platform': return Object.freeze({ kind, ids: Object.freeze([...new Set(ids)].sort()) });
    default: throw new TypeError(`Unknown search scope: ${kind}`);
  }
}
export const searchScopeKey = (scope) => JSON.stringify(searchScope(scope.kind, scope.ids));
export function searchesPlaylists(scope) {
  switch (scope.kind) {
    case 'everywhere': case 'playlists': case 'platform': return true;
    case 'online': return false;
    default: throw new TypeError(`Unknown search scope: ${scope.kind}`);
  }
}
export function searchesCatalogues(scope) {
  switch (scope.kind) {
    case 'everywhere': case 'online': case 'platform': return true;
    case 'playlists': return false;
    default: throw new TypeError(`Unknown search scope: ${scope.kind}`);
  }
}

/** SQLite BINARY title order, without locale-dependent collation or UTF-16 surrogate ordering. */
export function compareSearchText(first, second) {
  for (let a = 0, b = 0; a < first.length && b < second.length;) {
    const left = first.codePointAt(a), right = second.codePointAt(b);
    if (left !== right) return left - right;
    a += left > 0xffff ? 2 : 1; b += right > 0xffff ? 2 : 1;
  }
  return first.length - second.length;
}
export const compareSearchTracks = (a, b) => compareSearchText(a.name ?? '', b.name ?? '') || compareSearchText(a.url, b.url);

/** A max heap keeps only the globally first page while still counting every match. */
export class SearchSelection {
  #heap = [];
  total = 0;
  constructor(limit = SEARCH_SOURCE_LIMIT) {
    if (!Number.isInteger(limit) || limit < 1) throw new RangeError('Invalid search limit');
    this.limit = limit;
  }
  accept(row) {
    this.total++;
    const heap = this.#heap;
    if (heap.length < this.limit) {
      heap.push(row);
      for (let at = heap.length - 1; at > 0;) {
        const parent = (at - 1) >> 1;
        if (compareSearchTracks(heap[parent], heap[at]) >= 0) break;
        [heap[parent], heap[at]] = [heap[at], heap[parent]]; at = parent;
      }
    } else if (compareSearchTracks(row, heap[0]) < 0) {
      heap[0] = row;
      for (let at = 0;;) {
        const left = at * 2 + 1, right = left + 1;
        let largest = at;
        if (left < heap.length && compareSearchTracks(heap[left], heap[largest]) > 0) largest = left;
        if (right < heap.length && compareSearchTracks(heap[right], heap[largest]) > 0) largest = right;
        if (largest === at) break;
        [heap[at], heap[largest]] = [heap[largest], heap[at]]; at = largest;
      }
    }
  }
  rows() { return Object.freeze([...this.#heap].sort(compareSearchTracks)); }
}

/** Native source precedence and display placement are deliberately separate. */
export function combineSearchResults(index = [], playlists = [], catalogues = [], live = []) {
  const seen = new Set(), local = [], bulk = [], front = [];
  for (const [source, into] of [[index, local], [playlists, local], [catalogues, bulk], [live, front]]) {
    for (const track of source) if (!seen.has(track.url)) { seen.add(track.url); into.push(track); }
  }
  return Object.freeze([...local, ...front, ...bulk]);
}

/** Android keeps one track row; browser playlists keep copies. Latest saved metadata wins. */
export function savedSearchTracks(playlists) {
  const known = new Map();
  const recent = [...playlists].sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0) || compareSearchText(a.id, b.id));
  for (const playlist of recent) for (const track of playlist.tracks ?? []) {
    if (track.url && !known.has(track.url)) known.set(track.url, Object.freeze({ ...track }));
  }
  return Object.freeze([...known.values()].sort(compareSearchTracks));
}

export function playlistSearchMatches(query, track, platforms = []) {
  return searchMatches(query, track.name, track.file)
    && (!platforms.length || platforms.includes(platformForFileName(track.file ?? '')?.id));
}

/** The browser supports the saved-reference subset of native Local, without filesystem scanning. */
export class LibrarySearch {
  constructor({ playlists, catalogues }) { Object.assign(this, { playlists, catalogues }); }
  async run({ query, scope }, signal) {
    scope = searchScope(scope.kind, scope.ids);
    const check = () => { if (signal?.aborted) throw new DOMException('Aborted', 'AbortError'); };
    const platforms = scope.kind === 'platform' ? scope.ids : [];
    const saved = searchesPlaylists(scope) ? savedSearchTracks(await this.playlists()) : [];
    check();
    const matching = [];
    for (const track of saved) {
      if (playlistSearchMatches(query, track, platforms)) matching.push(track);
      if (matching.length === SEARCH_SOURCE_LIMIT) break;
    }
    const online = searchesCatalogues(scope) ? await this.catalogues(query,
      scope.kind === 'online' ? scope.ids : [], platforms, SEARCH_SOURCE_LIMIT, signal) : { rows: [], total: 0, held: [] };
    check();
    return Object.freeze({ query, scope: searchScope(scope.kind, scope.ids),
      tracks: combineSearchResults([], matching, online.rows),
      catalogueMatches: online.rows.length === SEARCH_SOURCE_LIMIT ? online.total : 0,
      playlistCapped: matching.length === SEARCH_SOURCE_LIMIT, held: Object.freeze(online.held) });
  }
}
