// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native TracksView's selected identities, scoped to the list that produced them. */
export class TrackSelection {
  #owner = null;
  #tracks = Object.freeze([]);
  #selected = new Set();
  constructor({ changed = () => {} } = {}) { this.changed = changed; }
  get owner() { return this.#owner; }
  get active() { return this.#selected.size > 0; }
  get count() { return this.#selected.size; }
  get tracks() { return this.#tracks; }
  get all() { return this.active && this.#tracks.every((track) => this.has(track.url)); }
  has(id) { return this.#selected.has(id); }
  accepts(owner, id) { return owner === this.owner && this.tracks.some((track) => track.url === id); }
  replace(owner, tracks) {
    const members = new Set(tracks.map((track) => track.url));
    this.#selected = owner === this.owner ? new Set([...this.#selected].filter((id) => members.has(id))) : new Set();
    this.#owner = owner; this.#tracks = Object.freeze([...tracks]); this.changed();
  }
  start(owner, id) {
    if (!this.accepts(owner, id)) return false;
    this.#selected.add(id); this.changed(); return true;
  }
  toggle(owner, id) {
    if (!this.accepts(owner, id)) return false;
    if (this.has(id)) this.#selected.delete(id); else this.#selected.add(id);
    this.changed(); return true;
  }
  toggleAll() {
    this.#selected = this.all ? new Set() : new Set(this.tracks.map((track) => track.url)); this.changed();
  }
  clear() { if (!this.active) return; this.#selected = new Set(); this.changed(); }
  capture() { return Object.freeze(this.tracks.filter((track) => this.has(track.url)).map((track) => Object.freeze({ ...track }))); }
  take() { const tracks = this.capture(); this.clear(); return tracks; }
}
