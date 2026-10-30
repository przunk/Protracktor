// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { NormalizationPreferences } from '../web/src/normalization-preferences.js';
let checks = 0;
function test(name, run) { run(); checks++; console.log(`✓ ${name}`); }
for (const raw of [null, '0', '1', '', 'true', 'false', 'future-policy']) test(`native opt-in policy restores ${JSON.stringify(raw)} without writing`, () => {
  let writes = 0;
  const prefs = new NormalizationPreferences(() => ({ getItem: (key) => { assert.equal(key, 'protracktor.volumeNormalization'); return raw; }, setItem: () => writes++ }));
  assert.deepEqual(prefs.read(), { value: raw === '1', failure: null }); assert.equal(writes, 0);
});
test('both choices persist only the normalization key and a new reader restores the choice', () => {
  const map = new Map([['protracktor.stereoMix', 'mono'], ['protracktor.repeat', 'one']]);
  const storage = () => ({ getItem: (key) => map.get(key), setItem: (key, value) => map.set(key, value) });
  for (const enabled of [true, false]) {
    assert.equal(new NormalizationPreferences(storage).write(enabled), null);
    assert.equal(new NormalizationPreferences(storage).read().value, enabled);
  }
  assert.equal(map.get('protracktor.stereoMix'), 'mono'); assert.equal(map.get('protracktor.repeat'), 'one');
});
test('read and write refusals retain the actual reason and use default Off', () => {
  const failure = new Error('access refused'), prefs = new NormalizationPreferences(() => { throw failure; });
  assert.deepEqual(prefs.read(), { value: false, failure }); assert.equal(prefs.write(true), failure);
});
test('a refused write does not claim persistence and a later successful selection recovers', () => {
  let refused = true, raw = '0'; const failure = new Error('quota');
  const prefs = new NormalizationPreferences(() => ({ getItem: () => raw, setItem: (_key, value) => { if (refused) throw failure; raw = value; } }));
  assert.equal(prefs.write(true), failure); assert.equal(prefs.read().value, false);
  refused = false; assert.equal(prefs.write(true), null); assert.equal(prefs.read().value, true);
});
test('invalid selections are rejected before touching storage', () => {
  const prefs = new NormalizationPreferences(() => { throw new Error('must not be accessed'); });
  for (const value of [null, 0, 1, '1', 'true', undefined, {}]) assert.throws(() => prefs.write(value), TypeError);
});
assert(checks > 0); console.log(`${checks} normalization preference cases passed`);
