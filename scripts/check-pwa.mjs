// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// node scripts/check-pwa.mjs — manifest paths, build invalidation and offline request isolation.

import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import vm from 'node:vm';
import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { buildServiceWorker } from './stage-web-pwa.mjs';

/** Actual Fetch verifies request integrity; only the Cache Storage transaction is modelled. */
async function checkDeliveredVersion(built, next, oldBytes, nextBytes) {
  let served = oldBytes, responses = 0;
  const server = http.createServer((request, response) => {
    const file = decodeURIComponent(new URL(request.url, 'http://localhost').pathname).slice('/Protracktor/'.length);
    const bytes = served.get(file);
    if (!bytes) { response.writeHead(404); response.end(); return; }
    responses++;
    response.writeHead(200, { 'Content-Length': bytes.length });
    response.end(bytes);
  });
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  const base = `http://127.0.0.1:${server.address().port}/Protracktor/`, stores = new Map();
  const caches = {
    async open(name) {
      if (!stores.has(name)) stores.set(name, new Map());
      return {
        async match(url) {
          const bytes = stores.get(name).get(url);
          return bytes ? new Response(bytes) : undefined;
        },
        async addAll(requests) {
          // Await every transfer before closing the server, including a rejected update.
          const results = await Promise.allSettled(requests.map(async request => {
            const response = await fetch(request);
            if (!response.ok) throw Error(`Asset HTTP ${response.status}`);
            return [request.url, Buffer.from(await response.arrayBuffer())];
          }));
          const failure = results.find(result => result.status === 'rejected');
          if (failure) throw failure.reason;
          for (const result of results) stores.get(name).set(...result.value);
        },
      };
    },
    async delete(name) { return stores.delete(name); },
  };
  const install = async worker => {
    const handlers = {};
    vm.runInNewContext(worker.source, {
      self: { registration: { scope: base }, addEventListener(type, handler) { handlers[type] = handler; } },
      caches, Request, Response, URL, fetch,
    });
    let installed;
    handlers.install({ waitUntil(promise) { installed = promise; } });
    await installed;
    return handlers;
  };
  const name = worker => `protracktor-shell:${base}:${worker.revision}`;
  try {
    await install(built);
    assert.equal(stores.get(name(built)).size, oldBytes.size);
    for (const file of ['vendor/engine.wasm', 'src/app.js']) {
      served = new Map(nextBytes);
      served.set(file, oldBytes.get(file));
      const before = responses;
      await assert.rejects(install(next), /fetch|integrity/i, `${file}: HTTP 200 with wrong bytes must reject the update`);
      assert.equal(responses - before, next.files.length, 'all responses were successful HTTP 200');
      assert(!stores.has(name(next)), 'no mixed version survives the failed installation');
      assert(stores.has(name(built)), 'the installed previous version survives');
    }
    served = nextBytes;
    const handlers = await install(next);
    for (const [file, expected] of nextBytes) assert.deepEqual(stores.get(name(next)).get(base + file), expected);
    const missing = 'vendor/engine.wasm';
    stores.get(name(next)).delete(base + missing);
    const loadMissing = async () => {
      let result;
      handlers.fetch({ request: new Request(base + missing), respondWith(promise) { result = promise; } });
      return result;
    };
    served = oldBytes;
    await assert.rejects(loadMissing(), /fetch|integrity/i, 'a cache miss must not mix an older network asset into the active version');
    served = nextBytes;
    assert.deepEqual(Buffer.from(await (await loadMissing()).arrayBuffer()), nextBytes.get(missing));
    console.log('  ✓ real Fetch checks install and cache-miss bytes, retains the old cache and installs matching assets');
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
}

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
  const bytesFor = worker => new Map(worker.files.map(file => [file, fs.readFileSync(path.join(fixture, file))]));
  const oldBytes = bytesFor(built);
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
  fs.appendFileSync(path.join(fixture, 'vendor/engine.wasm'), '\nnext engine version\n');
  const next = buildServiceWorker(fixture);
  await checkDeliveredVersion(built, next, oldBytes, bytesFor(next));

  for (const base of ['https://example.test/', 'https://example.test/Protracktor/']) {
    const handlers = {}, stores = new Map();
    let failInstall = false, claimed = false, skipped = false, network = 0;
    const caches = {
      async open(name) {
        if (!stores.has(name)) stores.set(name, new Map());
        const entries = stores.get(name);
        return {
          async addAll(requests) {
            for (const request of requests) {
              assert.equal(request.cache, 'reload');
              assert.match(request.integrity, /^sha256-[A-Za-z0-9+/]{43}=$/);
            }
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
    failInstall = false;
    class UnsupportedIntegrityRequest extends Request { get integrity() { return ''; } }
    vm.runInNewContext(next.source, { ...workerContext, Request: UnsupportedIntegrityRequest });
    await assert.rejects(lifecycle('install'), /integrity is unavailable/);
    assert.ok(!stores.has(`protracktor-shell:${base}:${next.revision}`));
    assert.ok(stores.has(ownCache), 'an unsupported integrity API preserves the old version');
  }
  console.log('  ✓ offline startup, scoped cleanup, integrity fallback, safe updates and pairing/network isolation');
} finally {
  fs.rmSync(fixture, { recursive: true, force: true });
}
console.log('✅ PWA checks passed');
