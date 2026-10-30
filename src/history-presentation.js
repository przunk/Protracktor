// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Query only requested IDs; old reads cannot restore presentation after Clear or navigation. */
export class HistoryPresentationReads {
  #shown = new Map();
  #key = null;
  #generation = 0;
  #read = null;
  constructor({ query, show, failed = () => {}, currentRevision }) {
    Object.assign(this, { query, show, failed, currentRevision });
  }
  async refresh(ids, revision) {
    const wanted = new Set(ids);
    const key = JSON.stringify([[...wanted].sort(), revision.number, revision.clearing]);
    if (key === this.#key) return;
    this.#key = key;
    const generation = ++this.#generation;
    this.#read?.abort();
    this.#read = new AbortController();
    this.#shown = revision.clearing ? new Map() : new Map([...this.#shown].filter(([id]) => wanted.has(id)));
    this.show(new Map(this.#shown));
    if (revision.clearing || !wanted.size) return;
    try {
      const found = await this.query(wanted, this.#read.signal);
      const current = this.currentRevision?.() ?? revision;
      if (generation !== this.#generation || current.number !== revision.number || current.clearing !== revision.clearing) return;
      this.#shown = new Map([...found].filter(([id]) => wanted.has(id)).map(([id, row]) => [id, Object.freeze({ ...row })]));
      this.show(new Map(this.#shown));
    } catch (error) {
      if (generation === this.#generation) { this.#key = null; this.failed(error); }
    }
  }
}
