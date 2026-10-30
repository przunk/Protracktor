// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { MusicCacheView } from '../web/src/music-cache-view.js';
import { useLanguage, translateStatic } from '../web/src/i18n.js';
const dom = new JSDOM('<section><dl></dl></section>', { pretendToBeVisual: true });
globalThis.document = dom.window.document;
const { document } = dom.window, root = document.querySelector('dl'), section = document.querySelector('section');
const empty = { bytes: 0, count: 0, loading: false, failed: null };
const one = { ...empty, bytes: 64, count: 1 };
const notices = []; let resolve, reject, calls = 0;
const view = new MusicCacheView({ root, state: empty, icon: () => '<svg aria-hidden="true"></svg>',
  onClear: () => { calls++; return new Promise((done, fail) => { resolve = done; reject = fail; }); },
  onRetry: async () => {}, notice: (event) => notices.push(event) });
const check = (value, label) => { assert(value, label); console.log(`✓ ${label}`); };
const settle = () => new Promise((done) => setTimeout(done, 0));
try {
  translateStatic(document.body);
  check(view.body.hidden && view.title.hidden, 'known empty cache has no dead Clear action');
  view.render({ ...empty, loading: true });
  check(!view.body.hidden && view.clear.disabled && view.detail.textContent.includes('Reading'), 'unknown storage shows its read state rather than a false zero count');
  view.render(one); const clear = view.clear, icon = clear.querySelector('svg'); clear.focus();
  view.render({ ...one, bytes: 2048, count: 2 });
  check(view.clear === clear && icon.isConnected && document.activeElement === clear && clear.textContent === 'Clear', 'cache refresh retains the labelled Clear node icon and focus');
  check(view.detail.textContent === 'Less than 1 MB of 512 MB', 'small stored payloads have an honest size and fixed native budget');
  clear.click(); clear.click();
  check(calls === 1 && clear.disabled && view.detail.textContent.includes('Clearing'), 'actual Clear accepts one operation and remains explicitly busy');
  resolve(2048); await settle();
  check(notices.at(-1)?.kind === 'freed' && notices.at(-1).bytes === 2048 && !clear.disabled, 'Clear reports its actual freed bytes and releases its busy state');
  clear.click(); reject(new Error('clear refused')); await settle();
  check(notices.at(-1)?.kind === 'failed' && notices.at(-1).error.message === 'clear refused' && !clear.disabled, 'refused Clear reports failure and leaves retry possible');
  view.render({ ...one, failed: new Error('Raw Random') }); view.retry.focus();
  useLanguage('pl'); translateStatic(document.body);
  check(view.title.textContent === 'Pobrane utwory' && view.clear.textContent === 'Wyczyść' && view.problem.textContent.includes('Raw Random') && document.activeElement === view.retry, 'live language refresh translates controls without translating raw errors or replacing focus');
  const before = calls; section.hidden = true; clear.click(); section.hidden = false; root.replaceChildren(); clear.click();
  check(calls === before, 'hidden and retired Clear nodes cannot act on newer storage');
  console.log('9 music-cache presentation cases passed (host DOM only)');
} finally { dom.window.close(); delete globalThis.document; }
