// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { HistoryWrites } from '../web/src/history-writes.js';
import { HistoryPresentationReads } from '../web/src/history-presentation.js';
let checks = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; } checks++; console.log(`✓ ${name}`); };
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; };
await test('record publishes its revision after persistence, before qualification', async () => {
  const gate = deferred();
  const writes = new HistoryWrites({ record: () => gate.promise, qualify: () => {}, erase: () => {} });
  const job = writes.record({ url: 'a' }); await Promise.resolve();
  assert.equal(writes.revision.number, 0); gate.resolve(); await job;
  assert.deepEqual(writes.revision, { number: 1, clearing: false });
});
await test('Clear waits for active writes, rejects queued old work and retains new plays', async () => {
  const gate = deferred(), rows = new Set();
  const writes = new HistoryWrites({ record: async (entry) => { await gate.promise; rows.add(entry.url); },
    qualify: (url) => rows.add(`qualified:${url}`), erase: () => rows.clear() });
  const old = writes.record({ url: 'old' }, 0); await Promise.resolve();
  const qualify = writes.qualify({ trackId: 'old', historyGeneration: 0 });
  const clear = writes.clear(); assert.equal(writes.revision.clearing, true);
  const late = writes.record({ url: 'late' }, 0), fresh = writes.record({ url: 'new' }, writes.generation);
  gate.resolve(); await Promise.all([old, qualify, clear, late, fresh]);
  assert.deepEqual([...rows], ['new']); assert.equal(writes.revision.clearing, false);
});
await test('qualification follows record without changing its ordering', async () => {
  const events = [];
  const writes = new HistoryWrites({ record: (entry) => events.push(`record:${entry.url}`),
    qualify: (url) => events.push(`qualify:${url}`), erase: () => events.splice(0) });
  await Promise.all([writes.record({ url: 'a' }, 0), writes.qualify({ trackId: 'a', historyGeneration: 0 })]);
  assert.deepEqual(events, ['record:a', 'qualify:a']); assert.equal(writes.revision.number, 2);
});
await test('failed writes do not poison subsequent writes and Clear reports erase failure', async () => {
  const errors = [], rows = [];
  const writes = new HistoryWrites({ record: (entry) => { if (entry.url === 'bad') throw Error('quota'); rows.push(entry.url); },
    qualify: () => {}, erase: () => { throw Error('denied'); }, failed: (error) => errors.push(error.message) });
  assert.equal(await writes.record({ url: 'bad' }), false); await writes.record({ url: 'good' });
  await assert.rejects(writes.clear(), /denied/); await writes.record({ url: 'after' });
  assert.deepEqual(rows, ['good', 'after']); assert.deepEqual(errors, ['quota', 'denied']); assert.equal(writes.revision.clearing, false);
});
await test('a second queued Clear keeps presentation clearing until both erases finish', async () => {
  const first = deferred(), second = deferred(); let erases = 0;
  const writes = new HistoryWrites({ record: () => {}, qualify: () => {}, erase: () => (++erases === 1 ? first : second).promise });
  const a = writes.clear(); await Promise.resolve(); const b = writes.clear();
  first.resolve(); await a; assert.equal(writes.revision.clearing, true);
  second.resolve(); await b; assert.equal(writes.revision.clearing, false);
});
const row = (name, listened = true) => Object.freeze({ name, listened });
await test('record revisions refresh the same IDs before qualification and Clear empties them', async () => {
  let revision = { number: 0, clearing: false }, name = 'First'; const shown = [];
  const reader = new HistoryPresentationReads({ currentRevision: () => revision,
    query: async () => new Map([['a', row(name, false)]]), show: (rows) => shown.push(rows) });
  await reader.refresh(['a'], revision); assert.deepEqual(shown.at(-1).get('a'), row('First', false));
  name = 'Better'; revision = { number: 1, clearing: false }; await reader.refresh(['a'], revision);
  assert.deepEqual(shown.at(-1).get('a'), row('Better', false));
  revision = { number: 2, clearing: true }; await reader.refresh(['a'], revision); assert.equal(shown.at(-1).size, 0);
});
await test('Clear rejects a noncancellable old read and waits before querying again', async () => {
  let revision = { number: 0, clearing: false }, queries = 0; const gate = deferred(), shown = [];
  const reader = new HistoryPresentationReads({ currentRevision: () => revision,
    query: async () => { if (++queries === 1) { await gate.promise; return new Map([['old', row('Old')]]); } return new Map(); }, show: (rows) => shown.push(rows) });
  const old = reader.refresh(['old'], revision); revision = { number: 1, clearing: true };
  await reader.refresh(['old'], revision); gate.resolve(); await old;
  assert.equal(shown.some((rows) => rows.has('old')), false); assert.equal(queries, 1);
  revision = { number: 2, clearing: false }; await reader.refresh(['old'], revision); assert.equal(queries, 2);
});
await test('only requested IDs are queried; known marks survive a delayed refresh', async () => {
  let revision = { number: 0, clearing: false }, gate = null; const requested = [], shown = [];
  const reader = new HistoryPresentationReads({ currentRevision: () => revision,
    query: async (ids) => { requested.push([...ids]); await gate?.promise; return new Map([...ids].map((id) => [id, row(id)])); }, show: (rows) => shown.push(rows) });
  await reader.refresh(['a'], revision); gate = deferred(); revision = { number: 1, clearing: false };
  const refreshing = reader.refresh(['a'], revision); assert.equal(shown.at(-1).has('a'), true);
  gate.resolve(); await refreshing; await reader.refresh(['b'], revision);
  assert.deepEqual(requested, [['a'], ['a'], ['b']]); assert.deepEqual([...shown.at(-1).keys()], ['b']);
});
await test('navigation retires a noncancellable result and snapshots do not alias query rows', async () => {
  const old = deferred(), shown = []; let revision = { number: 0, clearing: false };
  const live = { name: 'B', listened: true };
  const reader = new HistoryPresentationReads({ currentRevision: () => revision,
    query: (ids) => ids.has('a') ? old.promise : new Map([['b', live], ['extra', row('Extra')]]), show: (rows) => shown.push(rows) });
  const a = reader.refresh(['a'], revision); await reader.refresh(['b'], revision);
  live.listened = false; old.resolve(new Map([['a', row('A')]])); await a;
  assert.deepEqual([...shown.at(-1).keys()], ['b']); assert.equal(shown.at(-1).get('b').listened, true);
});
await test('an unavailable read reports failure and the same visible IDs can retry', async () => {
  let fails = true, errors = 0, last;
  const revision = { number: 0, clearing: false };
  const reader = new HistoryPresentationReads({ currentRevision: () => revision,
    query: () => { if (fails) throw Error('unavailable'); return new Map([['a', row('A')]]); },
    show: (rows) => { last = rows; }, failed: () => { errors++; } });
  await reader.refresh(['a'], revision); assert.equal(errors, 1); fails = false;
  await reader.refresh(['a'], revision); assert.equal(last.get('a').listened, true);
});
console.log(`${checks} History writer/presentation cases passed.`);
