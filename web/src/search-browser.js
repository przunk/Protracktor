// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { searchScope, searchScopeKey } from './library-search.js';

/** The phone's `BrowseWorkflow.FILTER_RERUN_DELAY_MS`: a second chip, not a wait. */
export const FILTER_RERUN_DELAY_MS = 300;

/** BrowseWorkflow's explicit-submit search, with a separate immutable producing result. */
export class SearchBrowser {
  #ticket = 0;
  #rerunPending = false;
  #controller = null;
  #active = false;
  #state = Object.freeze({ query: '', scope: searchScope(), result: null, loading: false, failed: null });
  constructor({ run, changed = () => {}, failed = () => {},
    pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms)) }) {
    Object.assign(this, { run, changed, failed, pause });
  }
  get state() { return this.#state; }
  get active() { return this.#active; }
  #publish(values) { this.#state = Object.freeze({ ...this.state, ...values }); this.changed(this.state); }
  #retire() { this.#ticket++; this.#rerunPending = false; this.#controller?.abort(); this.#controller = null; return this.#ticket; }
  /** Whether a search is on screen or on its way, so a filter change should ask it again. */
  get #shown() { return !!this.state.result || this.state.loading; }
  /**
   * A filter changed while a search was shown asks it again (Q17, as the phone does): the words in
   * the field, after a short pause so several chips in a row are one search. Any newer edit,
   * submission or leaving retires it before it starts.
   */
  #rerunAfterFilter(wasShown, keepShown) {
    if (!wasShown) return;
    const ticket = this.#ticket;
    this.#rerunPending = true;
    Promise.resolve(this.pause(FILTER_RERUN_DELAY_MS)).then(() => {
      if (this.active && this.#ticket === ticket) void this.submit(this.state.query, { keepShown });
    });
  }
  open() { this.#active = true; this.#publish({}); }
  leave() {
    this.#retire(); this.#active = false;
    if (this.state.loading) this.#state = Object.freeze({ ...this.state, loading: false });
  }
  setQuery(query) {
    if (!this.active || query === this.state.query) return;
    this.#retire(); this.#publish({ query, loading: false, failed: null });
  }
  /** Another kind of search clears the rows; [rerun] false for Back, which only widens. */
  setScope(scope, { rerun = true } = {}) {
    if (!this.active) return;
    const wasShown = this.#shown;
    this.#retire(); this.#publish({ scope: searchScope(scope.kind, scope.ids), result: null, loading: false, failed: null });
    if (rerun) this.#rerunAfterFilter(wasShown, false);
  }
  toggleId(id) {
    const scope = this.state.scope;
    if (!this.active || scope.kind !== 'online' && scope.kind !== 'platform') return;
    const wasShown = this.#shown;
    this.#retire();
    const ids = new Set(scope.ids);
    if (ids.has(id)) ids.delete(id); else ids.add(id);
    this.#publish({ scope: searchScope(scope.kind, [...ids]), loading: false, failed: null });
    // The same kind of question, wider or narrower: the rows stay until the new answer.
    this.#rerunAfterFilter(wasShown, true);
  }
  async submit(query = this.state.query, { keepShown = false } = {}) {
    if (!this.active) this.#active = true;
    const ticket = this.#retire(), controller = new AbortController(), scope = this.state.scope;
    this.#controller = controller;
    const owns = () => this.active && this.#ticket === ticket;
    this.#publish({ query, result: keepShown ? this.state.result : null, loading: true, failed: null });
    if (!owns()) return;
    try {
      const result = await this.run({ query, scope }, controller.signal);
      if (owns()) this.#publish({ result, loading: false, failed: null });
      return owns() ? result : undefined;
    } catch (error) {
      if (owns()) { this.#publish({ loading: false, failed: error }); this.failed(error); }
      return undefined;
    } finally { if (owns()) this.#controller = null; }
  }
  restore(route) {
    this.#retire(); this.#active = true;
    const scope = route.searchScope ? searchScope(route.searchScope.kind, route.searchScope.ids) : searchScope();
    this.#publish({ query: route.query, scope, loading: false, failed: null,
      result: Object.freeze({ query: route.query, scope, tracks: route.results,
        ...(route.searchSummary ?? { catalogueMatches: 0, playlistCapped: false, held: [], fallbackNote: route.note }) }) });
  }
  /** Rows answering another question -- not while that question is already being asked again. */
  get staleAnswer() {
    const { result, query, scope, loading } = this.state;
    if (loading || this.#rerunPending) return false;
    return !!result && (result.query !== query || searchScopeKey(result.scope) !== searchScopeKey(scope));
  }
}
