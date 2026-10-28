// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Actual field, pager, source-return and Clear events against production IndexedDB. */
export default async function checkHistorySearch({ window, check }) {
  const $ = (id) => window.document.getElementById(id), api = window.__api, played = window.__played;
  const settle = (ms = 20) => new Promise((resolve) => setTimeout(resolve, ms));
  const until = async (condition) => {
    for (let i = 0; i < 250; i++) { if (condition()) return true; await settle(); }
    return false;
  };
  const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };
  const tracks = () => [...$('browselist').querySelectorAll('[data-url]')];
  const input = $('historysearch');
  const type = (text) => { input.value = text; input.dispatchEvent(new window.Event('input', { bubbles: true })); };
  const currentRead = played.read;
  const enter = async () => {
    api.showPanel('browse'); await api.browseTo([]);
    [...$('browselist').children].find((row) => row.querySelector('.bname')?.textContent === window.__i18n.t('History')).click();
    return until(() => !api.historyNow().loading && api.historyNow().total === 200);
  };
  const action = (label) => [...$('browselist').children].find((row) => row.textContent === window.__i18n.t(label));
  await played.clear();
  // Match the native UI fixture's newest-first 0..199 ordering.
  for (let i = 199; i >= 0; i--) await played.record({ url: `https://example.test/history-search/${i}.mod`, name: `History track ${i}`,
    author: i === 150 ? 'Hülsbeck' : '', file: i === 199 ? 'special_%!.mod' : `${i}.mod`, replayable: true, meta: 'History fixture' });
  const beforeRows = JSON.stringify(await played.recent()), recents = JSON.stringify(api.recentQueriesNow());
  check(await enter(), 'the real History domain entry reads the newest hundred and matching total');
  check(!input.hidden && !$('historyquery').hidden && $('browsequery').hidden && input.value === '',
    'History has its own live field, separate from recent general searches');
  input.focus();
  const beforeCommands = window.__toWorklet.length, beforePlan = api.planNow();
  type('TRACK 150');
  check(api.historyNow().loading && tracks().length === 0, 'editing the actual History field clears old results during debounce');
  if (!api.historyNow().loading) return;
  check(await until(() => !api.historyNow().loading && tracks()[0]?.textContent.includes('History track 150')),
    'a live title phrase finds a track beyond page zero');
  check(window.document.activeElement === input && input.value === 'TRACK 150', 'result publication retains the same focused field and raw phrase');
  type('HÜLSBECK');
  check(await until(() => !api.historyNow().loading && tracks()[0]?.dataset.url.endsWith('/150.mod')),
    'live author matching uses the stored author field with Unicode case folding');
  type('SPECIAL_%!');
  check(await until(() => !api.historyNow().loading && tracks()[0]?.dataset.url.endsWith('/199.mod')),
    'filename wildcard characters are literal in the actual input path');
  type('track 1');
  check(await until(() => !api.historyNow().loading && api.historyNow().total === 111), 'matching count covers all History, not the displayed page');
  $('history-older').click();
  check(await until(() => !api.historyNow().loading && api.historyNow().page === 1 && tracks()[0]?.textContent.includes('History track 189')),
    'the real Older action reaches the filtered second page');
  type('');
  check(await until(() => !api.historyNow().loading && api.historyNow().total === 200 && tracks()[0]?.textContent.includes('History track 0')),
    'clearing the phrase restores full History at page zero');
  check(api.planNow().context === beforePlan.context && api.planNow().schedule === beforePlan.schedule
    && window.__toWorklet.length === beforeCommands, 'filtering and paging never replace the captured playback plan');
  check(JSON.stringify(await played.recent()) === beforeRows && JSON.stringify(api.recentQueriesNow()) === recents,
    'live filtering changes neither stored History nor recent submitted searches');

  type('track 1'); await until(() => !api.historyNow().loading && api.historyNow().total === 111);
  $('history-older').click(); await until(() => !api.historyNow().loading && api.historyNow().page === 1);
  const sourceRows = tracks().map((row) => row.dataset.url); tracks()[0].click();
  await until(() => api.sourceNow().origin === 'history');
  const context = api.planNow().context;
  check(context.route.historyQuery === 'track 1' && context.route.historyPage === 1,
    'playing a filtered match captures the producing History phrase and page');
  type('track 150'); await until(() => !api.historyNow().loading && api.historyNow().total === 1);
  check(api.planNow().context === context, 'a later filter does not mutate the playing History source');
  api.showPanel(null); const sourceCommands = window.__toWorklet.length;
  $('showqueue').click(); $('queue-source').click();
  check(await until(() => !$('browse').hidden && input.value === 'track 1' && api.historyNow().page === 1),
    'Queue Go to source restores the producing History query and page');
  check(tracks().map((row) => row.dataset.url).join() === sourceRows.join() && api.planNow().context === context
    && window.__toWorklet.length === sourceCommands, 'source return preserves result membership and playback without new commands');
  $('showqueue').click(); $('queue-close').click(); input.focus(); type('track 150');
  check(await until(() => !api.historyNow().loading && api.historyNow().total === 1),
    'the History field still filters after opening and closing Queue above it');
  $('browselist').scrollTop = 400;
  check(await enter(), 'a fresh History entry resets a previous filter');
  check(input.value === '' && api.historyNow().page === 0 && $('browselist').scrollTop === 0,
    'a fresh entry resets phrase, page and scroll to the newest row');
  $('browsesearch').value = 'unrelated retained query';
  $('browseback').click();
  check(await until(() => $('browsetitle').textContent === window.__i18n.t('Browse'))
    && $('browsesearch').value === 'unrelated retained query',
    'History Back returns to Browse in one action without consuming retained general-search text');
  check(await enter(), 'History reopens normally after the actual Back action');

  try {
    const entered = deferred(), gate = deferred();
    played.read = async (query, page, signal) => {
      if (query === 'old') { entered.resolve(); return gate.promise; }
      return currentRead(query, page, signal);
    };
    input.focus(); type('old');
    await Promise.race([entered.promise, settle(3000).then(() => { throw new Error('The History input did not start the controlled read'); })]);
    type('track 150'); await until(() => !api.historyNow().loading && api.historyNow().total === 1);
    gate.resolve({ total: 1, page: 0, rows: [{ url: 'obsolete', name: 'Obsolete', replayable: true }] }); await settle();
    check(tracks()[0]?.dataset.url.endsWith('/150.mod') && !tracks().some((row) => row.dataset.url === 'obsolete'),
      'an uncooperative obsolete read cannot overwrite newer rows through the live field');
    played.read = async () => { throw new Error('controlled History refusal'); };
    type('retry'); await until(() => api.historyNow().failed);
    check($('browsenote').textContent === window.__i18n.t('Could not load History. Please try again.')
      && !!action('Retry')?.querySelector('svg'), 'a real read failure stops loading and offers labelled Retry, not no matches');
    played.read = currentRead;
    action('Retry').dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    check(await until(() => !api.historyNow().loading && !api.historyNow().failed), 'the actual Retry keyboard action repeats the same phrase successfully');
    type('no such title'); await until(() => !api.historyNow().loading);
    check($('browsenote').textContent === window.__i18n.t('No History entries match this phrase.') && !!action('Clear the history'),
      'no matches differ from empty History and still offer Clear');
    action('Clear the history').click();
    check(await until(() => !api.historyNow().loading && api.historyNow().total === 0)
      && await played.count() === 0, 'Clear from a no-match view erases all stored History');
    type(''); await until(() => !api.historyNow().loading);
    check($('browsenote').textContent === window.__i18n.t('Nothing played yet. What the page plays is kept here, one row a tune.'),
      'clearing the phrase after erase explains the empty History');
    window.__i18n.useLanguage('pl'); await api.renderBrowse();
    check(input.getAttribute('aria-label') === 'Szukaj w historii', 'the History field has a Polish accessible label');
    input.focus(); input.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    check(window.document.activeElement !== input, 'Enter dismisses History focus without submitting a general search');
  } finally { played.read = currentRead; window.__i18n.useLanguage('en'); api.showPanel(null); }
}
