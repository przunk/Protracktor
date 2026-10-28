// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import * as archive from '../web/src/catalogue.js';

export default async function checkSavedPlaces({ window, check }) {
  console.log('\nSaved places through actual page controls:');
  const api = window.__api, saved = window.__savedPlaces, store = window.__bookmarks;
  const $ = (id) => window.document.getElementById(id), settle = (ms = 100) => new Promise((resolve) => setTimeout(resolve, ms));
  const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; };
  const bounded = (promise) => Promise.race([promise, new Promise((_, reject) => window.setTimeout(() => reject(Error('Bookmark boundary not reached')), 5000))]);
  const listRows = () => [...$('browselist').querySelectorAll('li.saved-place')];
  const named = (author) => listRows().find((node) => JSON.parse(node.dataset.place)[2] === author);
  const openButton = (node) => node.querySelector('.saved-actions button:first-child');
  const bookmarkButton = (node) => node.querySelector('.saved-actions button:last-child');
  const enter = async () => {
    api.showPanel('browse'); await api.browseTo([]);
    [...$('browselist').children].find((node) => node.querySelector('.bname')?.textContent === 'Saved places').click(); await settle();
  };
  const key = { catalogueId: 'modland', format: 'Protracker', author: "O'Neil / A+B" };
  const keyOther = { catalogueId: 'modland', format: 'Fasttracker 2', author: key.author };
  const lines = `1\tProtracker/${key.author}/one.mod\n1\tProtracker/${key.author}/two.mod\n1\tFasttracker 2/${key.author}/other.xm`;
  const install = async (text = lines) => {
    const built = archive.toRecords(text, 'modland', () => true);
    await api.catalogueStore.replacePrefix('modland:', [...built.records, { key: 'modland:meta', tracks: built.tracks, total: built.total, complete: true }]);
  };
  for (const place of await store.list()) await store.remove(place.key);
  await install(); await saved.refresh(); api.clearPlaybackQueue();
  const audible = { name: 'Independent context', url: 'https://example.test/bookmarks/playing.mod' };
  api.openAway([audible], 0, 'browse'); await settle();
  window.document.body.dispatchEvent(new window.Event('pointerdown', { bubbles: true })); await settle(0);
  const open = window.__toWorklet.filter((message) => message.type === 'open').at(-1);
  window.__workletPort.onmessage({ data: { type: 'opened', requestId: open.requestId, duration: 90, subsongs: 1, describe: '', rate: 44100 } });
  api.enqueueTrack({ name: 'Manual kept', url: 'https://example.test/bookmarks/manual.mod' });
  // The explicit queue action owns its read-ahead; start measuring Bookmark after that fixture settles.
  for (let n = 0; n < 200 && !api.musicNow().has('https://example.test/bookmarks/manual.mod'); n++) await settle(10);
  check(api.musicNow().has('https://example.test/bookmarks/manual.mod'), 'the queued fixture finishes its independent read-ahead before Bookmark measurement');
  const plan = api.planNow(), messages = window.__toWorklet.length;
  const random = api.rememberedRandomNow(), heard = random?.history.playedPicks;
  let requests = 0; const fetch = window.fetch;
  window.fetch = (...args) => { requests++; return fetch(...args); };
  try {
    await enter();
    check($('browsetitle').textContent === 'Saved places' && $('browsenote').textContent.includes('Open an author') && !listRows().length,
      'Browse exposes Saved places with the native explanatory empty state');
    $('browseback').click(); await settle();
    const savedDomain = [...$('browselist').children].find((node) => node.querySelector('.bname')?.textContent === 'Saved places');
    savedDomain.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true })); await settle();
    check(savedDomain.getAttribute('role') === 'button' && savedDomain.tabIndex === 0 && $('browsetitle').textContent === 'Saved places',
      'the Saved places entry is also operable through its actual keyboard handler');
    await api.browseTo(['modland', key.format]); await settle();
    check($('saved-place-header').hidden, 'a format listing has no ambiguous author bookmark action');
    await api.browseTo(['modland', key.format, key.author]); await settle();
    const toggle = $('saved-place-header').querySelector('button');
    check(!$('saved-place-header').hidden && toggle.textContent === 'Bookmark' && toggle.getAttribute('aria-pressed') === 'false' && !toggle.disabled,
      'an exact author folder offers the labelled outlined Bookmark with accessible state');
    toggle.click(); await settle();
    const original = (await store.list())[0];
    check(original && JSON.stringify(original.key) === JSON.stringify(key) && toggle.getAttribute('aria-pressed') === 'true' && toggle.textContent === 'Bookmark',
      'the actual Bookmark saves the full route including slash apostrophe and plus without changing its label');
    if (!original) return;
    check(api.planNow().context === plan.context && api.scheduleNow() === plan.schedule && window.__toWorklet.length === messages && requests === 0,
      'saving/listing bookmarks neither changes the playing context/manual FIFO nor fetches music');

    await store.save({ key: keyOther, catalogueName: 'Modland', savedAt: original.savedAt + 1 }); await enter();
    check(listRows().length === 2 && listRows().every((node) => node.querySelector('.bname').textContent === key.author)
      && listRows()[0].querySelector('.bmeta').textContent === 'Modland › Fasttracker 2',
      'namesakes retain distinct formats and the newest saved place appears first');
    const row = named(key.author), pair = row.querySelector('.saved-actions');
    check([...pair.children].map((button) => button.textContent).join('|') === 'Open|Bookmark'
      && window.getComputedStyle(pair).display === 'grid' && window.getComputedStyle(pair).gridTemplateColumns.includes('repeat(2'),
      'Saved rows put Open then filled Bookmark at the right in two equal CSS tracks');

    const target = listRows().find((node) => JSON.parse(node.dataset.place)[1] === key.format);
    openButton(target).click(); await settle();
    check($('browsetitle').textContent === `${key.format} / ${key.author}` && $('browselist').querySelectorAll('.btrack').length === 2,
      'Open resolves the exact current author folder and its actual contents');
    $('browseback').click(); await settle();
    check($('browsetitle').textContent === 'Saved places', 'Back from a saved folder returns to Saved places in one step');
    $('browseback').click(); await settle();
    check($('browsetitle').textContent === 'Browse', 'another Back returns from Saved places to Browse root');

    await enter();
    const removed = listRows().find((node) => JSON.parse(node.dataset.place)[1] === key.format);
    bookmarkButton(removed).click(); await settle();
    check(!listRows().some((node) => JSON.parse(node.dataset.place)[1] === key.format) && !$('snackundo').hidden,
      'removing a bookmark through the saved list offers its own snackbar Undo');
    $('browseback').click(); await settle(); $('snackundo').click(); await settle();
    check(JSON.stringify((await store.list()).find((place) => place.key.format === key.format)) === JSON.stringify(original),
      'Undo after navigation restores the exact original bookmark and save time');
    check(api.planNow().context === plan.context && api.scheduleNow() === plan.schedule && window.__toWorklet.length === messages,
      'bookmark Undo does not restore a playlist edit or change playback');
    check(api.rememberedRandomNow() === random && api.rememberedRandomNow()?.history.playedPicks === heard,
      'bookmark navigation and Undo retain the remembered Random session and heard record');

    await enter(); await api.catalogueStore.clear('modland:'); await settle();
    check(listRows().length === 2 && listRows().every((node) => openButton(node).disabled && node.querySelector('.saved-reason').textContent.includes('index')),
      'index deletion keeps the bookmarks and explains their disabled Open controls');
    const beforePrepare = requests;
    listRows()[0].querySelector(':scope > button').click(); await settle();
    check($('browsetitle').textContent === 'Online catalogues' && requests === beforePrepare,
      'the explicit Catalogues recovery action navigates without starting an index download');
    await install(); await enter();
    check(listRows().every((node) => !openButton(node).disabled), 'reindexing makes the exact retained folders available again');
    await install(`1\tProtracker/Replacement/new.mod`); await settle();
    check(listRows().every((node) => openButton(node).disabled && node.querySelector('.saved-reason').textContent.includes('exact folder')),
      'a missing group retains its bookmark and never opens a similar folder');
    const unknown = { key: { catalogueId: 'removed', format: 'Group', author: 'Unavailable' }, catalogueName: 'Old archive', savedAt: Date.now() + 1 };
    await store.save(unknown); await saved.refresh();
    const unavailable = named('Unavailable');
    check(openButton(unavailable).disabled && unavailable.querySelector(':scope > button').hidden && !bookmarkButton(unavailable).disabled,
      'a removed catalogue remains visible and removable without a false preparation action');
    bookmarkButton(unavailable).click(); await settle();
    check(!named('Unavailable'), 'the unavailable-source Bookmark action actually removes its record');

    await install(); await enter();
    const held = deferred(), entered = deferred(), resolve = saved.resolve;
    saved.resolve = async (...args) => { const destination = await resolve(...args); entered.resolve(); await held.promise; return destination; };
    try {
      openButton(listRows()[0]).click(); await bounded(entered.promise); $('browseback').click(); await settle(); held.resolve(); await settle();
      check($('browsetitle').textContent === 'Browse' && saved.state.opening === null,
        'Back retires a noncancellable pending Open so its late result cannot navigate');
    } finally { held.resolve(); saved.resolve = resolve; }

    await enter(); const list = store.list;
    store.list = async () => { throw Error('Unavailable storage'); };
    try { $('saved-place-tools').querySelector('button').click(); await settle();
      check(saved.state.failed && $('browsenote').textContent.includes('Could not update') && !$('browsenote').textContent.includes('Nothing saved'),
        'a failed list read explains Retry instead of pretending that no bookmarks exist');
    } finally { store.list = list; }
    $('saved-place-tools').querySelector('button').click(); await settle();
    check(!saved.state.failed && listRows().length === 2, 'the actual Refresh control recovers the saved list after storage becomes readable');

    await enter();
    openButton(listRows().find((node) => JSON.parse(node.dataset.place)[1] === key.format)).click(); await settle();
    $('browselist').querySelector('.btrack').click(); await settle();
    const playing = api.planNow(); api.showPanel(null); $('showqueue').click(); $('queue-source').click(); await settle();
    check($('browsetitle').textContent === `${key.format} / ${key.author}` && api.planNow().context === playing.context,
      'Queue return to a bookmarked folder restores that source without replacing its playback');
    $('browseback').click(); await settle();
    check($('browsetitle').textContent === 'Saved places', 'Queue source restoration also preserves the saved-folder Back destination');
    for (const domain of ['history', 'search']) {
      await api.browseTo([domain]); await settle();
      check($('saved-place-header').hidden, `the ${domain} view never offers an ambiguous folder bookmark`);
    }

    window.__i18n.useLanguage('pl'); await api.browseTo(['modland', key.format, key.author]); await settle();
    check($('saved-place-header').querySelector('button').textContent === 'Zakładka'
      && $('saved-place-header').querySelector('button').getAttribute('aria-label').includes('Dodano zakładkę'),
      'the folder Bookmark and accessible saved-state description use the Polish contract');
    $('saved-place-header').querySelector('button').click(); await settle();
    check(!$('saved-place-header').querySelector('button').disabled && $('saved-place-header').querySelector('button').getAttribute('aria-pressed') === 'false'
      && $('snackundo').hidden,
      'the ordinary folder toggle removes directly without borrowing the saved-list Undo flow');
  } finally { window.fetch = fetch; window.__i18n.useLanguage('en'); api.showPanel(null); }
}
