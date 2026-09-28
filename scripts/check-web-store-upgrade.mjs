// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The page's database, upgraded from version 3 to 4 (`docs/review-2026-09-28.md` F2): a browser
// that already holds playlists and History opens it with the new code, and keeps them, with History
// read a page at a time along the new index. A migration meets somebody's data once.
import path from 'path';
await import(path.resolve('web/node_modules/fake-indexeddb/auto/index.mjs'));
// A version-3 database as the page left it: playlists and history, no index on playedAt.
await new Promise((resolve, reject) => {
  const r = indexedDB.open('protracktor', 3);
  r.onupgradeneeded = () => {
    const db = r.result;
    db.createObjectStore('playlists', { keyPath: 'id' }).put({ id: 'mine', name: 'Mine', tracks: [], index: 0 });
    db.createObjectStore('settings', { keyPath: 'key' });
    db.createObjectStore('catalogue', { keyPath: 'key' });
    const played = db.createObjectStore('played', { keyPath: 'url' });
    for (let i = 0; i < 150; i++) played.put({ url: `u${i}`, name: `t${i}`, playedAt: 1000 + i, playCount: 1 });
  };
  r.onsuccess = () => { r.result.close(); resolve(); };
  r.onerror = () => reject(r.error);
});
const { played, playlists } = await import(path.resolve('web/src/store.js'));
const page = await played.page(0, 100);
const ok = page.length === 100 && page[0].url === 'u149' && page[99].url === 'u50'
  && await played.count() === 150 && (await playlists.get('mine'))?.name === 'Mine';
console.log(ok ? '✓ a version-3 database opens as version 4: history paged, playlists kept' : `✗ ${JSON.stringify(page.slice(0,2))}`);
process.exit(ok ? 0 : 1);
