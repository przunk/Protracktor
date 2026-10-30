// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

export const LOUDNESS_WORKER_TIMEOUT_MS = 5000;
const aborted = () => new DOMException('Aborted', 'AbortError');

/** At most one worker/job; only this job's private source buffer may be transferred. */
export class LoudnessAnalyser {
  #worker = null;
  #active = null;
  #nextId = 0;
  constructor({ wasmBinary, createWorker = () => new Worker(new URL('./loudness-worker.js', import.meta.url), { type: 'module' }),
    timeout = LOUDNESS_WORKER_TIMEOUT_MS, setTimer = setTimeout, clearTimer = clearTimeout }) {
    Object.assign(this, { wasmBinary, createWorker, timeout, setTimer, clearTimer });
  }
  get running() { return this.#active !== null; }
  analyseOwned({ bytes, name, subsong, width }, { signal } = {}) {
    if (signal?.aborted) return Promise.reject(aborted());
    if (this.#active) throw new Error('A loudness analysis is already running');
    let fresh = false;
    if (!this.#worker) {
      try { this.#worker = this.createWorker(); fresh = true; }
      catch { return Promise.resolve(null); }
      if (!this.#worker || typeof this.#worker.postMessage !== 'function' || typeof this.#worker.terminate !== 'function') {
        this.#worker = null; return Promise.resolve(null);
      }
    }
    const worker = this.#worker, owner = { id: ++this.#nextId }; this.#active = owner;
    return new Promise((resolve, reject) => {
      let finished = false, timer;
      const finish = (value, error = null, dispose = false) => {
        if (finished) return; finished = true;
        signal?.removeEventListener('abort', stop); this.clearTimer(timer);
        if (this.#active === owner) this.#active = null;
        if (dispose && this.#worker === worker) { this.#worker = null; worker.terminate(); }
        if (error) reject(error); else resolve(value);
      };
      const stop = () => finish(null, aborted(), true);
      owner.cancel = stop;
      worker.onmessage = ({ data }) => {
        if (this.#worker === worker && this.#active === owner && data?.id === owner.id) finish(data.values ?? null);
      };
      const failed = (event) => {
        event?.preventDefault?.();
        if (this.#worker !== worker) return;
        if (!finished) finish(null, null, true);
        else { this.#worker = null; worker.terminate(); }
      };
      worker.onerror = failed;
      worker.onmessageerror = failed;
      signal?.addEventListener('abort', stop, { once: true });
      timer = this.setTimer(() => finish(null, null, true), this.timeout);
      if (finished) return;
      if (signal?.aborted) { stop(); return; }
      try {
        const request = { id: owner.id, bytes, name, subsong, width }, transfer = [bytes];
        if (fresh) { request.wasmBinary = this.wasmBinary.slice(0); transfer.push(request.wasmBinary); }
        worker.postMessage(request, transfer);
      } catch { finish(null, null, true); }
    });
  }
  close() {
    if (this.#active) this.#active.cancel();
    else if (this.#worker) { this.#worker.terminate(); this.#worker = null; }
  }
}
