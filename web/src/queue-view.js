// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { t } from './i18n.js';
import { bindTrackGestures } from './row-gestures.js';
import { PendingReorder } from './queue-reorder.js';

/** Keyed presentation of the production plan; this component never owns transport or storage. */
export class QueueView {
  constructor({ root, list, source, empty, repeatNote, icon, sourceLabel, subtitle,
    onPlaySource, onPlayManual, onAdd, onRemove, onInfo, onMove, onMenu }) {
    Object.assign(this, { root, list, source, empty, repeatNote, icon, sourceLabel, subtitle,
      onPlaySource, onPlayManual, onAdd, onRemove, onInfo, onMove, onMenu });
    this.nodes = new Map();
    this.currentKey = null;
    this.exitId = 0;
    this.plan = null;
    this.reorder = new PendingReorder({ list, generation: () => this.plan?.schedule.generation,
      rows: () => (this.plan?.schedule.pending ?? []).flatMap((request) => {
        const node = this.nodes.get(`manual:${request.id}`); return node && !node._exit ? [{ id: request.id, node }] : [];
      }), move: onMove, onStart: () => { for (const node of this.nodes.values()) node._animation?.cancel(); } });
  }

  get dragging() { return this.reorder.active; }

  get reducedMotion() { return this.root.ownerDocument.defaultView.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false; }

  play(node) {
    if (node._exit || node._row.track.local) return;
    const row = node._row;
    if (row.kind === 'manual') this.onPlayManual(row.request.id);
    else this.onPlaySource(row.id, node._generation);
  }

  historyOrigin(row) { return row.kind === 'manual' ? row.request.fromHistory : this.plan.context.fromHistory; }

  menu(node, anchor = node) {
    if (node._exit) return;
    const row = node._row;
    const pending = this.plan.schedule.pending;
    const at = row.kind === 'manual' ? pending.findIndex((entry) => entry.id === row.request.id) : -1;
    const items = [
      [t('Information'), () => this.onInfo(row.track), !row.track.local, 'info'],
      [t('Add to queue'), () => this.onAdd(row.track, this.historyOrigin(row)), !row.track.local, 'add'],
      [t('Remove'), () => this.onRemove(row, node._generation), true, 'remove'],
    ];
    if (at >= 0) {
      items.push([t('Move earlier'), () => this.onMove(row.request.id, pending[at - 1].id), at > 0, 'up']);
      items.push([t('Move later'), () => this.onMove(row.request.id, pending[at + 1].id), at + 1 < pending.length, 'down']);
    }
    this.onMenu(items, anchor);
  }

