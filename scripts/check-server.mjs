// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// What `serve-web.mjs` answers, asked over a real socket.
//
// **This exists because of `docs/STATUS.md` C29**, which was a static server serving a directory's
// index at a URL with no trailing slash. Every relative reference in the page then resolved one
// level too high, `app.js` was fetched from the wrong place, and the browser reported it as a MIME
// problem — so the evening went into a MIME table that was correct all along. The page checks could
// not have seen it: jsdom is handed the file, and the whole bug is in the address it came from.
//
// Started on a spare port, asked six questions, stopped.
//
//   node scripts/check-server.mjs

import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const port = 8100 + Math.floor(Math.random() * 400);
const base = `http://127.0.0.1:${port}`;

const failures = [];
const check = (ok, what) => {
  console.log(`  ${ok ? '✓' : '✗'} ${what}`);
  if (!ok) failures.push(what);
};

const server = spawn('node', [path.join(root, 'scripts', 'serve-web.mjs'), String(port)], {
  cwd: root, stdio: ['ignore', 'pipe', 'pipe'],
});
// The log matters as much as the answers: a 404 nobody can see is how C29 stayed a mystery for a
// day on a machine in another room.
let log = '';
server.stdout.on('data', (chunk) => { log += chunk; });
server.stderr.on('data', (chunk) => { log += chunk; });

/** The server needs a moment to bind, and a dead one must not look like a slow one. */
async function waitForIt() {
  for (let i = 0; i < 100; i++) {
    try { await fetch(`${base}/src/`); return true; } catch { /* not yet */ }
    await new Promise((r) => setTimeout(r, 50));
  }
  return false;
}

try {
  if (!await waitForIt()) {
    console.log(`✗ the server never came up on ${port}\n${log}`);
    process.exit(1);
  }
  console.log('server:');

  // **The one that cost the evening.** A directory without a trailing slash has to redirect, or the
  // page loads at an address its own relative URLs are wrong from.
  const bare = await fetch(`${base}/src`, { redirect: 'manual' });
  check(bare.status === 301 && bare.headers.get('location') === '/src/',
    'a directory without a trailing slash redirects to one');

  const root302 = await fetch(`${base}/`, { redirect: 'manual' });
  check(root302.status === 302 && root302.headers.get('location') === '/src/',
    'and the root points at the player');

  const page = await fetch(`${base}/src/`);
  check(page.status === 200 && (page.headers.get('content-type') ?? '').startsWith('text/html'),
    'the page is served as HTML');

  // A module refused for its type is the failure this whole file is about.
  const script = await fetch(`${base}/src/app.js`);
  check(script.status === 200 &&
        (script.headers.get('content-type') ?? '').startsWith('text/javascript'),
    'and its module as JavaScript');

  // The page reads its format list at load and filters the whole index through it, so a server
  // that answered it with the wrong type -- or not at all -- would leave every format "unplayable".
  const list = await fetch(`${base}/src/formats.tsv`);
  check(list.status === 200
        && (list.headers.get('content-type') ?? '').startsWith('text/tab-separated-values')
        && (await list.text()).includes('extension\tmod\topenmpt'),
    'the format list is served, as text, with the phone\'s names in it');

  // A missing asset must say "missing", not "wrong type" -- the browser's MIME sentence is what
  // sent the diagnosis into the wrong server.
  const gone = await fetch(`${base}/src/nothing-here.js`);
  check(gone.status === 404 && (gone.headers.get('content-type') ?? '').startsWith('text/javascript'),
    'a missing script answers 404 rather than a MIME the loader refuses');

  // A path a person typed keeps the sentence that tells them where to go instead.
  const typo = await fetch(`${base}/srcc/`);
  check(typo.status === 404 && (await typo.text()).includes('the player is at'),
    'and a mistyped path still says where the player is');

  // **Waited for, not slept on.** This was 100 ms flat, which is a bet that the child's stdout has
  // reached this process by then -- and it lost one run in a suite that had otherwise been green
  // all afternoon. A check that fails when the machine is busy is a check nobody trusts, and an
  // untrusted check gets re-run until it passes, which is the same as not having it.
  for (let i = 0; i < 100 && !log.includes('404 /src/nothing-here.js'); i++) {
    await new Promise((r) => setTimeout(r, 20));
  }
  check(log.includes('404 /src/nothing-here.js'), 'every miss is written to the log');
} finally {
  server.kill();
}

console.log(failures.length ? `\n❌ ${failures.length} failed` : '\n✅ server checks passed');
process.exit(failures.length ? 1 : 0);
