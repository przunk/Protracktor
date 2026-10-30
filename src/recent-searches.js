// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

export const RECENT_RETAINED = 20;
export const RECENT_SHOWN = 3;

import { trimQuery, trimQueryStart, sameQueryCase } from './query-text.js';

export function cleanRecentQueries(value) {
  const kept = [];
  for (const candidate of Array.isArray(value) ? value : []) {
    if (typeof candidate !== 'string') continue;
    const query = trimQuery(candidate);
    if (query && kept.length < RECENT_RETAINED && !kept.some((old) => sameQueryCase(old, query))) kept.push(query);
  }
  return Object.freeze(kept);
}

export function recordRecentQuery(queries, submitted) {
  return trimQuery(submitted) ? cleanRecentQueries([submitted, ...queries]) : queries;
}

export function recentSearchSuggestions(queries, typed) {
  const prefix = trimQueryStart(typed);
  return Object.freeze(queries.filter((query) => query.length >= prefix.length
    && sameQueryCase(query.slice(0, prefix.length), prefix)).slice(0, RECENT_SHOWN));
}

/** Serialized persistence like RecentSearchHistory; a failed read must not overwrite unknown data. */
export class RecentSearchHistory {
  #queries = Object.freeze([]);
  #loaded = false;
  #readError = null;
  #queue;

  constructor({ read, write, onChange = () => {} }) {
    this.read = read;
    this.write = write;
    this.onChange = onChange;
    this.ready = this.#load().catch(() => this.#queries);
    this.#queue = this.ready;
  }

  get queries() { return this.#queries; }
  get readError() { return this.#readError; }
  suggestions(typed) { return recentSearchSuggestions(this.#queries, typed); }
  #notify() { queueMicrotask(() => this.onChange(this.#queries)); }

  async #load() {
    try {
      const next = cleanRecentQueries(await this.read());
      this.#queries = next;
      this.#loaded = true;
      this.#readError = null;
      this.#notify();
      return next;
    } catch (error) { this.#readError = error; throw error; }
  }

  record(query) {
    const task = this.#queue.then(async () => {
      if (!trimQuery(query)) return this.#queries;
      if (!this.#loaded) await this.#load();
      const next = recordRecentQuery(this.#queries, query);
      if (next.length === this.#queries.length && next.every((text, i) => text === this.#queries[i])) return this.#queries;
      await this.write(next);
      this.#queries = next;
      this.#notify();
      return next;
    });
    this.#queue = task.catch(() => {});
    return task;
  }
}
