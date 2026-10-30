// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Folder data spending, progress and cancellation through real Browse/Settings controls. */
export default async function checkFolderPrefetch({ window, check }) {
  const api = window.__api, { document } = window, $ = (id) => document.getElementById(id);
  const pause = (ms = 15) => new Promise((done) => setTimeout(done, ms));
  const until = async (predicate) => { for (let i = 0; i < 300; i++) { if (await predicate()) return true; await pause(); } return false; };
  const archive = window.__archive, paths = ['a', 'b', 'c', 'd', 'e'].map((name) => `Protracker/AheadAuthor/${name}.mod`);
  const urls = paths.map((path) => archive.fileUrlFor(path)), requests = [], releases = new Map();
  const fetch = window.fetch, get = api.catalogueStore.get; let releaseOld;
  const mode = (value) => document.querySelector(`input[name="cacheahead"][value="${value}"]`);
  const folder = async () => {
    api.showPanel('browse'); await api.browseTo(['modland', 'Protracker']);
    const author = [...$('browselist').children].find((row) => row.querySelector('.bname')?.textContent === 'AheadAuthor');
    author.click(); return until(() => $('browselist').querySelectorAll('.btrack').length === 5);
  };
  window.fetch = (input, options) => {
    const url = String(input); if (!urls.includes(url)) return fetch(input, options);
    requests.push(url); return new Promise((resolve) => { releases.set(url, () => resolve(new window.Response(new Uint8Array([urls.indexOf(url) + 41])))); });
  };
  try {
    api.showPanel(null); await api.discardEdits(); api.clearPlaybackQueue(); await api.musicNow().clear();
    const built = archive.toRecords(paths.map((path) => '1\t' + path).join('\n'), 'modland', () => true);
    await api.catalogueStore.replacePrefix('modland:', [...built.records, {key:'modland:meta',tracks:5,total:5,complete:true}]);
    check(mode('wifi').checked && api.folderAheadNow().mode === 'wifi', 'Settings restores the native Wi-Fi-only folder default');
    const entered = await folder();
    const paused = await Promise.race([api.folderAheadNow().completion.then(() => true), pause(100).then(() => false)]);
    check(entered && paused && requests.length === 0 && !$('folder-prefetch-note').hidden
      && $('folder-prefetch-note').textContent.includes('cannot check network data limits'),
      'a real folder opens without unsolicited default downloads and explains unknown network authority');
    const commands = window.__toWorklet.length;
    $('tab-settings').click();
    check(await until(() => !$('settings').hidden), 'the actual Settings action opens before a folder-policy choice');
    mode('always').click();
    check(api.folderAheadNow().mode === 'always' && window.localStorage.getItem('protracktor.cacheAhead') === 'always',
      'the real Settings radio persists explicit permission for folder downloads');
    await folder();
    check(await until(() => requests.length === 3) && requests.join('|') === urls.slice(0, 3).join('|'),
      'the actual author row starts exactly three missing files in folder order');
    const row = $('browselist').querySelector('.btrack'), leading = row.querySelector('.leading-primary');
    check(!!leading.querySelector('.spinner') && leading.getAttribute('aria-label') === 'Caching ahead',
      'a pending folder read uses the native leading spinner and an accessible label');
    row.querySelector('.bmore').click(); [...$('menu').querySelectorAll('button')].find((button) => button.textContent === 'Select').click();
    const tick = row.querySelector('.tick'); tick.focus(); $('browselist').scrollTop = 80; releases.get(urls[0])();
    check(await until(() => requests.length === 4 && api.musicNow().has(urls[0]))
      && requests[3] === urls[3] && row.isConnected && row.querySelector('.tick') === tick && document.activeElement === tick && tick.checked
      && $('browselist').scrollTop === 80,
      'completing a folder read starts only the next candidate and preserves the selected row, focus and scroll');
    $('browseback').click();
    check(row.querySelector('.leading-primary svg') && row.querySelector('.leading-primary').getAttribute('aria-label') === 'Downloaded copy — available offline',
      'leaving selection reveals actual retained availability in the same leading slot');
    $('browseback').click();
    for (const url of urls.slice(1, 4)) releases.get(url)?.();
    check(await until(() => urls.slice(0, 4).every((url) => api.musicNow().has(url))) && !requests.includes(urls[4])
      && api.folderAheadNow().state.key === null && api.folderAheadNow().state.fetching.length === 0,
      'Back stops unstarted files while shared in-flight transfers may finish in cache');
    check(window.__toWorklet.length === commands, 'folder controls and storage progress leave the actual decoder untouched');

    await api.musicNow().clear(); api.showPanel('browse'); await api.browseTo(['modland', 'Protracker']);
    let held = false;
    api.catalogueStore.get = async function(key) {
      const result = await get.call(this, key);
      if (key === 'modland:tracks:Protracker/AheadAuthor') { held = true; await new Promise((resolve) => { releaseOld = resolve; }); }
      return result;
    };
    [...$('browselist').children].find((item) => item.querySelector('.bname')?.textContent === 'AheadAuthor').click();
    check(await until(() => held), 'the real author action can wait on an older folder read');
    $('tab-settings').click();
    check(await until(() => !$('settings').hidden), 'Settings actually hides the waiting folder before its old result is released');
    const before = requests.length; releaseOld(); api.catalogueStore.get = get;
    await pause(); await pause();
    check(requests.length === before && api.folderAheadNow().state.key === null,
      'a late folder result cannot start downloads after Settings has hidden Browse');
    const radio = mode('always'); document.querySelector('input[name="language"][value="pl"]').click();
    check(mode('always') === radio && radio.checked && radio.parentElement.textContent.includes('Zawsze'),
      'folder controls change language in place without losing their selected mode');
    document.querySelector('input[name="language"][value="en"]').click(); mode('off').click();
    const set = window.Storage.prototype.setItem;
    window.Storage.prototype.setItem = function(key, value) {
      if (key === 'protracktor.cacheAhead') throw new Error('folder choice write refused');
      return set.call(this, key, value);
    };
    try {
      mode('always').click();
      check(api.folderAheadNow().mode === 'always' && mode('always').checked && $('snacktext').textContent.includes('folder choice write refused'),
        'a refused real setting write explains the failure while keeping the runtime choice usable');
    } finally { window.Storage.prototype.setItem = set; }
    mode('off').click();
    check(api.folderAheadNow().mode === 'off' && window.__toWorklet.length === commands,
      'Off and language changes preserve sound and stop future folder work');
  } finally { releaseOld?.(); api.catalogueStore.get = get; window.fetch = fetch; }
}
