// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Browse's Search on the page, as the phone has it: the bar's button and field, the level's rows, Back and Escape. */
export default async function checkBrowseFilter({ window, check }) {
  const { document } = window, $ = (id) => document.getElementById(id), api = window.__api;
  const settle = (ms = 20) => new Promise((resolve) => setTimeout(resolve, ms));
  const until = async (condition) => { for (let i = 0; i < 250; i++) { if (await condition()) return true; await settle(); } return false; };
  // The level's own rows; the tunes below are checked by name at the end.
  const shown = () => [...$('browselist').children].filter((li) => !li.hidden && !li.dataset.below).map((li) => li.querySelector('.bname')?.textContent);
  const below = () => [...$('browselist').querySelectorAll('[data-below="track"]')].filter((li) => !li.hidden)
    .map((li) => li.querySelector('.bname')?.textContent);
  const heading = () => $('browselist').querySelector('[data-below="heading"]');
  const type = async (text) => { $('browsefilter-input').value = text; $('browsefilter-input').dispatchEvent(new window.Event('input')); await settle(); };
  const escape = async () => { document.activeElement.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })); await settle(); };
  const open = () => !$('browsefilter-form').hidden;
  const pointer = (target, type) => {
    const event = new window.MouseEvent(type, { button: 0, bubbles: true, cancelable: true });
    Object.defineProperty(event, 'pointerId', { value: 1 }); Object.defineProperty(event, 'pointerType', { value: 'touch' });
    target.dispatchEvent(event);
  };

  const archive = window.__archive;
  await api.catalogueStore.replacePrefix('modland:', [...archive.toRecords([
    '1\tProtracker/Nork/savethefunky.mod', '1\tProtracker/Nork/other.mod', '1\tProtracker/Nork/third.mod',
    '1\tProtracker/Nooze/strap.mod', '1\tProtracker/4-Mat/elysium.mod', '1\tFasttracker 2/Nork/xm.xm',
  ].join('\n'), 'modland', () => true).records, { key: 'modland:meta', tracks: 6, total: 6, complete: true }]);
  api.showPanel('browse');

  for (const path of [[], ['catalogues'], ['search']]) {
    await api.browseTo(path); await settle(50);
    check($('browsefilter-open').hidden, `no Search where there is no list to narrow: ${JSON.stringify(path)}`);
  }

  await api.browseTo(['modland', 'Protracker']);
  check(await until(() => shown().length === 3) && !$('browsefilter-open').hidden && !open()
    && $('browsefilter-open').textContent.trim() === window.__i18n.t('Search') && $('browsefilter-open').querySelector('svg'),
    'a format\'s authors offer Search, with its icon and label, and the field closed');
  $('browsefilter-open').click(); await settle();
  check(open() && $('browsetitle').hidden && $('browsefilter-open').hidden && document.activeElement === $('browsefilter-input'),
    'Search puts the field in the title\'s place, with the keyboard');
  await type('NORK');
  check(shown().join() === 'Nork', 'the authors narrow by the playlist\'s rule, case aside');

  await escape();
  check(open() && document.activeElement !== $('browsefilter-input') && shown().join() === 'Nork',
    'Escape drops the keyboard first and keeps the narrowed list');
  await escape();
  check(!open() && !$('browsetitle').hidden && shown().length === 3 && !$('browse').hidden,
    'Escape again closes Search, not Browse, and the whole level returns');

  // Each level its own.
  $('browsefilter-open').click(); await type('no');
  check(shown().sort().join() === 'Nooze,Nork', 'narrowed to the two matching authors');
  [...$('browselist').children].find((li) => li.querySelector('.bname')?.textContent === 'Nork').click();
  check(await until(() => $('browselist').querySelectorAll('.btrack').length === 3) && !open() && !$('browsefilter-open').hidden,
    'the author\'s own level starts with Search closed');
  $('browsefilter-open').click(); await type('funky');
  check(shown().length === 1 && /funky/i.test(shown()[0]), 'an author\'s tunes narrow too');

  // A selection is of what is shown: two of the three tunes have "the" in their names.
  await type('the');
  check(shown().length === 2, 'two tunes left to select from');
  const row = [...$('browselist').querySelectorAll('.btrack')].find((li) => !li.hidden).querySelector('.btext');
  pointer(row, 'pointerdown'); await settle(540); pointer(row, 'pointerup'); row.click(); await settle();
  $('browse-select-all').click(); await settle();
  check(api.browseSelectionNow().count === 2, 'Select all takes what Search shows, not the hidden tune');
  $('browseback').click(); await settle();
  check(api.browseSelectionNow().count === 0 && open(), 'Back leaves the selection before Search');

  await type('zzz');
  check(!$('browsefilter-empty').hidden && shown().length === 0, 'nothing matching says so');
  $('browseback').click(); await settle(50);
  check(!open() && $('browsefilter-empty').hidden && $('browselist').querySelectorAll('.btrack').length === 3,
    'Back closes Search on this level and stays on it');
  $('browseback').click();
  check(await until(() => shown().length > 0 && open()) && $('browsefilter-input').value === 'no' && shown().sort().join() === 'Nooze,Nork',
    'one level up, its own Search is still there');

  api.showPanel(null); await settle();
  api.showPanel('browse'); await api.browseTo(['modland', 'Protracker']);
  check(await until(() => shown().length === 3) && !open(), 'leaving Browse forgets every level\'s Search');

  // Below the authors, the tunes further down that match (2026-11-09).
  $('browsefilter-open').click(); await type('funky');
  check(await until(() => below().length === 1) && below()[0] === 'savethefunky.mod' && shown().length === 0
    && heading()?.textContent === window.__i18n.t('In folders below') && $('browsefilter-empty').hidden,
    'no author is called funky, and below them the tune that is, under its heading');
  await type('nor');
  check(await until(() => below().length === 3), 'three tunes below for "nor"');
  await type('nork');
  check(below().length === 3, 'while newer words wait, the older answer stays rather than the section blinking out');
  check(await until(() => below().length === 3) && shown().join() === 'Nork' && !below().includes('xm.xm'),
    'an author\'s name finds the author and, below, the tunes in this format filed under it');
  const tunesFound = [...$('browselist').querySelectorAll('[data-below="track"]')];
  tunesFound[2].querySelector('.btext').click(); await settle(50);
  check(api.queueNow().length === 3 && api.indexNow() === 2
    && api.queueNow().join() === tunesFound.map((li) => li.dataset.url).join(),
    'a tune below plays the tunes found, from it');
  api.showPanel('browse'); await settle(50);
  const first = $('browselist').querySelector('[data-below="track"] .btext');
  pointer(first, 'pointerdown'); await settle(540); pointer(first, 'pointerup'); first.click(); await settle();
  $('browse-select-all').click(); await settle();
  check(api.browseSelectionNow().count === 3, 'Select all takes the tunes below too');
  $('browseback').click(); await settle();
  await type('zzz');
  check(await until(() => !$('browsefilter-empty').hidden) && below().length === 0 && !heading(),
    'nothing matching on the level or below says so, once below has answered');
  await type('');
  check(await until(() => shown().length === 3) && !heading(), 'cleared, the level is whole again with nothing below');

  // The archive's formats: every format's tunes below.
  await api.browseTo(['modland']);
  check(await until(() => shown().length === 2), 'the formats');
  $('browsefilter-open').click(); await type('nork');
  check(await until(() => below().length === 4) && below().includes('xm.xm') && shown().length === 0,
    'below the formats, the tunes of every format');
  // An author's tunes are the bottom: nothing is below them.
  await api.browseTo(['modland', 'Protracker', 'Nork']);
  $('browsefilter-open').click(); await type('the');
  await settle(400);
  check(!heading() && below().length === 0 && shown().length === 2, 'an author\'s level has nothing below');
  api.showPanel(null);
}