  create(key) {
    const document = this.root.ownerDocument;
    const node = document.createElement('li');
    node.classList.add('track', 'queue-track');
    node.dataset.key = key;
    const origin = document.createElement('div'); origin.className = 'queue-origin';
    const text = document.createElement('div'); text.className = 'text'; text.tabIndex = 0; text.setAttribute('role', 'button');
    const title = document.createElement('div'); title.className = 'title';
    const meta = document.createElement('div'); meta.className = 'meta';
    text.append(title, meta); node.append(origin, text);
    node._elements = { origin, text, title, meta };
    node.onclick = () => this.play(node);
    node.oncontextmenu = (event) => { event.preventDefault(); this.menu(node); };
    text.onkeydown = (event) => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); event.stopPropagation(); this.play(node); }
      if (event.key === 'ContextMenu' || event.key === 'F10' && event.shiftKey) {
        event.preventDefault(); event.stopPropagation(); this.menu(node);
      }
    };
    node._disposeGesture = bindTrackGestures(node, {
      icon: this.icon, addLabel: t('Add to queue'), removeLabel: t('Remove'),
      onAdd: () => this.onAdd(node._row.track, this.historyOrigin(node._row)),
      onRemove: () => this.onRemove(node._row, node._generation),
      onLongPress: () => this.onInfo(node._row.track),
      canSwipe: () => !node._exit && !this.dragging,
      canAdd: () => !node._row.track.local,
      canLongPress: () => !node._exit && !node._row.track.local && !this.dragging,
    });
    return node;
  }

  update(node, row, generation, hasSourceRows) {
    node._row = row; node._generation = generation;
    node.dataset.kind = row.kind;
    node.dataset.url = row.track.url;
    node.dataset.request = row.kind === 'manual' ? row.request.id : '';
    node.classList.toggle('playing', row.playing);
    node.classList.toggle('manual', row.kind === 'manual');
    node.classList.toggle('inserted', row.kind === 'manual' && hasSourceRows);
    node.classList.toggle('local', !!row.track.local);
    node.setAttribute('aria-current', row.playing ? 'true' : 'false');
    const { origin, text, title, meta } = node._elements;
    origin.innerHTML = (row.playing ? this.icon('play') : '')
      + (row.kind === 'manual' ? this.icon('add') : !row.playing ? this.icon('source') : '');
    title.textContent = row.track.name;
    meta.textContent = this.subtitle(row.track);
    text.setAttribute('aria-label', `${row.track.name}${row.kind === 'manual' ? ` · ${t('Manually queued')}` : ''}`);
    text.setAttribute('aria-disabled', row.track.local ? 'true' : 'false');
    const at = row.kind === 'manual' ? this.plan.schedule.pending.findIndex((entry) => entry.id === row.request.id) : -1;
    if (at >= 0 && this.plan.schedule.pending.length > 1) {
      if (!node._grip) {
        const grip = node.ownerDocument.createElement('button');
        grip.className = 'queue-grip';
        grip.innerHTML = this.icon('grip'); grip.append(t('Reorder'));
        grip.onclick = (event) => { event.stopPropagation(); this.menu(node, grip); };
        grip.onkeydown = (event) => {
          if (!['ArrowUp', 'ArrowDown'].includes(event.key)) return;
          event.preventDefault(); event.stopPropagation();
          const pending = this.plan.schedule.pending;
          const at = pending.findIndex((entry) => entry.id === node._row.request.id);
          const target = pending[at + (event.key === 'ArrowUp' ? -1 : 1)];
          if (at >= 0 && target) this.onMove(node._row.request.id, target.id);
        };
        node.querySelector('.swipe-surface').append(grip);
        node._grip = grip;
        node._disposeReorder = this.reorder.bind(grip, node, row.request.id);
      }
      node._grip.setAttribute('aria-label', t('Reorder {name}', { name: row.track.name }));
    } else if (node._grip) { node._disposeReorder(); node._grip.remove(); node._grip = null; }
  }

  render(plan) {
    this.plan = plan;
    this.source.textContent = this.sourceLabel(plan.context.source);
    const rows = plan.schedule.rows(plan.context);
    const hasSource = rows.some((row) => row.kind === 'source');
    const keyOf = (row) => row.kind === 'source' ? `${plan.schedule.generation}:${row.key}` : row.key;
    const wanted = new Set(rows.map(keyOf));
    const before = new Map([...this.nodes].map(([key, node]) => [key, node.getBoundingClientRect()]));
    this.reorder.validate();
    if (!this.reorder.active) this.reorder.clearPreview();
    const listRect = this.list.getBoundingClientRect();
    for (const [key, node] of this.nodes) {
      if (wanted.has(key) || node._exit) continue;
      const token = ++this.exitId;
      node._exit = token;
      node.inert = true;
      node.setAttribute('aria-hidden', 'true');
      node.setAttribute('aria-current', 'false');
      node._elements.text.tabIndex = -1;
      if (node._grip) node._grip.disabled = true;
      node.style.cssText = `position:absolute;top:${before.get(key).top - listRect.top + this.list.scrollTop}px;left:0;right:0;pointer-events:none`;
      const finish = () => {
        if (node._exit !== token) return;
        node._disposeReorder?.(); node._disposeGesture(); node.remove(); this.nodes.delete(key);
      };
      if (!this.reducedMotion && node.animate) {
        node._animation?.cancel();
        node._animation = node.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 160, fill: 'forwards' });
        node._animation.finished.catch(() => {}).then(finish);
      } else finish();
    }
    let cursor = this.list.firstElementChild;
    for (const row of rows) {
      const key = keyOf(row);
      let node = this.nodes.get(key);
      if (!node) { node = this.create(key); this.nodes.set(key, node); }
      if (node._exit) {
        node._exit = null; node._animation?.cancel(); node.style.cssText = '';
        node.inert = false;
        node.removeAttribute('aria-hidden'); node._elements.text.tabIndex = 0;
        if (node._grip) node._grip.disabled = false;
      }
      this.update(node, row, plan.schedule.generation, hasSource);
      while (cursor?._exit) cursor = cursor.nextElementSibling;
      if (cursor !== node) this.list.insertBefore(node, cursor);
      cursor = node.nextElementSibling;
    }
    if (!this.reducedMotion && !this.dragging) for (const row of rows) {
      const key = keyOf(row), node = this.nodes.get(key), previous = before.get(key);
      if (!previous || !node.animate) continue;
      const y = previous.top - node.getBoundingClientRect().top;
      if (Math.abs(y) > 0.5) { node._animation?.cancel(); node._animation = node.animate(
        [{ transform: `translateY(${y}px)` }, { transform: 'translateY(0)' }], { duration: 220 });
        node._animation.finished.catch(() => {});
      }
    }
    this.empty.hidden = rows.length > 0;
    this.repeatNote.hidden = !(plan.context.order.repeat === 'one' && plan.schedule.pending.length);
    const current = rows.find((row) => row.playing);
    const key = current ? keyOf(current) : null;
    if (!this.root.hidden && key && key !== this.currentKey) this.nodes.get(key)?.scrollIntoView?.({ block: 'nearest' });
    this.currentKey = key;
  }

  revealCurrent() {
    const current = this.nodes.get(this.currentKey);
    if (current) this.list.scrollTop = current.offsetTop;
  }
}
