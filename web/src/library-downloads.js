// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

export const DOWNLOAD_CHOICES = Object.freeze(['modland', 'asma', 'songmetadata']);
export const DOWNLOAD_STEPS = Object.freeze(['modland', 'favourites', 'asma', 'songlengths', 'trackmetadata']);
// Credits only: the WEB scope deliberately excludes songdb's separate duration dataset.
export const DOWNLOAD_MIB = Object.freeze({ modland: 6, favourites: 1, asma: 20, songlengths: 5, trackmetadata: 15 });
export function downloadSteps(selected) {
  const chosen = new Set(selected), steps = [];
  if (chosen.has('modland')) steps.push('modland', 'favourites');
  if (chosen.has('asma')) steps.push('asma');
  if (chosen.has('songmetadata')) steps.push('songlengths', 'trackmetadata');
  return steps;
}
export const downloadMegabytes = (selected) => downloadSteps(selected).reduce((total, id) => total + DOWNLOAD_MIB[id], 0);
export const missingDownloads = (held) => DOWNLOAD_CHOICES.filter((id) => !held[id]);
const snapshot = (state) => Object.freeze({ ...state, rows: Object.freeze(state.rows.map((row) => Object.freeze({ ...row,
  progress: row.progress ? Object.freeze({ ...row.progress }) : null }))) });

/** Common step locks serve individual and grouped actions; only the selected run belongs to Stop. */
export class LibraryDownloads {
  #busy = new Map();
  #active = null;
  #latest = null;
  #listeners = new Set();
  constructor(steps, { changed = () => {}, busyChanged = () => {} } = {}) {
    for (const id of DOWNLOAD_STEPS) if (typeof steps[id] !== 'function') throw new TypeError(`Missing download step: ${id}`);
    this.steps = Object.freeze({ ...steps }); Object.assign(this, { changed, busyChanged });
    this.state = snapshot({ phase: 'idle', rows: [] }); this.completion = Promise.resolve(this.state);
  }
  get running() { return this.#active !== null; }
  get busy() { return [...this.#busy.keys()]; }
  get retryable() { return this.state.rows.filter((row) => row.status === 'failed' || row.status === 'pending').map((row) => row.id); }
  subscribe(listener) { this.#listeners.add(listener); return () => this.#listeners.delete(listener); }
  #publish(state) {
    this.state = snapshot(state); this.changed(this.state);
    for (const listener of this.#listeners) listener(this.state);
  }
  async executeStep(id, { signal, onProgress = () => {} } = {}) {
    if (!DOWNLOAD_STEPS.includes(id)) throw new TypeError(`Unknown download step: ${id}`);
    if (signal?.aborted) return { status: 'pending' };
    if (this.#busy.has(id)) return { status: 'busy', completion: this.#busy.get(id).completion };
    let finish, result;
    const owner = { completion: new Promise((resolve) => { finish = resolve; }) }; this.#busy.set(id, owner);
    try {
      this.busyChanged(this.busy);
      if (signal?.aborted) return result = { status: 'pending' };
      const value = await this.steps[id]({ signal, onProgress: (progress) => {
        if (this.#busy.get(id) === owner && !signal?.aborted) onProgress(progress);
      } });
      // A completed atomic commit remains completed even if Stop won just after publication.
      return result = { status: 'done', value };
    } catch (error) {
      return result = signal?.aborted ? { status: 'pending' } : { status: 'failed', error };
    } finally {
      if (this.#busy.get(id) === owner) { this.#busy.delete(id); finish(result); this.busyChanged(this.busy); }
    }
  }
  start(selected) {
    const ids = downloadSteps(selected);
    return ids.length ? this.#start(ids.map((id) => ({ id, status: 'pending' }))) : null;
  }
  retry() {
    if (!this.retryable.length) return null;
    return this.#start(this.state.rows.map((row) => row.status === 'done' ? row : { id: row.id, status: 'pending' }));
  }
  #start(rows) {
    if (this.#active) return null;
    const owner = { controller: new AbortController() }; this.#active = owner; this.#latest = owner;
    const { signal } = owner.controller;
    // Install completion before observers may synchronously Stop or begin another operation.
    this.completion = Promise.resolve().then(async () => {
      for (const original of rows) {
        if (signal.aborted || this.#active !== owner) break;
        if (original.status === 'done') continue;
        const update = (patch) => {
          if (this.#latest === owner) this.#publish({ ...this.state,
            rows: this.state.rows.map((row) => row.id === original.id ? { ...row, ...patch } : row) });
        };
        update({ status: 'running', progress: null });
        const { completion, ...result } = await this.executeStep(original.id, { signal, onProgress: (progress) => update({ progress }) });
        update({ ...result, progress: null });
        // Observe another action's result without owning or cancelling that action.
        if (completion) void completion.then((settled) => update({ ...settled, progress: null }));
      }
      if (this.#active !== owner) return this.state;
      const state = { ...this.state, phase: signal.aborted ? 'stopped' : 'finished' };
      this.#active = null; this.#publish(state); return snapshot(state);
    });
    this.#publish({ phase: 'running', rows }); return this.completion;
  }
  stop() {
    const active = this.#active; if (!active) return false;
    active.controller.abort();
    if (this.#active === active) this.#publish({ ...this.state, phase: 'stopping' });
    return true;
  }
}
