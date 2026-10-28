// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { readPlaylistFile, playlistNameIn } from '../web/src/playlist-file.js';

/** Actual Import input, FileReader, Export menus and IndexedDB; picker chrome is not simulated. */
export default async function checkPlaylistTransfer({ window, check }) {
  const { document } = window, $ = (id) => document.getElementById(id), api = window.__api, store = api.storeNow;
  const settle = (ms = 20) => new Promise((resolve) => setTimeout(resolve, ms));
  const until = async (condition) => { for (let i = 0; i < 250; i++) { if (await condition()) return true; await settle(); } return false; };
  const readText = (file) => new Promise((resolve, reject) => {
    const reader = new window.FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsText(file);
  });
  const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; };
  const rows = () => [...$('queue').querySelectorAll('li.track')];
  const urls = (tracks) => tracks.map((row) => row.url).join();
  const input = $('playlist-file'), downloads = [], clicked = [];
  const createUrl = window.URL.createObjectURL, revokeUrl = window.URL.revokeObjectURL, anchorClick = window.HTMLAnchorElement.prototype.click;
  window.URL.createObjectURL = (file) => { downloads.push(file); return `blob:playlist-${downloads.length}`; };
  window.URL.revokeObjectURL = () => {};
  window.HTMLAnchorElement.prototype.click = function () { if (this.download) clicked.push(this.download); else anchorClick.call(this); };
  const pick = (file) => {
    Object.defineProperty(input, 'files', { configurable: true, value: file ? [file] : [] });
    input.dispatchEvent(new window.Event('change', { bubbles: true }));
  };
  const file = (text, name = 'Input.m3u8') => new window.File([text], name, { type: 'audio/x-mpegurl' });
  const open = async () => { api.showPanel(null); $('playlistchip').click(); return until(() => !$('playlists').hidden && [...$('playlistlist').querySelectorAll('.pname')].some((node) => node.textContent === 'Transfer/A?')); };
  const menu = (name) => [...$('playlistlist').children].find((row) => row.querySelector('.pname')?.textContent === name)?.querySelector('.pmenu').click();
  const action = (name) => [...$('menu').querySelectorAll('button')].find((button) => button.textContent === window.__i18n.t(name));
  const exportList = async (name) => { await open(); menu(name); action('Export').click(); };
  const imports = () => store.all();
  const notice = () => $('snacktext').textContent;
  const tracks = [0, 1, 2].map((i) => ({ url: `https://example.test/transfer/${i}.mod`, name: `Transfer ${i}`, file: `${i}.mod`, author: 'Artist', size: 12 + i }));
  await api.discardEdits(); api.showPanel(null);
  await store.save({ id: 'transfer-a', name: 'Transfer/A?', tracks });
  await store.save({ id: 'transfer-b', name: 'Transfer B', tracks: [tracks[2]] });
  await store.save({ id: 'transfer-c', name: 'Transfer C', tracks: [tracks[0]] });
  await store.save({ id: 'transfer-empty', name: 'Transfer empty', tracks: [] });
  await api.choosePlaylist('transfer-a'); rows()[1].click(); await settle(80);
  api.onWorklet({ type: 'opened', describe: 'title\tTransfer playing\nformat\tProTracker MOD', duration: 90, subsongs: 1, current: 0, canSeek: true, rate: 44100 });
  api.onWorklet({ type: 'position', seconds: 17, subsong: 0 }); api.enqueueTrack(tracks[0]);
  rows()[2].querySelector('.rowmenu').click(); action('Remove from this playlist').click();
  const plan = api.planNow(), draft = api.editorNow(), commands = window.__toWorklet.length;
  try {
    await exportList('Transfer/A?'); check(await until(() => downloads.length === 1), 'actual Export menu prepares a downloadable M3U8 file');
    const text = await readText(downloads[0]);
    check(clicked[0] === 'Transfer_A_.m3u8' && downloads[0].type.startsWith('audio/x-mpegurl')
      && playlistNameIn(text) === 'Transfer/A?', 'export sanitizes only the download filename while retaining the playlist name and MIME type');
    check(readPlaylistFile(text).map((entry) => entry.id).join() === urls(tracks.slice(0, 2))
      && (await store.get('transfer-a')).tracks.length === 3, 'actual active Export uses the unsaved draft without saving it');
    check(api.editorNow() === draft && api.planNow().context === plan.context && api.scheduleNow() === plan.schedule
      && api.dirtyNow() && window.__toWorklet.length === commands && $('elapsed').textContent === '0:17',
      'Export preserves independent playback, position, requests and dirty editor snapshot');
    let picks = 0; const clickInput = input.click; input.click = () => picks++;
    $('playlist-import').click(); input.click = clickInput;
    check(picks === 1 && $('playlist-import').querySelector('svg'), 'the labelled Import action reaches the native file-input picker');
    const before = (await imports()).length;
    pick(file(`#EXTM3U\n#PLAYLIST:Żółć 🎵\n${tracks[0].url}\n${tracks[0].url}\n${tracks[2].url}\nforeign/missing.mod\n`));
    check(await until(async () => (await imports()).some((row) => row.name === 'Żółć 🎵')), 'actual FileReader import creates a new named playlist');
    const imported = (await imports()).find((row) => row.name === 'Żółć 🎵');
    check((await imports()).length === before + 1 && urls(imported.tracks) === urls([tracks[0], tracks[2]]),
      'import keeps first occurrence order and deduplicates only the new playlist');
    check(notice().includes('3 of 4') && notice().includes('not available'), 'partial import reports found offerings before deduplication and explains missing local references');
    check(api.editorNow() === draft && api.planNow().context === plan.context && api.scheduleNow() === plan.schedule
      && api.dirtyNow() && window.__toWorklet.length === commands && $('unsaved').hidden,
      'Import neither selects the new playlist nor touches unsaved edits or playback');
    check(!input.value, 'the file input resets so the same file can be imported again');

    pick(file('foreign/missing.mod', 'Only missing.m3u'));
    check(await until(async () => (await imports()).some((row) => row.name === 'Only missing')), 'all-unresolved input still creates its own empty playlist');
    check((await imports()).find((row) => row.name === 'Only missing').tracks.length === 0 && notice().includes('0 of 1'),
      'the all-unresolved result has an honest count and uses the filename fallback');
    const noEntriesCount = (await imports()).length;
    pick(file('#EXTM3U\n# comment'));
    check(await until(() => notice() === 'The file contains no tracks.') && (await imports()).length === noEntriesCount,
      'comment-only files explain no entries without creating a playlist');
    pick(file(' \n'));
    check(await until(() => notice() === 'Could not read the playlist file.'), 'blank input has a distinct unreadable-file notice');
    const failedFile = file('ignored'); Object.defineProperty(failedFile, 'text', { value: () => Promise.reject(new Error('controlled file refusal')) });
    pick(failedFile);
    check(await until(() => notice().includes('controlled file refusal')), 'a file read refusal is visible through the actual input handler');
    pick(null); await settle();
    check((await imports()).length === noEntriesCount, 'picker cancellation and refused input write nothing');

    const asmaName = 'ASMA import';
    pick(file('#PLAYLIST:ASMA import\n#PROTRACKTOR:id=asma://Composers/Rogers_Charles/Quest_For_Cool.sap\n#EXTINF:-1,Charles - Quest\nComposers/Rogers_Charles/Quest_For_Cool.sap'));
    check(await until(async () => (await imports()).some((row) => row.name === asmaName))
      && (await imports()).find((row) => row.name === asmaName).tracks[0]?.url === 'https://asma.atari.org/asma/Composers/Rogers_Charles/Quest_For_Cool.sap',
      'Android internal ASMA references become canonical playable browser URLs without a catalogue download');
    check((await imports()).find((row) => row.name === asmaName).tracks[0]?.meta === 'ASMA/Composers/Rogers_Charles',
      'imported archive metadata keeps the browser author-folder navigation contract');

    await api.discardEdits();
    const get = store.get, gate = deferred();
    store.get = (id) => id === 'transfer-b' ? gate.promise : get.call(store, id);
    try {
      await exportList('Transfer B');
      await api.choosePlaylist('transfer-c');
      gate.resolve({ id: 'transfer-b', name: 'Changed later', tracks: [tracks[2]] });
      check(await until(() => downloads.length === 2), 'an unselected playlist Export waits for its own stored content');
      const other = await readText(downloads[1]);
      check(playlistNameIn(other) === 'Transfer B' && readPlaylistFile(other)[0].id === tracks[2].url
        && api.editorNow().id === 'transfer-c', 'a suspended export keeps its initial list/name without adopting later editor selection');
    } finally { store.get = get; }
    await exportList('Transfer empty');
    check(await until(() => notice() === 'There is nothing in this playlist.') && downloads.length === 2,
      'empty Export explains refusal without creating a download');
    window.URL.createObjectURL = () => { throw new Error('controlled download refusal'); };
    await exportList('Transfer B');
    check(await until(() => notice().includes('controlled download refusal')), 'download preparation failure is visible through the actual Export action');
    window.URL.createObjectURL = (blob) => { downloads.push(blob); return `blob:playlist-${downloads.length}`; };

    const random = window.crypto.getRandomValues;
    window.crypto.getRandomValues = (values) => { values.fill(0); return values; };
    try {
      pick(file('#PLAYLIST:Collision first\n' + tracks[0].url));
      check(await until(async () => (await imports()).some((row) => row.name === 'Collision first')), 'first fresh imported identity commits');
      pick(file('#PLAYLIST:Collision second\n' + tracks[2].url));
      check(await until(() => notice().startsWith('Could not import the playlist:'))
        && (await imports()).some((row) => row.name === 'Collision first') && !(await imports()).some((row) => row.name === 'Collision second'),
        'a colliding import identity reports failure and cannot overwrite an existing playlist');
    } finally { window.crypto.getRandomValues = random; }
    const originalCreate = store.create;
    store.create = async () => { throw new Error('controlled storage refusal'); };
    try {
      pick(file(tracks[0].url));
      check(await until(() => notice().includes('controlled storage refusal')), 'a failed database import does not claim success');
    } finally { store.create = originalCreate; }
    await open();
    const allBeforeRefresh = store.all;
    store.create = async (record) => {
      const saved = await originalCreate(record);
      store.all = async () => { throw new Error('controlled refresh refusal'); };
      return saved;
    };
    try {
      pick(file('#PLAYLIST:Committed before refresh\n' + tracks[0].url));
      check(await until(() => notice().startsWith('Imported') && notice().includes('controlled refresh refusal'))
        && (await allBeforeRefresh()).some((row) => row.name === 'Committed before refresh'),
        'a failed list refresh reports the committed import and its separate refresh failure honestly');
    } finally { store.create = originalCreate; store.all = allBeforeRefresh; }

    const delayed = file('ignored'), readGate = deferred();
    Object.defineProperty(delayed, 'text', { value: () => readGate.promise });
    pick(delayed); await api.choosePlaylist('transfer-b'); api.showPanel('browse');
    const laterEditor = api.editorNow(), laterPlan = api.planNow();
    readGate.resolve('#PLAYLIST:Late input\n' + tracks[0].url);
    check(await until(async () => (await imports()).some((row) => row.name === 'Late input'))
      && api.editorNow() === laterEditor && api.planNow().context === laterPlan.context
      && api.scheduleNow() === laterPlan.schedule && !$('browse').hidden,
      'a late file read completes its own import without stealing a newer editor or Browse view');

    const raw = [85, 77, 65, 78, 85, 65, 76], localUrl = 'content://transfer/held.mod';
    api.receive({ queue: [{ url: localUrl, title: 'Held local', file: 'held.mod', data: window.btoa(String.fromCharCode(...raw)) }], index: 0 });
    await settle(650);
    await open(); menu('From the phone');
    check([...$('menu').querySelectorAll('button')].map((row) => row.textContent).join() === 'Export',
      'protected From the phone gains read-only Export without rename/delete ownership');
    pick(file('#PLAYLIST:Held import\n#PROTRACKTOR:size=7\nforeign/held.mod'));
    check(await until(async () => (await imports()).some((row) => row.name === 'Held import')), 'a foreign local reference resolves matching filename/size from actual held phone bytes');
    const heldList = (await imports()).find((row) => row.name === 'Held import');
    check(heldList.tracks[0]?.url === localUrl && !('data' in heldList.tracks[0]), 'an imported local reference persists no audio bytes');
    api.receive({ queue: [{ url: tracks[0].url, title: tracks[0].name }], index: 0 }); await settle(650);
    await api.choosePlaylist(heldList.id);
    check(api.editorNow().order.tracks[0]?.data?.byteLength === raw.length && !rows()[0].classList.contains('local'),
      'choosing the imported list hydrates its transient byte owner after the phone list was replaced');
    rows()[0].click(); await settle(80);
    check(api.planNow().context.current?.data?.byteLength === raw.length
      && Array.from(new Uint8Array(api.planNow().context.current.data)).join() === raw.join(),
      'playing an imported local reference retains bytes across the real worklet transfer');
    api.playFromBrowse([tracks[2]], 0, 'browse'); await settle(80);
    $('nowkeep').click(); $('random-leave').click();
    check(api.dirtyNow() && api.editorNow().order.tracks.length === 2,
      'adding a remote tune edits the hydrated imported draft without losing its local reference');
    $('tab-save').click(); await until(() => !api.dirtyNow());
    check((await store.get(heldList.id)).tracks.length === 2 && (await store.get(heldList.id)).tracks.every((track) => !('data' in track)),
      'actual Save of an edited hydrated imported playlist still persists references only');
    await exportList('Held import');
    check(await until(() => downloads.length === 3) && !((await readText(downloads[2])).includes('data:'))
      && readPlaylistFile(await readText(downloads[2]))[0].size === '7',
      'held local export contains identity/path metadata rather than audio payload');
    await api.choosePlaylist('transfer-b'); api.dropImportedHeld();
    await api.choosePlaylist(heldList.id);
    const missingCommands = window.__toWorklet.length; rows()[0].click(); await settle();
    check(rows()[0].classList.contains('local') && !api.editorNow().order.tracks[0].data
      && window.__toWorklet.length === missingCommands,
      'with tab-only bytes forgotten, loading the saved local reference visibly disables it instead of fetching its URI');
    api.showPanel(null);
    window.__i18n.useLanguage('pl');
    pick(file('# comment'));
    check(await until(() => notice() === 'Plik nie zawiera utworów.'), 'file-transfer notices use the selected application language');
  } finally {
    window.__i18n.useLanguage('en'); window.URL.createObjectURL = createUrl; window.URL.revokeObjectURL = revokeUrl;
    window.HTMLAnchorElement.prototype.click = anchorClick; api.showPanel(null);
  }
}
