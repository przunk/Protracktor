// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { STEREO_WIDTHS, stereoMode, stereoWidth, StereoMixPreferences } from '../web/src/stereo-mix.js';
let count = 0;
function test(name, run) { run(); count++; console.log(`✓ ${name}`); }
const fixture = fs.readFileSync(new URL('../docs/rules/stereo-mix-cases.tsv', import.meta.url), 'utf8');
const rows = fixture.split('\n').filter((line) => line && !line.startsWith('#'));
assert.equal(rows.shift(), 'case\tstored\tmode\twidth');
assert.equal(rows.length, Number(fixture.match(/^# cases: (\d+)$/m)?.[1])); assert(rows.length > 0);
test('native stable names and agreed widths remain the only selectable modes', () => {
  assert.deepEqual(Object.entries(STEREO_WIDTHS), [['original', 100], ['narrow', 50], ['mono', 0]]);
  assert(Object.isFrozen(STEREO_WIDTHS));
});
for (const row of rows) {
  const [name, value, mode, width] = row.split('\t');
  test(`shared native stored mode: ${name}`, () => {
    const raw = value === '-' ? null : value === '<empty>' ? '' : value;
    assert.equal(stereoMode(raw), mode); assert.equal(stereoWidth(raw), Number(width));
  });
}
for (const raw of [null, 'original', 'narrow', 'mono', 'future-mode']) test(`reading ${raw} preserves retained data and never writes defaults`, () => {
  let writes = 0;
  const store = new StereoMixPreferences(() => ({ getItem: (key) => { assert.equal(key, 'protracktor.stereoMix'); return raw; }, setItem: () => writes++ }));
  assert.deepEqual(store.read(), { value: stereoMode(raw), failure: null }); assert.equal(writes, 0);
});
test('every selected mode persists only its own key and a new reader recovers it', () => {
  const values = new Map([['protracktor.shuffle', '1'], ['protracktor.theme', 'light']]), writes = [];
  const storage = () => ({ getItem: (key) => values.get(key), setItem: (key, value) => { writes.push(key); values.set(key, value); } });
  for (const value of Object.keys(STEREO_WIDTHS)) {
    assert.equal(new StereoMixPreferences(storage).write(value), null);
    assert.equal(new StereoMixPreferences(storage).read().value, value);
  }
  assert.deepEqual(writes, ['protracktor.stereoMix', 'protracktor.stereoMix', 'protracktor.stereoMix']);
  assert.equal(values.get('protracktor.shuffle'), '1'); assert.equal(values.get('protracktor.theme'), 'light');
});
test('refused storage access returns a safe choice and the actual reason', () => {
  const failure = new Error('access refused'), store = new StereoMixPreferences(() => { throw failure; });
  assert.deepEqual(store.read(), { value: 'original', failure }); assert.equal(store.write('mono'), failure);
});
test('a refused read is independent of a later successful selection', () => {
  const failure = new Error('read refused'); let saved;
  const store = new StereoMixPreferences(() => ({ getItem: () => { throw failure; }, setItem: (_key, value) => { saved = value; } }));
  assert.deepEqual(store.read(), { value: 'original', failure }); assert.equal(store.write('narrow'), null); assert.equal(saved, 'narrow');
});
test('quota refusal does not claim success and a later write can recover', () => {
  const failure = new Error('quota refused'); let refused = true, saved = 'original';
  const store = new StereoMixPreferences(() => ({ getItem: () => saved, setItem: (_key, value) => { if (refused) throw failure; saved = value; } }));
  assert.equal(store.write('mono'), failure); assert.equal(saved, 'original');
  refused = false; assert.equal(store.write('narrow'), null); assert.equal(store.read().value, 'narrow');
});
test('invalid selections are rejected without touching storage', () => {
  const store = new StereoMixPreferences(() => { throw new Error('must not touch storage'); });
  for (const value of [null, undefined, '', 'future-mode', 50, 'constructor']) assert.throws(() => store.write(value), TypeError);
});
assert(count > 0); console.log(`${count} shared stereo mode/persistence cases passed`);
