// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

const trackId = (track) => track.url ?? track.id;
const abortError = () => Object.assign(new Error('Aborted'), { name: 'AbortError' });

/** Native coroutine cancellation: release the observation/lock, never adopt a late producer. */
function owned(promise, signal) {
  if (!signal) return Promise.resolve(promise);
  return new Promise((resolve, reject) => {
    const abort = () => { signal.removeEventListener('abort', abort); reject(abortError()); };
    const complete = (finish) => (value) => { signal.removeEventListener('abort', abort); finish(value); };
    Promise.resolve(promise).then(complete(resolve), complete(reject));
    if (signal.aborted) abort(); else signal.addEventListener('abort', abort, { once: true });
  });
}

/** Android RandomHistory: the visible heard boundary and invisible prepared tail are distinct. */
export class RandomHistory {
  #tracks = [];
  #cursor = -1;
  #played = -1;
  #fresh = false;
  #generation = 0;
  #snapshot = Object.freeze([]);
  #fillTail = Promise.resolve();

  get cursor() { return this.#cursor; }
  get generation() { return this.#generation; }
  get current() { return this.#tracks[this.#cursor] ?? null; }
  get playedPicks() {
    return this.#snapshot ??= Object.freeze(this.#tracks.slice(0, this.#played + 1));
  }
  get nextIndex() { return this.#fresh ? this.#played + 1 : this.#cursor + 1; }
  get hasNext() { return this.nextIndex < this.#tracks.length; }

  clear() {
    ++this.#generation;
    this.#tracks = [];
    this.#cursor = this.#played = -1;
    this.#fresh = false;
    this.#snapshot = Object.freeze([]);
  }
  select(index) {
    if (!Number.isInteger(index) || index < 0 || index > this.#played || index >= this.#tracks.length) return null;
    ++this.#generation;
    this.#cursor = index;
    this.#fresh = false;
    return this.current;
  }
  previous() { return this.#cursor > 0 ? this.select(this.#cursor - 1) : null; }
  remove(index) {
    if (!Number.isInteger(index) || index < 0 || index > this.#played || index >= this.#tracks.length) return;
    ++this.#generation;
    this.#tracks.splice(index, 1);
    if (index <= this.#cursor) --this.#cursor;
    --this.#played;
    this.#snapshot = null;
  }
  changeScope() {
    ++this.#generation;
    this.#tracks.length = this.#played + 1;
    this.#fresh = false;
  }
  reroll() { this.changeScope(); this.#fresh = true; }
  advance() {
    if (!this.hasNext) return null;
    ++this.#generation;
    this.#cursor = this.nextIndex;
    if (this.#cursor > this.#played) { this.#played = this.#cursor; this.#snapshot = null; }
    this.#fresh = false;
    return this.current;
  }
  upcoming(count) { return Object.freeze(this.#tracks.slice(this.nextIndex, this.nextIndex + count)); }

  async fill(ahead, overdraw, sample, { signal } = {}) {
    const requested = this.#generation;
    const operation = this.#fillTail.then(async () => {
      if (signal?.aborted) throw abortError();
      if (requested !== this.#generation) return;
      const missing = Math.max(0, ahead - (this.#tracks.length - this.#played - 1));
      if (!missing) return;
      const seen = new Set(this.#tracks.map(trackId));
      const candidates = await owned(sample(missing * overdraw), signal);
      if (requested !== this.#generation) return;
      const unique = candidates.filter((track) => {
        const id = trackId(track);
        if (seen.has(id)) return false;
        seen.add(id); return true;
      }).slice(0, missing);
      const picks = unique.length ? unique : (await owned(sample(missing), signal)).slice(0, missing);
      if (requested !== this.#generation) return;
      this.#tracks.push(...picks);
    });
    // A cancelled waiter leaves the mutex chain in place until the previous owner releases it.
    this.#fillTail = operation.then(() => {}, () => {});
    await owned(operation, signal);
  }
}
