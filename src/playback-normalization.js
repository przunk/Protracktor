// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { LoudnessAnalyser } from './loudness-analyser.js';
import { LoudnessProfiles } from './loudness-cache.js';
import { LoudnessPreparation } from './loudness-preparation.js';
import { loudnessVariantKey, nativeLoudnessProfile } from './loudness-profiles.js';

const snapshot = (bytes) => ArrayBuffer.isView(bytes)
  ? new Uint8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength).slice().buffer : bytes.slice(0);

/** Own optional measurements; only matching worklet identities may configure audible playback. */
export class PlaybackNormalization {
  #sources = new Map();
  #current = null;
  #warming = new Set();
  #configuration = 0;
  #revisionReady = Promise.resolve();
  #preparation = null;
  #analyser = null;
  constructor({ send, cache = new LoudnessProfiles(), crypto = globalThis.crypto,
    enabled = false, width = 100, createAnalyser = (wasmBinary) => new LoudnessAnalyser({ wasmBinary }) }) {
    Object.assign(this, { send, cache, crypto, enabled, width, createAnalyser });
  }
  setEngine(wasmBinary) {
    this.cancelPending();
    for (const controller of this.#warming) controller.abort();
    this.#analyser?.close();
    this.#analyser = this.createAnalyser(wasmBinary);
    let revision;
    this.#revisionReady = Promise.resolve().then(async () => {
      const digest = await this.crypto.subtle.digest('SHA-256', wasmBinary);
      revision = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
    }).catch(() => {});
    this.#preparation = new LoudnessPreparation({ cache: this.cache, analyser: this.#analyser, crypto: this.crypto,
      revision: () => { if (!revision) throw new Error('The engine identity is unavailable'); return revision; } });
  }
  register(requestId, bytes, name, { signal } = {}) {
    this.cancelPending();
    for (const controller of this.#warming) controller.abort();
    const controller = new AbortController();
    const stop = () => controller.abort();
    signal?.addEventListener('abort', stop, { once: true });
    if (signal?.aborted) controller.abort();
    const source = { requestId, bytes: snapshot(bytes), name, controller, analysis: null,
      base: null, subsong: null, width: this.width, pending: true, adopted: false,
      release: () => signal?.removeEventListener('abort', stop) };
    this.#sources.set(requestId, source); this.#current = requestId;
  }
  cancelPending() {
    for (const [id, source] of this.#sources) if (source.pending || !source.adopted) {
      source.controller.abort(); source.analysis?.abort(); source.bytes = null; source.release();
      this.#sources.delete(id);
    }
  }
  #currentSource(source) {
    return this.#sources.get(source.requestId) === source && this.#current === source.requestId && !source.controller.signal.aborted;
  }
  opened(requestId) {
    const source = this.#sources.get(requestId);
    if (source && this.#currentSource(source)) source.adopted = true;
  }
  async decoded({ requestId, current }) {
    const source = this.#sources.get(requestId);
    if (!source || !source.pending || !this.#currentSource(source)) return;
    // Duplicate decode notifications must not start another private decoder for the same open.
    if (source.analysis) return;
    const configuration = this.#configuration, width = this.width, analysis = new AbortController();
    source.analysis = analysis; source.subsong = current; source.width = width;
    const stop = () => analysis.abort(); source.controller.signal.addEventListener('abort', stop, { once: true });
    let prepared = null;
    try {
      await this.#revisionReady;
      if (!this.#currentSource(source)) return;
      if (this.#preparation) {
        try {
          prepared = await this.#preparation.prepare({ bytes: source.bytes, name: source.name, subsong: current, width },
            { signal: analysis.signal, analyse: this.enabled });
        } catch {
          // A live setting may cancel optional prefix work while the selected file remains valid.
          if (!this.#currentSource(source)) return;
          prepared = await this.#preparation.prepare({ bytes: source.bytes, name: source.name, subsong: current, width: this.width },
            { signal: source.controller.signal, analyse: false });
        }
      }
      if (!this.#currentSource(source)) return;
      source.base = prepared?.base ?? null; source.width = this.width; source.pending = false;
      const profile = configuration === this.#configuration && width === this.width && this.enabled ? prepared?.profile ?? null : null;
      this.send({ type: 'prepared', requestId, current, width, profile });
    } catch {
      if (this.#currentSource(source)) {
        source.pending = false;
        this.send({ type: 'prepared', requestId, current, width, profile: null });
      }
    } finally {
      source.bytes = null; source.release(); source.controller.signal.removeEventListener('abort', stop);
    }
  }
  #changed() {
    this.#configuration++;
    for (const source of this.#sources.values()) if (source.pending) source.analysis?.abort();
    for (const controller of this.#warming) controller.abort();
  }
  setEnabled(enabled) {
    if (enabled === this.enabled) return;
    this.enabled = enabled; this.#changed();
    if (!enabled) this.#analyser?.close();
    this.send({ type: 'normalization', enabled });
  }
  setWidth(width) {
    if (width === this.width) return;
    this.width = width; this.#changed(); this.send({ type: 'stereo', percent: width });
  }
  async context({ requestId, subsong, width }) {
    const source = this.#sources.get(requestId);
    if (!source || source.pending || !this.#currentSource(source)) return;
    source.subsong = subsong; source.width = width;
    const configuration = this.#configuration, selection = source.contextRead = (source.contextRead ?? 0) + 1;
    if (!this.enabled || width !== this.width || !source.base) return;
    let profile = null;
    try { profile = await this.cache.read(await loudnessVariantKey(source.base, subsong, width, this.crypto)); }
    catch { return; }
    if (this.enabled && profile && selection === source.contextRead && configuration === this.#configuration && this.#currentSource(source)
      && source.subsong === subsong && source.width === width && this.width === width)
      this.send({ type: 'normalization-profile', requestId, subsong, width, profile });
  }
  async measured({ requestId, subsong, width, values, reason }) {
    const source = this.#sources.get(requestId), profile = nativeLoudnessProfile(values);
    if (!source?.base || !profile || reason === 'progress') return;
    // This may be the old decoder after the visible source changed; never reconfigure playback here.
    try { await this.cache.write(await loudnessVariantKey(source.base, subsong, width, this.crypto), profile); }
    catch { /* Derived statistics never interrupt playback. */ }
  }
  closed(requestId) {
    const source = this.#sources.get(requestId);
    if (!source) return;
    source.controller.abort(); source.analysis?.abort(); source.bytes = null; source.release();
    this.#sources.delete(requestId);
    if (this.#current === requestId) this.#current = null;
  }
  async warm({ bytes, name }, { signal } = {}) {
    if (!this.enabled || !this.#preparation || signal?.aborted) return;
    const owned = snapshot(bytes);
    const controller = new AbortController(), stop = () => controller.abort();
    signal?.addEventListener('abort', stop, { once: true }); this.#warming.add(controller);
    if (signal?.aborted) controller.abort();
    try {
      await this.#revisionReady;
      if (controller.signal.aborted || !this.enabled) return;
      await this.#preparation.prepare({ bytes: owned, name, subsong: -1, width: this.width }, { signal: controller.signal });
    } catch { /* Read-ahead failure has no effect on the selected track or fetched bytes. */ }
    finally { signal?.removeEventListener('abort', stop); this.#warming.delete(controller); }
  }
}
