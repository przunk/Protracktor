// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Preview stays in the pending block; only release commits the model's occurrence-ID move. */
export class PendingReorder {
  constructor({ list, rows, generation, move, onStart = () => {} }) {
    Object.assign(this, { list, rows, generation, move, onStart });
    this.state = null;
    this.preview = new Map();
    this.frame = null;
  }
  get active() { return !!this.state; }
  get draggedId() { return this.state?.id ?? null; }
  clearPreview() {
    for (const node of this.preview.keys()) { node.style.transform = ''; node.classList.remove('dragging', 'reorder-preview'); }
    this.preview.clear();
  }
  validate() {
    if (this.state && (this.state.generation !== this.generation()
      || this.rows().map((row) => row.id).join() !== this.state.ids.join()))
      this.state.finish(false);
  }

  bind(grip, node, id) {
    const document = node.ownerDocument, view = document.defaultView;
    let swallowClick = false;
    const down = (event) => {
      if (this.active || event.isPrimary === false || event.button > 0 || !this.rows().some((row) => row.id === id)) return;
      event.preventDefault(); event.stopPropagation();
      swallowClick = false;
      this.onStart();
      grip.focus({ preventScroll: true });
      const rect = node.getBoundingClientRect();
      const state = { id, pointer: event.pointerId, generation: this.generation(), y: event.clientY,
        startY: event.clientY, startX: event.clientX, startScroll: this.list.scrollTop, ids: this.rows().map((row) => row.id),
        grab: event.clientY - rect.top, height: rect.height, moved: false, target: id };
      const tops = new Map(this.rows().map((row) => [row.node, row.node.getBoundingClientRect().top + this.list.scrollTop]));
      this.state = state;
      const update = () => {
        if (this.state !== state) return;
        const entries = this.rows();
        if (state.generation !== this.generation() || entries.map((row) => row.id).join() !== state.ids.join()) { finish(false); return; }
        if (!state.moved || state.height <= 0) return;
        // Layout anchors exclude neighbour transitions and keep autoscroll under the pointer.
        const baseTop = (element) => tops.get(element) - this.list.scrollTop;
        const from = entries.findIndex((row) => row.id === id);
        // Android ManualQueueReorder swaps after a full row and clamps the visual offset too.
        const delta = state.y - state.startY + this.list.scrollTop - state.startScroll;
        const target = Math.max(0, Math.min(entries.length - 1, from + Math.trunc(delta / state.height)));
        state.target = entries[target].id;
        const top = Math.max(baseTop(entries[0].node), Math.min(baseTop(entries.at(-1).node), state.y - state.grab));
        state.center = top + state.height / 2;
        for (let i = 0; i < entries.length; i++) {
          const row = entries[i];
          const offset = row.id === id ? top - baseTop(node)
            : from < target && i > from && i <= target ? -state.height
              : from > target && i >= target && i < from ? state.height : 0;
          this.preview.set(row.node, offset);
          row.node.style.transform = offset ? `translateY(${offset}px)` : '';
          row.node.classList.toggle('dragging', row.id === id);
          row.node.classList.toggle('reorder-preview', row.id !== id && offset !== 0);
        }
      };
      let previousFrame = null;
      const autoScroll = (time) => {
        if (this.state !== state) return;
        const seconds = previousFrame === null ? 0 : Math.min(0.05, (time - previousFrame) / 1000);
        previousFrame = time;
        if (state.moved) {
          const bounds = this.list.getBoundingClientRect(), edge = 48;
          const target = state.ids.indexOf(state.target);
          const speed = state.center < bounds.top + edge && target > 0 ? -480 * seconds
            : state.center > bounds.bottom - edge && target < state.ids.length - 1 ? 480 * seconds : 0;
          if (speed) {
            this.list.scrollTop = Math.max(0, Math.min(this.list.scrollHeight - this.list.clientHeight, this.list.scrollTop + speed));
            update();
          }
        }
        this.frame = view.requestAnimationFrame(autoScroll);
      };
      const pointerMove = (move) => {
        if (move.pointerId !== state.pointer) return;
        move.preventDefault();
        state.y = move.clientY;
        if (Math.abs(move.clientX - state.startX) >= 8) swallowClick = true;
        if (Math.abs(state.y - state.startY) >= 8) { state.moved = true; swallowClick = true; }
        update();
      };
      const finish = (commit) => {
        if (this.state !== state) return;
        this.state = null;
        view.cancelAnimationFrame(this.frame);
        document.removeEventListener('pointermove', pointerMove, true);
        document.removeEventListener('pointerup', pointerUp, true);
        document.removeEventListener('pointercancel', pointerCancel, true);
        document.removeEventListener('pointerdown', additionalPointer, true);
        grip.removeEventListener('lostpointercapture', pointerCancel);
        if (grip.hasPointerCapture?.(state.pointer)) grip.releasePointerCapture(state.pointer);
        const entries = this.rows();
        const valid = state.generation === this.generation() && entries.some((row) => row.id === id)
          && entries.some((row) => row.id === state.target);
        if (commit && state.moved && valid && id !== state.target) this.move(id, state.target);
        this.clearPreview();
      };
      const pointerUp = (up) => { if (up.pointerId === state.pointer) finish(true); };
      const pointerCancel = (cancel) => { if (cancel.pointerId === state.pointer) finish(false); };
      const additionalPointer = (event) => { if (event.pointerId !== state.pointer) finish(false); };
      state.finish = finish;
      document.addEventListener('pointermove', pointerMove, { capture: true, passive: false });
      document.addEventListener('pointerup', pointerUp, true);
      document.addEventListener('pointercancel', pointerCancel, true);
      document.addEventListener('pointerdown', additionalPointer, true);
      grip.addEventListener('lostpointercapture', pointerCancel);
      try { grip.setPointerCapture?.(state.pointer); } catch { /* document listeners retain mouse fallback */ }
      this.frame = view.requestAnimationFrame(autoScroll);
    };
    const click = (event) => { if (swallowClick) { swallowClick = false; event.preventDefault(); event.stopImmediatePropagation(); } };
    const keyboard = () => { swallowClick = false; };
    grip.addEventListener('pointerdown', down);
    grip.addEventListener('click', click, true);
    grip.addEventListener('keydown', keyboard, true);
    return () => {
      if (this.state?.id === id) this.state.finish(false);
      grip.removeEventListener('pointerdown', down);
      grip.removeEventListener('click', click, true);
      grip.removeEventListener('keydown', keyboard, true);
    };
  }
}
