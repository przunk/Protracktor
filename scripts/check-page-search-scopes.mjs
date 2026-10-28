// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
export default async function checkSearchScopes({ window, check }) {
  const { document } = window, api = window.__api;
  const $ = (id) => document.getElementById(id), store = api.catalogueStore;
  const settle = (ms = 10) => new Promise((resolve) => setTimeout(resolve, ms));
  const until = async (condition) => { for (let i = 0; i < 300; i++) { if (condition()) return true; await settle(); } return false; };
  const scope = (key) => document.querySelector(`[data-search-scope="${key}"]`);
  const input = $('browsesearch'), type = (query) => { input.value = query; input.dispatchEvent(new window.Event('input', { bubbles: true })); };
  const submit = async (query) => { type(query); $('browsesubmit').click(); return until(() => !api.searchNow().loading && !!api.searchNow().result); };
  const tracks = () => [...$('browselist').querySelectorAll('.btrack')];
  const saved = api.storeNow, reader = api.searchLibraryNow.catalogues;
  const archive = window.__archive;
  const modland = ['Protracker/Scope Author/scope alpha.mod', 'Protracker/Scope Author/scope beta.mod'];
  await store.clear('modland:'); await store.clear('asma:');
  for (const [source, paths] of [['modland', modland], ['asma', ['Composers/Scope Author/scope gamma.sap']]]) {
    const parsed = archive.toRecords(paths.map((path) => `1\t${path}`).join('\n'), source);
    await store.putAll([...parsed.records, { key: `${source}:meta`, tracks: parsed.tracks, total: parsed.total,
      complete: true, platformCounts: parsed.platformCounts, formats: parsed.formats }]);
  }
  const alpha = archive.urlFor('Protracker', 'Scope Author', 'scope alpha.mod', 'modland');
  await saved.save({ id: 'scope-z', name: 'Other saved list', tracks: [{ url: alpha, name: 'Scope older alpha', file: 'scope alpha.mod' }] });
  await settle(2);
  await saved.save({ id: 'scope-a', name: 'Saved elsewhere', tracks: [
    { url: alpha, name: 'Scope known alpha', file: 'scope alpha.mod' },
    { url: 'https://example.test/scope-saved.mod', name: 'Scope saved only', file: 'scope-saved.mod' },
    { url: 'content://scope/missing.mod', name: 'Scope missing copy', file: 'scope missing.mod' },
  ] });
  let searches = 0;
  api.searchLibraryNow.catalogues = (...args) => { searches++; return reader(...args); };
  try {
    api.showPanel('browse'); await api.browseTo([]);
    [...$('browselist').children].find((row) => row.querySelector('.bname')?.textContent === 'Search').click();
    check(await until(() => !$('search-scopes').hidden), 'actual Search entry shows scope controls without submitting');
    if (api.searchNow().scope.kind !== 'everywhere') scope(api.searchNow().scope.kind).click();
    const before = searches, beforePlan = api.planNow(), beforeCommands = window.__toWorklet.length;
    check(await submit('scope'), 'actual explicit Search reads all saved playlists and catalogue indexes');
    check(api.searchNow().result.tracks.length === 5 && tracks().length === 4,
      'Everywhere deduplicates saved/catalogue identity while retaining a visible unavailable local copy');
    check(api.searchNow().result.tracks.find((row) => row.url === alpha)?.name === 'Scope known alpha',
      'the newest saved copy wins the same catalogue URL even outside the selected playlist');
    check($('browselist').querySelector('.gone')?.textContent.includes("phone's copy"),
      'a saved local reference without held bytes explains its unavailability instead of trying a network request');
    scope('playlists').click();
    check(api.searchNow().result === null && searches === before + 1 && input.value === 'scope',
      'changing scope kind clears stale rows, retains text and does not auto-search');
    check(await submit('scope') && api.searchNow().result.tracks.length === 3 && searches === before + 1,
      'Saved playlists searches all saved references without reading catalogue results');
    scope('online').click();
    check(await until(() => !scope('catalogue:asma').disabled), 'known archive chips become usable through storage availability');
    scope('catalogue:asma').click(); const selectedWithoutSearch = searches;
    check(await submit('scope') && api.searchNow().result.tracks.length === 1
      && api.searchNow().result.tracks[0].url.startsWith('https://asma.atari.org/'), 'Online ASMA excludes saved copies and Modland');
    check(input.getAttribute('aria-label').includes('ASMA') && scope('catalogue:asma').getAttribute('aria-pressed') === 'true',
      'selected archive scope has an accessible field label and pressed state');
    check(searches === selectedWithoutSearch + 1, 'ticking an archive did not issue a hidden query');
    check(await submit('') && api.searchNow().result.tracks.length === 1, 'an actual blank submission lists the chosen indexed scope');
    check(await submit('g') && api.searchNow().result.tracks.length === 1, 'one-character submissions are accepted');
    scope('platform').click();
    check(await until(() => !scope('platform:atari-8bit').disabled), 'By platform can be chosen and ASMA contributes Atari 8-bit availability');
    scope('platform:atari-8bit').click();
    check(await submit('') && api.searchNow().result.tracks.length === 1
      && api.searchNow().result.tracks[0].url.startsWith('https://asma.atari.org/'), 'platform input narrows both saved and catalogue results');
    const old = api.searchNow().result, calls = searches;
    scope('platform:amiga').click();
    check(api.searchNow().result === old && searches === calls, 'adding another platform retains the producing answer until explicit Search');
    $('browseback').click(); await settle();
    check(api.searchNow().scope.kind === 'everywhere' && $('browsetitle').textContent === 'Search'
      && api.searchNow().result === null, 'first Back widens a narrowed search without leaving it');
    type('scope'); $('browseback').click(); await settle();
    check($('browsetitle').textContent === 'Browse' && input.value === 'scope', 'second Back leaves Everywhere while retaining typed text');
    check(api.planNow().context === beforePlan.context && api.planNow().schedule === beforePlan.schedule
      && window.__toWorklet.length === beforeCommands, 'scope selection, queries and Back never issue playback commands');

    await api.browseTo(['modland', 'Protracker', 'Scope Author']);
    $('browseback').click();
    check(await until(() => $('browsetitle').textContent === 'Protracker') && input.value === 'scope',
      'folder Back climbs one level without consuming retained Search text');
    await api.browseTo([]);

    [...$('browselist').children].find((row) => row.querySelector('.bname')?.textContent === 'Search').click();
    scope('online').click(); scope('catalogue:asma').click(); await submit('scope');
    const producing = api.searchNow().result, retainedRow = tracks()[0];
    scope('catalogue:modland').click(); type('not yet submitted');
    check(tracks()[0] === retainedRow && retainedRow.isConnected,
      'pending text and same-kind chip edits preserve producing DOM rows instead of rebuilding the list');
    const retainedNote = $('browsenote').firstChild; type('another unsubmitted phrase');
    check($('browsenote').firstChild === retainedNote, 'unchanged pending-result notes retain their text node instead of repeatedly updating the live region');
    tracks()[0].click(); await settle(100);
    const context = api.planNow().context, route = context.route;
    check(route.query === 'scope' && route.searchScope.kind === 'online' && route.searchScope.ids.join() === 'asma',
      'playing retained results captures their producing query/scope rather than pending text or chips');
    api.showPanel(null); const commands = window.__toWorklet.length;
    $('showqueue').click(); $('queue-source').click();
    check(await until(() => !$('browse').hidden && input.value === 'scope' && api.searchNow().scope.ids.join() === 'asma'),
      'Queue Go to source restores the actual producing Search scope and query');
    check(api.planNow().context === context && window.__toWorklet.length === commands
      && tracks().map((row) => row.dataset.url).join() === producing.tracks.map((row) => row.url).join(),
      'Search-source return restores membership without opening a decoder or changing its context');
    $('showqueue').click(); $('queue-close').click(); type('gamma');
    check(api.searchNow().query === 'gamma', 'Search remains editable after opening and closing Queue over it');

    let release, entered;
    const gate = new Promise((resolve) => { release = resolve; }), started = new Promise((resolve) => { entered = resolve; });
    api.searchLibraryNow.catalogues = (query, ...args) => {
      if (query === 'slow') { entered(); return gate; }
      return reader(query, ...args);
    };
    type('slow'); $('browsesubmit').click();
    await Promise.race([started, settle(3000).then(() => { throw new Error('The actual Search did not start its controlled read'); })]);
    await submit('scope'); const latest = api.searchNow().result;
    release({ rows: [{ url: 'https://example.test/obsolete.mod', name: 'Obsolete', file: 'obsolete.mod' }], total: 1, held: [] }); await settle();
    check(api.searchNow().result === latest && !tracks().some((row) => row.dataset.url.includes('obsolete')),
      'an uncooperative old search cannot overwrite a newer explicit submission');
    api.searchLibraryNow.catalogues = async () => { throw new Error('Search storage refused'); };
    type('retry'); $('browsesubmit').click();
    check(await until(() => !!api.searchNow().failed) && $('browsenote').textContent.includes('Search storage refused'),
      'a current storage refusal clears loading and explains the failure');
    api.searchLibraryNow.catalogues = reader;
    check(await submit('scope'), 'the same explicit Search control retries after a storage refusal');

    const get = store.get;
    try {
      store.get = (key) => key === 'asma:meta' ? Promise.reject(new Error('scope count refused')) : get.call(store, key);
      scope('platform').click();
      check(await until(() => api.searchAvailabilityNow().failed) && !scope('retry').hidden,
        'scope availability refusal has its own labelled Retry without pretending data is missing');
      store.get = get; scope('retry').click();
      check(await until(() => !api.searchAvailabilityNow().loading && !api.searchAvailabilityNow().failed),
        'actual scope Retry refreshes verified availability');
    } finally { store.get = get; }
    scope('playlists').click();
    await store.clear('modland:'); await store.clear('asma:');
    check(await submit('scope') && api.searchNow().result.tracks.length === 3,
      'saved-reference search remains available with no catalogue indexes');

    const raw = [85, 77, 65, 78, 85, 65, 76];
    api.receive({ queue: [{ url: 'content://scope/held.mod', title: 'Scope held bytes', file: 'held.mod',
      data: window.btoa(String.fromCharCode(...raw)) }], index: 0 });
    await settle(650);
    api.showPanel('browse'); await api.browseTo(['search']);
    if (api.searchNow().scope.kind !== 'playlists') scope('playlists').click();
    check(await submit('Scope held bytes') && tracks().length === 1,
      'saved-reference Search recovers bytes still held by the phone editor/context');
    tracks()[0].click(); await settle(100);
    check(api.planNow().context.current?.data?.byteLength === raw.length
      && Array.from(new Uint8Array(api.planNow().context.current.data)).join() === raw.join(),
      'playing a saved local result preserves its held buffer across the actual worklet transfer');
    check((await saved.get('phone')).tracks.every((track) => !('data' in track)),
      'Search does not persist the phone handoff bytes while retaining their in-session playback');
  } finally {
    api.searchLibraryNow.catalogues = reader; api.showPanel(null);
    await saved.remove('scope-a'); await saved.remove('scope-z');
  }
}
