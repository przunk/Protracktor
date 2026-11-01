// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { searchMatches } from './rules.js';

/** Match the editor's existing metadata without fetching files or changing its playback order. */
export function playlistMatches(track, query) {
  let path = track.url ?? '';
  try { path = decodeURIComponent(path); } catch { /* A malformed escape still has searchable text. */ }
  return searchMatches(query, track.name, track.author, track.file, track.meta, path);
}

/** Owns presentation and focus only. Row actions continue addressing the unfiltered editor. */
export class PlaylistSearchView {
  constructor({ root, heading, input, opener, closer, clear, chooser, form, list, viewport, empty, changed, handlesBack = () => true }) {
    Object.assign(this, { root, heading, input, opener, chooser, form, list, viewport, empty, changed, handlesBack });
    this.opened = false;
    this.originalScroll = 0;
    opener.addEventListener('click', () => this.open());
    closer.addEventListener('click', () => this.close());
    clear.addEventListener('click', () => { input.value = ''; changed(); input.focus({ preventScroll: true }); });
    input.addEventListener('input', () => { viewport.scrollTop = 0; changed(); });
    input.addEventListener('focus', () => { if (this.opened) root.classList.add('playlist-search-editing'); });
    input.addEventListener('blur', () => root.classList.remove('playlist-search-editing'));
    form.addEventListener('submit', event => { event.preventDefault(); this.finishEditing(); });
    root.ownerDocument.addEventListener('keydown', event => {
      if (event.key !== 'Escape' || !this.opened || !this.handlesBack()) return;
      event.preventDefault(); event.stopImmediatePropagation();
      if (this.editing) this.finishEditing(); else this.close();
    }, true);
    // Prevent pointerdown's default blur so the following click can be consumed before it acts.
    const outside = target => this.editing && !form.contains(target) && !list.contains(target);
    root.addEventListener('pointerdown', event => {
      if (outside(event.target)) { event.preventDefault(); event.stopImmediatePropagation(); }
    }, true);
    root.addEventListener('click', event => {
      if (outside(event.target)) {
        event.preventDefault(); event.stopImmediatePropagation(); this.finishEditing();
      }
    }, true);
    this.draw();
  }

  get query() { return this.opened ? this.input.value : ''; }
  get editing() { return this.root.classList.contains('playlist-search-editing'); }
  get filtering() { return !!this.query.trim(); }

  draw() {
    this.heading.classList.toggle('search-open', this.opened);
    this.chooser.inert = this.opened;
    this.chooser.setAttribute('aria-hidden', String(this.opened));
    this.form.inert = !this.opened;
    this.form.setAttribute('aria-hidden', String(!this.opened));
    this.opener.setAttribute('aria-expanded', String(this.opened));
  }

  open() {
    if (this.opened) return;
    this.originalScroll = this.viewport.scrollTop;
    this.opened = true;
    this.draw();
    this.changed();
    this.input.focus({ preventScroll: true });
  }

  finishEditing() {
    this.input.blur();
    this.root.classList.remove('playlist-search-editing');
  }

  close({ restoreFocus = true } = {}) {
    if (!this.opened) return;
    this.finishEditing();
    this.opened = false;
    this.input.value = '';
    this.draw();
    this.changed();
    this.viewport.scrollTop = this.originalScroll;
    if (restoreFocus) this.opener.focus({ preventScroll: true });
  }

  apply(rows) {
    let matches = 0;
    for (const row of rows) {
      row.node.hidden = this.filtering && !playlistMatches(row.entry, this.query);
      if (!row.node.hidden) matches++;
    }
    this.empty.hidden = !this.filtering || rows.length === 0 || matches > 0;
  }
}
