// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { readPlaylistFile, writePlaylistFile, playlistNameIn, playlistFileName, importedPlaylistName,
  playlistSize, playlistReference, hydratePlaylistReference, networkPlaylistUrl } from '../web/src/playlist-file.js';
import { PlaylistTransfer } from '../web/src/playlist-transfer.js';
let checks = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error('✗ ' + name); throw error; }
  checks++; console.log('✓ ' + name); };
const cases = (name) => {
  const rows = fs.readFileSync(`docs/rules/${name}.tsv`, 'utf8').split('\n').filter((line) => line && !line.startsWith('#')).slice(1);
  assert.ok(rows.length); return rows.map((line) => line.split('\t').map(decodeURIComponent));
};
for (const [name, text, label, legacy, expected = legacy] of cases('playlist-file-read-cases')) await test('shared native M3U read: ' + name, () => {
  assert.equal(playlistNameIn(text) ?? '-', label);
  assert.equal(readPlaylistFile(text).map((entry) => [entry.id ?? '-', entry.title, entry.author, entry.size, entry.location, entry.file].join('\t')).join('\n') || '-', expected);
});
for (const [name, label, url, title, meta, file, author, size, text] of cases('playlist-file-write-cases')) await test('shared native M3U bytes: ' + name, () => {
  assert.equal(writePlaylistFile(label, [{ url, name: title, meta, file, author, size }]), text);
});
for (const [name, label, expected] of cases('playlist-filename-cases')) await test('shared native download name: ' + name, () => assert.equal(playlistFileName(label), expected));
const track = (url = 'https://example.org/one.mod', name = 'One') => ({ url, name, file: 'one.mod', author: 'Artist' });
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; };
const fixture = () => {
  const state = { selected: { id: 'a', name: 'Mix/One?', tracks: [track()] }, stored: new Map(), known: [], held: [], saved: [], reads: 0 };
  state.transfer = new PlaylistTransfer({ selection: () => state.selected,
    read: async (id) => { state.reads++; return state.stored.get(id); }, known: async () => state.known, held: () => state.held,
    newId: () => 'new-id', save: async (record) => { state.saved.push(record); } });
  return state;
};
await test('native active export captures unsaved draft and sanitizes only filename without storage reads', async () => {
  const f = fixture(), before = structuredClone(f.selected); f.stored.set('a', { tracks: [track('stale')] });
  const result = await f.transfer.export('a');
  assert.equal(result.filename, 'Mix_One_.m3u8'); assert.equal(playlistNameIn(result.text), 'Mix/One?');
  assert.equal(readPlaylistFile(result.text)[0].id, f.selected.tracks[0].url);
  assert.equal(f.reads, 0); assert.equal(f.saved.length, 0); assert.deepEqual(f.selected, before);
});
await test('native other-list export reads stored rows and retains initial name across suspension', async () => {
  const f = fixture(), gate = deferred(); f.transfer.read = () => gate.promise;
  const request = f.transfer.export('b', 'Other'); f.selected = { id: 'b', name: 'Changed', tracks: [track('wrong')] };
  gate.resolve({ tracks: [track()] }); const result = await request;
  assert.equal(result.filename, 'Other.m3u8'); assert.equal(playlistNameIn(result.text), 'Other');
  assert.equal(readPlaylistFile(result.text)[0].id, track().url);
});
await test('a held phone file exports its known byte length without exporting or detaching bytes', async () => {
  const f = fixture(), data = new Uint8Array([1, 2, 3]).buffer;
  f.selected.tracks = [{ url: 'content://held', name: 'Held', file: 'held.mod', data }];
  const result = await f.transfer.export('a');
  assert.equal(readPlaylistFile(result.text)[0].size, '3'); assert.equal(data.byteLength, 3);
  assert.ok(!result.text.includes('data:') && !result.text.includes('AQID'));
});
await test('an unknown artist and a title containing a separator round trip without an invented artist or prefix', () => {
  const title = 'Unknown - Intro';
  const entries = readPlaylistFile(writePlaylistFile('Unknown author', [{ url: 'content://x', name: title, file: 'x.mod' }]));
  assert.equal(entries[0].title, title); assert.equal(entries[0].author, '');
});
await test('native empty exports do not produce a download; read failures are not empty success', async () => {
  const f = fixture(); f.selected.tracks = [];
  assert.equal((await f.transfer.export('a')).kind, 'empty'); assert.equal((await f.transfer.export('b')).kind, 'empty');
  f.transfer.read = async () => { throw new Error('read failure'); };
  await assert.rejects(f.transfer.export('b'), /read failure/);
});
await test('native unreadable/blank and comment-only inputs produce distinct notices without writes', async () => {
  for (const text of [null, '', ' \n', '#EXTM3U\n# comment']) {
    const f = fixture(), result = await f.transfer.import(text, 'input.m3u');
    assert.equal(result.kind, text?.trim() ? 'no-tracks' : 'unreadable'); assert.equal(f.saved.length, 0);
  }
});
await test('native exact identity wins latest known metadata before other fallback paths', async () => {
  const f = fixture(), old = track(), latest = { ...old, name: 'Latest stored title' }; f.known = [old, latest];
  const result = await f.transfer.import(writePlaylistFile('Known', [old]), 'input.m3u8');
  assert.equal(result.found, 1); assert.equal(result.tracks[0].name, latest.name); assert.notEqual(result.id, f.selected.id);
  assert.equal(f.selected.id, 'a'); assert.equal(f.saved[0].name, 'Known');
});
await test('native remote id wins over location, then direct remote location supplies a track', async () => {
  const f = fixture();
  const result = await f.transfer.import('#PROTRACKTOR:id=https://example.org/id.mod\n#EXTINF:-1,A - ID\npath/file.mod\n#EXTINF:-1,B - Direct\nhttp://example.org/direct.mod', 'input');
  assert.deepEqual(result.tracks.map((track) => track.url), ['https://example.org/id.mod', 'http://example.org/direct.mod']);
  assert.deepEqual(result.tracks.map((track) => [track.name, track.author, track.meta]), [['ID', 'A', 'path'], ['Direct', 'B', 'http://example.org']]);
});
await test('native filename fallback requires a positive matching size; zero/negative use filename only', async () => {
  const f = fixture(), data = new Uint8Array([1, 2, 3]).buffer, local = { url: 'content://held', file: 'held.mod', name: 'Held', data };
  f.held = [local];
  for (const size of ['3', '0', '-4', '4']) {
    const result = await f.transfer.import(`#PROTRACKTOR:size=${size}\nforeign/held.mod`, 'input');
    assert.equal(result.found, size === '4' ? 0 : 1);
    if (result.found) { assert.equal(result.tracks[0].data, data); assert.equal(result.tracks[0].local, false); }
  }
  const differentCase = await f.transfer.import('foreign/Held.mod', 'input'); assert.equal(differentCase.found, 0);
});
await test('native duplicate offerings count before imported membership deduplicates in original order', async () => {
  const f = fixture(), a = track(), b = track('https://example.org/b.mod', 'B');
  const result = await f.transfer.import(writePlaylistFile('Duplicates', [a, a, b, a]), 'input');
  assert.equal(result.offered, 4); assert.equal(result.found, 4); assert.deepEqual(result.tracks.map((track) => track.url), [a.url, b.url]);
  assert.equal(f.saved[0].tracks.length, 2);
});
await test('native nonempty all-unresolved input still creates an empty new playlist with filename fallback', async () => {
  for (const [name, expected] of [['primary:Download/Fallback.m3u8', 'Fallback'], ['name.with.dots.m3u', 'name.with.dots'], ['', 'Imported']]) {
    const f = fixture(), selected = f.selected, result = await f.transfer.import('missing.mod', name);
    assert.equal(result.found, 0); assert.equal(result.offered, 1); assert.equal(result.name, expected);
    assert.deepEqual(f.saved[0].tracks, []); assert.equal(f.selected, selected);
  }
  assert.equal(importedPlaylistName('#PLAYLIST:Żółć 🎵', 'ignored'), 'Żółć 🎵');
});
await test('browser local references without held bytes are unresolved even if stored under an exact id', async () => {
  const f = fixture(); f.known = [{ url: 'content://missing', name: 'Missing', file: 'missing.mod' }];
  const result = await f.transfer.import('#PROTRACKTOR:id=content://missing\nmissing.mod', 'input');
  assert.equal(result.found, 0); assert.deepEqual(f.saved[0].tracks, []);
});
await test('held-byte imports persist only reference metadata and never detach the original buffer', async () => {
  const f = fixture(), data = new Uint8Array([7, 8, 9]).buffer; f.held = [{ url: 'content://held', name: 'Held', file: 'held.mod', data, arbitrary: { data } }];
  const result = await f.transfer.import('held.mod', 'input');
  assert.equal(result.tracks[0].data, data); assert.equal(data.byteLength, 3);
  assert.equal('data' in f.saved[0].tracks[0], false); assert.equal('arbitrary' in f.saved[0].tracks[0], false);
  assert.equal(readPlaylistFile(writePlaylistFile('Held', result.tracks))[0].size, '3');
});
await test('failed input lookup or write cannot return an imported success or mutate selection', async () => {
  for (const step of ['known', 'save']) {
    const f = fixture(), selected = f.selected; f.transfer[step] = async () => { throw new Error(step + ' failed'); };
    await assert.rejects(f.transfer.import('https://example.org/one.mod', 'input'), new RegExp(step + ' failed'));
    assert.equal(f.selected, selected); assert.equal(f.saved.length, 0);
  }
});
await test('late completed input creates its own playlist without adopting a newer selection', async () => {
  const f = fixture(), gate = deferred(); f.transfer.known = () => gate.promise;
  const request = f.transfer.import('https://example.org/one.mod', 'First.m3u');
  f.selected = { id: 'later', name: 'Later', tracks: [track('later')] }; gate.resolve([]);
  const result = await request; assert.equal(f.selected.id, 'later'); assert.equal(f.saved[0].name, 'First'); assert.equal(result.kind, 'import');
});
await test('line-based metadata cannot inject new playlist entries through embedded newlines', () => {
  const text = writePlaylistFile('A\n#PLAYLIST:B', [{ ...track(), name: 'One\nhttps://attacker.test/two.mod', author: 'A\rB' }]);
  assert.equal(readPlaylistFile(text).length, 1); assert.equal(playlistNameIn(text), 'A #PLAYLIST:B');
});
await test('runtime availability changes never rewrite the stored local flag, including after an immutable edit', () => {
  for (const stored of [track(), { url: 'content://held', name: 'Held', file: 'held.mod', local: false }, { url: null, name: 'Not sent', local: true }]) {
    const missing = hydratePlaylistReference(stored);
    assert.deepEqual(playlistReference(missing), playlistReference(stored));
    assert.equal(playlistReference({ ...missing, name: 'Edited' }).local, stored.local);
    const held = hydratePlaylistReference(missing, new ArrayBuffer(3));
    assert.equal(held.local, false); assert.equal(playlistReference(held).local, stored.local);
    assert.equal('data' in playlistReference(held), false);
  }
});
await test('empty or detached buffers do not masquerade as an available cached file', () => {
  const remote = hydratePlaylistReference({ ...track(), data: new ArrayBuffer(0) });
  assert.equal(remote.local, false); assert.equal('data' in remote, false);
  const local = hydratePlaylistReference({ url: 'content://empty', name: 'Empty', data: new ArrayBuffer(0) });
  assert.equal(local.local, true); assert.equal('data' in local, false);
});
await test('network references accept only valid HTTP(S), and size metadata preserves signed Long bounds', () => {
  for (const value of ['httpfoo', 'javascript:alert(1)', 'file:///music', 'https://', null]) assert.equal(networkPlaylistUrl(value), null);
  assert.equal(networkPlaylistUrl('https://example.org/a.mod'), 'https://example.org/a.mod');
  assert.equal(playlistSize('9223372036854775807'), '9223372036854775807'); assert.equal(playlistSize('9223372036854775808'), '0');
  assert.equal(playlistSize(9007199254740992), '0'); assert.equal(playlistReference({ url: 'x', name: 'X', data: new ArrayBuffer(3) }).data, undefined);
});
assert.ok(checks > 0); console.log(`${checks} native-based playlist-file/transfer checks passed`);
