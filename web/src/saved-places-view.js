// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { joinUiText } from './ui-message.js';
import { setUiText, setUiAttribute } from './ui-text.js';
import { message as uiMessageText, t } from './i18n.js';
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
    this.toggle = this.action(uiMessageText('Bookmark'), 'bookmark', () => {});
    this.retry = this.action(uiMessageText('Refresh'), 'refresh', onRetry);
    this.header.append(this.headerText, this.toggle, this.retry);
    this.refresh = this.action(uiMessageText('Refresh'), 'refresh', onRetry); this.tools.append(this.refresh);
  }
  action(label, glyph, run) {
    const button = document.createElement('button'); button.type = 'button'; button.className = 'saved-action';
    button.innerHTML = this.icon(glyph); const text = document.createElement('span'); setUiText(text, label); button.append(text);
    button.onclick = (event) => { event.stopPropagation(); run(); }; return button;
  }
  bookmark(button, saved) {
    button.innerHTML = this.icon(saved ? 'bookmarkFilled' : 'bookmark');
    const label = document.createElement('span'); setUiText(label, uiMessageText('Bookmark')); button.append(label);
    button.setAttribute('aria-pressed', String(saved));
    setUiAttribute(button, 'aria-label', joinUiText(uiMessageText('Bookmark'), ": ", saved ? uiMessageText('Bookmarked') : uiMessageText('Not bookmarked')));
  }
  render({ state, key, inList }) {
    this.header.hidden = !key;
    this.tools.hidden = !inList;
    if (key) {
      const saved = state.rows.some((row) => savedPlaceId(row.place.key) === savedPlaceId(key));
      setUiText(this.author, key.author || uiMessageText('(no author)')); this.format.textContent = key.format;
      this.bookmark(this.toggle, saved); this.toggle.disabled = state.loading || state.writing;
      this.toggle.hidden = state.failed; this.retry.hidden = !state.failed;
      this.toggle.onclick = () => this.onToggle(key, saved);
      this.header.dataset.busy = String(state.loading || state.writing);
      setUiText(this.feedback, state.failed ? uiMessageText('Could not update saved places. Please try again.') : state.loading || state.writing ? uiMessageText('Updating saved places…') : '');
      this.feedback.hidden = !this.feedback.textContent;
    }
    if (!inList) return;
    this.refresh.disabled = state.loading || state.writing;
    setUiText(this.note, state.failed ? uiMessageText('Could not update saved places. Please try again.')
      : state.loading || state.writing || state.opening ? uiMessageText('Updating saved places…')
      : !state.rows.length ? uiMessageText('Nothing saved yet. Open an author or game folder in an online catalogue and choose Bookmark.') : '');
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
        node.open = this.action(uiMessageText('Open'), 'folder', () => this.onOpen(row.place.key));
        node.removeAction = this.action(uiMessageText('Bookmark'), 'bookmarkFilled', () => this.onRemove(row.place.key));
        actions.append(node.open, node.removeAction); line.append(text, actions);
        node.reason = document.createElement('p'); node.reason.className = 'saved-reason';
        node.prepare = this.action(uiMessageText('Catalogues'), 'cloud', this.onPrepare);
        node.append(line, node.reason, node.prepare); this.#rows.set(id, node);
      }
      setUiText(node.name, row.place.key.author || uiMessageText('(no author)'));
      node.path.textContent = `${row.place.catalogueName} › ${row.place.key.format}`;
      const reason = row.availability === 'available' ? null : reasons[row.availability]?.();
      if (row.availability !== 'available' && !reason) throw new TypeError(`SavedPlaceAvailability(${row.availability})`);
      node.open.disabled = !!reason || state.writing || !!state.opening;
      this.bookmark(node.removeAction, true); node.removeAction.disabled = state.writing || !!state.opening;
      const availability = row.availability;
      node.reason.hidden = !reason;
      setUiText(node.reason, () => availability === 'available' ? '' : reasons[availability]?.() ?? '');
      node.prepare.hidden = !['index-missing', 'group-missing'].includes(row.availability);
      if (this.list.children[i] !== node) this.list.insertBefore(node, this.list.children[i] ?? null);
    }
    for (const [id, node] of this.#rows) if (!wanted.has(id)) { node.remove(); this.#rows.delete(id); }
  }
}
