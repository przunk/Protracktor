// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { setUiAttribute } from './ui-text.js';
import { message as uiMessageText } from './i18n.js';
import { followSubsong, selectSubsong } from './subsong-strip.js';

/** Keyed chips and deferred visibility work belong to one open strip, never to playback itself. */
export class SubsongStripView {
  constructor({ strip, visible, onSelect, requestFrame = (fn) => requestAnimationFrame(fn),
    cancelFrame = (id) => cancelAnimationFrame(id) }) {
    Object.assign(this, { strip, visible, onSelect, requestFrame, cancelFrame });
    this.key = null; this.count = 0; this.current = -1; this.lastSeen = -1; this.frame = null; this.visit = 0;
  }
  retire() {
    ++this.visit;
    if (this.frame !== null) this.cancelFrame(this.frame);
    this.frame = null;
  }
  hide() { this.retire(); this.lastSeen = -1; }
  clear() {
    this.hide(); this.key = null; this.count = 0; this.current = -1;
    this.strip.replaceChildren(); this.strip.scrollLeft = 0;
  }
  render({ key, count, current }) {
    this.retire();
    if (!Number.isSafeInteger(count) || count <= 1 || !Number.isInteger(current) || current < 0 || current >= count) {
      this.clear(); return;
    }
    if (this.key !== key || this.count !== count) {
      this.lastSeen = -1; this.key = key; this.count = count;
      const nodes = [];
      for (let index = 0; index < count; index++) {
        const button = this.strip.ownerDocument.createElement('button'); button.type = 'button'; button.className = 'subsong';
        button.textContent = String(index + 1);
        button.onclick = () => {
          if (button.isConnected && this.visible() && this.key === key && this.count === count && selectSubsong(index, this.current, count)) this.onSelect(index);
        };
        nodes.push(button);
      }
      this.strip.replaceChildren(...nodes);
    }
    this.current = current;
    for (let index = 0; index < count; index++) {
      const button = this.strip.children[index];
      button.setAttribute('aria-pressed', String(index === current));
      setUiAttribute(button, 'aria-label', uiMessageText('Tune {n} of {count}', { n: index + 1, count }));
    }
    this.show();
  }
  isVisible(index) {
    const node = this.strip.children[index];
    if (!node) return false;
    const row = node.getBoundingClientRect(), view = this.strip.getBoundingClientRect();
    return view.width > 0 && row.right > view.left && row.left < view.right;
  }
  show() {
    this.retire();
    if (!this.count || !this.visible()) { this.lastSeen = -1; return; }
    const visit = this.visit;
    this.frame = this.requestFrame(() => {
      if (visit !== this.visit) return;
      this.frame = null;
      if (!this.visible() || !this.strip.isConnected) { this.lastSeen = -1; return; }
      if (followSubsong({ opening: this.lastSeen < 0, previousVisible: this.isVisible(this.lastSeen), currentVisible: this.isVisible(this.current) })) {
        const left = this.strip.scrollLeft + this.strip.children[this.current].getBoundingClientRect().left - this.strip.getBoundingClientRect().left;
        // Direct alignment avoids an old smooth scroll continuing after a newer chip or manual drag.
        this.strip.scrollLeft = left;
      }
      this.lastSeen = this.current;
    });
  }
}
