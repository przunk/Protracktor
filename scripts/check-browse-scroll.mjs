// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { BrowseScroll } from '../web/src/browse-scroll.js';
let checks = 0;
const test = (name, run) => {
  try { run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  checks++; console.log(`✓ ${name}`);
};

test('native pending returns survive repeated reads until used', () => {
  const s = new BrowseScroll(); s.descendingFrom('online', 'modland');
  assert.deepEqual(s.restoreFor('online', ['asma', 'modland']), { kind: 'scroll', index: 1 });
  assert.deepEqual(s.restoreFor('online', ['modland', 'asma']), { kind: 'scroll', index: 0 });
  s.returned('online'); assert.deepEqual(s.restoreFor('online', ['modland']), { kind: 'nothing' });
});
test('native old/loading or empty rows wait without consuming the marker', () => {
  const s = new BrowseScroll(); s.descendingFrom('online/modland', 'Protracker');
  assert.deepEqual(s.restoreFor('online/modland', ['4-Mat', 'Jester'], true), { kind: 'wait' });
  assert.deepEqual(s.restoreFor('online/modland', []), { kind: 'wait' });
  assert.deepEqual(s.restoreFor('online/modland', ['Ad Lib', 'Protracker']), { kind: 'scroll', index: 1 });
});
test('native genuinely missing row is forgotten, never replaced by an old numeric index', () => {
  const s = new BrowseScroll(); s.descendingFrom('local/uri', 'old.mod');
  assert.deepEqual(s.restoreFor('local/uri', ['a.mod', 'b.mod']), { kind: 'forget' });
  s.returned('local/uri'); assert.deepEqual(s.restoreFor('local/uri', ['old.mod']), { kind: 'nothing' });
});
test('native levels have independent return markers and unmarked levels do nothing', () => {
  const s = new BrowseScroll(); s.descendingFrom('online', 'modland'); s.descendingFrom('online/modland', 'Protracker');
  s.returned('online/modland');
  assert.deepEqual(s.restoreFor('online', ['modland']), { kind: 'scroll', index: 0 });
  assert.deepEqual(s.restoreFor('online/modland', ['Protracker']), { kind: 'nothing' });
});
test('per-level offsets default to zero and survive independent visits', () => {
  const s = new BrowseScroll(); assert.equal(s.position('new'), 0);
  s.save('online', 300); s.save('online/modland', 90);
  s.save('online', NaN); s.save('online/modland', -5);
  assert.equal(s.position('online'), 300); assert.equal(s.position('online/modland'), 0);
});
test('fresh History/query removal keeps other Browse levels and full session reset clears everything', () => {
  const s = new BrowseScroll();
  for (const key of ['online', 'history/0', 'history/1']) { s.save(key, 100); s.descendingFrom(key, 'row'); }
  s.forgetWhere((key) => key.startsWith('history/'));
  assert.equal(s.position('history/1'), 0); assert.equal(s.position('online'), 100);
  assert.deepEqual(s.restoreFor('history/0', ['row']), { kind: 'nothing' });
  s.clear(); assert.equal(s.position('online'), 0); assert.deepEqual(s.restoreFor('online', ['row']), { kind: 'nothing' });
});
console.log(`${checks} native-derived Browse restoration checks passed`);
