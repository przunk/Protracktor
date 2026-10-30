// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { trimQuery } from './query-text.js';
import { historyPageClamp } from './rules.js';

const emptyHistory = () => ({ query: '', total: 0, page: 0, rows: Object.freeze([]), loading: false, failed: false });

/** Native HistoryBrowser/HistoryReads ownership, independent of DOM and playback. */
export class HistoryBrowser {
  #ticket = 0;
  #controller = null;
  #timer = null;
  #finishDelay = null;
  #state = Object.freeze(emptyHistory());
  active = false;
  constructor({ read, changed = () => {}, failed = () => {}, setTimer = setTimeout, cancelTimer = clearTimeout }) {
    Object.assign(this, { read, changed, failed, setTimer, cancelTimer });
  }
  get state() { return this.#state; }
  #publish(values) { this.#state = Object.freeze({ ...this.#state, ...values }); this.changed(this.#state); }
  #retire() {
    this.#ticket++;
    this.#controller?.abort(); this.#controller = null;
    if (this.#timer !== null) this.cancelTimer(this.#timer);
    this.#timer = null;
    this.#finishDelay?.(); this.#finishDelay = null;
    return this.#ticket;
  }
  leave() { this.#retire(); this.active = false; }
  open() {
    this.#retire(); this.active = true;
    this.#state = Object.freeze(emptyHistory());
    return this.#load(0, false, true);
  }
  restore({ historyQuery = '', historyPage = 0, historyRows = [], historyTotal = 0 }) {
    this.#retire(); this.active = true;
    this.#publish({ query: historyQuery, page: historyPage, rows: Object.freeze([...(historyRows ?? [])]),
      total: historyTotal, loading: false, failed: false });
  }
  setQuery(query) {
    if (!this.active || query === this.state.query && !this.state.failed) return Promise.resolve();
    return this.#load(0, true, true, query);
  }
  turn(page) {
    if (!this.active || this.state.loading) return Promise.resolve();
    return this.#load(historyPageClamp(page, this.state.total), false, false);
  }
  async #load(page, debounce, reset, query = this.state.query) {
    const ticket = this.#retire(), controller = new AbortController();
    this.#controller = controller;
    const owns = () => this.active && this.#ticket === ticket;
    this.#publish({ query, loading: true, failed: false,
      ...(reset ? { page: 0, total: 0, rows: Object.freeze([]) } : {}) });
    if (!owns()) return;
    if (debounce) await new Promise((resolve) => {
      this.#finishDelay = resolve;
      this.#timer = this.setTimer(() => { this.#timer = null; this.#finishDelay = null; resolve(); }, 200);
    });
    if (!owns()) return;
    try {
      const result = await this.read(trimQuery(query), page, controller.signal);
      if (owns()) this.#publish({ ...result, rows: Object.freeze([...result.rows]), loading: false, failed: false });
    } catch (error) {
      if (owns()) { this.#publish({ loading: false, failed: true }); this.failed(error); }
    } finally { if (owns()) this.#controller = null; }
  }
  async clear(action, onFailure = () => {}) {
    const ticket = this.#retire(), active = this.active;
    // Erasure is an explicit action: leaving the view may suppress publication, never the erase.
    try {
      await action();
      if (active && this.active && this.#ticket === ticket) this.#publish({ total: 0, page: 0,
        rows: Object.freeze([]), loading: false, failed: false });
    } catch (error) {
      if (active && this.active && this.#ticket === ticket && this.state.loading) await this.#load(0, false, true);
      onFailure(error);
    }
  }
}
