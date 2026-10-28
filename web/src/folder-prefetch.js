// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

export const FOLDER_PREFETCH_BUDGET = 100 * 1024 * 1024;
export const FOLDER_PREFETCH_PARALLEL = 3;
const trackId = (track) => track.url ?? track.id;
const sizeOf = (track) => Math.max(0, Number(track.sizeBytes ?? track.size) || 0);

export const storedCacheAhead = (value) => ['wifi', 'always', 'off'].includes(value) ? value : 'wifi';
export const cacheAheadAllowed = (mode, metered) => mode === 'always' || mode === 'wifi' && metered === false;

/** Native folder order: cached files are free; stop, rather than skip, at the first over-budget row. */
export function planFolderPrefetch(tracks, cached, budget = FOLDER_PREFETCH_BUDGET) {
  const wanted = []; let spent = 0;
  for (const track of tracks) {
    const id = trackId(track);
    if (cached.has(id)) continue;
    const bytes = sizeOf(track);
    if (spent + bytes > budget) break;
    spent += bytes; wanted.push(id);
  }
  return wanted;
}

/** Ordered concurrent starts; leaving the folder retires waiters, not another caller's shared fetch. */
export async function runFolderPrefetch(urls, { fetch, signal, mayContinue = () => true,
  onStart = () => {}, onDone = () => {}, parallel = FOLDER_PREFETCH_PARALLEL }) {
  let next = 0;
  await Promise.all(Array.from({ length: Math.max(1, parallel) }, async () => {
    while (!signal?.aborted && mayContinue()) {
      if (next === urls.length) return;
      const url = urls[next++]; onStart(url);
      if (signal?.aborted) return;
      let success = false;
      try { success = !!await fetch(url, { signal }); } catch (error) { if (signal?.aborted) return; }
      if (signal?.aborted) return;
      onDone(url, success);
    }
  }));
}

export class CacheAheadPreferences {
  constructor(storage) { this.storage = storage; }
  read() {
    try { return { value: storedCacheAhead(this.storage().getItem('protracktor.cacheAhead')), failure: null }; }
    catch (failure) { return { value: 'wifi', failure }; }
  }
  write(value) {
    if (!['wifi', 'always', 'off'].includes(value)) throw new TypeError(`Invalid folder download choice: ${value}`);
    try { this.storage().setItem('protracktor.cacheAhead', value); return null; } catch (failure) { return failure; }
  }
}

/** One browsed-folder lifetime. Data-spending authority is supplied, never inferred from a bearer name. */
export class FolderPrefetch {
  #generation = 0;
  #controller = null;
  #folder = null;
  #identity = null;
  #mode;
  constructor({ fetch, cached, metered = () => null, mode = 'wifi', changed = () => {} }) {
    Object.assign(this, { fetch, cached, metered, changed }); this.#mode = storedCacheAhead(mode);
    this.state = Object.freeze({ key: null, fetching: Object.freeze([]), blocked: null });
    this.completion = Promise.resolve();
  }
  get mode() { return this.#mode; }
  #publish(state) { this.state = Object.freeze({ ...state, fetching: Object.freeze([...state.fetching]) }); this.changed(this.state); }
  setMode(mode) {
    const next = storedCacheAhead(mode);
    if (this.#mode === next) return;
    this.#mode = next; this.#start();
  }
  setFolder(key, tracks = []) {
    const identity = key && tracks.length ? JSON.stringify([key, tracks.map((track) => [trackId(track), sizeOf(track)])]) : null;
    if (identity === this.#identity) return;
    this.#identity = identity;
    this.#folder = identity ? { key, tracks: tracks.map((track) => Object.freeze({ ...track })) } : null;
    this.#start();
  }
  #start() {
    const request = ++this.#generation, old = this.#controller;
    this.#controller = null; old?.abort();
    if (request !== this.#generation) return;
    const folder = this.#folder;
    if (!folder) {
      // An observer can synchronously open another folder.
      this.completion = Promise.resolve(); this.#publish({ key: null, fetching: [], blocked: null }); return;
    }
    const held = new Set(folder.tracks.filter((track) => this.cached(trackId(track))).map(trackId));
    const wanted = planFolderPrefetch(folder.tracks, held), metered = this.metered();
    const blocked = wanted.length && this.#mode === 'wifi' ? metered == null ? 'unknown' : metered ? 'metered' : null : null;
    this.#publish({ key: folder.key, fetching: [], blocked });
    if (request !== this.#generation) return;
    if (!cacheAheadAllowed(this.#mode, metered) || !wanted.length) {
      this.completion = Promise.resolve(); return;
    }
    const controller = new AbortController(); this.#controller = controller;
    const live = () => request === this.#generation && !controller.signal.aborted;
    const tracks = new Map(folder.tracks.map((track) => [trackId(track), track]));
    // Install ownership before a progress observer can synchronously change folder or policy.
    this.completion = Promise.resolve().then(() => runFolderPrefetch(wanted, {
      signal: controller.signal, mayContinue: () => live() && cacheAheadAllowed(this.#mode, this.metered()),
      fetch: (url, options) => this.fetch(tracks.get(url), options),
      onStart: (url) => { if (live()) this.#publish({ ...this.state, fetching: [...this.state.fetching, url] }); },
      onDone: (url) => { if (live()) this.#publish({ ...this.state, fetching: this.state.fetching.filter((id) => id !== url) }); },
    })).finally(() => {
      if (!live()) return;
      this.#controller = null; this.#publish({ ...this.state, fetching: [] });
    });
  }
}
