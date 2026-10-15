// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Template rendered by stage-web-pwa.mjs with a content hash and the shipped asset list.

const REVISION = "fad48c3c8602bf45e52cc396cb7f8fe7aff52b2cf065693469db494a08d02093";
const FILES = ["lib/qrcode.js","src/app.js","src/audio-export.js","src/catalogue.js","src/formats.tsv","src/i18n-pl.js","src/i18n.js","src/icons/apple-touch-icon.png","src/icons/favicon-32.png","src/icons/icon-192.png","src/icons/icon-512.png","src/icons/icon-maskable-512.png","src/index.html","src/m4a.js","src/manifest.webmanifest","src/processor.js","src/pwa.js","src/rules.js","src/songdb.js","src/songlengths.js","src/store.js","vendor/engine.mjs","vendor/engine.wasm","vendor/legal/build.json","vendor/legal/privacy-policy.md","vendor/notices/asap/COPYING","vendor/notices/components.tsv","vendor/notices/emscripten/emscripten-LICENSE.txt","vendor/notices/emscripten/libcxx-LICENSE.txt","vendor/notices/emscripten/musl-COPYRIGHT.txt","vendor/notices/fmt/LICENSE.rst","vendor/notices/gme/license.txt","vendor/notices/hively/LICENSE","vendor/notices/lhasa/AUTHORS","vendor/notices/lhasa/COPYING.md","vendor/notices/libopenmpt/LICENSE","vendor/notices/minimp3/LICENSE","vendor/notices/protracktor/LICENSE","vendor/notices/qrcode/qrcode-MIT.txt","vendor/notices/sc68/AUTHORS","vendor/notices/sc68/COPYING","vendor/notices/sidplayfp/AUTHORS.md","vendor/notices/sidplayfp/COPYING","vendor/notices/sources.tsv","vendor/notices/zlib/zlib-LICENSE.txt","vendor/notices/zxtune/LICENSE.md"];
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
