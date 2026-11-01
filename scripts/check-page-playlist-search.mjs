// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
export default async function ({ window, check }) {
  const $ = id => window.document.getElementById(id), api = window.__api;
  const tracks = [
    { url: 'https://modland.com/pub/modules/Protracker/Barry/lotus_3.mod', name: 'Space Ninja', file: 'lotus_3.mod' },
    { url: 'https://modland.com/pub/modules/Impulsetracker/Jakim/studium.it', name: 'Studium Fałszu', author: 'Jakim' },
    { url: 'https://modland.com/pub/modules/Protracker/Micha%C5%82/echo.mod', name: 'Space echoes' },
  ];
  await api.storeNow.save({ id: 'playlist-search-test', name: 'Search trial', tracks, index: 0 });
  await api.switchTo('playlist-search-test', { preservePlayback: true });
  api.showPanel(null);
  const input = $('playlist-search-input'), root = input.closest('.app');
  const visible = () => [...$('queue').children].filter(row => !row.hidden);
  const enter = query => { input.value = query; input.dispatchEvent(new window.Event('input', { bubbles: true })); };
  const escape = () => input.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  const sourceBefore = api.planNow();
  $('playlist-search-open').click();
  check(window.document.activeElement === input && root.classList.contains('playlist-search-editing'),
    'playlist Search focuses its existing header field and dims the surrounding UI');
  check(api.planNow().context === sourceBefore.context && api.planNow().schedule === sourceBefore.schedule,
    'opening playlist Search does not replace the currently playing source or manual requests');
  for (const [query, wanted] of [['SPACE', [0, 2]], ['falszu jakim', [1]], ['lotus_3', [0]], ['michal', [2]], ['missing', []]]) {
    enter(query);
    check(JSON.stringify(visible().map(row => Number(row.querySelector('.n').textContent) - 1)) === JSON.stringify(wanted),
      `playlist Search matches original row indices for ${query}`);
  }
  check(!$('playlist-search-empty').hidden && $('emptyplaylist').hidden,
    'a query with no matches describes the filter, not an empty underlying playlist');
  enter('space');
  check(!$('queue').querySelector('.playlist-grip'), 'a nonblank playlist filter hides reorder handles');
  escape();
  check(!root.classList.contains('playlist-search-editing') && input.value === 'space'
    && $('playlist-heading').classList.contains('search-open'), 'Escape ends editing while retaining filtered results');
  input.focus();
  check(root.classList.contains('playlist-search-editing'), 'refocusing the retained query dims again');
  escape(); escape();
  check(!$('playlist-heading').classList.contains('search-open') && visible().length === 3 && input.value === '',
    'the next Escape restores the normal playlist header and full list');
  $('playlist-search-open').click();
  enter('echoes');
  visible()[0].querySelector('.text').click();
  await new Promise(resolve => setTimeout(resolve, 30));
  check(!root.classList.contains('playlist-search-editing') && input.value === 'echoes' && visible().length === 1,
    'playing a filtered result ends editing but keeps the phrase and results');
  check(api.planNow().context.source.kind === 'playlist' && api.planNow().context.order.currentIndex === 2
    && api.planNow().context.order.tracks.length === 3, 'filtered playback addresses the original full-playlist context');
  check(!api.dirtyNow(), 'searching and playing a match do not mark the playlist edited');
  $('playlist-search-close').click();
  check(visible().length === 3, 'Close search restores every track');
  $('playlist-search-open').click(); enter('echoes');
  api.showPanel('browse');
  check(!$('playlist-heading').classList.contains('search-open') && !root.classList.contains('playlist-search-editing'),
    'leaving the playlist closes search and removes its scrim');
  api.showPanel(null);
  $('playlist-search-open').click(); enter('space');
  $('tab-browse').dispatchEvent(new window.MouseEvent('pointerdown', { bubbles: true, cancelable: true }));
  $('tab-browse').click();
  check($('browse').hidden && !root.classList.contains('playlist-search-editing') && input.value === 'space',
    'the first press on dimmed chrome only ends editing and keeps the filter');
  $('tab-browse').click();
  check(!$('browse').hidden && !$('playlist-heading').classList.contains('search-open'),
    'a second chrome press performs its normal action and closes the playlist filter');
  api.showPanel(null);
}
