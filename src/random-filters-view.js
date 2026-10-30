// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { setUiText, setUiAttribute } from './ui-text.js';
import { PLATFORMS } from './random-scope.js';
import { message as uiMessageText } from './i18n.js';

/** Presentation only; verified counts and selection outlive this panel. */
export class RandomFiltersView {
  constructor(root, { icon, onPick, onRetry, onDownload }) {
    const document = root.ownerDocument;
    this.chips = document.createElement('div');
    this.chips.className = 'randomchips';
    this.chips.setAttribute('role', 'group');
    this.buttons = new Map();
    const scopes = [['everything', null], ['favourites', null],
      ...PLATFORMS.map((platform) => [`platform:${platform.id}`, platform.name])];
    for (const [scope, name] of scopes) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'chip';
      button.dataset.scope = scope;
      button.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><circle class="scope-dot" cx="12" cy="12" r="5"/></svg>';
      const dot = button.querySelector('.scope-dot');
      dot.style.visibility = 'hidden';
      const label = document.createElement('span');
      button.append(label);
      button.onclick = () => { if (!button.disabled) onPick(scope); };
      this.buttons.set(scope, { button, label, dot, name });
      this.chips.append(button);
    }
    const action = (kind, run, id) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'action';
      button.id = id;
      button.innerHTML = icon(kind);
      const label = document.createElement('span');
      button.append(label);
      button.onclick = run;
      return { button, label };
    };
    this.checking = document.createElement('div');
    this.checking.className = 'random-filter-explanation';
    this.status = document.createElement('p');
    this.status.id = 'random-filter-status';
    this.status.setAttribute('role', 'status');
    this.status.setAttribute('aria-live', 'polite');
    this.retry = action('refresh', onRetry, 'random-filters-retry');
    this.checking.append(this.status, this.retry.button);
    this.favourites = document.createElement('div');
    this.favourites.className = 'random-filter-explanation';
    this.reason = document.createElement('p');
    this.reason.id = 'random-favourites-note';
    this.download = action('download', onDownload, 'random-favourites-download');
    this.favourites.append(this.reason, this.download.button);
    this.unavailable = document.createElement('p');
    this.unavailable.id = 'random-platforms-note';
    root.replaceChildren(this.chips, this.checking, this.favourites, this.unavailable);
  }

  render(scope, { availability, loading, failed }, downloading) {
    setUiAttribute(this.chips, 'aria-label', uiMessageText('Choose what Random picks from'));
    let unavailablePlatform = false;
    for (const [key, { button, label, dot, name }] of this.buttons) {
      setUiText(label, name ?? uiMessageText(key === 'everything' ? 'Everything' : 'Favourites'));
      const count = key === 'favourites' ? availability?.favouriteCount : availability?.platformCounts[key.slice(9)];
      button.disabled = key !== 'everything' && !(count > 0);
      button.classList.toggle('on', key === scope);
      button.setAttribute('aria-pressed', String(key === scope));
      dot.style.visibility = key === scope ? 'visible' : 'hidden';
      if (button.disabled) {
        button.setAttribute('aria-describedby', !availability ? 'random-filter-status'
          : key === 'favourites' ? 'random-favourites-note' : 'random-platforms-note');
      } else button.removeAttribute('aria-describedby');
      if (key.startsWith('platform:') && button.disabled) unavailablePlatform = true;
    }
    this.checking.hidden = !!availability && !loading && !failed;
    setUiText(this.status, uiMessageText(failed ? 'Could not check available filters. Try again.' : 'Checking available filters…'));
    this.retry.button.hidden = !failed;
    setUiText(this.retry.label, uiMessageText('Retry'));
    this.favourites.hidden = !availability || availability.favouriteCount > 0;
    setUiText(this.reason, availability?.favouritesListed > 0
      ? uiMessageText('None of the favourites are in your Modland index. Index Modland first.')
      : uiMessageText("Favourites needs Modland's own list — 142 KB, downloaded once."));
    this.download.button.hidden = !availability || availability.favouritesListed > 0;
    this.download.button.disabled = downloading;
    setUiText(this.download.label, uiMessageText(downloading ? 'Downloading…' : 'Download'));
    this.unavailable.hidden = !availability || !unavailablePlatform;
    setUiText(this.unavailable, uiMessageText('Platforms with no indexed, playable music are unavailable.'));
  }
}
