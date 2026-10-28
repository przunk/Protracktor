// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

export const RECENT_RETAINED = 20;
export const RECENT_SHOWN = 3;

// Kotlin Char.isWhitespace includes separator controls, but not the BOM trimmed by JS trim().
const QUERY_SPACE = '[\\u0009-\\u000d\\u001c-\\u0020\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]';
const LEADING_SPACE = new RegExp(`^${QUERY_SPACE}+`, 'u');
const TRAILING_SPACE = new RegExp(`${QUERY_SPACE}+$`, 'u');
const trimStart = (value) => value.replace(LEADING_SPACE, '');
const trim = (value) => trimStart(value).replace(TRAILING_SPACE, '');

// Java ignoreCase uses single-character upper/lower mappings, not expanding SS or ligatures.
function foldCharacter(character) {
  const upper = character.toUpperCase();
  const simple = [...upper].length === 1 ? upper : character;
  return simple === '\u0130' ? 'i' : simple.toLowerCase();
}

export function sameQueryCase(first, second) {
  if (first.length !== second.length) return false;
  const a = [...first], b = [...second];
  return a.length === b.length && a.every((character, i) => character === b[i]
    || foldCharacter(character) === foldCharacter(b[i]));
}

export function cleanRecentQueries(value) {
  const kept = [];
  for (const candidate of Array.isArray(value) ? value : []) {
    if (typeof candidate !== 'string') continue;
    const query = trim(candidate);
    if (query && kept.length < RECENT_RETAINED && !kept.some((old) => sameQueryCase(old, query))) kept.push(query);
  }
  return Object.freeze(kept);
}

export function recordRecentQuery(queries, submitted) {
  return trim(submitted) ? cleanRecentQueries([submitted, ...queries]) : queries;
}

export function recentSearchSuggestions(queries, typed) {
  const prefix = trimStart(typed);
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
      if (!trim(query)) return this.#queries;
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
