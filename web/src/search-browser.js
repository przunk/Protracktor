// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { searchScope, searchScopeKey } from './library-search.js';

/** BrowseWorkflow's explicit-submit search, with a separate immutable producing result. */
export class SearchBrowser {
  #ticket = 0;
  #controller = null;
  #active = false;
  #state = Object.freeze({ query: '', scope: searchScope(), result: null, loading: false, failed: null });
  constructor({ run, changed = () => {}, failed = () => {} }) { Object.assign(this, { run, changed, failed }); }
  get state() { return this.#state; }
  get active() { return this.#active; }
  #publish(values) { this.#state = Object.freeze({ ...this.state, ...values }); this.changed(this.state); }
  #retire() { this.#ticket++; this.#controller?.abort(); this.#controller = null; return this.#ticket; }
  open() { this.#active = true; this.#publish({}); }
  leave() {
    this.#retire(); this.#active = false;
    if (this.state.loading) this.#state = Object.freeze({ ...this.state, loading: false });
  }
  setQuery(query) {
    if (!this.active || query === this.state.query) return;
    this.#retire(); this.#publish({ query, loading: false, failed: null });
  }
  setScope(scope) {
    if (!this.active) return;
    this.#retire(); this.#publish({ scope: searchScope(scope.kind, scope.ids), result: null, loading: false, failed: null });
  }
  toggleId(id) {
    const scope = this.state.scope;
    if (!this.active || scope.kind !== 'online' && scope.kind !== 'platform') return;
    this.#retire();
    const ids = new Set(scope.ids);
    if (ids.has(id)) ids.delete(id); else ids.add(id);
    this.#publish({ scope: searchScope(scope.kind, [...ids]), loading: false, failed: null });
  }
  async submit(query = this.state.query) {
    if (!this.active) this.#active = true;
    const ticket = this.#retire(), controller = new AbortController(), scope = this.state.scope;
    this.#controller = controller;
    const owns = () => this.active && this.#ticket === ticket;
    this.#publish({ query, result: null, loading: true, failed: null });
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
  get staleAnswer() {
    const { result, query, scope } = this.state;
    return !!result && (result.query !== query || searchScopeKey(result.scope) !== searchScopeKey(scope));
  }
}
