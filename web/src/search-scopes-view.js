// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { setUiText, setUiAttribute } from './ui-text.js';
import { PLATFORMS } from './random-scope.js';
import { message as uiMessageText, t } from './i18n.js';

const setText = (node, text) => { setUiText(node, text); };

export function searchScopeLabel(scope, sourceName, sourceIds = null) {
  const join = (names) => `${names.slice(0, 2).join(', ')}${names.length > 2 ? ` +${names.length - 2}` : ''}`;
  switch (scope.kind) {
    case 'everywhere': return t('Everywhere');
    case 'playlists': return t('Saved playlists');
    case 'online': {
      const names = (sourceIds ?? scope.ids).filter((id) => scope.ids.includes(id)).map(sourceName);
      return names.length ? t('Online: {names}', { names: join(names) }) : t('Online');
    }
    case 'platform': {
      const names = PLATFORMS.filter((platform) => scope.ids.includes(platform.id)).map((platform) => platform.name);
      return names.length ? join(names) : t('All platforms');
    }
    default: throw new TypeError(`Unknown search scope: ${scope.kind}`);
  }
}

/** Controls retain their nodes so keyboard focus and chip scrolling survive data/query redraws. */
export class SearchScopesView {
  constructor(root, { icon, sourceName, sourceIds, onScope, onToggle, onRetry }) {
    this.root = root; this.sourceName = sourceName;
    const document = root.ownerDocument;
    const group = (className) => { const node = document.createElement('div'); node.className = className;
      node.setAttribute('role', 'group'); return node; };
    this.tiles = group('search-scope-tiles'); this.catalogues = group('search-scope-chips'); this.platforms = group('search-scope-chips');
    this.buttons = new Map();
    const make = (parent, key, label, picture, run) => {
      const button = document.createElement('button'); button.type = 'button'; button.className = 'chip';
      button.dataset.searchScope = key; button.innerHTML = picture;
      const text = document.createElement('span'); button.append(text); button.onclick = run; parent.append(button);
      const entry = { button, text, label }; this.buttons.set(key, entry); return entry;
    };
    for (const [kind, label, picture] of [['playlists', 'Saved playlists', 'playlist'], ['online', 'Online catalogues', 'cloud'], ['platform', 'By platform', 'joystick']]) {
      make(this.tiles, kind, label, icon(picture), () => onScope(kind));
    }
    const checkbox = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="4" fill="none" stroke="currentColor" stroke-width="2"/><path class="scope-check" d="m6 12 4 4 8-8" fill="none" stroke="currentColor" stroke-width="2"/></svg>';
    for (const id of sourceIds) make(this.catalogues, `catalogue:${id}`, sourceName(id), checkbox, () => onToggle(id));
    for (const platform of PLATFORMS) make(this.platforms, `platform:${platform.id}`, platform.name, checkbox, () => onToggle(platform.id));
    this.status = document.createElement('p'); this.status.id = 'search-scope-status'; this.status.className = 'note';
    this.status.setAttribute('role', 'status');
    this.retry = make(root, 'retry', 'Retry', icon('refresh'), onRetry);
    root.replaceChildren(this.tiles, this.catalogues, this.platforms, this.status, this.retry.button);
  }
  render(scope, { value, loading, failed }) {
    setUiAttribute(this.tiles, 'aria-label', uiMessageText('Search scope'));
    setUiAttribute(this.catalogues, 'aria-label', uiMessageText('Online catalogues'));
    setUiAttribute(this.platforms, 'aria-label', uiMessageText('By platform'));
    this.catalogues.hidden = scope.kind !== 'online'; this.platforms.hidden = scope.kind !== 'platform';
    const relevant = scope.kind === 'online' || scope.kind === 'platform';
    this.status.hidden = !relevant;
    setText(this.status, uiMessageText(failed ? 'Could not check search scopes. Try again.' : loading || !value
      ? 'Checking search sources…' : scope.kind === 'online' && !value.catalogues.length
        ? 'No catalogue index is held. Download one in Online catalogues.'
        : scope.kind === 'platform' ? 'Platforms with no indexed, playable music are unavailable.'
          : 'No catalogue selected means all indexed catalogues.'));
    this.retry.button.hidden = !relevant || !failed;
    for (const [key, { button, text, label }] of this.buttons) {
      setText(text, ['playlists', 'online', 'platform', 'retry'].includes(key) ? uiMessageText(label) : label);
      const parts = key.split(':');
      const selected = parts.length === 1 ? key === scope.kind : scope.ids.includes(parts[1]);
      button.classList.toggle('on', selected); button.setAttribute('aria-pressed', String(selected));
      if (key === 'retry') button.removeAttribute('aria-pressed');
      const tick = button.querySelector('.scope-check'); if (tick) tick.style.visibility = selected ? 'visible' : 'hidden';
      if (parts[0] === 'catalogue') { button.hidden = !!value && !value.catalogues.includes(parts[1]); button.disabled = !value; }
      if (parts.length > 1 && parts[0] === 'platform') button.disabled = !(value?.platformCounts[parts[1]] > 0);
      if (button.disabled) button.setAttribute('aria-describedby', 'search-scope-status'); else button.removeAttribute('aria-describedby');
    }
  }
}
