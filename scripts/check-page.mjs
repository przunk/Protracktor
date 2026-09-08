// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The page, loaded and driven without a browser.
//
// **The web half had no test of any kind**, and the agent writing it cannot open a browser — which
// is a bad combination for a screen somebody else has to judge. This does what a probe does for a
// decoder: runs the real thing against real input and checks something specific came out.
//
// What it cannot do is see. It catches a script that throws on load, an element the script reaches
// for and the markup does not have, a queue that arrives and does not appear, and a control that
// stays disabled when it should not. It says nothing about whether the result looks like the app.
//
//   node scripts/check-page.mjs

import fs from 'fs';
import path from 'path';
import { JSDOM } from './../web/node_modules/jsdom/lib/api.js';

const failures = [];
const ok = (what) => console.log(`  ✓ ${what}`);
const check = (condition, what) => {
  if (condition) ok(what);
  else { failures.push(what); console.log(`  ✗ ${what}`); }
};

const html = fs.readFileSync('web/src/index.html', 'utf8');
const dom = new JSDOM(html, {
  url: 'http://localhost:8173/src/',
  runScripts: 'outside-only',
  pretendToBeVisual: true,
});
const { window } = dom;

// The engine, the audio device and the network are not what this checks, so they are stubbed
// exactly as far as the page touches them.
window.qrcode = () => ({ addData() {}, make() {}, createTableTag: () => '<table></table>' });
window.AudioContext = class {
  constructor() { this.state = 'suspended'; this.audioWorklet = { addModule: async () => {} }; }
  async resume() { this.state = 'running'; }
  get destination() { return {}; }
};
window.AudioWorkletNode = class {
  constructor() { this.port = { onmessage: null, postMessage() {} }; }
  connect() {}
};
// The operating system's media controls, recorded rather than performed.
const handlers = {};
let metadata = null;
window.MediaMetadata = class { constructor(fields) { Object.assign(this, fields); } };
window.navigator.mediaSession = {
  playbackState: 'none',
  set metadata(value) { metadata = value; },
  get metadata() { return metadata; },
  setActionHandler(name, fn) { handlers[name] = fn; },
};

const posted = [];
window.fetch = async (url) => {
  const u = String(url);
  if (u.endsWith('/pair/host')) return { ok: true, json: async () => ({ base: 'https://example.test' }) };
  if (u.includes('/next?')) return new Promise(() => {});   // a poll that never answers
  if (u.endsWith('engine.wasm')) return { ok: true, arrayBuffer: async () => new ArrayBuffer(8) };
  posted.push(u);
  return { ok: true, arrayBuffer: async () => new ArrayBuffer(64) };
};

const source = fs.readFileSync('web/src/app.js', 'utf8')
  .replace(/^import .*$/gm, '')                       // no module loader here
  .replace(/\bawait /g, 'await ');                    // kept: the harness wraps it

console.log('page:');
try {
  window.eval(`(async () => { ${source} \n globalThis.__api = { setQueue, entryFor, render, receive, showPanel, onWorklet, orderLength: () => order.length }; })()`);
} catch (error) {
  failures.push(`the script throws on load: ${error.message}`);
  console.log(`  ✗ the script throws on load: ${error.message}`);
}

await new Promise((r) => setTimeout(r, 200));
const $ = (id) => window.document.getElementById(id);

check($('status').textContent.length > 0, 'the status line says something');
check(!!window.__api, 'the script finished loading');

