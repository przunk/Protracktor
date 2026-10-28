// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Drive the real page controls rather than calling its query-history methods. */
export default async function checkRecentSearches({ window, check }) {
  const { document } = window;
  const $ = (id) => document.getElementById(id);
  const api = window.__api;
  const until = async (condition) => {
    for (let tries = 0; tries < 200; tries++) { if (condition()) return true; await new Promise((r) => setTimeout(r, 10)); }
    return false;
  };
  await api.recentReady;
  const input = $('browsesearch');
  api.showPanel('browse');
  await api.browseTo([]);
  const entry = [...$('browselist').children].find((row) => row.querySelector('.bname')?.textContent === window.__i18n.t('Search'));
  entry.click();
  check(await until(() => !input.hidden && document.activeElement === input), 'actual Search entry focuses the query field once');
  const type = (query) => { input.value = query; input.dispatchEvent(new window.Event('input', { bubbles: true })); };
  const before = window.__toWorklet.length;
  for (const query of ['delta one', 'ALPHA', 'delta two', 'delta three', 'delta four']) {
    input.focus(); type(query); $('browsesubmit').click();
    check(await until(() => api.recentQueriesNow()[0] === query), 'actual integrated Search submits and retains ' + query);
  }
  input.focus(); type('  dElTa');
  const options = [...$('search-suggestions').children];
  check(options.length === 3 && options.map((row) => row.textContent).join('|') === 'delta four|delta three|delta two',
    'actual page filters recent prefixes before limiting to three');
  check(!options.some((row) => row.querySelector('svg,button')), 'actual suggestions are plain text without icon buttons');
  const retained = [...api.recentQueriesNow()];
  type('only typed'); await new Promise((r) => setTimeout(r, 300));
  check(JSON.stringify(api.recentQueriesNow()) === JSON.stringify(retained), 'typing alone never creates a recent submission');
  await api.renderBrowse();
  check(api.submittedSearchNow() === null && $('browsenote').textContent === window.__i18n.t('Press Search or Enter to search.'),
    'a redraw does not silently submit the newly typed query');
  type('delta'); const chosen = $('search-suggestions').children[2];
  check(!!chosen, 'typing a matching prefix restores the three actual options');
  chosen?.click();
  check(await until(() => api.recentQueriesNow()[0] === 'delta two'), 'actual suggestion click submits the selected query immediately');
  check(input.value === 'delta two' && document.activeElement !== input && $('search-suggestions').hidden,
    'selected suggestion clears focus and closes the popup');
  await api.renderBrowse();
  check(document.activeElement !== input && $('search-suggestions').hidden, 'async result redraw cannot reopen the keyboard or popup');
  check(window.__toWorklet.length === before, 'search suggestions never issue playback commands');
  const stored = await api.settingsNow.get('recentSearches');
  check(JSON.stringify(stored) === JSON.stringify(api.recentQueriesNow()), 'real query submissions persist through the production settings adapter');
  const originalSet = api.settingsNow.set;
  try {
    api.settingsNow.set = (key, value) => key === 'recentSearches'
      ? Promise.reject(new Error('recent quota refused')) : originalSet.call(api.settingsNow, key, value);
    input.focus(); type('will not persist'); input.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    check(await until(() => $('snacktext').textContent.includes('recent quota refused')), 'a refused recent-query write gives feedback from the actual submit path');
    check(api.recentQueriesNow()[0] === 'delta two', 'failed persistence does not publish an unsaved recent entry');
  } finally { api.settingsNow.set = originalSet; }
  api.showPanel(null);
}
