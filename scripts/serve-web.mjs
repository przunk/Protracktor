// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The page, and the smallest thing that can introduce a phone to it.
//
// **A browser page cannot listen for a connection**, which is the fact the pairing design turns on:
// the phone cannot post a playlist "to the page". It posts to a server the page is listening to.
// Today that server is this one -- already running, already serving the page -- so pairing costs no
// new infrastructure at all. The same three routes move to a Worker unchanged when the page is
// hosted somewhere the phone can reach from outside a LAN (docs/PLAN_HANDOFF.md §3 H2).
//
// Nothing is stored. A room is a queue of listeners and its last message, and it dies with the
// process.
import http from 'http';
import fs from 'fs';
import os from 'os';
import path from 'path';
import crypto from 'crypto';

const port = Number(process.argv[2] ?? 8173);
const root = path.resolve('web');
const types = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8', '.wasm': 'application/wasm',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json',
};

/**
 * Where the phone should send things.
 *
 * **Not `localhost`**, which is the page's own address and means the phone itself. The QR has to
 * carry an address the *phone* can reach, so this prefers a real interface — and says so loudly
 * when the only one it can find is a WSL NAT address, which a phone cannot reach either without a
 * port proxy on the Windows side. `PROTRACKTOR_PAIR_HOST` overrides it for a tunnel or a host.
 */
function advertisedHost() {
  if (process.env.PROTRACKTOR_PAIR_HOST) return process.env.PROTRACKTOR_PAIR_HOST;
  const candidates = [];
  for (const [name, addresses] of Object.entries(os.networkInterfaces())) {
    for (const address of addresses ?? []) {
      if (address.family === 'IPv4' && !address.internal) candidates.push({ name, ip: address.address });
    }
  }
  const lan = candidates.find((c) => /^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)/.test(c.ip));
  return (lan ?? candidates[0])?.ip ?? 'localhost';
}

const rooms = new Map();

function room(id) {
  if (!rooms.has(id)) rooms.set(id, { listeners: new Set(), last: null });
  return rooms.get(id);
}

function send(response, status, body, type = 'application/json') {
  response.writeHead(status, { 'content-type': type, 'access-control-allow-origin': '*' });
  response.end(typeof body === 'string' ? body : JSON.stringify(body));
}

http.createServer((request, response) => {
  const url = new URL(request.url, `http://localhost:${port}`);

  // --- pairing ------------------------------------------------------------------------------
  // The page asks where the *phone* should send things. It cannot work this out itself: it knows
  // its own origin, and that is usually `localhost`, which from a phone means the phone.
  if (url.pathname === '/pair/host') {
    send(response, 200, { base: `http://${advertisedHost()}:${port}` });
    return;
  }

  if (url.pathname === '/pair/new') {
    // 128 bits, carried in the QR. The id **is** the secret: whoever has it can push to this page,
    // and nothing flows the other way, so a stolen code costs a noise in a tab and reveals nothing
    // about the phone (docs/PLAN_HANDOFF.md §3 H2).
    const id = crypto.randomBytes(16).toString('hex');
    room(id);
    send(response, 200, { room: id, post: `http://${advertisedHost()}:${port}/pair/${id}` });
    return;
  }

  const match = url.pathname.match(/^\/pair\/([0-9a-f]{32})(\/events)?$/);
  if (match) {
    const [, id, events] = match;
    const here = room(id);

    if (events) {
      response.writeHead(200, {
        'content-type': 'text/event-stream',
        'cache-control': 'no-store',
        connection: 'keep-alive',
      });
      response.write(': open\n\n');
      here.listeners.add(response);
      // The page may have been reloaded after the phone sent something; the last message is kept so
      // a reconnect does not lose the queue.
      if (here.last) response.write(`data: ${here.last}\n\n`);
      request.on('close', () => here.listeners.delete(response));
      return;
    }

    if (request.method === 'OPTIONS') {
      response.writeHead(204, {
        'access-control-allow-origin': '*',
        'access-control-allow-methods': 'POST, OPTIONS',
        'access-control-allow-headers': 'content-type',
      });
      response.end();
      return;
    }

    if (request.method === 'POST') {
      let body = '';
      request.on('data', (chunk) => {
        body += chunk;
        if (body.length > 1_000_000) request.destroy();
      });
      request.on('end', () => {
        here.last = body;
        for (const listener of here.listeners) listener.write(`data: ${body}\n\n`);
        send(response, 200, { delivered: here.listeners.size });
      });
      return;
    }

    // A GET here is almost always a phone camera that treated the QR as an ordinary link. Saying
    // what this address is beats a 405 that reads as "broken".
    send(response, 200,
      'This is a Protracktor pairing address.\n\n' +
      'It is not a page. The Protracktor app posts a playlist here and the browser that showed\n' +
      'the code plays it. Scanning the code with a camera app opens this text instead, which is\n' +
      'what just happened.\n', 'text/plain; charset=utf-8');
    return;
  }

  // --- the page -----------------------------------------------------------------------------
  if (url.pathname === '/') {
    response.writeHead(302, { location: '/src/' }).end();
    return;
  }
  let file = path.join(root, decodeURIComponent(url.pathname));
  if (!file.startsWith(root)) { response.writeHead(403).end(); return; }
  if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
  fs.readFile(file, (error, data) => {
    if (error) {
      response.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' })
        .end(`not here: ${url.pathname}\n\nthe player is at http://localhost:${port}/src/\n`);
      return;
    }
    response.writeHead(200, {
      'content-type': types[path.extname(file)] ?? 'application/octet-stream',
      'cache-control': 'no-store',
    });
    response.end(data);
  });
}).listen(port, () => {
  const host = advertisedHost();
  console.log(`🌐 page:  http://localhost:${port}/src/`);
  console.log(`📱 phone: http://${host}:${port}/`);
  // WSL has its own network. A phone on the same Wi-Fi reaches the *Windows* address, and nothing
  // arrives here until Windows forwards the port -- so the address in the QR would be right and the
  // connection would still fail, which is the worst kind of wrong.
  if (/^172\.(1[6-9]|2[0-9]|3[01])\./.test(host)) {
    const wsl = host;
    console.log('');
    console.log('⚠ that is this WSL machine\'s own address; a phone cannot reach it.');
    console.log('  In an *administrator* PowerShell on Windows, once:');
    console.log(`    netsh interface portproxy add v4tov4 listenport=${port} listenaddress=0.0.0.0 connectport=${port} connectaddress=${wsl}`);
    console.log(`    New-NetFirewallRule -DisplayName "Protracktor ${port}" -Direction Inbound -LocalPort ${port} -Protocol TCP -Action Allow`);
    console.log('  then start this with the Windows address, so the code carries it:');
    console.log(`    PROTRACKTOR_PAIR_HOST=192.168.x.y scripts/serve-web.sh ${port}`);
    console.log('');
    console.log('  The WSL address changes when WSL restarts, so the portproxy has to be redone');
    console.log('  then -- or set networkingMode=mirrored in .wslconfig and skip all of this.');
  }
});
