// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { trimQuery, sameQueryCase } from './query-text.js';
import { HISTORY_PAGE, historyPageClamp } from './rules.js';

/** Match each field separately; accents and SQL metacharacters retain their literal meaning. */
export function historyMatches(query, title = '', author = '', file = '') {
  const phrase = trimQuery(query);
  return [title, author, file].some((field) => {
    for (let i = 0; i <= field.length - phrase.length; i++) {
      if (sameQueryCase(field.slice(i, i + phrase.length), phrase)) return true;
    }
    return false;
  });
}

/** A streaming scan retains at most the requested hundred and the last-page fallback. */
export class HistoryPageScan {
  #selected = [];
  #last = [];
  #total = 0;
  constructor(requested) { this.wanted = Math.max(0, Math.trunc(requested)); }
  accept(row) {
    const start = this.wanted * HISTORY_PAGE;
    if (this.#total % HISTORY_PAGE === 0) this.#last = [];
    this.#last.push(row);
    if (this.#total >= start && this.#total < start + HISTORY_PAGE) this.#selected.push(row);
    this.#total++;
  }
  result() {
    const page = historyPageClamp(this.wanted, this.#total);
    return Object.freeze({ total: this.#total, page,
      rows: Object.freeze(page === this.wanted ? this.#selected : this.#last) });
  }
}
