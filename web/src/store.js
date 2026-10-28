// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// What the page keeps, and keeps across a reload.
//
// **IndexedDB rather than `localStorage`**, which is a few megabytes and synchronous — and the
// index in `docs/PLAN_WEB_LIBRARY.md` S3 is eighteen. Measured on two machines: 473 GB and 256 GB
// offered, `persist()` granted on both, and the whole of Modland written in 2.6 s and 1.9 s. So
// this stores what it needs and reports what it holds; there is no budget to ration and no
// eviction rule to design.
//
// Everything here is promises over the callback API, in one place, because the alternative is
// callbacks in the page.

import { HistoryWrites } from './history-writes.js';
import { savedPlaceId, immutablePlace, compareSavedPlaces } from './saved-places.js';
import { historyMatches, HistoryPageScan } from './history-filter.js';
import { trimQuery } from './query-text.js';
import { HISTORY_PAGE, historyPageClamp } from './rules.js';

const DB = 'protracktor';
const VERSION = 5;

/** The playlist that is not a document: replaced wholesale by every handoff, never deleted. */
export const PHONE = 'phone';

let opening = null;
let connection = null;

function open() {
  if (opening) return opening;
  const attempt = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB, VERSION);
    let abandoned = false;
    request.onupgradeneeded = (event) => {
      if (abandoned) { request.transaction.abort(); return; }
      const db = request.result;
      // `playlists` holds both kinds -- the phone's and the browser's own. One store, because they
      // differ in who writes them and in nothing else the page does with them.
      if (!db.objectStoreNames.contains('playlists')) {
        db.createObjectStore('playlists', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('settings')) {
        db.createObjectStore('settings', { keyPath: 'key' });
      }
      // The archives' indexes, as buckets rather than as rows. `docs/PLAN_WEB_LIBRARY.md` S3 has
      // the measurement: 43,715 records instead of 515,509, 2.6 s instead of 41, and no index --
      // because the key *is* the lookup.
      if (!db.objectStoreNames.contains('catalogue')) {
        db.createObjectStore('catalogue', { keyPath: 'key' });
      }
      // Version 3: what has been played, one row per tune, keyed by its address.
      const played = db.objectStoreNames.contains('played')
        ? request.transaction.objectStore('played')
        : db.createObjectStore('played', { keyPath: 'url' });
      // Version 4: in the order it was played, so History reads its page and not the whole store
      // (`docs/review-2026-09-28.md` F2) -- the phone's `idx_play_history_recent`.
      if (!played.indexNames.contains('playedAt')) played.createIndex('playedAt', 'playedAt');
      // Existing History is the accepted first-install seed; later records must earn their mark.
      if (event.oldVersion > 0 && event.oldVersion < 5) {
        played.openCursor().onsuccess = (event) => {
          const cursor = event.target.result;
          if (!cursor) return;
          cursor.update({ ...cursor.value, listened: true });
          cursor.continue();
        };
      }
      if (!db.objectStoreNames.contains('bookmarks')) db.createObjectStore('bookmarks', { keyPath: 'key' });
    };
    request.onsuccess = () => {
      const db = request.result;
      if (abandoned) { db.close(); return; }
      connection = db;
      db.onversionchange = () => {
        db.close();
        if (connection === db) { connection = null; opening = null; }
      };
      resolve(db);
    };
    request.onerror = () => reject(request.error);
    // A second tab holding an older version open. Nothing to do but say so; the page works without
    // storage and a silent hang is the worse failure.
    request.onblocked = () => {
      abandoned = true;
      reject(new Error('another tab is holding the database open'));
    };
  });
  opening = attempt.catch((error) => { if (opening === pending) opening = null; throw error; });
  const pending = opening;
  return pending;
}

async function tx(store, mode, run) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(store, mode);
    const request = run(transaction.objectStore(store));
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error ?? new DOMException('Aborted', 'AbortError'));
    transaction.oncomplete = () => resolve(request?.result);
  });
}

