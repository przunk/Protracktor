// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { readZipMember } from './zip-archive.js';
const cancelled = (signal) => { if (signal?.aborted) throw new DOMException('Aborted', 'AbortError'); };

/** Permanent-archive facts come from payloads, independently of catalogue rows and fetched copies. */
export class OfflineAsma {
  #refresh = 0;
  #record = null;
  #urls = new Map();
  #damageRevision = null;
  #damaged = new Set();
  #problem = null;
  #listeners = new Set();
  constructor({ storage, urlForPath, changed }) {
    Object.assign(this, { storage, urlForPath });
    this.state = Object.freeze({ bytes: 0, files: 0, tracks: 0, at: null, loading: true, failed: null });
    changed(() => { void this.refresh(); });
    this.ready = this.refresh();
  }
  has(url) { return this.#urls.has(url); }
  subscribe(listener) { this.#listeners.add(listener); return () => this.#listeners.delete(listener); }
  #publish(state) { this.state = Object.freeze(state); for (const listener of this.#listeners) listener(this.state); }
  async #load() {
    const asked = ++this.#refresh;
    try {
      const record = await this.storage.read();
      if (asked === this.#refresh) {
        this.#record = record;
        const revision = record?.revision ?? null;
        if (revision !== this.#damageRevision) {
          this.#damageRevision = revision; this.#damaged = new Set(); this.#problem = null;
        }
        this.#urls = new Map((record?.entries ?? []).filter((entry) => entry.size > 0)
          .map((entry) => [this.urlForPath(entry.name), entry]).filter(([url]) => !this.#damaged.has(url)));
        this.#publish({ bytes: record?.body.size ?? 0, files: record?.entries.length ?? 0, tracks: record?.tracks ?? 0,
          at: record?.at ?? null, loading: false, failed: this.#problem });
      }
      return record;
    } catch (failed) {
      if (asked === this.#refresh) {
        this.#record = null; this.#urls = new Map();
        this.#publish({ bytes: 0, files: 0, tracks: 0, at: null, loading: false, failed });
      }
      return null;
    }
  }
  refresh() { return this.#load(); }
  async bytes(url, { signal } = {}) {
    cancelled(signal);
    if (typeof url !== 'string' || !url.startsWith('https://asma.atari.org/asma/')) return null;
    // Like native ZipFile-per-read, this observes deletion/replacement in another tab too.
    const record = await this.#load(); cancelled(signal);
    if (record?.revision === this.#damageRevision && this.#damaged.has(url)) return null;
    const entry = record?.entries.find((candidate) => this.urlForPath(candidate.name) === url);
    if (!entry) return null;
    try { return await readZipMember(record.body, entry, { signal }); }
    catch (failed) {
      if (signal?.aborted) throw failed;
      if (record.revision === this.#record?.revision) {
        this.#damaged.add(url); this.#problem = failed;
        this.#urls.delete(url); this.#publish({ ...this.state, failed });
      }
      return null; // A damaged archive must not prevent an individual HTTP/cached copy from playing.
    }
  }
}
