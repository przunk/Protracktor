// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Template rendered by stage-web-pwa.mjs with a content hash and the shipped asset list.

const REVISION = __PWA_REVISION__;
const FILES = __PWA_FILES__;
const BASE = self.registration.scope;
// Cache Storage is shared by the origin. Another app or a second Protracktor installation
// under a different path must keep its own caches when this one updates.
const PREFIX = `protracktor-shell:${BASE}:`;
const CACHE = PREFIX + REVISION;
const ASSETS = new Set(FILES.map((file) => new URL(file, BASE).href));
const PAGE = new URL('src/index.html', BASE).href;

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    try {
      const cache = await caches.open(CACHE);
      // Install the whole version or keep the previous worker. Never cache a partial update.
      await cache.addAll([...ASSETS].map((url) => new Request(url, { cache: 'reload' })));
    } catch (error) {
      await caches.delete(CACHE);
      throw error;
    }
  })());
  // No skipWaiting: a new engine must not replace the scripts beneath a playing tab.
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(names.filter((name) => name.startsWith(PREFIX) && name !== CACHE)
      .map((name) => caches.delete(name)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET' || request.headers.has('range')) return;
  const url = new URL(request.url);
  // Only shipped files: pairing, remote catalogues, music and user files stay on their own paths.
  if (url.origin !== new URL(BASE).origin) return;
  url.search = '';
  if (request.mode === 'navigate') {
    if (url.href === BASE || url.href === new URL('src', BASE).href) {
      event.respondWith(Response.redirect(new URL(`src/${new URL(request.url).search}`, BASE).href));
      return;
    }
    if (url.href === new URL('src/', BASE).href) url.href = PAGE;
  }
  if (!ASSETS.has(url.href)) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    return (await cache.match(url.href)) || fetch(request);
  })());
});