/**
 * Asks the browser not to evict this.
 *
 * Without it a hand-built playlist is a cache entry, which is data loss rather than a miss. Asked
 * once, and a refusal is not an error — the page works either way, and saying so is
 * `storageNote`'s job.
 */
export async function makePersistent() {
  try {
    if (await navigator.storage?.persisted?.()) return true;
    return (await navigator.storage?.persist?.()) ?? false;
  } catch { return false; }
}

/** What the page is holding, for a storage line the phone has and the page did not. */
export async function estimate() {
  try {
    const { usage = 0, quota = 0 } = (await navigator.storage?.estimate?.()) ?? {};
    return { usage, quota };
  } catch { return { usage: 0, quota: 0 }; }
}

export const playlists = {
  /** Every playlist, the phone's first and the rest by name. */
  async all() {
    const found = (await tx('playlists', 'readonly', (s) => s.getAll())) ?? [];
    return found.sort((a, b) => (a.id === PHONE ? -1 : b.id === PHONE ? 1 : a.name.localeCompare(b.name)));
  },

  get(id) { return tx('playlists', 'readonly', (s) => s.get(id)); },

  /**
   * Writes one whole.
   *
   * Whole rather than appended, because that is what both writers do: a handoff replaces the
   * phone's, and an edit in the page rewrites its own. A playlist is a few hundred rows at most.
   */
  save(playlist) {
    return tx('playlists', 'readwrite', (s) => s.put({ ...playlist, updatedAt: Date.now() }));
  },

  /** The phone's cannot go: it is a view of the last thing sent, not something the user made. */
  remove(id) {
    if (id === PHONE) return Promise.resolve();
    return tx('playlists', 'readwrite', (s) => s.delete(id));
  },
};

let catalogueRevision = 0;
const catalogueListeners = new Set();
const datasetRevisions = new Map();
function catalogueChanged(prefixes) {
  const change = Object.freeze({ revision: ++catalogueRevision, prefixes: Object.freeze([...new Set(prefixes)]) });
  for (const prefix of change.prefixes) datasetRevisions.set(prefix, catalogueRevision);
  for (const listener of catalogueListeners) queueMicrotask(() => listener(change));
}
const datasetPrefix = (key) => key.slice(0, key.indexOf(':') + 1);

