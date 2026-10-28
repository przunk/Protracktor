// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { planFolderPrefetch, runFolderPrefetch, storedCacheAhead, cacheAheadAllowed, CacheAheadPreferences, FolderPrefetch, FOLDER_PREFETCH_BUDGET, FOLDER_PREFETCH_PARALLEL } from '../web/src/folder-prefetch.js';
const track = (url, size = 1) => ({ url, size });
const tick = () => new Promise((done) => setImmediate(done));
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
const until = async (predicate) => { for (let i = 0; i < 50; i++) { if (predicate()) return; await tick(); } throw new Error('Boundary not reached'); };
let checks = 0;
async function test(name, run) { try { await run(); } catch (error) { console.error('✗ ' + name); throw error; } checks++; console.log('✓ ' + name); }

await test('the shared folder cases preserve order, cached exemptions and stop boundaries', async () => {
  const lines = fs.readFileSync('docs/rules/folder-prefetch-cases.tsv', 'utf8').trim().split('\n');
  const count = Number(lines.find((line) => line.startsWith('# cases: ')).slice(9));
  const rows = lines.filter((line) => line && !line.startsWith('#')); assert.equal(rows.shift(), 'case\tsizes\tcached\tbudget\texpected');
  assert(count > 0); assert.equal(rows.length, count);
  const ids = (value) => value === '-' ? [] : value.split(',').map((n) => 't' + n);
  for (const row of rows) {
    const [name, sizes, cached, budget, expected] = row.split('\t');
    const tracks = sizes === '-' ? [] : sizes.split(',').map((n, i) => track('t' + i, Number(n)));
    assert.deepEqual(planFolderPrefetch(tracks, new Set(ids(cached)), Number(budget)), ids(expected), name);
  }
  assert.equal(FOLDER_PREFETCH_BUDGET, 100 * 1024 * 1024); assert.equal(FOLDER_PREFETCH_PARALLEL, 3);
});
await test('native stored modes and metering decisions include an explicit unknown fallback', async () => {
  assert.equal(storedCacheAhead(null), 'wifi'); assert.equal(storedCacheAhead('nonsense'), 'wifi');
  for (const mode of ['wifi', 'always', 'off']) assert.equal(storedCacheAhead(mode), mode);
  assert.equal(cacheAheadAllowed('wifi', false), true); assert.equal(cacheAheadAllowed('wifi', true), false); assert.equal(cacheAheadAllowed('wifi', null), false);
  for (const metered of [null, false, true]) { assert.equal(cacheAheadAllowed('always', metered), true); assert.equal(cacheAheadAllowed('off', metered), false); }
});
await test('at most three workers start, in folder order, and each row is attempted once', async () => {
  const started = [], gate = deferred(); let running = 0, most = 0;
  const job = runFolderPrefetch(['a', 'b', 'c', 'd', 'e'], { fetch: async () => { running++; most = Math.max(most, running); await gate.promise; running--; return true; }, onStart: (url) => started.push(url) });
  assert.deepEqual(started, ['a', 'b', 'c']); gate.resolve(); await job;
  assert.equal(most, 3); assert.deepEqual(started, ['a', 'b', 'c', 'd', 'e']);
});
await test('ordinary failures are reported once and do not retry or halt later candidates', async () => {
  const attempts = [], done = [];
  await runFolderPrefetch(['bad', 'good'], { parallel: 1, fetch: async (url) => { attempts.push(url); if (url === 'bad') throw new Error('404'); return true; }, onDone: (url, ok) => done.push([url, ok]) });
  assert.deepEqual(attempts, ['bad', 'good']); assert.deepEqual(done, [['bad', false], ['good', true]]);
});
await test('a changed metering decision stops everything not yet started', async () => {
  let allowed = true; const attempts = [];
  await runFolderPrefetch(['a', 'b', 'c'], { parallel: 1, mayContinue: () => allowed, fetch: async (url) => { attempts.push(url); allowed = false; return true; } });
  assert.deepEqual(attempts, ['a']);
});
await test('leaving the folder starts nothing after the first three waiters', async () => {
  const started = [], gate = deferred(), controller = new AbortController();
  const job = runFolderPrefetch(['a', 'b', 'c', 'd', 'e'], { signal: controller.signal, fetch: () => gate.promise, onStart: (url) => started.push(url) });
  controller.abort(); gate.resolve(true); await job; assert.deepEqual(started, ['a', 'b', 'c']);
});
await test('nonpositive worker counts use one worker as native does', async () => {
  const starts = [], gate = deferred();
  const job = runFolderPrefetch(['a', 'b'], { parallel: 0, fetch: () => gate.promise, onStart: (id) => starts.push(id) });
  assert.deepEqual(starts, ['a']); gate.resolve(true); await job; assert.deepEqual(starts, ['a', 'b']);
});
await test('preference restoration reads without writes and refusal preserves the selected runtime mode', async () => {
  const data = new Map(), writes = [];
  const store = { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => { writes.push([key, value]); data.set(key, value); } };
  const preferences = new CacheAheadPreferences(() => store); assert.equal(preferences.read().value, 'wifi'); assert.deepEqual(writes, []);
  assert.equal(preferences.write('always'), null); assert.equal(preferences.read().value, 'always'); assert.deepEqual(writes, [['protracktor.cacheAhead', 'always']]);
  assert.throws(() => preferences.write('all'), TypeError);
  const failure = new Error('storage refused'); const refused = new CacheAheadPreferences(() => { throw failure; });
  assert.equal(refused.read().failure, failure); assert.equal(refused.read().value, 'wifi'); assert.equal(refused.write('off'), failure);
});
await test('unknown metering gives a paused state and never starts unsolicited default downloads', async () => {
  const calls = [], state = [], controller = new FolderPrefetch({ fetch: async (ref) => { calls.push(ref.url); return true; }, cached: () => false, changed: (s) => state.push(s) });
  controller.setFolder('modland/format/author', [track('a')]); await controller.completion;
  assert.equal(controller.mode, 'wifi'); assert.equal(controller.state.blocked, 'unknown'); assert.deepEqual(calls, []);
  controller.setMode('always'); await controller.completion; assert.deepEqual(calls, ['a']); assert.equal(controller.state.blocked, null); assert.deepEqual(controller.state.fetching, []);
});
await test('same folder facts retain pending workers and a new folder retires old completion state', async () => {
  const gates = new Map(['a', 'b', 'c', 'fresh'].map((id) => [id, deferred()])), calls = [];
  const controller = new FolderPrefetch({ mode: 'always', fetch: (ref) => { calls.push(ref.url); return gates.get(ref.url).promise; }, cached: () => false });
  controller.setFolder('old', ['a', 'b', 'c', 'never'].map((id) => track(id))); const old = controller.completion;
  await until(() => calls.length === 3); controller.setFolder('old', ['a', 'b', 'c', 'never'].map((id) => track(id)));
  assert.deepEqual(calls, ['a', 'b', 'c']); assert.equal(controller.completion, old);
  controller.setFolder('new', [track('fresh')]); const fresh = controller.completion; await until(() => calls.includes('fresh'));
  for (const id of ['a', 'b', 'c']) gates.get(id).resolve(true); await old;
  assert.equal(controller.state.key, 'new'); assert.deepEqual(controller.state.fetching, ['fresh']); assert(!calls.includes('never'));
  gates.get('fresh').resolve(true); await fresh; assert.deepEqual(controller.state.fetching, []);
});
await test('Off and leaving both cancel pending waiters and leave prior results unable to restore UI', async () => {
  for (const leave of [false, true]) {
    const gate = deferred(); let signal;
    const controller = new FolderPrefetch({ mode: 'always', cached: () => false, fetch: (ref, options) => { signal = options.signal; return gate.promise; } });
    controller.setFolder('old', [track('a')]); const old = controller.completion;
    await until(() => signal); if (leave) controller.setFolder(null); else controller.setMode('off');
    assert.equal(signal.aborted, true); gate.resolve(true); await old;
    assert.deepEqual(controller.state.fetching, []); assert.equal(controller.state.key, leave ? null : 'old');
  }
});
await test('already held files need no background work or misleading network warning', async () => {
  let calls = 0;
  const controller = new FolderPrefetch({ cached: () => true, fetch: () => { calls++; } });
  controller.setFolder('held', [track('a')]); await controller.completion; assert.equal(calls, 0); assert.equal(controller.state.blocked, null);
});
await test('a synchronous state observer cannot replace the newer completion with an old early return', async () => {
  const gate = deferred(); let controller, reentered = false;
  controller = new FolderPrefetch({ mode: 'wifi', cached: () => false, fetch: () => gate.promise, changed: (state) => {
    if (state.blocked && !reentered) { reentered = true; controller.setMode('always'); }
  } });
  controller.setFolder('folder', [track('a')]); let done = false; controller.completion.then(() => { done = true; }); await tick();
  assert.equal(done, false); gate.resolve(true); await controller.completion; assert.deepEqual(controller.state.fetching, []);
});
await test('cancellation from the first progress update prevents even that fetch from starting', async () => {
  const calls = []; let controller, cancelled = false;
  controller = new FolderPrefetch({ mode: 'always', cached: () => false, fetch: async (ref) => { calls.push(ref.url); return true; }, changed: (state) => {
    if (state.fetching.length && !cancelled) { cancelled = true; controller.setMode('off'); }
  } });
  controller.setFolder('folder', ['a', 'b', 'c', 'd'].map((id) => track(id)));
  const old = controller.completion; await old; await controller.completion;
  assert.equal(controller.mode, 'off'); assert.deepEqual(calls, []); assert.deepEqual(controller.state.fetching, []);
});
await test('a cleared-folder observer cannot replace the new folder completion with a resolved old one', async () => {
  const gate = deferred(); let controller, redirect = false;
  controller = new FolderPrefetch({ mode: 'always', cached: () => false, fetch: () => gate.promise, changed: (state) => {
    if (state.key === null && redirect) { redirect = false; controller.setFolder('fresh', [track('fresh')]); }
  } });
  controller.setFolder('old', [track('old')]); const old = controller.completion;
  await until(() => controller.state.fetching.length === 1); redirect = true; controller.setFolder(null);
  let done = false; controller.completion.then(() => { done = true; }); await tick();
  assert.equal(controller.state.key, 'fresh'); assert.equal(done, false);
  gate.resolve(true); await old; await controller.completion; assert.deepEqual(controller.state.fetching, []);
});
assert.equal(checks, 15);
console.log('15 production folder/policy/lifecycle cases pass.');
