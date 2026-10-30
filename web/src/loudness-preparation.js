// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { loudnessContentKey, loudnessVariantKey, nativeLoudnessProfile, measuredSubsong } from './loudness-profiles.js';
const active = (signal) => { if (signal?.aborted) throw new DOMException('Aborted', 'AbortError'); };

/** Native preparation order and cache aliases; source bytes are snapshotted before any await. */
export class LoudnessPreparation {
  #tail = Promise.resolve();
  constructor({ cache, analyser, revision, crypto = globalThis.crypto }) {
    Object.assign(this, { cache, analyser, revision, crypto });
  }
  async prepare({ bytes, name, subsong, width }, { signal, analyse = true } = {}) {
    active(signal);
    const owned = ArrayBuffer.isView(bytes)
      ? new Uint8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength).slice().buffer : bytes.slice(0);
    let base = null;
    try { base = await loudnessContentKey(owned, name, [], this.revision(), this.crypto); }
    catch { /* A measurement cache must never reject a playable source. */ }
    active(signal);
    if (!analyse) return { base, profile: null };
    const task = this.#tail.then(async () => {
      active(signal);
      let key = null;
      try { if (base) key = await loudnessVariantKey(base, subsong, width, this.crypto); } catch { base = null; }
      let known = null;
      try { if (key) known = await this.cache.read(key); } catch { /* Optional statistics. */ }
      active(signal);
      if (known) return { base, profile: known };
      let values = null;
      try { values = await this.analyser.analyseOwned({ bytes: owned, name, subsong, width }, { signal }); }
      catch { active(signal); }
      active(signal);
      const profile = nativeLoudnessProfile(values), actual = measuredSubsong(values?.[4]);
      if (profile && actual !== null && base) {
        try {
          await this.cache.write(await loudnessVariantKey(base, actual, width, this.crypto), profile);
          active(signal);
          if (subsong < 0) await this.cache.write(await loudnessVariantKey(base, -1, width, this.crypto), profile);
        } catch { active(signal); }
      }
      active(signal);
      return { base, profile: profile && (subsong < 0 || actual === subsong) ? profile : null };
    });
    this.#tail = task.catch(() => {});
    if (!signal) return task;
    return new Promise((resolve, reject) => {
      let settled = false;
      const finish = (done, value) => { if (settled) return; settled = true; signal.removeEventListener('abort', stop); done(value); };
      const stop = () => finish(reject, new DOMException('Aborted', 'AbortError'));
      signal.addEventListener('abort', stop, { once: true });
      task.then((value) => finish(resolve, value), (error) => finish(reject, error));
      if (signal.aborted) stop();
    });
  }
}
