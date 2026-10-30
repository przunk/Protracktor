// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { downloadMusic } from './music-download.js';

const cacheableMusicUrl = (url) => typeof url === 'string' && /^https?:\/\//i.test(url);
const cancelled = () => new DOMException('Aborted', 'AbortError');
const usableBlob = (blob) => Object.prototype.toString.call(blob) === '[object Blob]' && blob.size > 0;

/** Disk ownership is separate from waiting callers and transferable decoder buffers. */
export class RemoteMusic {
  #jobs = new Map();
  #publication = 0;
  #clear = Promise.resolve();
  #refresh = 0;
  #maintenance = 0;
  #problem = null;
  #urls = new Set();
  #listeners = new Set();
  constructor({ cache, fetch = globalThis.fetch, inUse = () => [], failure = () => {} }) {
    Object.assign(this, { cache, fetchNetwork: fetch, inUse, failure });
    this.state = Object.freeze({ bytes: 0, count: 0, loading: true, failed: null });
    cache.subscribe(() => { void this.refresh(); });
    this.ready = this.retry();
  }
  subscribe(listener) { this.#listeners.add(listener); return () => this.#listeners.delete(listener); }
  has(url) { return this.#urls.has(url); }
  #publish(state) {
    this.state = Object.freeze(state);
    for (const listener of this.#listeners) listener(this.state);
  }
  #rememberFailure(error) {
    this.#problem = error;
    this.#publish({ ...this.state, failed: error });
    this.failure(error);
  }
  #recovered() {
    this.#problem = null;
    if (this.state.failed) this.#publish({ ...this.state, failed: null });
  }
  async retry() {
    const asked = ++this.#maintenance;
    this.#publish({ ...this.state, loading: true });
    try {
      await this.cache.enforce({ inUse: this.inUse });
      if (asked !== this.#maintenance) return;
      this.#recovered();
    } catch (error) {
      if (asked !== this.#maintenance) return;
      this.#rememberFailure(error);
    }
    return this.refresh();
  }
  async refresh() {
    const asked = ++this.#refresh;
    try {
      const rows = await this.cache.snapshot();
      if (asked !== this.#refresh) return;
      this.#urls = new Set(rows.map((row) => row.url));
      this.#publish({ bytes: rows.reduce((bytes, row) => bytes + row.sizeBytes, 0), count: rows.length, loading: false, failed: this.#problem });
    } catch (error) {
      if (asked !== this.#refresh) return;
      // A failed read cannot establish that previously held files remain offline.
      this.#urls = new Set();
      this.#publish({ bytes: 0, count: 0, loading: false, failed: error });
    }
  }
  async bytes(url, { signal } = {}) {
    if (signal?.aborted) throw cancelled();
    const publication = this.#publication;
    await this.#clear;
    if (signal?.aborted) throw cancelled();
    try {
      const blob = cacheableMusicUrl(url) ? await this.cache.read(url) : null;
      if (usableBlob(blob)) {
        const bytes = await blob.arrayBuffer();
        if (signal?.aborted) throw cancelled();
        // Touch is best effort as on Android; failure must not withhold already readable bytes.
        await this.cache.touch(url).catch((error) => this.#rememberFailure(error));
        if (signal?.aborted) throw cancelled();
        return bytes;
      }
    } catch (error) {
      if (signal?.aborted) throw error;
      this.#rememberFailure(error);
    }
    if (signal?.aborted) throw cancelled();
    let job = this.#jobs.get(url);
    if (!job || job.publication !== publication) {
      job = { publication };
      job.promise = this.#download(url, publication).finally(() => {
        if (this.#jobs.get(url) === job) this.#jobs.delete(url);
      });
      this.#jobs.set(url, job);
    }
    const result = await new Promise((resolve, reject) => {
      const abort = () => { cleanup(); reject(cancelled()); };
      const cleanup = () => signal?.removeEventListener('abort', abort);
      signal?.addEventListener('abort', abort, { once: true });
      job.promise.then((bytes) => { cleanup(); resolve(bytes); }, (error) => { cleanup(); reject(error); });
      if (signal?.aborted) abort();
    });
    if (signal?.aborted) throw cancelled();
    return result.slice(0);
  }
  async #download(url, publication) {
    const bytes = await downloadMusic(this.fetchNetwork, url);
    if (bytes.byteLength && cacheableMusicUrl(url) && publication === this.#publication) {
      try {
        // Construct the immutable payload before transfer; retention failure cannot stop playback.
        const blob = await new Response(bytes).blob();
        const stored = await this.cache.write(url, blob, { inUse: this.inUse, canPublish: () => publication === this.#publication });
        if (stored && publication === this.#publication) this.#recovered();
      } catch (error) { if (publication === this.#publication) this.#rememberFailure(error); }
    }
    return bytes;
  }
  async clear() {
    ++this.#publication; ++this.#maintenance; this.#jobs.clear();
    const clearing = this.#clear.then(() => this.cache.clear());
    // A failed maintenance action must not permanently reject future playback requests.
    this.#clear = clearing.catch(() => {});
    const freed = await clearing;
    this.#recovered();
    await this.refresh();
    return freed;
  }
}
