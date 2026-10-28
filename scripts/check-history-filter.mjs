// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { historyMatches, HistoryPageScan } from '../web/src/history-filter.js';
import { HistoryBrowser } from '../web/src/history-browser.js';

let checks = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error('✗ ' + name); throw error; }
  checks++; console.log('✓ ' + name); };
const rows = fs.readFileSync('docs/rules/history-filter-cases.tsv', 'utf8').split('\n').filter((line) => line && !line.startsWith('#'));
assert.equal(rows.shift(), 'case\tquery\ttitle\tauthor\tfile\texpected'); assert.ok(rows.length);
for (const line of rows) await test('shared Android match: ' + line.split('\t')[0], () => {
  const [name, ...cells] = line.split('\t');
  assert.equal(historyMatches(...cells.slice(0, 4).map(decodeURIComponent)), cells[4] === 'true', name);
});
await test('100007 streamed matches retain only the requested hundred', () => {
  const scan = new HistoryPageScan(1);
  for (let i = 0; i < 100007; i++) scan.accept(i);
  assert.deepEqual(scan.result(), { total: 100007, page: 1, rows: Array.from({ length: 100 }, (_, i) => i + 100) });
});
await test('shrinking and enormous pages return the last partial page; empty/negative clamp', () => {
  for (const wanted of [3, 2147483647]) {
    const scan = new HistoryPageScan(wanted);
    for (let i = 0; i < 207; i++) scan.accept(i);
    assert.deepEqual(scan.result(), { total: 207, page: 2, rows: [200, 201, 202, 203, 204, 205, 206] });
  }
  assert.deepEqual(new HistoryPageScan(9).result(), { total: 0, page: 0, rows: [] });
  const negative = new HistoryPageScan(-1); [0, 1, 2].forEach((row) => negative.accept(row));
  assert.deepEqual(negative.result(), { total: 3, page: 0, rows: [0, 1, 2] });
});
const tick = () => new Promise((resolve) => setImmediate(resolve));
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };
function fixture() {
  const timers = new Map(), calls = [], errors = [];
  let now = 0, nextTimer = 0;
  const f = { timers, calls, errors, reader: async (query, page) => ({ total: 1, page, rows: [query || 'all'] }) };
  f.browser = new HistoryBrowser({ read: (query, page, signal) => { calls.push([query, page]); return f.reader(query, page, signal); },
    failed: (error) => errors.push(error), setTimer: (run, ms) => { const id = ++nextTimer; timers.set(id, { run, at: now + ms }); return id; },
    cancelTimer: (id) => timers.delete(id) });
  f.advance = async (ms = 200) => { now += ms; for (const [id, timer] of [...timers]) if (timer.at <= now) { timers.delete(id); timer.run(); } await tick(); };
  return f;
}
await test('edits debounce exactly 200ms and only the latest trimmed query reads page zero', async () => {
  const f = fixture(); await f.browser.open();
  const first = f.browser.setQuery('superseded'), last = f.browser.setQuery(' latest ');
  assert.equal(f.browser.state.loading, true); assert.deepEqual(f.browser.state.rows, []);
  await f.advance(199); assert.deepEqual(f.calls, [['', 0]]);
  await f.advance(1); await Promise.all([first, last]);
  assert.deepEqual(f.calls, [['', 0], ['latest', 0]]);
  assert.equal(f.browser.state.query, ' latest '); assert.equal(f.browser.state.page, 0);
});
await test('an uncooperative identical-input read cannot replace a newer occurrence', async () => {
  const f = fixture(); await f.browser.open(); const gate = deferred();
  f.reader = () => gate.promise;
  const old = f.browser.setQuery('same'); await f.advance();
  f.browser.setQuery('intermediate'); f.reader = async () => ({ total: 1, page: 0, rows: ['same-2'] });
  const latest = f.browser.setQuery('same'); await f.advance(); await latest;
  gate.resolve({ total: 1, page: 0, rows: ['same-1'] }); await old;
  assert.deepEqual(f.browser.state.rows, ['same-2']); assert.deepEqual(f.errors, []);
});
await test('leave and new visit retire late errors/results without changing the new view', async () => {
  const f = fixture(); await f.browser.open(); const gate = deferred(); f.reader = () => gate.promise;
  const old = f.browser.setQuery('old'); await f.advance(); f.browser.leave();
  f.reader = async () => ({ total: 1, page: 0, rows: ['new visit'] }); await f.browser.open();
  gate.reject(new Error('obsolete failure')); await old;
  assert.deepEqual(f.browser.state.rows, ['new visit']); assert.equal(f.browser.state.query, ''); assert.deepEqual(f.errors, []);
});
await test('current error clears loading and unchanged phrase can retry', async () => {
  const f = fixture(); await f.browser.open(); f.reader = async () => { throw new Error('current'); };
  const failed = f.browser.setQuery('retry'); await f.advance(); await failed;
  assert.equal(f.browser.state.failed, true); assert.equal(f.browser.state.loading, false); assert.equal(f.errors.length, 1);
  f.reader = async () => ({ total: 1, page: 0, rows: ['retry'] });
  const retry = f.browser.setQuery('retry'); await f.advance(); await retry; assert.equal(f.browser.state.failed, false);
});
await test('Clear cancels pending debounce and erases even if the view is left', async () => {
  const f = fixture(); await f.browser.open(); const pending = f.browser.setQuery('pending');
  let erased = false; const gate = deferred();
  const clear = f.browser.clear(async () => { await gate.promise; erased = true; });
  f.browser.leave(); gate.resolve(); await Promise.all([clear, pending]); await f.advance();
  assert.equal(erased, true); assert.deepEqual(f.calls, [['', 0]]);
});
await test('failed Clear reloads a cancelled filter and reports the refusal', async () => {
  const f = fixture(); await f.browser.open(); f.browser.setQuery('pending'); let failures = 0;
  await f.browser.clear(async () => { throw new Error('clear'); }, () => failures++);
  assert.equal(failures, 1); assert.equal(f.browser.state.loading, false); assert.deepEqual(f.browser.state.rows, ['pending']);
});
await test('Clear retires an already running non-cancellable read before its result returns', async () => {
  const f = fixture(); await f.browser.open(); const gate = deferred(); f.reader = () => gate.promise;
  const old = f.browser.setQuery('old'); await f.advance();
  await f.browser.clear(async () => {});
  gate.resolve({ total: 200, page: 1, rows: ['erased'] }); await old;
  assert.equal(f.browser.state.total, 0); assert.equal(f.browser.state.page, 0);
  assert.deepEqual(f.browser.state.rows, []); assert.equal(f.browser.state.loading, false);
});
await test('leaving without a new visit suppresses an obsolete error', async () => {
  const f = fixture(); await f.browser.open(); const gate = deferred(); f.reader = () => gate.promise;
  const old = f.browser.setQuery('old'); await f.advance(); f.browser.leave(); const departed = f.browser.state;
  gate.reject(new Error('late')); await old;
  assert.equal(f.browser.state, departed); assert.deepEqual(f.errors, []);
});
await test('unchanged input and edits outside History do not read or mutate', async () => {
  const f = fixture(); await f.browser.open(); const shown = f.browser.state;
  await f.browser.setQuery(''); assert.equal(f.browser.state, shown);
  f.browser.leave(); await f.browser.setQuery('ignored'); assert.equal(f.browser.state, shown); assert.equal(f.calls.length, 1);
});
await test('turn is clamped without debounce and source restoration is independent of storage', async () => {
  const f = fixture(); await f.browser.open();
  f.browser.restore({ historyQuery: ' saved ', historyPage: 1, historyRows: ['snapshot'], historyTotal: 207 });
  assert.deepEqual(f.browser.state.rows, ['snapshot']); assert.equal(f.calls.length, 1);
  await f.browser.turn(2147483647); assert.deepEqual(f.calls.at(-1), ['saved', 2]); assert.equal(f.timers.size, 0);
});
console.log(`${checks} History filter/ownership checks passed.`);
