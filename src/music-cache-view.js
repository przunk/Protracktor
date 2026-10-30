// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { MUSIC_CACHE_CEILING } from './cache-budget.js';
import { message as uiMessageText } from './i18n.js';
import { makeUiText, setUiText } from './ui-text.js';

/** Storage-only presentation: refreshing a copy must not rebuild playback or focused actions. */
export class MusicCacheView {
  constructor({ root, icon, onClear, onRetry, notice, state }) {
    Object.assign(this, { root, onClear, onRetry, notice });
    this.busy = null;
    this.title = root.ownerDocument.createElement('dt'); setUiText(this.title, uiMessageText('Downloaded music'));
    this.body = root.ownerDocument.createElement('dd'); this.body.id = 'music-cache-storage';
    this.detail = root.ownerDocument.createElement('span');
    this.problem = root.ownerDocument.createElement('p'); this.problem.className = 'note'; this.problem.setAttribute('role', 'status');
    const action = (label, glyph, run) => {
      const button = root.ownerDocument.createElement('button'); button.className = 'plain'; button.type = 'button';
      button.innerHTML = icon(glyph); button.append(makeUiText(root.ownerDocument, uiMessageText(label))); button.onclick = run;
      return button;
    };
    this.clear = action('Clear', 'remove', () => this.act('clear', onClear));
    this.retry = action('Retry', 'refresh', () => this.act('retry', onRetry));
    this.body.append(this.detail, ' ', this.clear, this.retry, this.problem);
    root.append(this.title, this.body);
    this.render(state);
  }
  async act(kind, run) {
    if (this.busy || !this.body.isConnected || this.body.closest('[hidden]')) return;
    this.busy = kind; this.render(this.state);
    try { const result = await run(); if (kind === 'clear') this.notice({ kind: 'freed', bytes: result }); }
    catch (error) { this.notice({ kind: 'failed', error }); }
    finally { this.busy = null; if (this.body.isConnected) this.render(this.state); }
  }
  render(state) {
    this.state = state;
    this.title.hidden = this.body.hidden = !state.loading && !state.count && !state.failed && !this.busy;
    const limit = MUSIC_CACHE_CEILING / (1024 * 1024);
    setUiText(this.detail, this.busy === 'clear' ? uiMessageText('Clearing downloaded music…')
      : state.loading ? uiMessageText('Reading downloaded music…')
      : state.bytes ? state.bytes < 1024 * 1024 ? uiMessageText('Less than 1 MB of {limit} MB', { limit })
        : uiMessageText('{used} MB of {limit} MB', { used: Math.floor(state.bytes / (1024 * 1024)), limit })
      : uiMessageText('Nothing downloaded yet.'));
    this.clear.hidden = !state.count && this.busy !== 'clear';
    this.clear.disabled = !!this.busy || state.loading;
    this.retry.hidden = !state.failed;
    this.retry.disabled = !!this.busy || state.loading;
    this.problem.hidden = !state.failed;
    setUiText(this.problem, state.failed ? uiMessageText('This browser could not update downloaded music: {reason}', { reason: state.failed.message }) : '');
  }
}
