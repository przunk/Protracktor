// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native TracksView scenarios through real row, checkbox, Back, destination and share events. */
export default async function checkBrowseSelection({ window, check }) {
  const { document } = window, $ = (id) => document.getElementById(id), api = window.__api, store = api.storeNow;
  const settle = (ms = 20) => new Promise((resolve) => setTimeout(resolve, ms));
  const until = async (condition) => { for (let i = 0; i < 250; i++) { if (await condition()) return true; await settle(); } return false; };
  const deferred = () => { let resolve, reject; const promise = new Promise((done, failed) => { resolve = done; reject = failed; }); return { promise, resolve, reject }; };
  const rows = () => [...$('browselist').querySelectorAll('.btrack')];
  const urls = (tracks) => tracks.map((track) => track.url).join();
  const state = () => api.browseSelectionNow();
  const action = (name) => [...$('menu').querySelectorAll('button')].find((button) => button.textContent === window.__i18n.t(name));
  const key = (target, name) => target.dispatchEvent(new window.KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true }));
  const pointer = (target, type, x = 0, y = 0) => {
    const event = new window.MouseEvent(type, { clientX: x, clientY: y, button: 0, bubbles: true, cancelable: true });
    Object.defineProperty(event, 'pointerId', { value: 1 }); Object.defineProperty(event, 'pointerType', { value: 'touch' });
    target.dispatchEvent(event);
  };
  const hold = async (row) => {
    const text = row.querySelector('.btext'); pointer(text, 'pointerdown'); await settle(540);
    pointer(text, 'pointerup'); text.click(); await settle();
  };
  const select = (at) => { rows()[at].querySelector('.bmore').click(); action('Select').click(); };
  const folder = async () => { api.showPanel('browse'); await api.browseTo(['modland', 'Protracker', 'Selection']); return until(() => rows().length === 3); };
  const target = (name) => [...$('addtolist').children].find((row) => row.querySelector('.pname')?.textContent === name);
  const tracks = [0, 1, 2].map((i) => ({ url: `https://modland.com/pub/modules/Protracker/Selection/${i}.mod`, name: `${i}.mod`, file: `${i}.mod` }));
  await api.discardEdits(); api.showPanel(null);
  api.receive({ queue: [{ url: 'https://example.test/selection-source.mod', title: 'Independent source' }], index: 0 }); await settle(650);
  await store.save({ id: 'bulk-editor', name: 'Bulk current', tracks: [] });
  await store.save({ id: 'bulk-target', name: 'Bulk other', tracks: [] });
  await api.choosePlaylist('bulk-editor'); api.enqueueTrack(tracks[2]);
  const archive = window.__archive;
  await api.catalogueStore.replacePrefix('modland:', [...archive.toRecords('1\tProtracker/Selection/0.mod\n1\tProtracker/Selection/1.mod\n1\tProtracker/Selection/2.mod', 'modland', () => true).records,
    { key: 'modland:meta', tracks: 3, total: 3, complete: true }]);
  check(await folder(), 'catalogue folder exposes the same three selection inputs as native TracksView');
  const plan = api.planNow(), commands = window.__toWorklet.length, physical = rows();
  const headerHeight = window.getComputedStyle($('browse-selection-head')).height;
  await hold(rows()[2]);
  check(state().count === 1 && rows()[2].querySelector('.tick')?.checked && !$('selectbar').hidden,
    'the actual long press starts selection and consumes its generated click');
  check(rows().every((row, i) => row === physical[i]) && rows().every((row) => row.querySelector('.badd').hidden && row.querySelector('.bmore').hidden),
    'selection retains row nodes while hiding their contextual actions');
  check(headerHeight === '48px' && window.getComputedStyle($('browse-selection-head')).height === headerHeight
    && window.getComputedStyle(rows()[0].querySelector('.track-leading')).width === '40px',
    'the native-sized header/checkbox slot is reserved before and after ticking');
  rows()[0].querySelector('.btext').click();
  check(state().urls.join() === urls([tracks[0], tracks[2]]) && rows()[0].querySelector('.tick').checked,
    'further row taps toggle without playing and batch order follows the source');
  rows()[0].querySelector('.tick').click();
  check(state().count === 1 && !rows()[0].querySelector('.tick').checked, 'the actual checkbox click toggles once without bubbling into the row');
  rows()[0].querySelector('.tick').click();
  pointer(rows()[1].querySelector('.btext'), 'pointerdown'); pointer(document, 'pointermove', 250); pointer(document, 'pointerup', 250);
  check(api.planNow().context === plan.context && api.scheduleNow() === plan.schedule && window.__toWorklet.length === commands,
    'row/checkbox/long-press selection and a disabled swipe leave playback and pending requests untouched');
  $('browseback').click();
  check(state().count === 0 && $('browsetitle').textContent.includes('Selection') && !$('browse').hidden,
    'first actual Back clears selection without leaving the folder');
  select(1);
  const playAction = $('playpause').onclick; let shortcutCalls = 0;
  $('playpause').onclick = () => shortcutCalls++;
  try {
    $('browse-select-all').focus(); key($('browse-select-all'), ' '); $('browse-select-all').click();
    check(shortcutCalls === 0, 'Space on a focused selection button does not invoke the global audio shortcut');
    key(document.body, ' ');
    check(shortcutCalls === 1, 'Space outside an interactive control still reaches the global audio shortcut');
  } finally { $('playpause').onclick = playAction; }
  check(state().count === 3 && $('browse-select-all').textContent.includes('Select none'), 'actual Select all uses exactly the displayed playable folder');
  $('browse-select-all').click();
  check(state().count === 0 && $('browse-select-all').hidden, 'actual Select none exits selection instead of retaining a hidden mode');
  select(0); window.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
  check(state().count === 0 && !$('browse').hidden, 'Escape clears Browse selection before the global overlay handler');
  const text = rows()[0].querySelector('.btext'); text.focus(); key(text, 'ContextMenu'); action('Select').click();
  key(rows()[2].querySelector('.btext'), ' ');
  check(state().urls.join() === urls([tracks[0], tracks[2]]) && api.planNow().context === plan.context,
    'keyboard menu and Space reach the same selection operation without the audio shortcut');
  $('sel-add-current').click();
  check(state().count === 0 && urls(api.editorNow().order.tracks) === urls([tracks[0], tracks[2]]) && api.dirtyNow()
    && (await store.get('bulk-editor')).tracks.length === 0, 'actual Add current edits the selected draft in source order and waits for Save');
  check(api.planNow().context === plan.context && api.scheduleNow() === plan.schedule, 'batch Add current does not change the independent source or manual FIFO');
  select(1); rows()[0].querySelector('.btext').click(); $('sel-add').click();
  check(await until(() => !$('addto').hidden) && state().count === 0 && $('addtolist').firstElementChild.querySelector('.pname').textContent === 'Bulk current',
    'other-playlist Add captures then clears selection and prioritizes the selected destination');
  target('Bulk other').dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
  check(await until(async () => (await store.get('bulk-target')).tracks.length === 2)
    && urls((await store.get('bulk-target')).tracks) === urls([tracks[0], tracks[1]]) && !$('browse').hidden,
    'the actual keyboard destination action saves the ordered batch and returns to Browse');
  select(0); $('sel-add').click(); await until(() => !$('addto').hidden);
  $('addto').querySelector('[data-close]').click();
  check(!$('browse').hidden && state().count === 0, 'cancelling the destination chooser does not resurrect old selection');

  select(2); $('sel-add').click(); await until(() => !$('addto').hidden);
  const originalSave = store.save;
  store.save = async (record) => { if (record.id === 'bulk-target') throw new Error('bulk destination refused'); return originalSave(record); };
  try {
    target('Bulk other').click();
    check(await until(() => $('snacktext').textContent.includes('bulk destination refused')) && !$('addto').hidden
      && $('addto').getAttribute('aria-busy') === 'false', 'a refused batch write explains the failure and retains its destination snapshot for retry');
  } finally { store.save = originalSave; }
  target('Bulk other').click();
  check(await until(async () => (await store.get('bulk-target')).tracks.length === 3), 'retry after a destination refusal uses the same captured batch once');
  await until(() => !$('browse').hidden);

  select(0);
  const all = store.all, pendingRead = deferred();
  store.all = () => pendingRead.promise;
  $('sel-add').click(); await api.browseTo([]);
  pendingRead.resolve(await all()); store.all = all; await settle(80);
  check($('addto').hidden && $('browsetitle').textContent === 'Browse', 'a late destination read cannot reopen its chooser over another Browse level');
  await folder(); select(0);
  const refusedRead = deferred(), beforeRefusal = $('snacktext').textContent;
  store.all = () => refusedRead.promise;
  $('sel-add').click(); await api.browseTo([]); refusedRead.reject(new Error('obsolete destination refusal'));
  store.all = all; await settle(80);
  check($('snacktext').textContent === beforeRefusal && $('addto').hidden,
    'an obsolete destination refusal cannot replace feedback in a newer Browse visit');
  await folder(); select(2); rows()[0].querySelector('.btext').click();
  const prompt = window.prompt; window.prompt = () => 'Bulk new';
  try {
    $('sel-add').click(); await until(() => !$('addto').hidden); $('addto-new').click();
    check(await until(async () => (await store.all()).some((record) => record.name === 'Bulk new'))
      && urls((await store.all()).find((record) => record.name === 'Bulk new').tracks) === urls([tracks[0], tracks[2]]),
      'actual New playlist in the destination chooser commits the ordered batch under a fresh identity');
  } finally { window.prompt = prompt; }
  await until(() => !$('browse').hidden);

  await folder(); select(2); rows()[0].querySelector('.btext').click();
  const share = window.navigator.share, shared = [];
  window.navigator.share = async (value) => shared.push(value);
  try {
    $('sel-share').click();
    check(await until(() => shared.length === 1) && state().count === 0, 'actual batch Share captures then clears before opening the share adapter');
    const fragment = shared[0].url.split('#')[1];
    const content = await api.inflateFragment(fragment.slice('play:'.length));
    check(content.split('\n').join('|') === 'Protracker/Selection/0.mod|Protracker/Selection/2.mod'
      && !content.includes('data:'), 'batch sharing preserves source order and sends portable references without audio data');
  } finally { window.navigator.share = share; }

  await api.saveEdits();
  const raw = [85, 77, 65, 78, 85, 65, 76], localUrl = 'content://selection/held.mod';
  api.receive({ queue: [{ url: localUrl, title: 'Held batch', file: 'held.mod', data: window.btoa(String.fromCharCode(...raw)) }], index: 0 }); await settle(650);
  await api.choosePlaylist('bulk-editor'); api.showPanel('browse'); await api.browseTo(['search']);
  const input = $('browsesearch'); input.value = 'Held batch'; input.dispatchEvent(new window.Event('input', { bubbles: true })); $('browsesubmit').click();
  check(await until(() => rows().length === 1 && rows()[0].dataset.url === localUrl), 'Search exposes an actually held local input for the same selection machinery');
  select(0); $('sel-add-current').click();
  check(api.editorNow().order.tracks.some((track) => track.url === localUrl && track.data?.byteLength === raw.length)
    && !(await store.get('bulk-editor')).tracks.some((track) => track.url === localUrl),
    'batch Add current retains a local working buffer while persisted references remain unchanged before Save');
  $('tab-save').click(); await until(() => !api.dirtyNow());
  check((await store.get('bulk-editor')).tracks.every((track) => !('data' in track)), 'actual Save of a batch with local bytes stores only references');
  api.receive({ queue: [{ url: 'https://example.test/selection-replacement.mod', title: 'Replacement source' }], index: 0 }); await settle(650);
  await api.choosePlaylist('bulk-target');
  await api.choosePlaylist('bulk-editor');
  check(api.editorNow().order.tracks.find((track) => track.url === localUrl)?.data?.byteLength === raw.length,
    'saved local batch references rehydrate after switching editors without persisting the buffer');

  await store.save({ id: 'bulk-local-target', name: 'Bulk local destination', tracks: [] });
  api.dropImportedHeld();
  api.showPanel('browse'); await api.browseTo(['search']);
  input.value = 'Held batch'; input.dispatchEvent(new window.Event('input', { bubbles: true })); $('browsesubmit').click(); await until(() => rows().length === 1);
  select(0); $('sel-add').click(); await until(() => !$('addto').hidden);
  target('Bulk local destination').click();
  check(await until(async () => (await store.get('bulk-local-target')).tracks.length === 1)
    && !(await store.get('bulk-local-target')).tracks[0].data, 'batch other-playlist Add saves the local identity without its data');
  await until(() => !$('browse').hidden);
  await api.choosePlaylist('bulk-target');
  await api.choosePlaylist('bulk-local-target');
  check(api.editorNow().order.tracks[0].data?.byteLength === raw.length, 'other-playlist batch Add retains the same tab-only byte owner');

  const history = window.__played;
  await history.clear();
  for (const track of tracks) await history.record({ ...track, replayable: true });
  api.showPanel('browse'); await api.browseTo(['history']);
  check(await until(() => rows().length === 3), 'History uses the same three native selection inputs');
  await hold(rows()[0]); const selectedId = state().urls[0];
  await history.record({ ...tracks[0], replayable: true });
  check(await until(() => state().count === 1 && rows().some((row) => row.dataset.url === selectedId && row.querySelector('.tick')?.checked)),
    'same-owner live History refresh preserves selected identity across reordered row objects');
  $('showqueue').click();
  check(state().count === 1 && $('selectbar').hidden && !$('playback-panel').hidden, 'Queue hides the selection bar without destroying the underlying Browse selection');
  $('queue-close').click();
  check(!$('browse').hidden && state().count === 1 && !$('selectbar').hidden, 'closing Queue restores the same Browse selection owner');
  const phrase = $('historysearch'); phrase.value = '0.mod'; phrase.dispatchEvent(new window.Event('input', { bubbles: true }));
  check(await until(() => rows().length === 1 && state().count === 0), 'a new live History phrase retires selection from the previous result');
  rows()[0].querySelector('.bmore').click(); action('Select').click();
  window.__i18n.useLanguage('pl'); await api.renderBrowse();
  check($('browse-select-all').textContent.includes('Odznacz wszystkie')
    && rows()[0].querySelector('.tick').getAttribute('aria-label').startsWith('Zaznacz'), 'selection labels and checkbox descriptions follow Polish');
  window.__i18n.useLanguage('en');
  $('browseback').click(); $('browseback').click(); await settle();
  check($('browsetitle').textContent === 'Browse' && state().count === 0, 'Back after leaving selection returns from History to Browse normally');
  api.showPanel(null); await api.choosePlaylist('phone'); await folder(); select(0);
  check($('sel-add-current').hidden && !$('sel-add').hidden, 'protected From the phone offers only the other-playlist destination path for batch Add');
  $('sel-add').click(); await until(() => !$('addto').hidden);
  check(![...$('addtolist').querySelectorAll('.pname')].some((row) => row.textContent === 'From the phone'),
    'the batch destination chooser cannot write into the protected phone list');
  $('addto').querySelector('[data-close]').click(); api.showPanel(null);
}