export const catalogue = {
  get revision() { return catalogueRevision; },
  revisionOf(prefix) { return datasetRevisions.get(datasetPrefix(prefix)) ?? 0; },
  subscribe(listener) { catalogueListeners.add(listener); return () => catalogueListeners.delete(listener); },
  get(key) { return tx('catalogue', 'readonly', (s) => s.get(key)); },

  /** Atomic replacement; an optional metadata guard protects newer installs from stale rebuilds. */
  async replacePrefix(prefix, records, { signal, expected } = {}) {
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('catalogue', 'readwrite');
      const abort = () => { try { transaction.abort(); } catch { /* already completed or aborted */ } };
      let applied = false;
      const finish = (done) => () => { signal?.removeEventListener('abort', abort); done(); };
      transaction.oncomplete = finish(() => {
        if (applied) catalogueChanged([datasetPrefix(prefix)]);
        resolve(applied);
      });
      transaction.onabort = finish(() => reject(transaction.error ?? new DOMException('Aborted', 'AbortError')));
      transaction.onerror = () => {}; // onabort owns failure and listener cleanup.
      const fail = (error) => { transaction.onabort = finish(() => reject(error)); abort(); };
      const store = transaction.objectStore('catalogue');
      const write = () => {
        try {
          store.delete(IDBKeyRange.bound(prefix, `${prefix}\uffff`));
          for (const record of records) {
            if (!record.key?.startsWith(prefix)) throw new Error('A dataset record lies outside its prefix');
            store.put(record);
          }
          applied = true;
        } catch (error) { fail(error); }
      };
      try {
        if (signal?.aborted) { abort(); return; }
        signal?.addEventListener('abort', abort, { once: true });
        if (expected) {
          if (!expected.key?.startsWith(prefix)) throw new Error('The dataset guard lies outside its prefix');
          const request = store.get(expected.key);
          request.onsuccess = () => {
            if (JSON.stringify(request.result) === JSON.stringify(expected.value)) write();
          };
        } else {
          write();
        }
      } catch (error) { fail(error); }
    });
  },

  /** Bounded batches avoid materializing the full archive when deriving old index summaries. */
  async visitPrefix(prefix, visit, { signal, batch = 512 } = {}) {
    if (!Number.isInteger(batch) || batch < 1 || batch > 512) throw new RangeError('Invalid catalogue read batch');
    const db = await open();
    let after = null;
    while (true) {
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
      const found = await new Promise((resolve, reject) => {
        const transaction = db.transaction('catalogue', 'readonly');
        const range = IDBKeyRange.bound(after ?? prefix, `${prefix}\uffff`, after !== null);
        const request = transaction.objectStore('catalogue').getAll(range, batch);
        transaction.oncomplete = () => resolve(request.result);
        transaction.onabort = () => reject(transaction.error);
        transaction.onerror = () => {};
      });
      for (const record of found) visit(record);
      if (found.length < batch) return;
      after = found.at(-1).key;
    }
  },

  /**
   * Writes many at once, in one transaction per batch.
   *
   * **Batched because that is what the measurement rewarded.** His two machines both went fastest
   * at the largest batch tried, and the whole index landed in 2.6 s and 1.9 s written this way.
   */
  async putAll(records, batch = 2000, onProgress = null) {
    const db = await open();
    for (let i = 0; i < records.length; i += batch) {
      await new Promise((resolve, reject) => {
        const transaction = db.transaction('catalogue', 'readwrite');
        const store = transaction.objectStore('catalogue');
        for (let j = i; j < Math.min(i + batch, records.length); j++) store.put(records[j]);
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error);
      });
      onProgress?.(Math.min(i + batch, records.length), records.length);
    }
    if (records.length) catalogueChanged(records.map((record) => datasetPrefix(record.key)));
  },

  /**
   * Every record whose key starts with [prefix].
   *
   * A cursor over a key range, which is the one thing IndexedDB does well without an index —
   * because the key *is* the order. Used by search, which reads all 1,663 title shards and would
   * otherwise have to guess which two-character keys exist.
   */
  async byPrefix(prefix) {
    const db = await open();
    return new Promise((resolve, reject) => {
      const found = [];
      const transaction = db.transaction('catalogue', 'readonly');
      const range = IDBKeyRange.bound(prefix, `${prefix}\uffff`);
      transaction.objectStore('catalogue').openCursor(range).onsuccess = (event) => {
        const cursor = event.target.result;
        if (!cursor) return;
        found.push(cursor.value);
        cursor.continue();
      };
      transaction.oncomplete = () => resolve(found);
      transaction.onerror = () => reject(transaction.error);
    });
  },

  /**
   * Everything belonging to one archive, for a re-index or a delete.
   *
   * **One `delete` over the range, not a cursor deleting record by record.** The cursor version is
   * the obvious one and it is what was here: open a cursor over the prefix, `cursor.delete()`,
   * `cursor.continue()`. It is also **two orders of magnitude slower**, because each step is its
   * own request round trip through the transaction -- measured at 20,000 records, which is the
   * shape Modland actually produces: 281 ms to write them all and **26 seconds** to delete them
   * again.
   *
   * From the outside that reads as indexing taking two or three seconds the first time and twenty
   * the second: the first time there is nothing to clear.
   *
   * `IDBObjectStore.delete` takes a key range as happily as a key, and deletes everything in it in
   * one request. Same transaction semantics, same result, one round trip.
   */
  async clear(prefix) {
    const db = await open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('catalogue', 'readwrite');
      const range = IDBKeyRange.bound(prefix, `${prefix}\uffff`);
      transaction.objectStore('catalogue').delete(range);
      transaction.oncomplete = () => { catalogueChanged([datasetPrefix(prefix)]); resolve(); };
      transaction.onerror = () => reject(transaction.error);
    });
  },
};

