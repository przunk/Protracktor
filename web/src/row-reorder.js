// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { moveItems } from './list-reorder.js';

/** Live native-order moves; views keep physical row nodes in place until the gesture stops. */
export class RowReorder {
  constructor({ list, rows, generation, move, onStart = () => {}, onStop = () => {}, clampEdges = true }) {
    Object.assign(this, { list, rows, generation, move, onStart, onStop, clampEdges });
    this.state = null; this.preview = new Map(); this.frame = null;
  }
  get active() { return !!this.state; }
  get draggedId() { return this.state?.id ?? null; }
  clearPreview() {
    for (const node of this.preview.keys()) { node.style.transform = ''; node.classList.remove('dragging', 'reorder-preview'); }
    this.preview.clear();
  }
  validate() {
    if (!this.state) return;
    const rows = this.rows();
    if (this.state.generation !== this.generation() || rows.length !== this.state.ids.length
      || rows.some((row, i) => row.id !== this.state.ids[i])) this.state.finish();
  }
  cancel() { this.state?.finish(); }

  bind(grip, node, id) {
    const document = node.ownerDocument, view = document.defaultView;
    let swallowClick = false, disposed = false;
    const down = (event) => {
      const entries = this.rows();
      if (this.active || event.isPrimary === false || event.button > 0 || entries.length < 2 || !entries.some((row) => row.id === id)) return;
      event.preventDefault(); event.stopPropagation(); swallowClick = false;
      this.onStart(); grip.focus({ preventScroll: true });
      const state = { id, pointer: event.pointerId, generation: this.generation(), y: event.clientY,
        startY: event.clientY, startX: event.clientX, lastY: event.clientY, lastScroll: this.list.scrollTop,
        height: node.getBoundingClientRect().height, moved: false, offset: 0,
        ids: entries.map((row) => row.id), tops: new Map(), heights: new Map() };
      for (const row of entries) {
        const rect = row.node.getBoundingClientRect();
        state.tops.set(row.node, rect.top + this.list.scrollTop); state.heights.set(row.node, rect.height);
      }
      state.firstTop = state.tops.get(entries[0].node);
      const span = state.tops.get(entries.at(-1).node) - state.firstTop;
      const heightBeforeLast = entries.slice(0, -1).reduce((sum, row) => sum + state.heights.get(row.node), 0);
      state.gap = Math.max(0, (span - heightBeforeLast) / (entries.length - 1));
      this.state = state;
      const update = () => {
        if (this.state !== state) return;
        this.validate(); if (this.state !== state || !state.moved || state.height <= 0) return;
        const delta = state.y - state.lastY + this.list.scrollTop - state.lastScroll;
        state.lastY = state.y; state.lastScroll = this.list.scrollTop;
        const from = state.ids.indexOf(id), total = state.offset + delta;
        const target = Math.max(0, Math.min(state.ids.length - 1, from + Math.trunc(total / state.height)));
        state.offset = total - (target - from) * state.height;
        if (target !== from) {
          const targetId = state.ids[target];
          // Publish expected IDs before a synchronous model/render callback validates this drag.
          state.ids = moveItems(state.ids, from, target);
          this.move(id, targetId);
          if (this.state !== state) return;
          this.validate(); if (this.state !== state) return;
        }
        if (this.clampEdges) state.offset = Math.max(target === 0 ? 0 : -state.height,
          Math.min(target === state.ids.length - 1 ? 0 : state.height, state.offset));
        let top = state.firstTop;
        for (const row of this.rows()) {
          const offset = top - state.tops.get(row.node) + (row.id === id ? state.offset : 0);
          this.preview.set(row.node, offset); row.node.style.transform = offset ? `translateY(${offset}px)` : '';
          row.node.classList.toggle('dragging', row.id === id);
          row.node.classList.toggle('reorder-preview', row.id !== id && offset !== 0);
          if (row.id === id) state.center = top - this.list.scrollTop + state.offset + state.height / 2;
          top += state.heights.get(row.node) + state.gap;
        }
      };
      let previousFrame = null;
      const autoScroll = (time) => {
        if (this.state !== state) return;
        const seconds = previousFrame === null ? 0 : Math.min(0.05, (time - previousFrame) / 1000);
        previousFrame = time;
        if (state.moved) {
          const bounds = this.list.getBoundingClientRect(), at = state.ids.indexOf(id), edge = 48;
          const speed = state.center < bounds.top + edge && at > 0 ? -480 * seconds
            : state.center > bounds.bottom - edge && at < state.ids.length - 1 ? 480 * seconds : 0;
          if (speed) {
            this.list.scrollTop = Math.max(0, Math.min(this.list.scrollHeight - this.list.clientHeight, this.list.scrollTop + speed));
            update();
          }
        }
        if (this.state === state) this.frame = view.requestAnimationFrame(autoScroll);
      };
      const pointerMove = (move) => {
        if (move.pointerId !== state.pointer) return;
        move.preventDefault(); state.y = move.clientY;
        if (Math.abs(move.clientX - state.startX) >= 8) swallowClick = true;
        if (Math.abs(state.y - state.startY) >= 8) { state.moved = true; swallowClick = true; }
        update();
      };
      const finish = () => {
        if (this.state !== state) return;
        const focus = document.activeElement === grip;
        this.state = null; view.cancelAnimationFrame(this.frame);
        document.removeEventListener('pointermove', pointerMove, true);
        document.removeEventListener('pointerup', pointerUp, true);
        document.removeEventListener('pointercancel', pointerCancel, true);
        document.removeEventListener('pointerdown', additionalPointer, true);
        grip.removeEventListener('lostpointercapture', pointerCancel);
        if (grip.hasPointerCapture?.(state.pointer)) grip.releasePointerCapture(state.pointer);
        this.clearPreview(); this.onStop({ id, generation: state.generation, focus, moved: state.moved });
      };
      const pointerUp = (up) => { if (up.pointerId === state.pointer) finish(); };
      const pointerCancel = (cancel) => { if (cancel.pointerId === state.pointer) finish(); };
      const additionalPointer = (next) => { if (next.pointerId !== state.pointer) finish(); };
      state.finish = finish;
      document.addEventListener('pointermove', pointerMove, { capture: true, passive: false });
      document.addEventListener('pointerup', pointerUp, true);
      document.addEventListener('pointercancel', pointerCancel, true);
      document.addEventListener('pointerdown', additionalPointer, true);
      grip.addEventListener('lostpointercapture', pointerCancel);
      try { grip.setPointerCapture?.(state.pointer); } catch { /* Document listeners retain mouse fallback. */ }
      this.frame = view.requestAnimationFrame(autoScroll);
    };
    const click = (event) => {
      if (swallowClick) { swallowClick = false; event.preventDefault(); event.stopImmediatePropagation(); }
      if (disposed) grip.removeEventListener('click', click, true);
    };
    const keyboard = () => { swallowClick = false; };
    grip.addEventListener('pointerdown', down); grip.addEventListener('click', click, true); grip.addEventListener('keydown', keyboard, true);
    return () => {
      if (this.state?.id === id) this.state.finish();
      disposed = true; grip.removeEventListener('pointerdown', down); grip.removeEventListener('keydown', keyboard, true);
      // A trailing synthesized click may still target a grip removed by the completion render.
      if (!swallowClick) grip.removeEventListener('click', click, true);
    };
  }
}
