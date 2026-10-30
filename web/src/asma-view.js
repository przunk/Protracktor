// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { message as uiMessageText, pluralMessage as uiPluralText } from './i18n.js';
import { joinUiText } from './ui-message.js';
import { setUiText, setUiAttribute } from './ui-text.js';

/** Live archive facts update existing controls, independently from playback or view navigation. */
export class AsmaView {
  #read = 0;
  constructor({ document, snapshot, readIndex, icon, open }) { Object.assign(this, { document, snapshot, readIndex, icon, open }); }
  detail(index, state = this.snapshot()) {
    const count = index?.tracks ?? 0;
    return joinUiText(count ? uiPluralText(count, '{count} tune', '{count} tunes', { count: count.toLocaleString() }) : uiMessageText('Not indexed yet'), ' · ',
      state.bytes > 0 ? uiMessageText('Archive: {size} MB — available offline', { size: (state.bytes / (1024 * 1024)).toFixed(1) })
        : uiMessageText('No offline archive — download about 20 MB'));
  }
  label(state = this.snapshot()) {
    return state.bytes > 0 ? uiMessageText('Update the ASMA archive') : uiMessageText('Download the ASMA archive (about 20 MB)');
  }
  #problem(parent, failed, className) {
    let problem = parent.querySelector('[data-asma-problem]');
    if (!failed) { problem?.remove(); return; }
    if (!problem) {
      problem = this.document.createElement('p'); problem.dataset.asmaProblem = ''; problem.className = className;
      problem.setAttribute('role', 'status'); parent.append(problem);
    }
    setUiText(problem, uiMessageText('ASMA could not be read: {reason}', { reason: failed.message }));
  }
  catalogue(row, index, state = this.snapshot()) {
    row.dataset.asmaCatalogue = '';
    const held = (index?.tracks ?? 0) > 0, mark = row.querySelector('.bheld'), text = row.querySelector('.btext');
    setUiText(text.querySelector('.bmeta:not(.bwarn)'), this.detail(index, state));
    if (mark.classList.contains('yes') !== held) {
      mark.classList.toggle('yes', held); mark.innerHTML = this.icon(held ? 'downloaded' : 'cloud');
    }
    this.#problem(text, state.failed, 'bmeta bwarn');
    row.classList.toggle('closed', !held);
    row.onclick = held ? () => this.open(row) : null;
    text.tabIndex = held ? 0 : -1;
    if (held) text.setAttribute('role', 'button'); else text.removeAttribute('role');
    text.onkeydown = held ? (event) => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); event.stopPropagation(); row.click(); }
    } : null;
    const button = row.querySelector('button.bdl');
    if (button) {
      const downloaded = state.bytes > 0;
      if (button.dataset.asmaHeld !== String(downloaded)) {
        button.dataset.asmaHeld = String(downloaded); button.innerHTML = this.icon(downloaded ? 'refresh' : 'download');
      }
      setUiAttribute(button, 'aria-label', this.label(state)); setUiAttribute(button, 'title', this.label(state));
    }
  }
  storage(body, index, state = this.snapshot()) {
    body.dataset.asmaStorage = '';
    const detail = body.querySelector('[data-asma-storage-detail]'), button = body.querySelector('button');
    if (!detail || !button) return; // A confirmed deletion owns its temporary status until it completes.
    setUiText(detail, this.detail(index, state));
    button.disabled = !(index?.tracks || state.bytes || state.failed);
    this.#problem(body, state.failed, 'note');
  }
  async refresh() {
    const nodes = [...this.document.querySelectorAll('[data-asma-catalogue], [data-asma-storage]')];
    if (!nodes.length) return;
    const asked = ++this.#read, state = this.snapshot(); let index;
    try { index = await this.readIndex(); } catch { index = null; }
    if (asked !== this.#read || state !== this.snapshot()) return;
    for (const node of nodes) if (node.isConnected) {
      if ('asmaCatalogue' in node.dataset) this.catalogue(node, index, state); else this.storage(node, index, state);
    }
  }
}
