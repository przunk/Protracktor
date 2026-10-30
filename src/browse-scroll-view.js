// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** DOM adapter: capture before replacement and restore only a completed, visible owning visit. */
export class BrowseScrollView {
  constructor({ model, viewport, visible }) {
    Object.assign(this, { model, viewport, visible });
    this.level = null; this.visit = 0; this.complete = false; this.arriving = false; this.pending = null;
  }
  begin(level) {
    if (this.complete) this.model.save(this.level, this.viewport.scrollTop);
    if (this.level !== level) this.arriving = true;
    this.level = level; this.complete = false; this.pending = null;
    return ++this.visit;
  }
  remember(row) { if (this.level !== null) this.model.descendingFrom(this.level, row); }
  finish(visit, { loading = false } = {}) {
    if (visit !== this.visit || loading) return;
    this.pending = visit; this.reveal(this.level);
  }
  reveal(level) {
    if (level !== this.level || this.pending !== this.visit || !this.visible()) return;
    const rows = [...this.viewport.children].filter((node) => this.rowKey(node) !== null);
    this.viewport.scrollTop = this.model.position(level);
    if (this.arriving) {
      const action = this.model.restoreFor(level, rows.map((node) => this.rowKey(node)));
      switch (action.kind) {
        case 'wait': return;
        case 'scroll': {
          const row = rows[action.index];
          this.viewport.scrollTop += row.getBoundingClientRect().top - this.viewport.getBoundingClientRect().top;
          this.model.returned(level); break;
        }
        case 'forget': this.model.returned(level); break;
        case 'nothing': break;
        default: throw new TypeError(`BrowseRestore(${action.kind})`);
      }
    }
    this.arriving = false; this.pending = null;
    this.complete = rows.length > 0;
  }
  rowKey(node) { return node.dataset.url ?? node.dataset.place ?? node.dataset.browseKey ?? null; }
  reset() {
    ++this.visit; this.model.clear(); this.level = null; this.complete = false; this.arriving = false;
    this.pending = null; this.viewport.scrollTop = 0;
  }
}
