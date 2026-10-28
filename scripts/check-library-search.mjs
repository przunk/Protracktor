// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { searchMatches } from '../web/src/rules.js';
import { LibrarySearch, searchScope, searchScopeKey, SearchSelection, compareSearchTracks,
  combineSearchResults, savedSearchTracks } from '../web/src/library-search.js';
import { SearchBrowser } from '../web/src/search-browser.js';

let checks = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error('✗ ' + name); throw error; }
  checks++; console.log('✓ ' + name); };
const rows = fs.readFileSync('docs/rules/search-library-cases.tsv', 'utf8').split('\n').filter((line) => line && !line.startsWith('#'));
assert.equal(rows.shift(), 'case\tquery\tfirst\tsecond\texpected'); assert.ok(rows.length);
for (const line of rows) await test('shared Android search: ' + line.split('\t')[0], () => {
  const [name, query, first, second, expected] = line.split('\t');
  assert.equal(searchMatches(...[query, first, second].map(decodeURIComponent)), expected === 'true', name);
});
const track = (url, name = url, file = `${url}.mod`) => Object.freeze({ url, name, file });
await test('native SearchResults: saved copy wins the same URL from a catalogue', () => {
  const mine = track('same', 'Tempest fjortisfacials'), theirs = track('same', 'Tempest_fjortisfacials.sndh');
  assert.deepEqual(combineSearchResults([], [mine], [theirs]), [mine]);
});
await test('native SearchResults: every source pair and within-source duplicates collapse', () => {
  for (let a = 0; a < 4; a++) for (let b = 0; b < 4; b++) {
    const lists = Array.from({ length: 4 }, (_, i) => i === a || i === b ? [track('x'), track('x')] : []);
    assert.equal(combineSearchResults(...lists).length, 1);
  }
  assert.deepEqual(combineSearchResults([track('a'), track('a'), track('b')]).map((t) => t.url), ['a', 'b']);
});
await test('native SearchResults: placement follows survivors, with precedence independent of display', () => {
  const mine = track('a', 'mine');
  assert.deepEqual(combineSearchResults([mine, track('b')], [track('a'), track('b')], [track('c')], [track('a', 'theirs'), track('d')])
    .map((t) => t.url), ['a', 'b', 'd', 'c']);
  assert.equal(combineSearchResults([mine], [], [], [track('a')])[0], mine);
  assert.deepEqual(combineSearchResults(), []);
});
await test('native SearchResults: a live-only row remains ahead of 2000 catalogue rows', () => {
  const result = combineSearchResults([track('local/1')], [], Array.from({ length: 2000 }, (_, i) => track(`catalogue/${i + 1}`)), [track('live/a'), track('live/b')]);
  assert.equal(result.length, 2003); assert.deepEqual(result.slice(0, 4).map((t) => t.url), ['local/1', 'live/a', 'live/b', 'catalogue/1']);
});
await test('stream selection agrees with full SQL-binary sorting while retaining only the cap', () => {
  const input = Array.from({ length: 10007 }, (_, i) => track(`id-${i}`, String((i * 7919) % 10007)));
  input.push(track('supplementary', '𐀀'), track('bmp', '\ue000'));
  for (const cap of [1, 7, 2000, 20000]) {
    const selected = new SearchSelection(cap); input.forEach((row) => selected.accept(row));
    assert.equal(selected.total, input.length); assert.deepEqual(selected.rows(), [...input].sort(compareSearchTracks).slice(0, cap));
  }
  assert.ok(compareSearchTracks(track('a', '\ue000'), track('b', '𐀀')) < 0);
});
await test('canonical saved copies use latest persisted metadata and do not mutate playlists', () => {
  const old = { id: 'a', updatedAt: 1, tracks: [track('same', 'old'), track('a')] };
  const recent = { id: 'b', updatedAt: 2, tracks: [track('same', 'new'), track('b')] };
  const before = JSON.stringify([old, recent]);
  assert.deepEqual(savedSearchTracks([old, recent]).map((t) => [t.url, t.name]), [['a', 'a'], ['b', 'b'], ['same', 'new']]);
  assert.equal(JSON.stringify([old, recent]), before);
});
const fixture = () => {
  const calls = [], f = { saved: [], online: { rows: [], total: 0, held: [] } };
  f.search = new LibrarySearch({ playlists: async () => { calls.push('playlists'); return f.saved; },
    catalogues: async (query, ids, platforms, limit) => { calls.push({ query, ids, platforms, limit }); return f.online; } });
  f.run = (scope = searchScope(), query = 'song') => f.search.run({ scope, query });
  f.calls = calls; return f;
};
await test('native Local subset: saved scope never reads catalogues and matches title or filename', async () => {
  const f = fixture(); f.saved = [{ id: 'a', tracks: [track('song'), track('miss', 'different', 'other.mod')] }];
  assert.deepEqual((await f.run(searchScope('playlists'))).tracks.map((t) => t.url), ['song']); assert.deepEqual(f.calls, ['playlists']);
});
await test('native Online: explicit archive IDs and empty-means-all are passed without saved reads', async () => {
  for (const ids of [[], ['asma']]) {
    const f = fixture(); await f.run(searchScope('online', ids));
    assert.deepEqual(f.calls, [{ query: 'song', ids, platforms: [], limit: 2000 }]);
  }
});
await test('native platform scope narrows saved filenames and catalogue inputs, with empty meaning all', async () => {
  const f = fixture(); f.saved = [{ id: 'a', tracks: [track('amiga', 'song'), track('atari', 'song', 'tune.sap')] }];
  assert.deepEqual((await f.run(searchScope('platform', ['amiga']))).tracks.map((t) => t.url), ['amiga']);
  assert.deepEqual(f.calls[1], { query: 'song', ids: [], platforms: ['amiga'], limit: 2000 });
  assert.deepEqual((await f.run(searchScope('platform'))).tracks.map((t) => t.url), ['amiga', 'atari']);
});
await test('native blank query includes all saved tracks up to 2000 after canonicalization', async () => {
  const f = fixture(); const tracks = Array.from({ length: 2100 }, (_, i) => track(`track-${String(i).padStart(4, '0')}`));
  f.saved = [{ id: 'a', updatedAt: 1, tracks: tracks.flatMap((t) => [t, t]) }];
  const result = await f.run(searchScope('playlists'), ''); assert.equal(result.tracks.length, 2000);
  assert.equal(result.tracks.at(-1).url, 'track-1999'); assert.equal(result.playlistCapped, true); assert.equal(result.catalogueMatches, 0);
});
await test('native catalogue cap reports full matching count while saved duplicates retain precedence', async () => {
  const f = fixture(); const mine = track('same', 'song'); f.saved = [{ id: 'a', tracks: [mine] }];
  f.online = { rows: [track('same', 'other'), ...Array.from({ length: 1999 }, (_, i) => track(`online-${i}`))], total: 5000, held: [] };
  const result = await f.run(); assert.equal(result.tracks.length, 2000); assert.equal(result.catalogueMatches, 5000);
  assert.equal(result.tracks[0].name, 'song');
});
await test('scope IDs are canonical; unknown scopes fail explicitly', () => {
  assert.equal(searchScopeKey(searchScope('online', ['modland', 'asma', 'asma'])), searchScopeKey(searchScope('online', ['asma', 'modland'])));
  assert.throws(() => searchScope('invented'), /Unknown search scope/);
});
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };
const answer = ({ query, scope }) => Object.freeze({ query, scope, tracks: Object.freeze([track(query)]), catalogueMatches: 0, playlistCapped: false, held: [] });
await test('native scope and query edits never submit, scope-kind changes clear rows, chips retain them', async () => {
  let calls = 0; const b = new SearchBrowser({ run: async (request) => { calls++; return answer(request); } });
  b.open(); b.setQuery('typed'); assert.equal(calls, 0); await b.submit(); const produced = b.state.result;
  b.setQuery('pending'); assert.equal(b.state.result, produced); assert.equal(b.staleAnswer, true);
  b.setScope(searchScope('online')); assert.equal(b.state.result, null); assert.equal(calls, 1);
  await b.submit(); const old = b.state.result; b.toggleId('asma'); assert.equal(b.state.result, old); assert.equal(b.staleAnswer, true); assert.equal(calls, 2);
});
await test('native latest owner suppresses uncooperative identical-query results and obsolete errors', async () => {
  const gates = [], errors = []; const b = new SearchBrowser({ run: (request) => { const gate = deferred(); gates.push({ ...gate, request }); return gate.promise; }, failed: (e) => errors.push(e) });
  b.open(); const old = b.submit('same'); b.setQuery('between'); const next = b.submit('same');
  gates[1].resolve(answer(gates[1].request)); await next; const current = b.state.result;
  gates[0].reject(new Error('obsolete')); await old; assert.equal(b.state.result, current); assert.deepEqual(errors, []);
});
await test('leaving retires work and reopening preserves query without automatically searching', async () => {
  const gate = deferred(); let calls = 0; const b = new SearchBrowser({ run: () => { calls++; return gate.promise; } });
  b.open(); const work = b.submit('old'); b.leave(); gate.resolve(answer({ query: 'old', scope: searchScope() })); await work;
  b.open(); assert.equal(b.state.query, 'old'); assert.equal(b.state.loading, false); assert.equal(b.state.result, null); assert.equal(calls, 1);
});
await test('current failure stops loading and explicit resubmit retries the same query', async () => {
  let fail = true, errors = 0; const b = new SearchBrowser({ run: async (request) => { if (fail) throw new Error('denied'); return answer(request); }, failed: () => errors++ });
  await b.submit('retry'); assert.equal(b.state.failed.message, 'denied'); assert.equal(b.state.loading, false); assert.equal(errors, 1);
  fail = false; await b.submit(); assert.equal(b.state.failed, null); assert.equal(b.state.result.query, 'retry');
});
await test('source restoration retires an abandoned read and restores producing scope and rows', async () => {
  const gate = deferred(); const b = new SearchBrowser({ run: () => gate.promise }); const work = b.submit('abandoned');
  const scope = searchScope('online', ['asma']), tracks = Object.freeze([track('old')]);
  b.restore({ query: 'producing', searchScope: scope, results: tracks, searchSummary: { catalogueMatches: 123, held: [], playlistCapped: false } });
  gate.resolve(answer({ query: 'abandoned', scope: searchScope() })); await work;
  assert.equal(b.state.query, 'producing'); assert.deepEqual(b.state.scope, scope); assert.equal(b.state.result.tracks, tracks);
  assert.equal(b.state.result.catalogueMatches, 123); assert.equal(b.staleAnswer, false);
});
console.log(`${checks} library search checks passed.`);
