// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native BrowseScroll's per-session offsets and identity-based pending return. */
export class BrowseScroll {
  #positions = new Map();
  #entered = new Map();
  position(key) { return this.#positions.get(key) ?? 0; }
  save(key, top) { if (Number.isFinite(top)) this.#positions.set(key, Math.max(0, top)); }
  descendingFrom(key, row) { this.#entered.set(key, row); }
  returned(key) { this.#entered.delete(key); }
  restoreFor(key, rows, loading = false) {
    if (!this.#entered.has(key)) return { kind: 'nothing' };
    if (loading || !rows.length) return { kind: 'wait' };
    const index = rows.indexOf(this.#entered.get(key));
    return index < 0 ? { kind: 'forget' } : { kind: 'scroll', index };
  }
  forgetWhere(matches) {
    for (const map of [this.#positions, this.#entered]) for (const key of map.keys()) if (matches(key)) map.delete(key);
  }
  clear() { this.#positions.clear(); this.#entered.clear(); }
}
