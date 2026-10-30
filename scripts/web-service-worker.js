// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Template rendered by stage-web-pwa.mjs with a content hash and the shipped asset list.

const REVISION = __PWA_REVISION__;
const FILES = __PWA_FILES__;
const INTEGRITY = __PWA_INTEGRITY__;
const BASE = self.registration.scope;
// Cache Storage is shared by the origin. Another app or a second Protracktor installation
// under a different path must keep its own caches when this one updates.
const PREFIX = `protracktor-shell:${BASE}:`;
const CACHE = PREFIX + REVISION;
const ASSETS = new Map(FILES.map((file) => [new URL(file, BASE).href, file]));
const PAGE = new URL('src/index.html', BASE).href;

function assetRequest(file, input = new URL(file, BASE)) {
  const integrity = INTEGRITY[file];
  const request = new Request(input, { cache: 'reload', integrity });
  if (request.integrity !== integrity) throw new Error('Offline asset integrity is unavailable');
  return request;
}

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    try {
      const cache = await caches.open(CACHE);
      // HTTP 200 alone cannot prove that an in-place deployment supplied this version's engine.
      const requests = FILES.map((file) => assetRequest(file));
      // Fetch checks every digest; a mismatch rejects addAll and leaves the previous worker active.
      await cache.addAll(requests);
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
  const file = ASSETS.get(url.href);
  if (!file) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    return (await cache.match(url.href)) || fetch(assetRequest(file, request));
  })());
});
