// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
export function savedPlaceId(key) {
  const fields = [key.catalogueId, key.format, key.author];
  if (!fields.every((value) => typeof value === 'string')) throw new TypeError('Invalid saved place key');
  return JSON.stringify(fields);
}
export function immutablePlace(place) {
  savedPlaceId(place.key);
  return Object.freeze({ key: Object.freeze({ ...place.key }), catalogueName: place.catalogueName, savedAt: place.savedAt });
}
const placeEncoder = new TextEncoder();
function binaryCompare(a, b) {
  const left = placeEncoder.encode(a), right = placeEncoder.encode(b);
  for (let i = 0; i < Math.min(left.length, right.length); i++) if (left[i] !== right[i]) return left[i] - right[i];
  return left.length - right.length;
}
/** SQLite's saved_at DESC, catalogue_id, format, author order, including Unicode binary ties. */
export function compareSavedPlaces(a, b) {
  return b.savedAt - a.savedAt || binaryCompare(a.key.catalogueId, b.key.catalogueId)
    || binaryCompare(a.key.format, b.key.format) || binaryCompare(a.key.author, b.key.author);
}
export function saveablePlace(path, sources) {
  return path.length === 3 && sources.includes(path[0])
    ? Object.freeze({ catalogueId: path[0], format: path[1], author: path[2] }) : null;
}
export function savedPlaceReason(key, meta, supported) {
  if (!supported) return 'catalogue-unavailable';
  if (!(meta?.tracks > 0 || meta?.total > 0)) return 'index-missing';
  return null;
}
/** Native SavedPlaces: serial local writes, one-shot Undo and owned navigation, without transport. */
export class SavedPlaces {
  #state = Object.freeze({ rows: Object.freeze([]), loading: true, failed: false, writing: false, opening: null });
  #tail = Promise.resolve();
  #pending = 0;
  #readTicket = 0;
  #openTicket = 0;
  #reading = null;
  #opening = null;
  constructor({ store, availability, resolve, show, isShowingList, notice, removed, changed = () => {} }) {
    Object.assign(this, { store, availability, resolve, show, isShowingList, notice, removed, changed });
  }
  get state() { return this.#state; }
  contains(key) { const id = savedPlaceId(key); return this.#state.rows.some((row) => savedPlaceId(row.place.key) === id); }
  #publish(change) { this.#state = Object.freeze({ ...this.#state, ...change }); this.changed(this.#state); }
  #exclusive(action) { const result = this.#tail.then(action); this.#tail = result.catch(() => {}); return result; }
  async refresh() {
    const ticket = ++this.#readTicket;
    this.#reading?.abort();
    const abort = this.#reading = new AbortController();
    this.#publish({ loading: true, failed: false });
    try {
      const rows = await this.#exclusive(async () => {
        if (ticket !== this.#readTicket) return null;
        return this.availability(await this.store.list(), abort.signal);
      });
      if (ticket === this.#readTicket) this.#publish({ rows: Object.freeze(rows.map((row) => Object.freeze({ ...row, place: immutablePlace(row.place) }))), loading: false });
    } catch (error) {
      if (ticket === this.#readTicket) this.#publish({ loading: false, failed: true });
    }
  }
  async #write(action) {
    this.#pending++; this.#publish({ writing: true });
    try { await this.#exclusive(action); }
    catch { this.notice('failed'); }
    finally { this.#pending--; this.#publish({ writing: this.#pending > 0 }); await this.refresh(); }
  }
  save(place) {
    const snapshot = immutablePlace(place);
    return this.#write(async () => this.notice(await this.store.save(snapshot) ? 'saved' : 'already-saved'));
  }
  remove(key) {
    const exact = Object.freeze({ ...key }), id = savedPlaceId(exact), offerUndo = this.isShowingList();
    return this.#write(async () => {
      const found = (await this.store.list()).find((place) => savedPlaceId(place.key) === id);
      const previous = found ? immutablePlace(found) : null;
      await this.store.remove(exact);
      let used = false;
      const undo = offerUndo && previous ? () => { if (!used) { used = true; return this.save(previous); } } : null;
      if (this.removed) this.removed(undo); else this.notice('removed');
    });
  }
  cancelOpening() {
    this.#openTicket++; this.#opening?.abort(); this.#opening = null; this.#publish({ opening: null });
  }
  async open(key) {
    this.cancelOpening();
    const ticket = this.#openTicket, abort = this.#opening = new AbortController();
    const exact = Object.freeze({ ...key }); this.#publish({ opening: exact });
    try {
      const destination = await this.resolve(exact, abort.signal);
      if (ticket !== this.#openTicket || !this.isShowingList()) return;
      if (destination.reason) {
        const id = savedPlaceId(exact);
        this.#publish({ rows: Object.freeze(this.#state.rows.map((row) => savedPlaceId(row.place.key) === id
          ? Object.freeze({ ...row, availability: destination.reason }) : row)) });
        this.notice('unavailable');
      } else this.show(exact, destination);
    } catch {
      if (ticket === this.#openTicket && this.isShowingList()) this.notice('failed');
    } finally { if (ticket === this.#openTicket) this.#publish({ opening: null }); }
  }
}
