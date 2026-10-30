// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { SearchScopesView, searchScopeLabel } from '../web/src/search-scopes-view.js';
import { searchScope } from '../web/src/library-search.js';
import { useLanguage } from '../web/src/i18n.js';

const dom = new JSDOM('<main><input><div id="scopes"></div></main>', { pretendToBeVisual: true });
globalThis.document = dom.window.document;
const root = dom.window.document.getElementById('scopes'), picked = [], toggled = [];
let retries = 0, checks = 0;
const test = (name, run) => { try { run(); } catch (error) { console.error('✗ ' + name); throw error; } checks++; console.log('✓ ' + name); };
const view = new SearchScopesView(root, { icon: () => '<svg aria-hidden="true"></svg>',
  sourceIds: ['modland', 'asma'], sourceName: (id) => ({ modland: 'Modland', asma: 'ASMA' })[id] ?? id,
  onScope: (kind) => picked.push(kind), onToggle: (id) => toggled.push(id), onRetry: () => retries++ });
const button = (key) => root.querySelector(`[data-search-scope="${key}"]`);
const known = { value: { catalogues: ['modland', 'asma'], platformCounts: { amiga: 2, 'atari-8bit': 1 } }, loading: false, failed: false };
test('Everywhere has labelled scope tiles and no unrequested local-folder promise', () => {
  view.render(searchScope(), known);
  assert.equal(view.catalogues.hidden, true); assert.equal(view.platforms.hidden, true);
  assert.deepEqual([...view.tiles.querySelectorAll('button span')].map((node) => node.textContent), ['Saved playlists', 'Online catalogues', 'By platform']);
  assert.ok([...root.querySelectorAll('button')].every((node) => node.querySelector('svg') && node.textContent));
});
test('real scope clicks invoke the requested kind rather than submitting searches', () => {
  for (const key of ['playlists', 'online', 'platform']) button(key).click();
  assert.deepEqual(picked, ['playlists', 'online', 'platform']);
});
test('unknown availability explains checking rather than claiming no indexes', () => {
  view.render(searchScope('online'), { value: null, loading: true, failed: false });
  assert.equal(view.status.textContent, 'Checking search sources…');
  assert.equal(button('catalogue:asma').disabled, true); assert.equal(button('catalogue:asma').getAttribute('aria-describedby'), 'search-scope-status');
});
test('known archive chips toggle IDs, and empty selection states all-indexed semantics', () => {
  view.render(searchScope('online'), known); button('catalogue:asma').click();
  assert.deepEqual(toggled, ['asma']); assert.equal(view.status.textContent, 'No catalogue selected means all indexed catalogues.');
  view.render(searchScope('online', ['asma']), known);
  assert.equal(button('catalogue:asma').getAttribute('aria-pressed'), 'true');
  assert.equal(button('catalogue:asma').querySelector('.scope-check').style.visibility, 'visible');
});
test('known platform chips enable available data and explain unavailable choices', () => {
  view.render(searchScope('platform', ['amiga']), known);
  assert.equal(button('platform:amiga').disabled, false); assert.equal(button('platform:sharp').disabled, true);
  button('platform:atari-8bit').click(); assert.equal(toggled.at(-1), 'atari-8bit');
  assert.equal(button('platform:amiga').getAttribute('aria-pressed'), 'true');
});
test('redraw preserves a focused chip and previously verified availability during refresh', () => {
  const node = button('platform:amiga'); node.focus();
  view.render(searchScope('platform', ['amiga']), { ...known, loading: true });
  assert.equal(button('platform:amiga'), node); assert.equal(dom.window.document.activeElement, node); assert.equal(node.disabled, false);
});
test('failure has a labelled actual Retry action without changing the selected scope', () => {
  view.render(searchScope('platform', ['amiga']), { ...known, failed: true });
  assert.equal(view.retry.button.hidden, false); view.retry.button.click(); assert.equal(retries, 1);
  assert.equal(button('platform:amiga').getAttribute('aria-pressed'), 'true'); assert.equal(view.retry.button.hasAttribute('aria-pressed'), false);
});
test('confirmed empty archives explain preparation and hide unavailable archive chips', () => {
  view.render(searchScope('online'), { ...known, value: { catalogues: [], platformCounts: {} } });
  assert.equal(view.status.textContent, 'No catalogue index is held. Download one in Online catalogues.'); assert.equal(button('catalogue:asma').hidden, true);
});
test('scope label spells at most two names before counting and supports Polish', () => {
  assert.equal(searchScopeLabel(searchScope('platform', ['amiga', 'pc', 'sega']), (id) => id), 'Amiga, PC +1');
  assert.equal(searchScopeLabel(searchScope('platform'), (id) => id), 'All platforms');
  assert.equal(searchScopeLabel(searchScope('platform', ['atari-8bit', 'pc']), (id) => id), 'PC, Atari 8-bit');
  assert.equal(searchScopeLabel(searchScope('online', ['asma', 'modland']), (id) => id, ['modland', 'asma']), 'Online: modland, asma');
  useLanguage('pl'); view.render(searchScope('online'), known);
  assert.equal(button('playlists').textContent, 'Zapisane playlisty'); assert.equal(view.tiles.getAttribute('aria-label'), 'Zakres wyszukiwania');
  assert.equal(searchScopeLabel(searchScope('online', ['asma']), () => 'ASMA'), 'Online: ASMA');
  useLanguage('en');
});
test('unchanged availability leaves the live status text node intact', () => {
  const chosen = searchScope('online'); view.render(chosen, known);
  const status = view.status.firstChild; view.render(chosen, known);
  assert.equal(view.status.firstChild, status);
});
dom.window.close(); delete globalThis.document; console.log(`${checks} Search scope presentation checks passed.`);
