// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// node scripts/check-pwa.mjs — manifest paths, build invalidation and offline request isolation.

import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import { buildServiceWorker } from './stage-web-pwa.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const web = path.join(root, 'web');
const manifest = JSON.parse(fs.readFileSync(path.join(web, 'src/manifest.webmanifest')));
const html = fs.readFileSync(path.join(web, 'src/index.html'), 'utf8');
assert.match(html, /rel="manifest" href="\.\/manifest.webmanifest"/);
assert.equal(manifest.display, 'standalone');
assert.equal(manifest.name, 'Protracktor');
for (const prefix of ['/', '/Protracktor/']) {
  const url = new URL(`https://example.test${prefix}src/manifest.webmanifest`);
  assert.equal(new URL(manifest.id, url).pathname, prefix);
  assert.equal(new URL(manifest.scope, url).pathname, prefix);
  assert.equal(new URL(manifest.start_url, url).pathname, `${prefix}src/`);
  for (const icon of manifest.icons) {
    assert.ok(new URL(icon.src, url).pathname.startsWith(`${prefix}src/icons/`));
  }
}
for (const [name, size] of Object.entries({
  'icon-192.png': 192, 'icon-512.png': 512, 'icon-maskable-512.png': 512,
  'apple-touch-icon.png': 180, 'favicon-32.png': 32,
})) {
  const png = fs.readFileSync(path.join(web, 'src/icons', name));
  assert.equal(png.subarray(1, 4).toString(), 'PNG');
  assert.equal(png.readUInt32BE(16), size);
  assert.equal(png.readUInt32BE(20), size);
}
assert.ok(manifest.icons.some((icon) => icon.purpose === 'maskable'));
console.log('  ✓ manifest and icon sizes work at localhost and the GitHub Pages subpath');

// A disposable site needs no compiled engine. Its fake bytes still exercise the real build hash.
const fixture = fs.mkdtempSync(path.join(os.tmpdir(), 'protracktor-pwa-'));
try {
  for (const dir of ['src', 'lib']) fs.cpSync(path.join(web, dir), path.join(fixture, dir), { recursive: true });
  for (const file of ['engine.mjs', 'engine.wasm', 'legal/build.json', 'notices/components.tsv']) {
    const target = path.join(fixture, 'vendor', file);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, file);
  }
  fs.writeFileSync(path.join(fixture, 'vendor/private-diagnostics.html'), 'must not be cached');
  const built = buildServiceWorker(fixture);
  assert.ok(built.files.includes('vendor/engine.wasm'));
  assert.ok(!built.files.some((file) => file.includes('diagnostics')));
  assert.equal(buildServiceWorker(fixture).revision, built.revision);
  for (const file of ['src/app.js', 'vendor/engine.wasm', 'vendor/legal/build.json', 'src/icons/icon-512.png']) {
    const target = path.join(fixture, file), original = fs.readFileSync(target);
    fs.appendFileSync(target, 'changed');
    assert.notEqual(buildServiceWorker(fixture).revision, built.revision);
    fs.writeFileSync(target, original);
  }
  console.log('  ✓ scripts, engine, legal records and icons invalidate the cache; diagnostics stay out');
  fs.appendFileSync(path.join(fixture, 'src/app.js'), '\n// next version\n');
  const next = buildServiceWorker(fixture);

  for (const base of ['https://example.test/', 'https://example.test/Protracktor/']) {
    const handlers = {}, stores = new Map();
    let failInstall = false, claimed = false, skipped = false, network = 0;
    const caches = {
      async open(name) {
        if (!stores.has(name)) stores.set(name, new Map());
        const entries = stores.get(name);
        return {
          async addAll(requests) {
            entries.set(requests[0].url, new Response('partial'));
            if (failInstall) throw new Error('download failed');
            for (const request of requests) entries.set(request.url, new Response(request.url));
          },
          async match(url) { return entries.get(url)?.clone(); },
        };
      },
      async keys() { return [...stores.keys()]; },
      async delete(name) { return stores.delete(name); },
    };
    const workerContext = {
      self: {
        registration: { scope: base }, clients: { async claim() { claimed = true; } },
        skipWaiting() { skipped = true; },
        addEventListener(type, handler) { handlers[type] = handler; },
      },
      caches, URL, Request, Response, Set,
      fetch: async () => { network++; throw new Error('offline'); },
    };
    vm.runInNewContext(built.source, workerContext);
    const lifecycle = async (type) => {
      let work;
      handlers[type]({ waitUntil(promise) { work = promise; } });
      await work;
    };
    const request = async (relative, { mode = 'cors', method = 'GET', headers = {} } = {}) => {
      let response;
      handlers.fetch({
        request: { url: new URL(relative, base).href, mode, method, headers: new Headers(headers) },
        respondWith(value) { response = value; },
      });
      return await response;
    };
    await lifecycle('install');
    assert.equal(skipped, false);
    const ownCache = `protracktor-shell:${base}:${built.revision}`;
    stores.set(`protracktor-shell:${base}:old`, new Map());
    stores.set('another-app', new Map());
    stores.set('protracktor-shell:https://example.test/other/:old', new Map());
    await lifecycle('activate');
    assert.ok(claimed);
    assert.ok(stores.has(ownCache));
    assert.ok(!stores.has(`protracktor-shell:${base}:old`));
    assert.ok(stores.has('another-app'));
    assert.ok(stores.has('protracktor-shell:https://example.test/other/:old'));
    assert.equal(await (await request('src/?test=1', { mode: 'navigate' })).text(), `${base}src/index.html`);
    assert.equal(await (await request('vendor/engine.wasm')).text(), `${base}vendor/engine.wasm`);
    const redirect = await request('?test=1', { mode: 'navigate' });
    assert.equal(redirect.headers.get('location'), `${base}src/?test=1`);
    for (const relative of ['pair/host', 'pair/room/next?since=2', 'tools/storage-check.html', 'https://modland.com/tune.mod']) {
      assert.equal(await request(relative), undefined);
    }
    assert.equal(await request('src/app.js', { method: 'POST' }), undefined);
    assert.equal(await request('vendor/engine.wasm', { headers: { range: 'bytes=0-4' } }), undefined);
    assert.equal(network, 0);
    failInstall = true;
    vm.runInNewContext(next.source, { ...workerContext });
    await assert.rejects(lifecycle('install'), /download failed/);
    assert.ok(!stores.has(`protracktor-shell:${base}:${next.revision}`));
    assert.ok(stores.has(ownCache), 'a failed update preserves the active version');
  }
  console.log('  ✓ offline startup, scoped cleanup, safe updates and pairing/network isolation');
} finally {
  fs.rmSync(fixture, { recursive: true, force: true });
}
console.log('✅ PWA checks passed');
