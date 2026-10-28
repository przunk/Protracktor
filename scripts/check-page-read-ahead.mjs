// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native upcoming ownership through actual playlist, transport and queue actions. */
export default async function checkReadAhead({ window, check }) {
  const api = window.__api, { document } = window, $ = (id) => document.getElementById(id);
  const pause = () => new Promise((done) => setTimeout(done, 15));
  const until = async (predicate) => { for (let i = 0; i < 300; i++) { if (await predicate()) return true; await pause(); } return false; };
  const base = 'https://example.test/read-ahead/', urls = ['a', 'b', 'c', 'm1', 'm2', 'm3', 'delayed'].map((name) => base + name + '.mod');
  const track = (at) => ({ url: urls[at], name: 'Ahead ' + at, file: urls[at].split('/').at(-1) });
  const opens = () => window.__toWorklet.filter((message) => message.type === 'open');
  const ack = () => api.onWorklet({ type: 'opened', requestId: opens().at(-1).requestId,
    describe: 'title\tAhead played', duration: 90, rate: 44100, subsongs: 1, current: 0, canSeek: true });
  const fetch = window.fetch, read = api.musicStore.read, requests = []; let releaseDelayed, transferSignal;
  window.fetch = (input, options) => {
    const url = String(input), at = urls.indexOf(url);
    if (at < 0) return fetch(input, options);
    requests.push(url);
    if (at === 6) { transferSignal = options.signal; return new Promise((resolve) => { releaseDelayed = () => resolve(new window.Response(new Uint8Array([26, 99]))); }); }
    return Promise.resolve(new window.Response(new Uint8Array([20 + at, 99])));
  };
  try {
    api.showPanel(null); await api.discardEdits(); api.clearPlaybackQueue(); await api.musicNow().clear();
    if ($('shuffle').getAttribute('aria-pressed') === 'true') $('shuffle').click();
    for (let i = 0; i < 3 && $('repeat').getAttribute('aria-pressed') === 'true'; i++) $('repeat').click();
    await api.storeNow.save({ id: 'read-ahead-ui', name: 'Read-ahead', tracks: [0, 1, 2].map(track), index: 0 });
    await api.choosePlaylist('read-ahead-ui');
    check(requests.length === 0, 'selecting an inactive playlist does not start read-ahead or music');
    const before = opens().length; $('queue').querySelector('li.track').click();
    check(await until(() => opens().length > before), 'the actual playlist tap opens its first file'); ack();
    check(await until(() => api.aheadNow().sizeBytes === 2 && api.musicNow().has(urls[1]))
      && requests.join('|') === [urls[0], urls[1]].join('|') && api.aheadNow().ids.join('|') === urls[1],
      'an opened finite source prepares exactly its next file through the shared owner');
    check(!(await window.__played.recent()).some((row) => row.url === urls[1] || row.url === urls[2]),
      'prepared files are not played-before marks or History plays');
    let diskReads = 0;
    api.musicStore.read = async function(url) { if (url === urls[1]) { diskReads++; throw new Error('A completed prepared file needs no second disk read'); } return read.call(this, url); };
    const nextBefore = opens().length; $('next').click();
    check(await until(() => opens().length > nextBefore) && new Uint8Array(opens().at(-1).bytes)[0] === 21
      && diskReads === 0 && requests.filter((url) => url === urls[1]).length === 1,
      'actual Next consumes the prepared transferable buffer without another disk or network read');
    api.musicStore.read = read; ack();
    check(await until(() => api.aheadNow().sizeBytes === 2 && api.aheadNow().ids.join('|') === urls[2]),
      'the new actual file refreshes the single finite-source successor');
    api.enqueueTrack(track(3)); api.enqueueTrack(track(3)); api.enqueueTrack(track(4)); api.enqueueTrack(track(5));
    check(await until(() => api.aheadNow().sizeBytes === 6)
      && api.aheadNow().ids.join('|') === [urls[3], urls[4], urls[5]].join('|'),
      'the three distinct pending FIFO files lead the source and duplicate requests share bytes');
    const oneBefore = opens().length; $('next').click();
    check(await until(() => opens().length > oneBefore && api.actualNow().url === urls[3]), 'Next chooses the first prepared manual occurrence'); ack();
    const firstRequest = api.scheduleNow().current.request.id, duplicateBefore = opens().length; $('next').click();
    check(await until(() => opens().length > duplicateBefore && api.actualNow().url === urls[3])
      && api.scheduleNow().current.request.id !== firstRequest && requests.filter((url) => url === urls[3]).length === 1,
      'duplicate manual occurrences remain independent while reusing one retained file'); ack();
    const backBefore = opens().length; $('prev').click();
    check(await until(() => opens().length > backBefore && api.actualNow().url === urls[1]),
      'Previous from manual playback restores the source anchor rather than an already consumed request'); ack();
    const commands = window.__toWorklet.length; api.clearPlaybackQueue();
    check(api.aheadNow().ids.length === 0 && api.aheadNow().sizeBytes === 0 && window.__toWorklet.length === commands,
      'Clear drops speculation while retaining the current decoder and sound ownership');

    api.enqueueTrack(track(6)); check(await until(() => releaseDelayed), 'the actual queue action can warm a stalled shared transfer');
    const delayedBefore = opens().length; $('next').click(); api.clearPlaybackQueue();
    const adopted = api.actualNow(); releaseDelayed();
    check(await until(() => opens().length > delayedBefore && opens().at(-1).name === 'delayed.mod')
      && api.sourceNow().kind === 'none' && api.actualNow() === adopted && new Uint8Array(opens().at(-1).bytes)[0] === 26,
      'Clear while a foreground selection adopts pending read-ahead preserves that selected load');
    check(!transferSignal.aborted && requests.filter((url) => url === urls[6]).length === 1,
      'cancelling a prepared waiter never aborts or duplicates the foreground shared transfer'); ack();
  } finally { api.musicStore.read = read; window.fetch = fetch; }
}
