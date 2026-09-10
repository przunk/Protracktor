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
const VERSION = 1;

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

export const settings = {
  async get(key, fallback = null) {
    const found = await tx('settings', 'readonly', (s) => s.get(key));
    return found ? found.value : fallback;
  },
  set(key, value) { return tx('settings', 'readwrite', (s) => s.put({ key, value })); },
};

/** For the checks, which need a clean database per run and no leftovers between them. */
export function forget() { opening = null; }