/**
 * Strictly increasing within a session, so two plays in one millisecond still have an order, and
 * History lists them the way they happened.
 */
let lastStamp = 0;

/** Keep read/modify/write atomic, including another tab's replay and synchronous quota failures. */
async function changeRecord(name, id, change) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(name, 'readwrite');
    const store = transaction.objectStore(name), request = store.get(id);
    let failure;
    let changed = false;
    request.onsuccess = () => {
      try {
        const after = change(request.result);
        if (after) { store.put(after); changed = true; }
      } catch (error) { failure = error; transaction.abort(); }
    };
    transaction.oncomplete = () => resolve(changed);
    transaction.onabort = () => reject(failure ?? transaction.error ?? new DOMException('Aborted', 'AbortError'));
    transaction.onerror = () => {};
  });
}

function recordHistory(entry) {
  return changeRecord('played', entry.url, (before) => {
    lastStamp = Math.max(Date.now(), lastStamp + 1, (before?.playedAt ?? 0) + 1);
    return { ...before, ...entry, listened: before?.listened === true,
      playedAt: lastStamp, playCount: (before?.playCount ?? 0) + 1 };
  });
}

function qualifyHistory(url) {
  return changeRecord('played', url, (before) => before && !before.listened ? { ...before, listened: true } : null);
}

/** Bookmarks have independent lifetime: clearing downloaded indexes or History cannot delete them. */
export const bookmarks = {
  save(place) {
    const snapshot = immutablePlace(place), key = savedPlaceId(snapshot.key);
    return changeRecord('bookmarks', key, (before) => before ? null : { key, place: snapshot });
  },
  async list() {
    const rows = (await tx('bookmarks', 'readonly', (store) => store.getAll())) ?? [];
    return rows.map((row) => immutablePlace(row.place)).sort(compareSavedPlaces);
  },
  remove(key) { return tx('bookmarks', 'readwrite', (store) => store.delete(savedPlaceId(key))); },
};

const historyFailures = new Set();
const historyWrites = new HistoryWrites({ record: recordHistory, qualify: qualifyHistory,
  erase: () => tx('played', 'readwrite', (store) => store.clear()),
  failed: (error) => { for (const listener of historyFailures) listener(error); } });

/**
 * What has been played, with the phone's rules (`data/HistoryStore.kt`).
 *
 * **One row per tune rather than per play**, moved to the top and counted when it is played again:
 * the question it answers is "that tune two days ago, what was it", not "audit this page". The
 * title is written every time, because it improves -- a tune is filed under its file name until
 * it has been opened, and afterwards under the name it gives itself.
 */
