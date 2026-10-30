// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native HistoryWrites: one ordered writer, with Clear retiring queued work synchronously. */
export class HistoryWrites {
  #generation = 0;
  #revision = Object.freeze({ number: 0, clearing: false });
  #tail = Promise.resolve();
  #listeners = new Set();
  constructor({ record, qualify, erase, failed = () => {} }) {
    Object.assign(this, { writeRecord: record, writeQualification: qualify, erase, failed });
  }
  get generation() { return this.#generation; }
  get revision() { return this.#revision; }
  get settled() { return this.#tail; }
  subscribe(listener) { this.#listeners.add(listener); return () => this.#listeners.delete(listener); }
  #publish(clearing = this.#revision.clearing) {
    this.#revision = Object.freeze({ number: this.#revision.number + 1, clearing });
    for (const listener of this.#listeners) listener(this.#revision);
  }
  #chain(write) {
    const operation = this.#tail.then(write);
    this.#tail = operation.then(() => {}, () => {});
    return operation;
  }
  #enqueue(ticket, write) {
    return this.#chain(async () => {
      if (ticket !== this.#generation) return false;
      try {
        await write();
        this.#publish();
        return true;
      } catch (error) { this.failed(error); return false; }
    });
  }
  record(entry, ticket = this.#generation) { return this.#enqueue(ticket, () => this.writeRecord(entry)); }
  qualify(result) { return this.#enqueue(result.historyGeneration, () => this.writeQualification(result.trackId)); }
  clear() {
    const ticket = ++this.#generation;
    this.#publish(true);
    return this.#chain(async () => {
      try { await this.erase(); }
      catch (error) { this.failed(error); throw error; }
      finally { if (ticket === this.#generation) this.#publish(false); }
    });
  }
}
