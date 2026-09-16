// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// What the page keeps, and keeps across a reload.
//
// **IndexedDB rather than `localStorage`**, which is a few megabytes and synchronous — and the
// index in `docs/PLAN_WEB_LIBRARY.md` S3 is eighteen. Measured on the owner's two machines,
// 2026-09-10: 473 GB and 256 GB offered, `persist()` granted on both, and the whole of Modland
// written in 2.6 s and 1.9 s. So this stores what it needs and reports what it holds; there is no
// budget to ration and no eviction rule to design.
//
// Everything here is promises over the callback API, in one place, because the alternative is
// callbacks in the page.

const DB = 'protracktor';
const VERSION = 3;

/** The playlist that is not a document: replaced wholesale by every handoff, never deleted. */
export const PHONE = 'phone';

let opening = null;

function open() {
  if (opening) return opening;
  opening = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB, VERSION);
    request.onupgradeneeded = () => {
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
      if (!db.objectStoreNames.contains('played')) {
        db.createObjectStore('played', { keyPath: 'url' });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    // A second tab holding an older version open. Nothing to do but say so; the page works without
    // storage and a silent hang is the worse failure.
    request.onblocked = () => reject(new Error('another tab is holding the database open'));
  });
  return opening;
}

async function tx(store, mode, run) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(store, mode);
    const request = run(transaction.objectStore(store));
    transaction.onerror = () => reject(transaction.error);
    transaction.oncomplete = () => resolve(request?.result);
  });
}

/**
 * Asks the browser not to evict this.
 *
 * Without it a hand-built playlist is a cache entry, which is data loss rather than a miss. Granted
 * on both of the owner's machines; asked once, and a refusal is not an error — the page works
 * either way and saying so is `storageNote`'s job.
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

export const catalogue = {
  get(key) { return tx('catalogue', 'readonly', (s) => s.get(key)); },

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
   * The owner found it from the outside and described it exactly: indexing takes two or three
   * seconds the first time and twenty the second. The first time there is nothing to clear.
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
      transaction.oncomplete = resolve;
      transaction.onerror = () => reject(transaction.error);
    });
  },
};

/** The phone's `PLAY_HISTORY_LIMIT`: the last 500 tunes, the oldest forgotten. */
export const PLAYED_LIMIT = 500;

/**
 * Strictly increasing within a session, so two plays in one millisecond still have an order -- the
 * oldest is what the limit forgets, and a tie would make which one goes a coin toss.
 */
let lastStamp = 0;

/**
 * What has been played (`GOAL.md` round 8, item 4), with the phone's rules (`data/HistoryStore.kt`).
 *
 * **One row per tune rather than per play**, moved to the top and counted when it is played again:
 * the question it answers is "that tune two days ago, what was it", not "audit this page". The
 * title is written every time, because it improves -- a tune is filed under its file name until
 * it has been opened, and afterwards under the name it gives itself.
 */
export const played = {
  async record(entry, { limit = PLAYED_LIMIT } = {}) {
    lastStamp = Math.max(Date.now(), lastStamp + 1);
    const before = await tx('played', 'readonly', (s) => s.get(entry.url));
    await tx('played', 'readwrite', (s) => s.put({
      ...before, ...entry, playedAt: lastStamp, playCount: (before?.playCount ?? 0) + 1,
    }));
    // The oldest forgotten once the list is over its limit -- read whole only then, which for 500
    // rows is nothing and on most plays does not happen at all.
    if ((await tx('played', 'readonly', (s) => s.count())) > limit) {
      const all = (await tx('played', 'readonly', (s) => s.getAll())) ?? [];
      all.sort((a, b) => b.playedAt - a.playedAt);
      const drop = all.slice(limit).map((row) => row.url);
      await tx('played', 'readwrite', (s) => { drop.forEach((url) => s.delete(url)); return null; });
    }
  },

  /** Most recently played first. */
  async recent() {
    const all = (await tx('played', 'readonly', (s) => s.getAll())) ?? [];
    return all.sort((a, b) => b.playedAt - a.playedAt);
  },

  clear() { return tx('played', 'readwrite', (s) => s.clear()); },
};

export const settings = {
  async get(key, fallback = null) {
    const found = await tx('settings', 'readonly', (s) => s.get(key));
    return found ? found.value : fallback;
  },
  set(key, value) { return tx('settings', 'readwrite', (s) => s.put({ key, value })); },
};

/** For the checks, which need a clean database per run and no leftovers between them. */
export function forget() { opening = null; }