if (window.__api) {
  // A queue arriving from the phone, in the shape WebRemote sends.
  window.__api.receive({
    queue: [
      { url: 'https://modland.com/pub/modules/Protracker/4-Mat/hi%20there.mod', title: 'hi there' },
      { url: 'https://api.modarchive.org/downloads.php?moduleid=42#lotus.mod', title: 'L3_CD6' },
    ],
    index: 0,
  });
  await new Promise((r) => setTimeout(r, 100));

  const rows = window.document.querySelectorAll('#queue li.track');
  check(rows.length === 2, 'two tracks arrive as two rows');
  check(rows[0]?.querySelector('.title')?.textContent === 'hi there', 'the phone\'s title is used, not the filename');
  check(rows[0]?.querySelector('.meta')?.textContent === 'Modland/Protracker/4-Mat',
    'the subtitle says where the track came from');
  check(rows[1]?.querySelector('.title')?.textContent === 'L3_CD6',
    'a Mod Archive row is named by its title rather than downloads.php');
  check($('count').textContent === '2 tracks', 'the header counts them');
  check(rows[0]?.classList.contains('playing'), 'the first row is marked playing');
  check($('playpause').disabled === false, 'play becomes available');
  check($('next').disabled === false, 'next becomes available');

  window.__api.showPanel('paste');
  check($('paste').hidden === false && $('pair').hidden === true, 'the panels switch');

  // Now Playing, driven the way the worklet drives it.
  window.__api.onWorklet({
    type: 'opened',
    describe: 'title\tCrazy Comets\nartist\tRob Hubbard\nformat\tCommodore 64 (SID)\nyear\t1985',
    duration: 0,
    subsongs: 3,
    canSeek: false,
    preferredRate: 44100,
    rate: 44100,
  });
  await new Promise((r) => setTimeout(r, 50));
  const labels = [...window.document.querySelectorAll('#fields dt')].map((n) => n.textContent);
  const values = [...window.document.querySelectorAll('#fields dd')].map((n) => n.textContent);
  check(labels.join(',') === 'title,artist,format,year',
    'Now Playing shows the fields in the phone\'s order');
  check(values.includes('Rob Hubbard'), 'and their values');
  check(window.document.querySelectorAll('.subsong').length === 3,
    'a file with three tunes gets three subsong buttons');
  check($('sub').textContent.includes('Commodore 64'), 'the dock card says what it is playing');
  check($('seek').disabled === true, 'a backend that cannot seek disables the slider');

  // The two modes, whose rules are PlayQueue.kt's rather than invented here.
  const api = window.__api;
  check($('next').disabled === false && $('prev').disabled === true,
    'at the first of two tracks, next works and previous does not');

  $('repeat').click();                       // off -> all
  check($('prev').disabled === false, 'repeat-all gives the first track a previous');
  check($('repeat').classList.contains('on'), 'and the button shows it');
  $('repeat').click();                       // all -> one
  check($('repeat').title === 'Repeat one', 'a second press means repeat one');
  check($('repeat').querySelector('path').getAttribute('d').length > 90,
    'and the glyph changes, so the mode survives being read without colour');
  $('repeat').click();                       // one -> off
  check($('prev').disabled === true, 'off puts previous back where it was');

  $('shuffle').click();
  check($('shuffle').classList.contains('on'), 'shuffle lights up');
  check(api.orderLength() === 2, 'and a permutation covers the queue');
  $('shuffle').click();
  check(!$('shuffle').classList.contains('on'), 'and turns off again');

  // What the operating system is told, which is how a media key reaches a buried tab.
  check(metadata?.title === 'Crazy Comets', 'the system media controls learn the title');
  check(metadata?.artist === 'Rob Hubbard', 'and the artist');
  check(metadata?.album.includes('Commodore 64'), 'and what it is');
  check(typeof handlers.play === 'function' && typeof handlers.nexttrack === 'function',
    'and the media keys are wired to the transport');
  check(window.document.title.startsWith('hi there'),
    'the tab says what is playing, for a page among twenty');

  // The keys somebody at a desk will try, and the one place they must not fire.
  let played = 0;
  $('playpause').addEventListener('click', () => { played += 1; });
  window.document.body.dispatchEvent(
    new window.KeyboardEvent('keydown', { key: ' ', bubbles: true }));
  check(played === 1, 'space plays');
  $('urls').dispatchEvent(new window.KeyboardEvent('keydown', { key: ' ', bubbles: true }));
  check(played === 1, 'and a space typed into the paste box stays a space');
}

// --- the engine's shape, if it has been built -------------------------------------------------
//
// **This catches the failure that costs an hour every time**: the page or the worklet calls a
// function the engine does not export, because the engine was not rebuilt after the C changed. It
// is a grep, and it is the difference between "silence in the browser" and a line of output here.
if (fs.existsSync('web/vendor/engine.mjs')) {
  console.log('\nengine:');
  const glue = fs.readFileSync('web/vendor/engine.mjs', 'utf8');
  const callers = ['web/src/processor.js', 'web/src/app.js', 'scripts/probe-web.mjs'];
  const wanted = new Set();
  for (const file of callers) {
    for (const m of fs.readFileSync(file, 'utf8').matchAll(/\b_(pt_\w+)\b/g)) wanted.add(m[1]);
  }
  const missing = [...wanted].filter((name) => !glue.includes(`_${name}`));
  check(wanted.size > 0, `${wanted.size} engine functions are called`);
  check(missing.length === 0,
    missing.length ? `the built engine is missing: ${missing.join(', ')}` : 'and the built engine exports all of them');
}

console.log(failures.length ? `\n❌ ${failures.length} failed` : '\n✅ page checks passed');
process.exit(failures.length ? 1 : 0);
