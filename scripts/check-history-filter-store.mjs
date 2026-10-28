// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import { IDBFactory } from '../web/node_modules/fake-indexeddb/build/esm/index.js';

let checks = 0, instance = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error('✗ ' + name); throw error; }
  checks++; console.log('✓ ' + name); };
const requestResult = (request) => new Promise((resolve, reject) => {
  request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
});
async function fresh(rows) {
  globalThis.indexedDB = new IDBFactory();
  const store = await import(`../web/src/store.js?history-filter=${++instance}`);
  await store.played.count();
  const db = await requestResult(indexedDB.open('protracktor'));
  await new Promise((resolve, reject) => {
    const transaction = db.transaction('played', 'readwrite');
    for (const row of rows) transaction.objectStore('played').put(row);
    transaction.oncomplete = resolve; transaction.onabort = () => reject(transaction.error);
  });
  db.close(); return store;
}
const row = (i, extra = {}) => ({ url: `id-${i}`, name: `Other ${i}`, author: '', file: `${i}.mod`, playedAt: 100000 - i,
  playCount: 3, listened: i === 150, replayable: true, ...extra });
await test('production cursor finds older title author and literal filename without changing data', async () => {
  const store = await fresh(Array.from({ length: 200 }, (_, i) => row(i, i === 150
    ? { name: 'Mały żółw', author: 'Hülsbeck' } : i === 199 ? { file: 'special_%!.mod' } : {})));
  const before = await store.played.recent();
  for (const query of ['ŻÓŁW', 'HÜLSBECK', '150.MOD']) assert.equal((await store.played.read(query, 0)).rows[0].url, 'id-150');
  assert.equal((await store.played.read('SPECIAL_%!', 0)).rows[0].url, 'id-199');
  assert.equal((await store.played.read('%', 0)).total, 1);
  assert.deepEqual(await store.played.recent(), before);
  assert.equal((await store.played.read(' ', 0)).total, 200); store.forget();
});
await test('10000-row scan counts all 5000 matches and clamps to the real last page with no bulk read', async () => {
  const store = await fresh(Array.from({ length: 10000 }, (_, i) => row(i, { name: i % 2 === 0 ? `Needle ${i}` : `Other ${i}` })));
  const getAll = IDBObjectStore.prototype.getAll, getAllKeys = IDBObjectStore.prototype.getAllKeys;
  IDBObjectStore.prototype.getAll = IDBObjectStore.prototype.getAllKeys = () => { throw new Error('Whole-History allocation'); };
  try {
    const start = performance.now(), found = await store.played.read('NEEDLE', 1);
    console.log(`host/fake IndexedDB: 10000 rows, 5000 matches, ${(performance.now() - start).toFixed(1)}ms; not browser latency`);
    assert.equal(found.total, 5000); assert.equal(found.page, 1); assert.equal(found.rows.length, 100);
    assert.equal(found.rows[0].url, 'id-200'); assert.equal(found.rows[99].url, 'id-398');
    assert.equal((await store.played.read('needle', 2147483647)).page, 49);
    const unfiltered = await store.played.read('', 2147483647);
    assert.equal(unfiltered.page, 99); assert.equal(unfiltered.rows[0].url, 'id-9900');
  } finally { IDBObjectStore.prototype.getAll = getAll; IDBObjectStore.prototype.getAllKeys = getAllKeys; }
  store.forget();
});
await test('unfiltered count and page share one readonly transaction and skip to the requested hundred', async () => {
  const store = await fresh(Array.from({ length: 207 }, (_, i) => row(i)));
  const transaction = IDBDatabase.prototype.transaction; const transactions = [];
  IDBDatabase.prototype.transaction = function (...args) { const result = transaction.apply(this, args); transactions.push(result); return result; };
  try {
    const found = await store.played.read('', 3);
    assert.equal(found.total, 207); assert.equal(found.page, 2); assert.equal(found.rows.length, 7);
    assert.equal(found.rows[0].url, 'id-200'); assert.equal(transactions.length, 1); assert.equal(transactions[0].mode, 'readonly');
  } finally { IDBDatabase.prototype.transaction = transaction; }
  store.forget();
});
await test('aborted active cursor stops with AbortError and the next read still succeeds', async () => {
  const store = await fresh(Array.from({ length: 207 }, (_, i) => row(i)));
  const controller = new AbortController(), advance = IDBCursor.prototype.continue; let visited = 0;
  IDBCursor.prototype.continue = function (...args) {
    if (++visited === 5) { controller.abort(); return; }
    return advance.apply(this, args);
  };
  try { await assert.rejects(store.played.read('Other', 0, controller.signal), { name: 'AbortError' }); }
  finally { IDBCursor.prototype.continue = advance; }
  assert.equal(visited, 5); assert.equal((await store.played.read('Other', 0)).total, 207);
  const stopped = new AbortController(); stopped.abort();
  await assert.rejects(store.played.read('', 0, stopped.signal), { name: 'AbortError' }); store.forget();
});
await test('ordered writes and Clear complete before a newly requested filtered read', async () => {
  const store = await fresh([]);
  const record = store.played.record(row(1, { name: 'Needle' }));
  assert.equal((await store.played.read('needle', 0)).total, 1); await record;
  const clear = store.played.clear();
  assert.deepEqual(await store.played.read('needle', 9), { total: 0, page: 0, rows: [] }); await clear;
  store.forget();
});
await test('cancellation at transaction completion rejects without aborting an already finished transaction', async () => {
  const store = await fresh([row(1)]), controller = new AbortController();
  const transaction = IDBDatabase.prototype.transaction;
  IDBDatabase.prototype.transaction = function (...args) {
    const result = transaction.apply(this, args);
    result.addEventListener('complete', () => controller.abort(), { once: true });
    return result;
  };
  try { await assert.rejects(store.played.read('', 0, controller.signal), { name: 'AbortError' }); }
  finally { IDBDatabase.prototype.transaction = transaction; }
  store.forget();
});
console.log(`${checks} History filtering storage checks passed.`);
