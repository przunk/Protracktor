// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Inputs and expected outcomes ported from Android RandomHistoryTest at 3183eee.
import assert from 'node:assert/strict';
import { RandomHistory } from '../web/src/random-history.js';
const track = (url) => Object.freeze({ url, name: url });
const names = (tracks) => tracks.map((entry) => entry.url);
const items = (count, prefix) => Array.from({ length: count }, (_, index) => track(`${prefix}-${index + 1}`));
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; };
const heard = async (count) => {
  const history = new RandomHistory();
  await history.fill(count, 4, () => items(count, 'heard'));
  for (let i = 0; i < count; i++) history.advance();
  return history;
};
let checks = 0;
const test = async (name, run) => {
  try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  checks++; console.log(`✓ ${name}`);
};
await test('changing scope from an earlier track preserves heard history and hides speculative picks', async () => {
  const history = new RandomHistory(), played = items(10, 'amiga');
  await history.fill(11, 4, () => [...played, track('old-prefetch')]);
  for (let i = 0; i < 10; i++) history.advance();
  history.select(4); history.changeScope();
  assert.deepEqual(history.playedPicks, played); assert.equal(history.current, played[4]);
  const fresh = items(4, 'atari'); await history.fill(4, 4, () => fresh);
  assert.deepEqual(history.playedPicks, played);
  for (let i = 5; i < 10; i++) { assert.ok(history.hasNext); assert.equal(history.advance(), played[i]); assert.equal(history.cursor, i); }
  assert.equal(history.advance(), fresh[0]); assert.deepEqual(history.playedPicks, [...played, fresh[0]]);
  assert.equal(history.advance(), fresh[1]); assert.equal(history.cursor, 11);
});
await test('without a scope change Next walks the heard record without drawing', async () => {
  const history = await heard(10); history.select(2);
  assert.ok(history.hasNext); assert.equal(history.advance().url, 'heard-4'); assert.equal(history.cursor, 3);
  assert.equal(history.playedPicks.length, 10);
});
await test('each advance publishes one new pick rather than its three read-ahead entries', async () => {
  const history = new RandomHistory(); await history.fill(4, 4, () => items(4, 'new'));
  assert.equal(history.playedPicks.length, 0); assert.equal(history.advance().url, 'new-1');
  assert.deepEqual(names(history.playedPicks), ['new-1']); assert.deepEqual(names(history.upcoming(3)), ['new-2', 'new-3', 'new-4']);
  await history.fill(4, 4, () => [track('new-5')]); history.advance();
  assert.deepEqual(names(history.playedPicks), ['new-1', 'new-2']); assert.equal(history.cursor, 1);
});
await test('empty new scope keeps the current tune and heard record', async () => {
  const history = await heard(10), before = history.playedPicks;
  history.changeScope(); await history.fill(4, 4, () => []);
  assert.equal(history.advance(), null); assert.equal(history.current.url, 'heard-10'); assert.deepEqual(history.playedPicks, before);
  await history.fill(4, 4, () => [track('new')]); assert.equal(history.advance().url, 'new'); assert.equal(history.cursor, 10);
});
await test('empty new scope does not block walking remaining heard history', async () => {
  const history = await heard(5); history.select(1); history.changeScope(); await history.fill(3, 4, () => []);
  for (let i = 3; i <= 5; i++) assert.equal(history.advance().url, `heard-${i}`);
  assert.equal(history.advance(), null); assert.equal(history.playedPicks.length, 5);
});
await test('background preparation targets the unheard tail rather than the current cursor', async () => {
  const history = await heard(10); history.select(1); history.changeScope(); let calls = 0;
  await history.fill(3, 4, () => { calls++; return items(3, 'fresh'); });
  await history.fill(3, 4, () => { throw new Error('Tail already prepared'); });
  assert.equal(calls, 1); assert.equal(history.playedPicks.length, 10);
  for (let i = 0; i < 8; i++) history.advance();
  assert.deepEqual(names(history.upcoming(3)), ['fresh-1', 'fresh-2', 'fresh-3']);
});
await test('overlapping fills share the same read-ahead capacity', async () => {
  const history = await heard(2), entered = deferred(), result = deferred();
  const first = history.fill(3, 4, () => { entered.resolve(); return result.promise; }); await entered.promise;
  const second = history.fill(3, 4, () => { throw new Error('First fill already prepared the tail'); });
  result.resolve(items(3, 'fresh')); await Promise.all([first, second]);
  assert.deepEqual(names(history.upcoming(10)), ['fresh-1', 'fresh-2', 'fresh-3']);
});
await test('model reroll preserves heard entries and cannot move selection before a fresh pick exists', async () => {
  const history = await heard(5); history.select(1); history.reroll(); assert.equal(history.current.url, 'heard-2');
  await history.fill(4, 4, () => [track('fresh')]); assert.equal(history.advance().url, 'fresh');
  assert.equal(history.playedPicks.length, 6); assert.equal(history.previous().url, 'heard-5'); assert.equal(history.advance().url, 'fresh');
});
await test('unplayed entries cannot be selected or removed', async () => {
  const history = await heard(2); await history.fill(4, 4, () => [track('ahead')]);
  assert.equal(history.select(2), null); history.remove(2); assert.equal(history.advance().url, 'ahead');
  history.remove(0); assert.equal(history.cursor, 1); assert.deepEqual(names(history.playedPicks), ['heard-2', 'ahead']);
});
await test('scope changed during the first draw rejects old-scope results', async () => {
  const history = await heard(2), entered = deferred(), result = deferred();
  const job = history.fill(4, 4, () => { entered.resolve(); return result.promise; }); await entered.promise;
  history.changeScope(); result.resolve([track('stale')]); await job; assert.equal(history.upcoming(3).length, 0);
  await history.fill(4, 4, () => [track('fresh')]); assert.equal(history.advance().url, 'fresh');
});
await test('scope changed during small-pool fallback rejects that result too', async () => {
  const history = await heard(2), entered = deferred(), result = deferred(); let calls = 0;
  const job = history.fill(4, 4, () => { if (++calls === 1) return history.playedPicks; entered.resolve(); return result.promise; });
  await entered.promise; history.changeScope(); result.resolve([track('heard-1')]); await job;
  assert.equal(history.upcoming(3).length, 0); assert.equal(history.playedPicks.length, 2);
});
await test('a new session rejects an old draw', async () => {
  const history = await heard(2), entered = deferred(), result = deferred();
  const job = history.fill(4, 4, () => { entered.resolve(); return result.promise; }); await entered.promise;
  history.clear(); const fresh = history.fill(4, 4, () => [track('new-session')]);
  result.resolve([track('old-session')]); await Promise.all([job, fresh]);
  assert.equal(history.advance().url, 'new-session'); assert.equal(history.playedPicks.length, 1); assert.equal(history.upcoming(3).length, 0);
});
await test('manual selection invalidates suspended advance work', async () => {
  const history = await heard(5), entered = deferred(), result = deferred(), generation = history.generation;
  const job = history.fill(4, 4, () => { entered.resolve(); return result.promise; }); await entered.promise;
  history.select(1); result.resolve([track('late')]); await job;
  assert.notEqual(history.generation, generation); assert.equal(history.current.url, 'heard-2');
  assert.equal(history.playedPicks.length, 5); assert.equal(history.advance().url, 'heard-3');
});
await test('cancelled draw leaves the record unchanged', async () => {
  const history = await heard(2), entered = deferred(), result = deferred(), controller = new AbortController();
  const job = history.fill(4, 4, () => { entered.resolve(); return result.promise; }, { signal: controller.signal }).catch((error) => error);
  await entered.promise; controller.abort();
  const error = await Promise.race([job, new Promise((resolve) => setTimeout(() => resolve('timeout'), 30))]);
  assert.equal(error.name, 'AbortError'); result.resolve([track('cancelled')]); await Promise.resolve();
  assert.equal(history.playedPicks.length, 2); assert.equal(history.upcoming(3).length, 0);
});
await test('draw deduplicates history and candidates but small pools may repeat', async () => {
  const history = await heard(2), requests = [];
  await history.fill(4, 4, (count) => { requests.push(count); return [track('heard-1'), track('fresh'), track('fresh')]; });
  assert.deepEqual(requests, [16]); assert.deepEqual(names(history.upcoming(4)), ['fresh']); history.advance();
  await history.fill(4, 4, (count) => { requests.push(count); return [track('heard-1')]; });
  assert.deepEqual(requests, [16, 16, 4]); assert.equal(history.advance().url, 'heard-1'); assert.equal(history.playedPicks.length, 4);
});
await test('cancelling a queued fill retains serialization until the previous owner finishes', async () => {
  const history = await heard(2), entered = deferred(), result = deferred(), controller = new AbortController();
  const first = history.fill(3, 4, () => { entered.resolve(); return result.promise; }); await entered.promise;
  const second = history.fill(3, 4, () => { throw new Error('Cancelled waiter entered'); }, { signal: controller.signal }).catch((error) => error);
  controller.abort(); assert.equal((await second).name, 'AbortError'); let draws = 0;
  const third = history.fill(3, 4, () => { draws++; return []; }); await Promise.resolve(); assert.equal(draws, 0);
  result.resolve(items(3, 'fresh')); await Promise.all([first, third]); assert.equal(draws, 0); assert.equal(history.upcoming(3).length, 3);
});
await test('published arrays are immutable snapshots unaffected by later scope changes', async () => {
  const history = await heard(3), before = history.playedPicks;
  assert.throws(() => before.push(track('invalid')), TypeError);
  await history.fill(3, 4, () => items(3, 'fresh'));
  assert.throws(() => history.upcoming(3).push(track('invalid')), TypeError);
  history.select(0); history.changeScope(); assert.equal(history.playedPicks, before);
  assert.deepEqual(names(before), ['heard-1', 'heard-2', 'heard-3']);
});
assert.ok(checks > 0); console.log(`${checks} RandomHistory cases passed (15 native + 2 browser cancellation/snapshot guards)`);
