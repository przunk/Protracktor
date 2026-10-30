// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

export const READ_AHEAD_COUNT = 3;
export const MAX_PREFETCH_BYTES = 4 * 1024 * 1024;
export const MAX_PREFETCH_TOTAL_BYTES = 8 * 1024 * 1024;
const trackId = (track) => track.url ?? track.id;

/** Pending requests lead the source; duplicate files need bytes once, not duplicate queue entries. */
export function readAheadCandidates(pending, source) {
  const found = new Set(), wanted = [];
  for (const track of [...pending, ...source]) {
    const id = trackId(track);
    if (!id || track.local || found.has(id)) continue;
    found.add(id); wanted.push(track);
    if (wanted.length === READ_AHEAD_COUNT) break;
  }
  return wanted;
}

/** Native PlaybackReadAhead ownership: bounded completed buffers, independent from shared transfers. */
export class PlaybackReadAhead {
  #held = new Map();
  #wanted = new Set();
  #controller = null;
  #generation = 0;
  constructor(read) { this.read = read; }
  get ids() { return [...this.#wanted]; }
  get sizeBytes() { let bytes = 0; for (const value of this.#held.values()) bytes += value.byteLength; return bytes; }
  take(id) {
    const bytes = this.#held.get(id) ?? null;
    this.#held.delete(id); return bytes;
  }
  #retire() {
    const request = ++this.#generation, old = this.#controller;
    this.#controller = null; old?.abort();
    return request;
  }
  prepare(wanted) {
    const request = this.#retire();
    // An abort listener may have synchronously started a newer preparation.
    if (request !== this.#generation) return Promise.resolve();
    const unique = new Map();
    for (const track of wanted) if (!unique.has(trackId(track))) unique.set(trackId(track), track);
    this.#wanted = new Set(unique.keys());
    for (const id of this.#held.keys()) if (!unique.has(id)) this.#held.delete(id);
    const missing = [...unique].filter(([id]) => !this.#held.has(id));
    if (!missing.length) return Promise.resolve();
    const controller = new AbortController(); this.#controller = controller;
    const jobs = missing.map(async ([id, track]) => {
      if (controller.signal.aborted) return;
      const bytes = await this.read(track, { signal: controller.signal });
      if (bytes == null || controller.signal.aborted || request !== this.#generation) return;
      if (bytes.byteLength <= MAX_PREFETCH_BYTES && this.sizeBytes + bytes.byteLength <= MAX_PREFETCH_TOTAL_BYTES)
        this.#held.set(id, bytes);
    });
    return Promise.all(jobs).then(() => {}, (error) => {
      if (controller.signal.aborted || request !== this.#generation) return;
      controller.abort(error); throw error;
    }).finally(() => { if (this.#controller === controller) this.#controller = null; });
  }
  clear() {
    const request = this.#retire();
    if (request === this.#generation) { this.#held.clear(); this.#wanted.clear(); }
  }
}
