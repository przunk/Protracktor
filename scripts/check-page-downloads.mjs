// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { Blob as HostBlob } from 'node:buffer';
import { inflateRawSync } from 'node:zlib';
import { zipFixture } from './fixtures/zip.mjs';

/** Real picker, individual actions and dataset commits through the application's DOM handlers. */
export default async function checkDownloads({ window, check }) {
  const { document } = window, api = window.__api, archive = window.__archive, $ = (id) => document.getElementById(id);
  const pause = (ms = 15) => new Promise((done) => setTimeout(done, ms));
  const until = async (predicate) => { for (let i = 0; i < 300; i++) { if (await predicate()) return true; await pause(); } return false; };
  const fetch = window.fetch, blob = window.Blob, decompress = window.DecompressionStream;
  const modland = 'https://modland.com/allmods.zip', favourites = 'https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/master/songdb/sources/site/modland_favourites.tsv';
  const asma = 'https://asma.atari.org/asmadb/asma.zip', lengths = 'https://www.hvsc.de/download/C64Music/DOCUMENTS/Songlengths.md5';
  const credits = 'https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/1bad3e8/tsv/pretty/md5/metadata.tsv';
  const music = 'https://example.test/downloads-live.mod';
  const bodies = new Map([
    [modland, zipFixture([{ name: 'allmods.txt', body: '123\tProtracker/DownloadArtist/one.mod\n' }])],
    [favourites, '\t\t\t\t\t\t\t\t\t\tProtracker/DownloadArtist/one.mod\n'],
    [asma, zipFixture([{ name: 'asma/Composers/Downloader/One.sap', body: 'SAP One' }])],
    [lengths, 'abcdef123456abcdef123456abcdef12=1:02\n'],
    [credits, 'abcdef123456\tDownload Artist\tGroup\tAlbum\t1999\n'],
  ]);
  const requests = []; let failAsma = true, holdCredits = true, holdModland = false, releaseCredits, releaseModland, creditsSignal, modlandSignal;
  window.fetch = (input, options = {}) => {
    const url = String(input); if (url === music) return Promise.resolve(new window.Response(new Uint8Array([11, 22, 33])));
    if (!bodies.has(url)) return fetch(input, options);
    requests.push(url);
    if (url === asma && failAsma) return Promise.resolve(new window.Response('', { status: 503 }));
    if (url === credits && holdCredits) { creditsSignal = options.signal; return new Promise((resolve) => { releaseCredits = () => resolve(new window.Response(bodies.get(url))); }); }
    if (url === modland && holdModland) { modlandSignal = options.signal; return new Promise((resolve) => { releaseModland = () => resolve(new window.Response(bodies.get(url))); }); }
    return Promise.resolve(new window.Response(bodies.get(url)));
  };
  // jsdom Blob lacks streams; Node20 lacks raw deflate. Keep these host adapters out of the app.
  window.Blob = HostBlob;
  window.DecompressionStream = class {
    constructor(format) {
      if (format !== 'deflate-raw') return new decompress(format);
      const parts = [];
      return new TransformStream({ transform(chunk) { parts.push(Buffer.from(chunk)); },
        flush(controller) { controller.enqueue(inflateRawSync(Buffer.concat(parts))); } });
    }
  };
  const owner = () => api.downloadsNow(), picker = () => $('download-picker-body');
  const inputs = () => [...picker().querySelectorAll('input')], buttons = () => [...picker().querySelectorAll('button')];
  const action = () => picker().querySelector('.download-actions button'), retry = () => buttons().at(-1);
  const openPicker = async () => {
    api.showPanel('browse'); await api.browseTo(['catalogues']); $('choose-downloads').click();
    return until(() => !$('downloads').hidden);
  };
  try {
    api.showPanel(null); await api.discardEdits(); api.clearPlaybackQueue();
    for (const prefix of ['modland:', 'modland-favourites:', 'lengths:', 'songdb:']) await api.catalogueStore.clear(prefix);
    await archive.forgetIndex('asma'); await api.musicNow().clear();
    api.setQueue([]); api.render();
    check(await until(() => !$('emptyplaylist').hidden && $('emptyaction').textContent === 'Get some music to browse'),
      'an empty unindexed playlist exposes the actual missing-data action');
    $('emptyaction').click();
    check(await until(() => !$('downloads').hidden) && inputs().every((input) => input.checked) && requests.length === 0,
      'the real empty-list action opens all missing choices without downloading anything');
    check(action().textContent === 'Download (47 MB)', 'the actual picker estimates only the five approved WEB downloads');
    const close = $('downloads').querySelector('[data-close]');
    action().focus();action().dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }));
    check(document.activeElement === close, 'Tab wraps from the last actual picker action to its Close control');
    close.focus();close.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }));
    check(document.activeElement === action(), 'Shift-Tab wraps from actual Close to the last enabled picker action');
    close.querySelector('path').dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
    check($('downloads').hidden && !owner().running && requests.length === 0,
      'tapping the actual Close icon dismisses the picker without starting work or changing storage');
    if (!$('downloads').hidden) close.click(); // Keep subsequent diagnostics independent of this assertion.


    await api.storeNow.save({ id: 'downloads-live', name: 'Downloads live', tracks: [{ url: music, name: 'Live music', file: 'downloads-live.mod' }], index: 0 });
    await api.choosePlaylist('downloads-live');
    if ($('shuffle').getAttribute('aria-pressed') === 'true') $('shuffle').click();
    for (let i = 0; i < 3 && $('repeat').getAttribute('aria-pressed') === 'true'; i++) $('repeat').click();
    const opens = () => window.__toWorklet.filter((message) => message.type === 'open');
    const count = opens().length; $('queue').querySelector('li.track').click();
    check(await until(() => opens().length > count), 'a real playlist tap starts music before grouped downloads');
    api.onWorklet({ type: 'ready', backends: 'openmpt:0.8.9;sc68:3.0.0b;asap:8.0.0;gme:0.6.5;sidplayfp:3.1.1;minimp3:ea99364;zxtune:none' });
    api.onWorklet({ type: 'opened', requestId: opens().at(-1).requestId, describe: 'title\tLive music\nartist\tTester', duration: 90, rate: 44100, subsongs: 1, current: 0, canSeek: true });
    const held = { context: api.planNow().context, editor: api.editorNow(), schedule: api.scheduleNow(), commands: window.__toWorklet.length, audio: api.contextNow() };
    check(await openPicker(), 'Online catalogues exposes the same picker through its actual labelled action');
    const controls = inputs(), button = action(); button.click();
    const enteredCredits = await until(() => !!releaseCredits);
    check(enteredCredits && owner().running && controls.every((input) => input.disabled),
      'the real Download action freezes selection and reaches the final requested step');
    if (!enteredCredits) throw new Error('The actual Download action did not reach its requested steps.');
    check(requests.join('|') === [modland, favourites, asma, lengths, credits].join('|'),
      'actual requests follow the native order and continue independent steps after an ASMA failure');
    check(picker().querySelectorAll('input').length === 3 && inputs().every((input, i) => input === controls[i]) && action() === button,
      'byte/step progress retains the same row controls and primary button');
    check(owner().state.rows.map((row) => row.status).join(',') === 'done,done,failed,done,running',
      'the live picker reports successful, failed and current substeps separately');
    button.focus(); document.querySelector('input[name="language"][value="pl"]').click();
    check(action() === button && document.activeElement === button && inputs().every((input) => input.disabled)
      && $('downloads-title').textContent === 'Pobieranie', 'live Polish presentation preserves download controls, focus and locks');
    document.querySelector('input[name="language"][value="en"]').click();
    action().click(); const stopped = await until(() => !owner().running);
    check(stopped, 'actual Stop settles the blocked job within the host guard');
    if (!stopped) throw new Error('The actual Stop action left the grouped download running.');
    await owner().completion;
    check(!owner().running && owner().state.phase === 'stopped' && creditsSignal.aborted && $('downloads').hidden,
      'the actual Stop cancels its blocked waiter, clears busy state and closes the finished picker');
    check((await archive.meta('modland'))?.tracks === 1 && (await archive.songLengthsMeta())?.tunes === 1
      && (await api.catalogueStore.get('modland-favourites:meta'))?.listed === 1,
      'Stop retains the committed index, favourites and SID durations');
    check(!await archive.songMetadataMeta() && !await archive.meta('asma') && await until(() => !$('snackbar').hidden && $('snacktext').textContent.includes('Downloads stopped')),
      'Stop does not claim unfinished metadata or failed ASMA and gives explicit partial feedback');
    releaseCredits(); await pause();
    check(!await archive.songMetadataMeta(), 'the cancelled transfer cannot later restore an unfinished dataset');
    failAsma = false; holdCredits = false; const beforeRetry = requests.length;
    check(await openPicker() && !inputs()[0].checked && inputs()[1].checked && inputs()[2].checked && !retry().hidden,
      'reopening selects missing datasets and exposes Retry for only the unfinished substeps');
    retry().click(); await owner().completion;
    check(requests.slice(beforeRetry).join('|') === [asma, credits].join('|') && owner().state.rows.every((row) => row.status === 'done'),
      'the real Retry fetches ASMA and credits without repeating the retained index, favourites or SID durations');
    check((await archive.songMetadataMeta())?.tunes === 1 && api.asmaNow().state.bytes > 0 && $('downloads').hidden,
      'Retry publishes its final datasets and closes the successful picker');

    holdModland = true;
    check(await openPicker() && inputs().every((input) => !input.checked) && action().disabled,
      'a fully held library defaults to no refresh, with a visibly disabled Download');
    inputs()[0].click(); action().click();
    check(await until(() => !!releaseModland) && owner().running, 'an explicit refresh starts only its chosen catalogue group');
    $('downloads').querySelector('[data-close]').click();
    check($('downloads').hidden && owner().running && !modlandSignal.aborted, 'dismissal leaves the actual background download running');
    check(await until(() => !$('browse').hidden && document.activeElement === $('choose-downloads'))
      && $('browsetitle').textContent === 'Online catalogues', 'Close restores its originating catalogue view and labelled opener focus');
    check(await openPicker() && inputs()[0].checked && inputs().every((input) => input.disabled),
      'reopening a running operation restores its original frozen selection');
    window.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    check($('downloads').hidden && owner().running && !modlandSignal.aborted,
      'the actual Escape key dismisses presentation without stopping the selected run');
    check(await until(() => !$('browse').hidden && document.activeElement === $('choose-downloads')), 'Escape restores the originating catalogue view and opener focus');
    check(await openPicker(), 'a download dismissed with Escape can be inspected again');
    releaseModland(); await owner().completion;
    check(!owner().running && owner().busy.length === 0 && (await archive.meta('modland'))?.tracks === 1,
      'a dismissed and reopened refresh still commits and releases its locks');
    check(api.planNow().context === held.context && api.editorNow() === held.editor && api.scheduleNow() === held.schedule
      && api.contextNow() === held.audio && window.__toWorklet.length === held.commands,
      'all actual download actions preserve the live source, manual FIFO, editor, audio context and decoder commands');
    // A local capability rebuild is not a newer user-selected catalogue installation.
    holdModland = true; releaseModland = null;
    const older = archive.toRecords('90\tProtracker/PreviousArtist/old.mod\n', 'modland', () => true);
    await api.catalogueStore.replacePrefix('modland:', [...older.records, { key: 'modland:meta', tracks: 1,
      total: 1, buckets: 1, formats: 1, complete: true, fingerprint: 'older-native', fixtureTag: 'compatibility-held' }]);
    const replace = api.catalogueStore.replacePrefix; let releaseCompatibility;
    api.catalogueStore.replacePrefix = function(prefix, records, options) {
      if (prefix === 'modland:' && options?.expected && records.some((row) => row.key === 'modland:meta'
          && row.fixtureTag === 'compatibility-held' && row.fingerprint !== 'older-native'))
        return new Promise((resolve, reject) => { releaseCompatibility = () => replace.call(this, prefix, records, options).then(resolve, reject); });
      return replace.call(this, prefix, records, options);
    };
    try {
      api.onWorklet({ type: 'ready', backends: 'openmpt:0.8.9;sc68:3.0.0b;asap:8.0.0;gme:0.6.5;sidplayfp:3.1.1;minimp3:ea99364;zxtune:none' });
      check(await until(() => !!releaseCompatibility), 'a real engine-ready event can be rebuilding an older retained index');
      check(await openPicker(), 'the picker stays accessible while local compatibility work is pending');
      for (const input of inputs()) if (input.checked) input.click();
      inputs()[0].click(); const beforeRebuild = requests.length; action().click();
      await pause(100);
      check(owner().running && requests.length === beforeRebuild,
        'explicit index refresh waits for its local compatibility predecessor before capturing the old installation');
      await releaseCompatibility();
      check(await until(() => !!releaseModland), 'the selected index transfer starts after its compatibility predecessor finishes');
      releaseModland(); await owner().completion;
      check(owner().state.rows.every((row) => row.status === 'done')
        && (await api.catalogueStore.get('modland:tracks:Protracker/DownloadArtist'))?.tracks.length === 1
        && !await api.catalogueStore.get('modland:tracks:Protracker/PreviousArtist'),
        'a finishing compatibility rebuild cannot falsely invalidate the subsequent explicit index download');
      await replace.call(api.catalogueStore, 'modland:', [...older.records, { key: 'modland:meta', tracks: 1,
        total: 1, buckets: 1, formats: 1, complete: true, fingerprint: 'older-native', fixtureTag: 'compatibility-held' }]);
      releaseCompatibility = null;
      api.onWorklet({ type: 'ready', backends: 'openmpt:0.8.9;sc68:3.0.0b;asap:8.0.0;gme:0.6.5;sidplayfp:3.1.1;minimp3:ea99364;zxtune:none' });
      check(await until(() => !!releaseCompatibility) && await openPicker(),
        'another native-style compatibility predecessor can remain pending when the picker reopens');
      for (const input of inputs()) if (input.checked) input.click();
      inputs()[0].click(); const beforeCancel = requests.length; action().click(); await pause(100); action().click();
      await owner().completion;
      check(owner().state.phase === 'stopped' && !owner().running && owner().busy.length === 0 && requests.length === beforeCancel,
        'actual Stop cancels setup waiting without issuing the index or favourites requests');
      await releaseCompatibility(); await pause();
      check((await archive.meta('modland'))?.fingerprint !== 'older-native' && requests.length === beforeCancel,
        'cancelling one download waiter leaves the shared compatibility rebuild usable and starts no late download');
    } finally { releaseCompatibility?.(); api.catalogueStore.replacePrefix = replace; }
    check(!window.__browserErrors.some((error) => error.type === 'unhandled exception'), 'download gestures leave no uncaught UI exceptions');
  } finally {
    owner().stop(); releaseCredits?.(); releaseModland?.();
    window.fetch = fetch; window.Blob = blob; window.DecompressionStream = decompress;
  }
}
