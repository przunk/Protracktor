// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native cache/OnPhone rules through real row, player and Settings actions. */
export default async function checkMusicCache({ window, check }) {
  const { document } = window, api = window.__api, $ = (id) => document.getElementById(id), music = api.musicNow();
  const settle = (ms = 15) => new Promise((done) => setTimeout(done, ms));
  const until = async (predicate) => { for (let n = 0; n < 300; n++) { if (await predicate()) return true; await settle(); } return false; };
  const url = 'https://modland.com/pub/modules/Protracker/CacheAuthor/cache.mod';
  const payload = new Uint8Array(64); payload[0] = 31; payload[63] = 97;
  const originalFetch = window.fetch; let requests = 0, offline = false, releaseFile;
  const opens = () => window.__toWorklet.filter((message) => message.type === 'open');
  const ack = () => api.onWorklet({ type: 'opened', requestId: opens().at(-1)?.requestId,
    describe: 'title\tCache title\nartist\tCacheAuthor\nformat\tProtracker', duration: 90, rate: 44100, subsongs: 1, current: 0, canSeek: true });
  const folder = async () => { api.showPanel('browse'); await api.browseTo(['modland', 'Protracker', 'CacheAuthor']); return $('browselist').querySelector('.btrack'); };
  window.fetch = async (input, options) => {
    if (String(input) !== url) return originalFetch(input, options);
    requests++; if (offline) throw new TypeError('offline cache fixture');
    return new Promise((resolve) => { releaseFile = () => resolve(new Response(payload.slice())); });
  };
  try {
    await music.ready; await music.clear(); await api.discardEdits();
    const built = window.__archive.toRecords('64\tProtracker/CacheAuthor/cache.mod', 'modland', () => true);
    await api.catalogueStore.replacePrefix('modland:', [...built.records, { key: 'modland:meta', tracks: 1, total: 1, complete: true }]);
    await api.storeNow.save({ id: 'cache-kept-document', name: 'Cache kept', tracks: [{ url, name: 'Saved reference' }], index: 0 });
    const savedKey = { catalogueId: 'modland', format: 'Protracker', author: 'CacheAuthor' };
    await window.__bookmarks.save({ key: savedKey, catalogueName: 'Modland', savedAt: 5000000 });
    const row = await folder(); let leading = row.querySelector('.leading-primary');
    check(!leading.querySelector('svg') && !music.has(url), 'an indexed catalogue file alone is not an offline copy');
    const before = opens().length; row.querySelector('.btext').click();
    check(await until(() => typeof releaseFile === 'function'), 'the actual player can wait on a shared file transfer before publication');
    row.querySelector('.bmore').click(); [...$('menu').querySelectorAll('button')].find((button) => button.textContent === 'Select').click();
    const tick = row.querySelector('.tick');
    check(!!tick, 'actual Select creates its checkbox while a transfer is pending');
    if (!tick) { releaseFile(); return; }
    tick.focus(); releaseFile();
    check(await until(() => opens().length > before && music.has(url)), 'actual Browse playback publishes a complete retained file before opening it'); ack();
    check(tick.isConnected && row.querySelector('.tick') === tick && tick.checked && document.activeElement === tick,
      'completing a background publication preserves the selected checkbox row and focus');
    $('browseback').click(); leading = row.querySelector('.leading-primary');
    check(requests === 1 && opens().at(-1).bytes.byteLength === 64 && new Uint8Array(opens().at(-1).bytes)[0] === 31,
      'the first shared fetch sends its complete original bytes to the decoder');
    check(row.isConnected && row.querySelector('.leading-primary') === leading && leading.querySelector('svg')
      && leading.getAttribute('aria-label') === 'Downloaded copy — available offline', 'successful publication decorates the same Browse leading slot with the native phone/tick and accessible state');
    check((await api.musicStore.snapshot()).find((item) => item.url === url)?.sizeBytes === 64, 'persistent accounting uses the actual positive payload size');

    offline = true; const replayBefore = opens().length; row.querySelector('.btext').click();
    check(await until(() => opens().length > replayBefore) && requests === 1, 'actual offline replay reads the retained file without another network request'); ack();
    check(opens().at(-1).bytes.byteLength === 64 && new Uint8Array(opens().at(-1).bytes)[63] === 97,
      'a second decoder transfer cannot detach or empty the persisted copy');
    const reopened = await import('../web/src/store.js?cache-page-reopen');
    const stored = await reopened.musicCache.read(url); reopened.forget();
    check(stored?.size === 64 && new Uint8Array(await stored.arrayBuffer())[0] === 31,
      'a separately opened production database connection reads the same immutable file payload');

    await api.enqueueTrack({ url: 'https://example.test/cache/manual.mod', name: 'Manual' });
    api.onWorklet({ type: 'position', seconds: 17 });
    const state = { elapsed: $('elapsed').textContent, transport: $('playpause').title, context: api.planNow().context, schedule: api.scheduleNow(), editor: api.editorNow(),
      actual: api.actualNow(), audio: api.contextNow(), commands: window.__toWorklet.length,
      history: (await window.__played.recent()).map((entry) => entry.url).join('|') };
    api.showPanel(null); $('tab-settings').click();
    check(await until(() => !$('settings').hidden && $('music-cache-storage')?.querySelector('button')),
      'the real Settings action exposes retained-music storage controls');
    const storage = $('music-cache-storage'), clear = [...storage.querySelectorAll('button')].find((button) => button.textContent === 'Clear');
    check(storage.textContent.includes('Less than 1 MB of 512 MB') && clear.querySelector('svg') && !clear.disabled,
      'storage reports the actual tiny payload and native budget beside a labelled Clear action');
    let confirms = 0; const confirm = window.confirm; window.confirm = () => { confirms++; return false; };
    try { clear.click(); check(await until(() => !music.has(url) && $('snacktext').textContent === 'Freed less than 1 MB.'), 'actual Clear removes the retained copy and gives completion feedback'); }
    finally { window.confirm = confirm; }
    check(confirms === 0 && $('snacktext').textContent === 'Freed less than 1 MB.', 'fetched-copy Clear needs no confirmation and never calls a tiny deletion zero MB');
    check(api.planNow().context === state.context && api.scheduleNow() === state.schedule && api.editorNow() === state.editor
      && api.actualNow() === state.actual && api.contextNow() === state.audio && window.__toWorklet.length === state.commands && $('elapsed').textContent === state.elapsed && $('playpause').title === state.transport,
      'clearing downloaded music preserves actual audio, source, manual requests, draft and current file');
    check((await api.storeNow.get('cache-kept-document')).name === 'Cache kept'
      && (await api.catalogueStore.get('modland:meta')).tracks === 1
      && (await window.__bookmarks.list()).some((place) => place.key.author === 'CacheAuthor')
      && (await window.__played.recent()).map((entry) => entry.url).join('|') === state.history,
      'clear leaves saved playlists, catalogue indexes, bookmarks and History intact');
    check(!leading.querySelector('svg'), 'clearing a retained copy removes its old offline mark without rebuilding the Browse row');

    api.showPanel(null); const phoneUrl = 'https://example.test/cache/transferred.mod';
    api.receive({ queue: [{ url: phoneUrl, title: 'Transferred HTTP', file: 'transferred.mod', data: 'AQIDBA==' }], index: 0 }); await settle(80);
    const phoneOpens = opens().length; $('playpause').click();
    check(await until(() => opens().length > phoneOpens && opens().at(-1).name === 'transferred.mod'), 'a real HTTP-URL handoff still plays its transferred bytes through the player'); ack();
    check(opens().at(-1).bytes.byteLength === 4 && !(await api.musicStore.snapshot()).some((entry) => entry.url === phoneUrl),
      'transferred phone bytes bypass persistent fetched-music storage even when their identity is HTTP');
    const transferredAgain = await api.bytesOf({ url: phoneUrl, name: 'Transferred HTTP' });
    check(transferredAgain.byteLength === 4 && new Uint8Array(transferredAgain)[3] === 4,
      'the common file-action resolver reuses a live tab-held copy without a network lookup');
    const beforeClear = window.__toWorklet.length; await music.clear();
    check((await api.bytesOf({ url: phoneUrl })).byteLength === 4 && window.__toWorklet.length === beforeClear,
      'fetched-cache Clear does not erase transferred buffers or disturb their actual playback');
    const quotaUrl = 'https://example.test/cache/quota.mod', put = window.IDBObjectStore?.prototype.put ?? globalThis.IDBObjectStore.prototype.put;
    const objectStore = window.IDBObjectStore ?? globalThis.IDBObjectStore;
    objectStore.prototype.put = function (...args) {
      if (this.name === 'remoteMusic' && args[0]?.url === quotaUrl) throw new DOMException('page quota refused', 'QuotaExceededError');
      return put.apply(this, args);
    };
    try {
      const beforeQuota = opens().length;api.playFromBrowse([{ url: quotaUrl, name: 'Quota', file: 'quota.mod' }], 0, 'browse');
      check(await until(() => opens().length > beforeQuota && opens().at(-1).name === 'quota.mod'),
        'a real persistence quota refusal does not stop a successfully fetched file from reaching the decoder');ack();
      check(!music.has(quotaUrl) && !(await api.musicStore.snapshot()).some((entry) => entry.url === quotaUrl) && music.state.failed?.name === 'QuotaExceededError',
        'a refused publication never claims an offline copy and retains its explicit failure state');
      api.showPanel(null);$('tab-settings').click();
      check(await until(() => !$('settings').hidden && $('music-cache-storage')?.textContent.includes('page quota refused')),
        'actual Settings exposes the refused storage reason and a usable Retry action');
    } finally {objectStore.prototype.put = put;}
    const retry = [...$('music-cache-storage').querySelectorAll('button')].find((button) => button.textContent === 'Retry');retry.click();
    check(await until(() => music.state.failed === null && !music.state.loading), 'actual storage Retry can recover after a browser refusal is lifted');
  } finally { window.fetch = originalFetch; api.showPanel(null); }
}
