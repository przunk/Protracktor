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
// Nothing is stored. A room is the last thing said in it plus whoever is waiting to hear
// something, and it dies with the process.
import http from 'http';
import fs from 'fs';
import os from 'os';
import path from 'path';
import crypto from 'crypto';

const root = path.resolve('web');

/**
 * Settings, from a file beside the page rather than from flags.
 *
 * **Written on first run if it is not there**, so the thing to edit exists before anybody needs to
 * ask what it is called. Deliberately **outside `web/`**: everything under that directory is served
 * to whoever asks, and a configuration file is not a page.
 *
 * `host` is the address the QR code carries — the one the *phone* must be able to reach. Left null
 * it is worked out from the network interfaces, which is right on an ordinary machine and wrong
 * inside WSL, where the interface belongs to a private network the phone cannot see.
 *
 * **It may be a whole base URL**, and behind a reverse proxy it has to be: `https://player.example`
 * rather than a bare name. The page needs a secure context to run an `AudioWorklet` at all, so
 * anything but `localhost` is served over TLS by something in front of this — and that something
 * listens on 443, not on this port. A bare name keeps the old meaning, `http://<name>:<port>`.
 */
const CONFIG = path.resolve('server.json');
const DEFAULTS = {
  port: 8173,
  host: null,
  bind: '0.0.0.0',
};

function settings() {
  let stored = {};
  if (fs.existsSync(CONFIG)) {
    try {
      stored = JSON.parse(fs.readFileSync(CONFIG, 'utf8'));
    } catch (error) {
      console.error(`⚠ ${CONFIG} is not valid JSON (${error.message}); using defaults.`);
    }
  } else {
    fs.writeFileSync(CONFIG, JSON.stringify(DEFAULTS, null, 2) + '\n');
    console.log(`📝 wrote ${CONFIG} — edit it to change the port or the advertised address.`);
  }
  return { ...DEFAULTS, ...stored };
}

const config = settings();
// A port on the command line still wins, because that is how somebody tries a second one.
const port = Number(process.argv[2] ?? process.env.PROTRACKTOR_PORT ?? config.port);
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
  if (config.host) return config.host;
  const candidates = [];
  for (const [name, addresses] of Object.entries(os.networkInterfaces())) {
    for (const address of addresses ?? []) {
      if (address.family === 'IPv4' && !address.internal) candidates.push({ name, ip: address.address });
    }
  }
  const lan = candidates.find((c) => /^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)/.test(c.ip));
  return (lan ?? candidates[0])?.ip ?? 'localhost';
}

/**
 * The base the QR code carries, scheme and all.
 *
 * A `host` with a scheme in it is taken whole — that is the reverse-proxy case, where the name
 * answers on 443 and this port is not part of the address anybody outside can use.
 */
function advertisedBase() {
  const host = advertisedHost();
  return host.includes('://') ? host.replace(/\/+$/, '') : `http://${host}:${port}`;
}

const rooms = new Map();

/**
 * A room: the last thing said in it, and whoever is currently waiting to hear something.
 *
 * **`seq` is what makes long polling safe.** A page asks "anything after 4?" and either gets it at
 * once or waits; either way it cannot miss a message that arrived between two of its requests,
 * which is the failure mode that makes naive polling drop things under exactly the conditions
 * nobody tests.
 *
 * `lastPoll` exists so the phone can still be told something true. With a stream, "somebody is
 * listening" was the socket being open; with polling there is no socket, so it is "somebody asked
 * within the last [LISTENING_WINDOW]".
 */
function room(id) {
  if (!rooms.has(id)) rooms.set(id, { seq: 0, last: null, waiters: new Set(), lastPoll: 0 });
  return rooms.get(id);
}

/** How long a poll is held before answering with nothing. Under every proxy's idle timeout. */
const HOLD_MS = 25_000;

/** How recently a page must have asked for the phone to be told it is listening. */
const LISTENING_WINDOW_MS = 90_000;

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
    send(response, 200, { base: advertisedBase() });
    return;
  }

  if (url.pathname === '/pair/new') {
    // 128 bits, carried in the QR. The id **is** the secret: whoever has it can push to this page,
    // and nothing flows the other way, so a stolen code costs a noise in a tab and reveals nothing
    // about the phone (docs/PLAN_HANDOFF.md §3 H2).
    const id = crypto.randomBytes(16).toString('hex');
    room(id);
    send(response, 200, { room: id, post: `${advertisedBase()}/pair/${id}` });
    return;
  }

  const match = url.pathname.match(/^\/pair\/([0-9a-f]{32})(\/next)?$/);
  if (match) {
    const [, id, next] = match;
    const here = room(id);

    if (next) {
      // **Long polling, and it replaced server-sent events for a measured reason.** A Cloudflare
      // quick tunnel buffers `text/event-stream`: the server reported delivering and the page
      // received nothing, through two rounds of anti-buffering headers and two kilobytes of padding
      // (`docs/PLAN_HANDOFF.md` §5c). A complete HTTP response is the one thing every proxy on
      // earth forwards, so that is what this sends.
      here.lastPoll = Date.now();
      const since = Number(url.searchParams.get('since') ?? 0) || 0;

      if (here.last && here.last.seq > since) {
        send(response, 200, { seq: here.last.seq, message: here.last.body });
        return;
      }

      // Nothing yet: hold the request rather than answering empty straight away, so a phone that
      // sends a second later is heard a second later rather than at the next poll.
      const waiter = { response, since };
      here.waiters.add(waiter);
      const giveUp = setTimeout(() => {
        here.waiters.delete(waiter);
        send(response, 200, { seq: since });
      }, HOLD_MS);
      waiter.giveUp = giveUp;
      request.on('close', () => {
        clearTimeout(giveUp);
        here.waiters.delete(waiter);
      });
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
        here.seq += 1;
        here.last = { seq: here.seq, body };
        for (const waiter of here.waiters) {
          clearTimeout(waiter.giveUp);
          send(waiter.response, 200, { seq: here.seq, message: body });
        }
        here.waiters.clear();
        // **"Listening" is now a claim about the recent past**, because there is no socket to look
        // at. A page polls every few seconds at worst, so a room nobody has asked about for a
        // minute and a half has no page behind it -- and the phone says "the player page is not
        // open there" rather than pretending.
        const listening = Date.now() - here.lastPoll < LISTENING_WINDOW_MS;
        send(response, 200, { delivered: listening ? 1 : 0 });
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
}).listen(port, config.bind, () => {
  const host = advertisedHost();
  console.log(`🌐 page:  http://localhost:${port}/src/   (settings: ${CONFIG})`);
  console.log(`📱 phone: ${advertisedBase()}/`);
  // WSL has its own network. A phone on the same Wi-Fi reaches the *Windows* address, and nothing
  // arrives here until Windows forwards the port -- so the address in the QR would be right and the
  // connection would still fail, which is the worst kind of wrong.
  if (!host.includes('://') && /^172\.(1[6-9]|2[0-9]|3[01])\./.test(host)) {
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
