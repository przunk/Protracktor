// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { cleanRecentQueries, recordRecentQuery, recentSearchSuggestions, RecentSearchHistory } from '../web/src/recent-searches.js';

let checks = 0;
const test = async (name, run) => {
  try { await run(); } catch (error) { console.error('✗ ' + name); throw error; }
  checks++; console.log('✓ ' + name);
};
const list = (value) => value === '-' ? [] : value.split('|').map(decodeURIComponent);
const lines = fs.readFileSync('docs/rules/recent-search-cases.tsv', 'utf8')
  .split('\n').filter((line) => line && !line.startsWith('#'));
assert.equal(lines.shift(), 'case\top\tqueries\tinput\texpected');
assert.ok(lines.length);
for (const line of lines) {
  const [name, op, queries, input, expected] = line.split('\t');
  await test('shared Android case: ' + name, () => {
    const given = list(queries), original = [...given];
    const actual = op === 'clean' ? cleanRecentQueries(given)
      : op === 'record' ? recordRecentQuery(given, decodeURIComponent(input))
        : recentSearchSuggestions(given, decodeURIComponent(input));
    assert.deepEqual(actual, list(expected));
    assert.deepEqual(given, original);
  });
}
await test('blank submissions preserve the original collection', () => {
  const queries = ['one', 'two'];
  assert.equal(recordRecentQuery(queries, ' \n\t '), queries);
});
await test('malformed stored values are filtered without a write', async () => {
  let writes = 0;
  const history = new RecentSearchHistory({ read: async () => [null, 5, ' old ', 'OLD', ''], write: async () => { writes++; } });
  await history.ready;
  assert.deepEqual(history.queries, ['old']); assert.equal(writes, 0);
});
await test('a record waits for the initial read and writes before publishing', async () => {
  let releaseRead, releaseWrite;
  const history = new RecentSearchHistory({
    read: () => new Promise((resolve) => { releaseRead = resolve; }),
    write: (next) => { assert.deepEqual(history.queries, ['old']); assert.deepEqual(next, ['new', 'old']);
      return new Promise((resolve) => { releaseWrite = resolve; }); },
  });
  const pending = history.record('new');
  releaseRead(['old']); await history.ready; await new Promise((r) => setImmediate(r));
  assert.deepEqual(history.queries, ['old']); releaseWrite(); await pending;
  assert.deepEqual(history.queries, ['new', 'old']);
});
await test('rapid submissions serialize and identical values do not write again', async () => {
  const writes = [];
  const history = new RecentSearchHistory({ read: async () => ['old'], write: async (next) => { writes.push([...next]); } });
  await Promise.all([history.record('one'), history.record('two'), history.record('two')]);
  assert.deepEqual(history.queries, ['two', 'one', 'old']); assert.equal(writes.length, 2);
});
await test('failed writes preserve state and do not poison later writes', async () => {
  let fail = true;
  const history = new RecentSearchHistory({ read: async () => ['old'], write: async () => { if (fail) throw new Error('quota'); } });
  await assert.rejects(history.record('failed'), /quota/); assert.deepEqual(history.queries, ['old']);
  fail = false; await history.record('new'); assert.deepEqual(history.queries, ['new', 'old']);
});
await test('an unavailable read is retried before a write can replace existing data', async () => {
  let fail = true, writes = 0;
  const history = new RecentSearchHistory({ read: async () => { if (fail) throw new Error('blocked'); return ['retained']; },
    write: async () => { writes++; } });
  await history.ready; await assert.rejects(history.record('first'), /blocked/); assert.equal(writes, 0);
  fail = false; await history.record('second'); assert.deepEqual(history.queries, ['second', 'retained']);
});
console.log(`${checks} recent-search cases passed.`);
