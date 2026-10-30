// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { PlaybackContext, PlaybackWorkspace, PlayOrder } from '../web/src/playback-context.js';
import { hasSourceView, sourceDestination, captureSourceRoute, restoreSourceRoute } from '../web/src/source-navigation.js';
let checks = 0;
const test = (name, run) => { run(); checks++; console.log(`✓ ${name}`); };
test('Android: manual-only and external sources have no fabricated view', () => {
  for (const kind of ['none', 'external']) {
    const source = { kind }; assert.equal(hasSourceView(source), false);
    assert.equal(sourceDestination(source, { playlistIds: [1], randomSession: 1, snapshot: {} }), null);
  }
});
test('Android: playlist destinations follow identity and reject a deleted playlist', () => {
  assert.equal(sourceDestination({ kind: 'playlist', id: 2, name: 'Old name' }, { playlistIds: [1, 2] }), 'playlist');
  assert.equal(sourceDestination({ kind: 'playlist', id: 3 }, { playlistIds: [1, 2] }), null);
});
test('Android: Random requires the remembered positive session instead of starting another', () => {
  assert.equal(sourceDestination({ kind: 'random', session: 7 }, { randomSession: 7 }), 'random');
  assert.equal(sourceDestination({ kind: 'random', session: 6 }, { randomSession: 7 }), null);
  assert.equal(sourceDestination({ kind: 'random', session: 0 }, { randomSession: 0 }), null);
});
test('Android: collection destinations need their captured route rather than a similar display label', () => {
  const source = { kind: 'collection', origin: 'search', path: 'search', query: 'same' };
  const snapshot = captureSourceRoute(source, { path: ['search'], query: 'same', searchScope: 'local' });
  assert.equal(sourceDestination(source, { snapshot }), 'browse');
  assert.equal(sourceDestination(source), null);
  assert.equal(sourceDestination({ ...source, query: 'different' }, { snapshot }), null);
});
test('Android: route restoration preserves live resources and retires unrelated navigation', () => {
  const source = { kind: 'collection', origin: 'search', path: 'search', query: 'saved' };
  const saved = captureSourceRoute(source, { path: ['search'], query: 'saved', searchScope: 'modland', results: [{ name: 'Old' }] });
  const current = { navigationVisit: 120, indexing: { new: 'downloading' }, storageBytes: [30, 40],
    held: { trackMetadata: true }, randomScope: 'favourites', trackMetadataCount: 80, jumpedTo: 'unrelated', detour: 'author' };
  const restored = restoreSourceRoute(saved, current);
  assert.equal(restored.navigationVisit, 121); assert.equal(restored.results, saved.results); assert.equal(restored.query, saved.query);
  for (const key of ['indexing', 'storageBytes', 'held', 'randomScope', 'trackMetadataCount']) assert.equal(restored[key], current[key]);
  assert.equal(restored.jumpedTo, null); assert.equal(restored.detour, null);
});
test('Android: History gets a fresh route visit but retains its captured page and filter', () => {
  const source = { kind: 'collection', origin: 'history', path: 'history', query: '' };
  const snapshot = captureSourceRoute(source, { path: ['history'], historyQuery: 'song', historyPage: 3 });
  const restored = restoreSourceRoute(snapshot, { navigationVisit: 9 });
  assert.equal(restored.navigationVisit, 10); assert.equal(restored.historyPage, 3); assert.equal(restored.historyQuery, 'song');
});
test('WEB finite share links do not fabricate a navigable source view', () => {
  const source = { kind: 'collection', origin: 'link' };
  assert.equal(hasSourceView(source), false);
  assert.equal(sourceDestination(source, { snapshot: captureSourceRoute(source) }), null);
});
test('native playlist return restores captured membership as a draft without rebuilding playback', () => {
  const tracks = ['a','b','c'].map((url) => ({url,name:url}));
  const context = new PlaybackContext({source:{kind:'playlist',id:1},order:new PlayOrder({tracks}).startAt(1)});
  const state = new PlaybackWorkspace({context,editor:{id:2,name:'Other',order:new PlayOrder(),dirty:false,revision:4}});
  const restored = state.selectPlaylist({id:1,name:'Original',tracks:tracks.slice(0,2)},{preservePlayback:true});
  assert.equal(restored.context, context); assert.equal(restored.editor.order, context.order);
  assert.equal(restored.editor.dirty,true); assert.equal(restored.editor.order.currentIndex,1);
});
test('native playlist return ignores metadata-only changes when deciding draft dirtiness', () => {
  const tracks = ['a','b'].map((url) => ({url,name:url}));
  const context = new PlaybackContext({source:{kind:'playlist',id:1},order:new PlayOrder({tracks}).startAt(1)});
  const state = new PlaybackWorkspace({context});
  const restored = state.selectPlaylist({id:1,name:'New document label',tracks:tracks.map((track)=>({...track,name:'New title'}))},{preservePlayback:true});
  assert.equal(restored.context, context); assert.equal(restored.editor.dirty,false);
});
assert.ok(checks); console.log(`${checks} Android-derived source-navigation checks passed`);
