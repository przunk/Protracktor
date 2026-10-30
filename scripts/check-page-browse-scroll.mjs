// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native BrowseScroll decisions exercised by actual folders, Back, paging and modal actions. */
export default async function checkBrowseScroll({ window, check }) {
  const { document } = window, $ = (id) => document.getElementById(id), api = window.__api, archive = window.__archive;
  const list = $('browselist'), settle = (ms = 20) => new Promise((resolve) => setTimeout(resolve, ms));
  const until = async (condition) => { for (let i = 0; i < 250; i++) { if (await condition()) return true; await settle(); } return false; };
  const named = (name) => [...list.children].find((row) => row.querySelector('.bname')?.textContent === name);
  const key = (target, name) => target.dispatchEvent(new window.KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true }));
  const replace = async ({ insert = false, remove = false } = {}) => {
    const names = [...(insert ? ['AA inserted'] : []), ...Array.from({ length: 20 }, (_, n) => `Scroll ${String(n).padStart(2, '0')}`)]
      .filter((name) => !remove || name !== 'Scroll 10');
    const lines = names.flatMap((name) => [0, 1].map((n) => `64\tProtracker/${name}/scroll ${name} ${n}.mod`));
    lines.push('64\tAHX/Other/other.ahx');
    const { records, ...counts } = archive.toRecords(lines.join('\n'), 'modland', () => true);
    await api.catalogueStore.replacePrefix('modland:', [...records, { ...counts, key: 'modland:meta', complete: true }]);
  };
  const originalRects = window.HTMLElement.prototype.getBoundingClientRect;
  window.HTMLElement.prototype.getBoundingClientRect = function () {
    if (this === list) return { top: 100, bottom: 300, height: 200, width: 300 };
    if (this.parentElement === list) {
      const top = 100 + [...list.children].indexOf(this) * 40 - list.scrollTop;
      return { top, bottom: top + 40, height: 40, width: 300 };
    }
    return originalRects.call(this);
  };
  const root = async () => {
    if (!$('browse').hidden) $('browseclose').click();
    $('tab-browse').click(); return until(() => $('browsetitle').textContent === 'Browse' && named('Online catalogues'));
  };
  const formats = async () => {
    await root(); named('Online catalogues').click();
    await until(() => named('Modland')); named('Modland').querySelector('.btext').click();
    return until(() => named('Protracker'));
  };
  try {
    await api.discardEdits(); api.showPanel(null); await replace();
    check(await formats(), 'actual Browse → Catalogues → Modland exposes the native folder levels');
    const plan = api.planNow(), commands = window.__toWorklet.length;
    list.scrollTop = 40; named('Protracker').focus(); key(named('Protracker'), 'Enter');
    check(await until(() => named('Scroll 10')) && list.scrollTop === 0, 'keyboard folder entry starts a new level at its own top');
    list.scrollTop = 200; named('Scroll 10').click();
    check(await until(() => list.querySelectorAll('.btrack').length === 2) && list.scrollTop === 0,
      'actual author entry does not inherit the parent list offset');
    await replace({ insert: true }); $('browseback').click();
    check(await until(() => named('AA inserted') && list.scrollTop === 440),
      'actual Back restores the exact departed author after its index changes');
    list.scrollTop = 333; named('Scroll 10').click(); await until(() => list.querySelectorAll('.btrack').length === 2);
    await replace({ insert: true, remove: true }); $('browseback').click();
    check(await until(() => $('browsetitle').textContent === 'Protracker' && named('Scroll 11')) && !named('Scroll 10') && list.scrollTop === 333,
      'a genuinely removed author retains the parent offset instead of using an obsolete row index');
    $('browseback').click();
    check(await until(() => $('browsetitle').textContent === 'Modland' && named('Protracker')) && list.scrollTop === 40,
      'a second actual Back restores the independently remembered format');

    const read = api.catalogueStore.get; let release, held = false;
    api.catalogueStore.get = async function (key) {
      const value = await read.call(this, key);
      if (!held && key === 'modland:authors:Protracker') {
        held = true; await new Promise((resolve) => { release = resolve; });
      }
      return value;
    };
    try {
      named('Protracker').click(); await until(() => typeof release === 'function');
      $('browseback').click(); await until(() => $('browsetitle').textContent === 'Modland' && named('Protracker'));
      const top = list.scrollTop;
      release(); await settle(80);
      check($('browsetitle').textContent === 'Modland' && !!named('AHX') && !named('Scroll 11') && list.scrollTop === top,
        'a late author result cannot replace or scroll the newer Back destination');
    } finally { release?.(); api.catalogueStore.get = read; }
    list.scrollTop = 50; await root();
    check(list.scrollTop === 0, 'a genuinely fresh Browse visit resets the prior session to the top');
    check(api.planNow().context === plan.context && api.scheduleNow() === plan.schedule && window.__toWorklet.length === commands,
      'folder/Back/restoration actions leave playback, manual requests and decoder ownership unchanged');

    await window.__played.clear();
    for (let n = 0; n < 101; n++) await window.__played.record({ url: `https://example.test/history-scroll/${n}.mod`,
      name: `History scroll ${n}`, meta: 'Scroll history', file: `${n}.mod`, replayable: true });
    named('History').click();
    check(await until(() => list.querySelectorAll('.btrack').length === 100) && list.scrollTop === 0,
      'fresh actual History entry starts its first page at the top');
    list.scrollTop = 480; $('history-older').click();
    check(await until(() => api.historyNow().page === 1 && !api.historyNow().loading) && list.scrollTop === 0,
      'the next History page has an independent initial position');
    list.scrollTop = 20; $('history-newer').click();
    check(await until(() => api.historyNow().page === 0 && !api.historyNow().loading) && list.scrollTop === 480,
      'returning to a History page restores its own offset within the query');
    const row = list.querySelector('.btrack'); row.querySelector('.bmore').click();
    [...$('menu').querySelectorAll('button')].find((button) => button.textContent === 'Select').click();
    $('sel-add').click(); await until(() => !$('addto').hidden);
    $('addto').querySelector('[data-close]').click();
    check(await until(() => !$('browse').hidden && !api.historyNow().loading) && api.historyNow().page === 0 && list.scrollTop === 480,
      'the temporary destination chooser returns to the same History visit and offset');
    $('showqueue').click(); $('queue-close').click();
    check(!$('browse').hidden && list.scrollTop === 480, 'Queue opening/closing preserves the underlying History offset');
    const input = $('historysearch'); input.value = 'History scroll 100'; input.dispatchEvent(new window.Event('input', { bubbles: true }));
    check(await until(() => !api.historyNow().loading && list.querySelectorAll('.btrack').length === 1) && list.scrollTop === 0,
      'changing the History phrase resets its result position to the top');
    list.scrollTop = 20; input.value = ''; input.dispatchEvent(new window.Event('input', { bubbles: true }));
    check(await until(() => !api.historyNow().loading && list.querySelectorAll('.btrack').length === 100) && list.scrollTop === 0,
      'returning to a prior History phrase starts fresh instead of restoring that phrase’s old offset');
    list.scrollTop = 360; $('browseback').click(); await until(() => named('History')); named('History').click();
    check(await until(() => list.querySelectorAll('.btrack').length === 100 && !api.historyNow().loading) && list.scrollTop === 0,
      'every fresh History entry resets the previous History visit');
    $('browseback').click(); await until(() => named('Search')); named('Search').click();
    if (api.searchNow().scope.kind !== 'everywhere') {
      document.querySelector(`[data-search-scope="${api.searchNow().scope.kind}"]`).click();
    }
    check(api.searchNow().scope.kind === 'everywhere', 'the position fixture explicitly selects its catalogue-containing Search scope');
    const search = $('browsesearch'); search.value = 'scroll'; search.dispatchEvent(new window.Event('input', { bubbles: true })); $('browsesubmit').click();
    check(await until(() => !api.searchNow().loading && list.querySelectorAll('.btrack').length > 10), 'Search supplies a producing list for position ownership');
    list.scrollTop = 280; search.value = 'not yet submitted'; search.dispatchEvent(new window.Event('input', { bubbles: true }));
    await api.renderBrowse();
    check(list.scrollTop === 280 && list.querySelectorAll('.btrack').length > 10,
      'pending Search text does not reset the producing result position');
    await root();
    for (const place of await window.__bookmarks.list()) await window.__bookmarks.remove(place.key);
    for (let n = 11; n <= 17; n++) await window.__bookmarks.save({
      key: { catalogueId: 'modland', format: 'Protracker', author: `Scroll ${n}` }, catalogueName: 'Modland', savedAt: n });
    named('Saved places').click();
    check(await until(() => list.querySelectorAll('.saved-place').length === 7 && !window.__savedPlaces.state.loading),
      'Saved places supplies stable bookmark identities for returning to the list');
    list.scrollTop = 100;
    [...list.querySelectorAll('.saved-place')].find((node) => node.querySelector('.bname').textContent === 'Scroll 14')
      .querySelector('.saved-actions button').click();
    await until(() => $('browsetitle').textContent === 'Protracker / Scroll 14');
    await window.__bookmarks.save({ key: { catalogueId: 'modland', format: 'Protracker', author: 'Scroll 19' }, catalogueName: 'Modland', savedAt: 1000 });
    $('browseback').click();
    check(await until(() => $('browsetitle').textContent === 'Saved places' && !window.__savedPlaces.state.loading
      && list.querySelectorAll('.saved-place').length === 8) && list.scrollTop === 160,
      'Saved places waits for its refreshed membership before restoring a moved departed bookmark');
    $('browseclose').click();
  } finally { window.HTMLElement.prototype.getBoundingClientRect = originalRects; }
}