export const played = {
  get generation() { return historyWrites.generation; },
  get revision() { return historyWrites.revision; },
  subscribe(listener) { return historyWrites.subscribe(listener); },
  onFailure(listener) { historyFailures.add(listener); return () => historyFailures.delete(listener); },
  record(entry, ticket = historyWrites.generation) { return historyWrites.record({ ...entry }, ticket); },
  qualify(result) { return historyWrites.qualify(result); },

  /** Only visible IDs, in bounded transactions; missing rows are never synthesized. */
  async presentationAmong(ids, signal) {
    const wanted = [...new Set(ids)], found = new Map();
    for (let i = 0; i < wanted.length; i += 500) {
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
      const db = await open();
      await new Promise((resolve, reject) => {
        const transaction = db.transaction('played', 'readonly'), store = transaction.objectStore('played');
        for (const id of wanted.slice(i, i + 500)) {
          const request = store.get(id);
          request.onsuccess = () => {
            const row = request.result;
            if (row) found.set(id, Object.freeze({ name: row.name?.trim() !== row.file ? row.name?.trim() ?? '' : '', listened: row.listened === true }));
          };
        }
        transaction.oncomplete = () => resolve();
        transaction.onabort = () => reject(transaction.error ?? new DOMException('Aborted', 'AbortError'));
        transaction.onerror = () => {};
      });
    }
    if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
    return found;
  },

  /**
   * [limit] tunes from [offset], most recently played first: one page of History, read backwards
   * along the `playedAt` index, so its cost does not grow with History (review F2).
   */
  async page(offset, limit) {
    const db = await open();
    return new Promise((resolve, reject) => {
      const rows = [];
      let skipped = offset === 0;
      const transaction = db.transaction('played', 'readonly');
      const request = transaction.objectStore('played').index('playedAt').openCursor(null, 'prev');
      request.onsuccess = () => {
        const cursor = request.result;
        if (!cursor) return;
        if (!skipped) { skipped = true; cursor.advance(offset); return; }
        rows.push(cursor.value);
        if (rows.length < limit) cursor.continue();
      };
      transaction.oncomplete = () => resolve(rows);
      transaction.onerror = () => reject(transaction.error);
    });
  },

  /** How many tunes History holds. */
  count() { return tx('played', 'readonly', (s) => s.count()); },

  /** One coherent newest-first page/count, after ordered writes; filtered scans stay bounded. */
  async read(query, requested, signal) {
    await historyWrites.settled;
    if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
    const db = await open();
    if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
    const phrase = trimQuery(query);
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('played', 'readonly'), store = transaction.objectStore('played');
      const abort = () => {
        try { transaction.abort(); }
        catch (error) { if (error.name !== 'InvalidStateError') throw error; }
      };
      signal?.addEventListener('abort', abort, { once: true });
      let result;
      if (phrase) {
        const scan = new HistoryPageScan(requested);
        const request = store.index('playedAt').openCursor(null, 'prev');
        request.onsuccess = () => {
          const cursor = request.result;
          if (!cursor) { result = scan.result(); return; }
          const row = cursor.value;
          if (historyMatches(phrase, row.name, row.author, row.file)) scan.accept(row);
          cursor.continue();
        };
      } else {
        const count = store.count();
        count.onsuccess = () => {
          const total = count.result, page = historyPageClamp(requested, total), rows = [];
          result = { total, page, rows };
          if (!total) return;
          let skipped = page === 0;
          const request = store.index('playedAt').openCursor(null, 'prev');
          request.onsuccess = () => {
            const cursor = request.result;
            if (!cursor) return;
            if (!skipped) { skipped = true; cursor.advance(page * HISTORY_PAGE); return; }
            rows.push(cursor.value);
            if (rows.length < HISTORY_PAGE) cursor.continue();
          };
        };
      }
      const cleanup = () => signal?.removeEventListener('abort', abort);
      transaction.oncomplete = () => {
        cleanup();
        if (signal?.aborted) reject(new DOMException('Aborted', 'AbortError')); else resolve(result);
      };
      transaction.onabort = () => { cleanup(); reject(transaction.error ?? new DOMException('Aborted', 'AbortError')); };
      transaction.onerror = () => {};
    });
  },

  /** Most recently played first, the whole of it: for the checks, not for History's screen. */
  async recent() {
    const all = (await tx('played', 'readonly', (s) => s.getAll())) ?? [];
    return all.sort((a, b) => b.playedAt - a.playedAt);
  },

  clear() { return historyWrites.clear(); },
};

export const settings = {
  async get(key, fallback = null) {
    const found = await tx('settings', 'readonly', (s) => s.get(key));
    return found ? found.value : fallback;
  },
  set(key, value) { return tx('settings', 'readwrite', (s) => s.put({ key, value })); },
};

/** For the checks, which need a clean database per run and no leftovers between them. */
export function forget() { connection?.close(); connection = null; opening = null; }
