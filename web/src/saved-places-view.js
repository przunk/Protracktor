// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { t } from './i18n.js';
import { savedPlaceId } from './saved-places.js';
const reasons = {
  'index-missing': () => t('Download this catalogue’s index in Online catalogues to open this place. The bookmark is safe.'),
  'group-missing': () => t('This exact folder has no playable tracks in the current index. You can refresh the index in Online catalogues; the bookmark is kept.'),
  'catalogue-unavailable': () => t('This catalogue is not available in this version of the app. The bookmark is kept.'),
};
/** Presentation only: bookmark state has no transport/download capability. */
export class SavedPlacesView {
  #rows = new Map();
  constructor({ header, tools, list, note, icon, onToggle, onOpen, onRemove, onPrepare, onRetry }) {
    Object.assign(this, { header, tools, list, note, icon, onToggle, onOpen, onRemove, onPrepare, onRetry });
    this.headerText = document.createElement('div'); this.headerText.className = 'saved-heading';
    this.author = document.createElement('div'); this.format = document.createElement('div'); this.format.className = 'bmeta';
    this.feedback = document.createElement('small'); this.feedback.setAttribute('role', 'status');
    this.headerText.append(this.author, this.format, this.feedback);
    this.toggle = this.action(t('Bookmark'), 'bookmark', () => {});
    this.retry = this.action(t('Refresh'), 'refresh', onRetry);
    this.header.append(this.headerText, this.toggle, this.retry);
    this.refresh = this.action(t('Refresh'), 'refresh', onRetry); this.tools.append(this.refresh);
  }
  action(label, glyph, run) {
    const button = document.createElement('button'); button.type = 'button'; button.className = 'saved-action';
    button.innerHTML = this.icon(glyph); const text = document.createElement('span'); text.textContent = label; button.append(text);
    button.onclick = (event) => { event.stopPropagation(); run(); }; return button;
  }
  bookmark(button, saved) {
    button.innerHTML = this.icon(saved ? 'bookmarkFilled' : 'bookmark');
    const label = document.createElement('span'); label.textContent = t('Bookmark'); button.append(label);
    button.setAttribute('aria-pressed', String(saved));
    button.setAttribute('aria-label', `${t('Bookmark')}: ${saved ? t('Bookmarked') : t('Not bookmarked')}`);
  }
  render({ state, key, inList }) {
    this.header.hidden = !key;
    this.tools.hidden = !inList;
    if (key) {
      const saved = state.rows.some((row) => savedPlaceId(row.place.key) === savedPlaceId(key));
      this.author.textContent = key.author || t('(no author)'); this.format.textContent = key.format;
      this.bookmark(this.toggle, saved); this.toggle.disabled = state.loading || state.writing;
      this.toggle.hidden = state.failed; this.retry.hidden = !state.failed;
      this.toggle.onclick = () => this.onToggle(key, saved);
      this.header.dataset.busy = String(state.loading || state.writing);
      this.feedback.textContent = state.failed ? t('Could not update saved places. Please try again.') : state.loading || state.writing ? t('Updating saved places…') : '';
      this.feedback.hidden = !this.feedback.textContent;
    }
    if (!inList) return;
    this.refresh.disabled = state.loading || state.writing;
    this.note.textContent = state.failed ? t('Could not update saved places. Please try again.')
      : state.loading || state.writing || state.opening ? t('Updating saved places…')
      : !state.rows.length ? t('Nothing saved yet. Open an author or game folder in an online catalogue and choose Bookmark.') : '';
    const wanted = new Set();
    for (let i = 0; i < state.rows.length; i++) {
      const row = state.rows[i], id = savedPlaceId(row.place.key); wanted.add(id);
      let node = this.#rows.get(id);
      if (!node) {
        node = document.createElement('li'); node.className = 'saved-place'; node.dataset.place = id;
        const line = document.createElement('div'); line.className = 'saved-place-line';
        const text = document.createElement('div'); text.className = 'btext';
        node.name = document.createElement('div'); node.name.className = 'bname';
        node.path = document.createElement('div'); node.path.className = 'bmeta'; text.append(node.name, node.path);
        const actions = document.createElement('div'); actions.className = 'saved-actions';
        node.open = this.action(t('Open'), 'folder', () => this.onOpen(row.place.key));
        node.removeAction = this.action(t('Bookmark'), 'bookmarkFilled', () => this.onRemove(row.place.key));
        actions.append(node.open, node.removeAction); line.append(text, actions);
        node.reason = document.createElement('p'); node.reason.className = 'saved-reason';
        node.prepare = this.action(t('Catalogues'), 'cloud', this.onPrepare);
        node.append(line, node.reason, node.prepare); this.#rows.set(id, node);
      }
      node.name.textContent = row.place.key.author || t('(no author)');
      node.path.textContent = `${row.place.catalogueName} › ${row.place.key.format}`;
      const reason = row.availability === 'available' ? null : reasons[row.availability]?.();
      if (row.availability !== 'available' && !reason) throw new TypeError(`SavedPlaceAvailability(${row.availability})`);
      node.open.disabled = !!reason || state.writing || !!state.opening;
      this.bookmark(node.removeAction, true); node.removeAction.disabled = state.writing || !!state.opening;
      node.reason.hidden = !reason; node.reason.textContent = reason ?? '';
      node.prepare.hidden = !['index-missing', 'group-missing'].includes(row.availability);
      if (this.list.children[i] !== node) this.list.insertBefore(node, this.list.children[i] ?? null);
    }
    for (const [id, node] of this.#rows) if (!wanted.has(id)) { node.remove(); this.#rows.delete(id); }
  }
}
