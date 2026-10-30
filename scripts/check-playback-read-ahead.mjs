// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Current native PlaybackReadAheadTest scenarios through the production WEB owner.
import assert from 'node:assert/strict';
import { PlaybackReadAhead, readAheadCandidates, READ_AHEAD_COUNT, MAX_PREFETCH_BYTES, MAX_PREFETCH_TOTAL_BYTES } from '../web/src/playback-read-ahead.js';
const track = (url) => ({ url }), byte = (n) => new Uint8Array([n]).buffer;
const tick = () => new Promise((done) => setImmediate(done));
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
let checks = 0;
async function test(name, run) { try { await run(); } catch (error) { console.error('✗ ' + name); throw error; } checks++; console.log('✓ ' + name); }

await test('a late result for the same ID cannot replace the new preparation', async () => {
  const old = deferred(), fresh = byte(2); let first = true;
  const ahead = new PlaybackReadAhead(() => { if (first) { first = false; return old.promise; } return fresh; });
  const before = ahead.prepare([track('same')]); await ahead.prepare([track('same')]); old.resolve(byte(1)); await before;
  assert.equal(ahead.take('same'), fresh);
});
await test('synchronous cancellation reentrancy wins over the outer preparation', async () => {
  const calls = []; let ahead, nested;
  ahead = new PlaybackReadAhead((ref, { signal }) => {
    calls.push(ref.url);
    if (ref.url !== 'old') return byte(1);
    return new Promise((resolve, reject) => signal.addEventListener('abort', () => {
      nested = ahead.prepare([track('reentrant')]); reject(new DOMException('cancelled', 'AbortError'));
    }, { once: true }));
  });
  const old = ahead.prepare([track('old')]); await ahead.prepare([track('outer')]); await nested; await old;
  assert.deepEqual(calls, ['old', 'reentrant']); assert.equal(ahead.take('outer'), null); assert(ahead.take('reentrant'));
});
await test('empty replacement and Clear reject cancellation-resistant results', async () => {
  for (const clear of [false, true]) {
    const gate = deferred(), ahead = new PlaybackReadAhead(() => gate.promise);
    const old = ahead.prepare([track('old')]); if (clear) ahead.clear(); else await ahead.prepare([]);
    gate.resolve(byte(9)); await old; assert.equal(ahead.take('old'), null);
  }
});
await test('three reads start concurrently and every completed buffer is consumed once', async () => {
  const calls = [], gates = new Map(['a', 'b', 'c'].map((id) => [id, deferred()]));
  const ahead = new PlaybackReadAhead((ref) => { calls.push(ref.url); return gates.get(ref.url).promise; });
  const job = ahead.prepare(['a', 'b', 'c'].map(track)); assert.deepEqual(calls, ['a', 'b', 'c']);
  for (const id of ['c', 'b', 'a']) gates.get(id).resolve(byte(id.charCodeAt(0)));
  await job;
  for (const id of ['a', 'b', 'c']) { assert.equal(new Uint8Array(ahead.take(id))[0], id.charCodeAt(0)); assert.equal(ahead.take(id), null); }
});
await test('only wanted completed entries survive and consumed entries may be fetched again', async () => {
  const calls = [], buffers = new Map(['a', 'b', 'c'].map((id) => [id, byte(id.charCodeAt(0))]));
  const ahead = new PlaybackReadAhead((ref) => { calls.push(ref.url); return buffers.get(ref.url); });
  await ahead.prepare(['a', 'b'].map(track)); await ahead.prepare(['b', 'c'].map(track));
  assert.deepEqual(calls, ['a', 'b', 'c']); assert.equal(ahead.take('a'), null);
  assert.equal(ahead.take('b'), buffers.get('b')); assert.equal(ahead.take('c'), buffers.get('c'));
  await ahead.prepare([track('b')]); assert.deepEqual(calls, ['a', 'b', 'c', 'b']);
});
await test('the per-file limit is inclusive and an empty buffer remains a valid result', async () => {
  const buffers = new Map([['limit', new ArrayBuffer(4 * 1024 * 1024)], ['over', new ArrayBuffer(4 * 1024 * 1024 + 1)], ['missing', null], ['empty', new ArrayBuffer(0)]]);
  const ahead = new PlaybackReadAhead((ref) => buffers.get(ref.url)); await ahead.prepare([...buffers.keys()].map(track));
  assert.equal(ahead.take('limit'), buffers.get('limit')); assert.equal(ahead.take('over'), null); assert.equal(ahead.take('missing'), null); assert.equal(ahead.take('empty'), buffers.get('empty'));
});
await test('the total budget uses completion order and taking a buffer frees capacity', async () => {
  const gates = new Map(['a', 'b', 'c', 'd'].map((id) => [id, deferred()]));
  const ahead = new PlaybackReadAhead((ref) => gates.get(ref.url).promise), fourMb = new ArrayBuffer(4 * 1024 * 1024);
  const job = ahead.prepare(['a', 'b', 'c'].map(track)); gates.get('a').resolve(fourMb); await tick(); gates.get('b').resolve(fourMb); await tick(); gates.get('c').resolve(byte(1)); await job;
  assert.equal(ahead.sizeBytes, 8 * 1024 * 1024); assert.equal(ahead.take('c'), null); assert.equal(ahead.take('a'), fourMb);
  const next = ahead.prepare(['b', 'd'].map(track)); gates.get('d').resolve(fourMb); await next;
  assert.equal(ahead.take('b'), fourMb); assert.equal(ahead.take('d'), fourMb);
});
await test('a new missing selection cancels the prior waiter and stores only the replacement', async () => {
  let cancelled = false;
  const ahead = new PlaybackReadAhead((ref, { signal }) => ref.url === 'old'
    ? new Promise((resolve, reject) => signal.addEventListener('abort', () => { cancelled = true; reject(new DOMException('cancelled', 'AbortError')); }, { once: true })) : byte(2));
  const before = ahead.prepare([track('old')]); await ahead.prepare([track('new')]); await before;
  assert.equal(cancelled, true); assert.equal(ahead.take('old'), null); assert.equal(new Uint8Array(ahead.take('new'))[0], 2);
});
await test('Clear cancels pending work, drops held bytes and allows a fresh preparation', async () => {
  let cancelled = false;
  const ahead = new PlaybackReadAhead((ref, { signal }) => ref.url === 'pending'
    ? new Promise((resolve, reject) => signal.addEventListener('abort', () => { cancelled = true; reject(new DOMException('cancelled', 'AbortError')); }, { once: true })) : byte(3));
  const before = ahead.prepare(['stored', 'pending'].map(track)); await tick(); ahead.clear(); await before;
  assert.equal(cancelled, true); assert.equal(ahead.take('stored'), null); assert.equal(ahead.take('pending'), null); assert.deepEqual(ahead.ids, []);
  await ahead.prepare([track('fresh')]); assert.equal(new Uint8Array(ahead.take('fresh'))[0], 3);
});
await test('an empty or fully held replacement retires pending reads before its early return', async () => {
  for (const keep of [false, true]) {
    let cancelled = false;
    const ahead = new PlaybackReadAhead((ref, { signal }) => ref.url === 'pending'
      ? new Promise((resolve, reject) => signal.addEventListener('abort', () => { cancelled = true; reject(new DOMException('cancelled', 'AbortError')); }, { once: true })) : byte(4));
    const before = ahead.prepare(['stored', 'pending'].map(track)); await tick(); await ahead.prepare(keep ? [track('stored')] : []); await before;
    assert.equal(cancelled, true); assert.equal(ahead.take('pending'), null); assert.equal(ahead.take('stored') !== null, keep);
  }
});
await test('an unexpected read failure cancels siblings and is visible to its caller', async () => {
  const failure = new Error('synthetic read failure'); let cancelled = false;
  const ahead = new PlaybackReadAhead((ref, { signal }) => {
    if (ref.url === 'failing') throw failure;
    return new Promise((resolve, reject) => signal.addEventListener('abort', () => { cancelled = true; reject(new DOMException('cancelled', 'AbortError')); }, { once: true }));
  });
  await assert.rejects(ahead.prepare(['waiting', 'failing'].map(track)), (error) => error === failure);
  assert.equal(cancelled, true); assert.equal(ahead.take('waiting'), null);
});
await test('manual FIFO leads, duplicate files are read once and at most three are wanted', async () => {
  const a = track('a'); assert.deepEqual(readAheadCandidates([a, a, track('b')], [track('b'), track('c'), track('d')]).map((ref) => ref.url), ['a', 'b', 'c']);
  assert.deepEqual(readAheadCandidates([{url:'local',local:true}], [a]), [a]); assert.equal(READ_AHEAD_COUNT, 3);
});
assert.equal(MAX_PREFETCH_BYTES, 4 * 1024 * 1024); assert.equal(MAX_PREFETCH_TOTAL_BYTES, 8 * 1024 * 1024); assert.equal(checks, 12);
console.log('12 production read-ahead cases pass; first eleven port the current native tests.');
