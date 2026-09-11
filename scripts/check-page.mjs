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
  get currentTime() { return 0; }
  // The page routes the worklet through a gain for its volume slider. Stubbed far enough to be
  // set and connected, which is all the page does with it.
  createGain() { return { gain: { value: 1, setTargetAtTime() {} }, connect() {} }; }
};
// Recorded rather than dropped: several checks below are about *what the page tells the worklet*,
// which is the only observable a page has for a decision it made.
window.__toWorklet = [];
window.AudioWorkletNode = class {
  constructor() {
    this.port = {
      onmessage: null,
      // **`structuredClone` with the transfer list, not a push.** A real `MessagePort` detaches
      // everything it is handed, and a stub that quietly does not is a stub that cannot see the
      // defect in `docs/review-round-8.md` R1 -- the page handing away the queue's own bytes. The
      // first version of this check passed with and without the fix, which is the play-glyph
      // lesson again: a check that reads the wrong thing is worse than no check.
      postMessage: (message, transfer) => window.__toWorklet.push(
        transfer ? structuredClone(message, { transfer }) : message,
      ),
    };
  }
  connect() {}
};
// jsdom serves this page from http://localhost, which the real page treats as secure; the flag is
// not set in jsdom, so it is set here rather than weakening the check the page makes.
Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });
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
let holdTrackFetch = false;
window.fetch = async (url, options) => {
  const u = String(url);
  if (u.endsWith('/pair/host')) return { ok: true, json: async () => ({ base: 'https://example.test' }) };
  if (u.includes('/next?')) return new Promise(() => {});   // a poll that never answers
  if (u.endsWith('engine.wasm')) return { ok: true, arrayBuffer: async () => new ArrayBuffer(8) };
  // The real list, off disk: the page reads it at load, and a stub of it would test nothing.
  if (u.endsWith('formats.tsv')) return { ok: true, text: async () => fs.readFileSync('web/src/formats.tsv', 'utf8') };
  posted.push(u);
  if (holdTrackFetch) {
    // A download that never finishes, so the press that calls one off can be tested at all.
    return new Promise((_, reject) => {
      options?.signal?.addEventListener('abort', () => {
        const error = new Error('aborted');
        error.name = 'AbortError';
        reject(error);
      });
    });
  }
  return { ok: true, arrayBuffer: async () => new ArrayBuffer(64) };
};

// **`rules.js` is inlined rather than stripped.** Everything else the page imports is a browser's
// business, but the rules are the page's own code -- dropping the import would leave the functions
// undefined and the checks would test a page that cannot run, which is the failure this harness has
// already shipped once (the worklet stub that ignored transfers, `docs/review-round-8.md` R1).
// A real IndexedDB, because jsdom has none and the page's playlists are the point of S2. The
// package is a dependency of `web/`, like jsdom, and for the same reason: a stub of storage would
// let a broken store pass.
// By path, the way jsdom is imported above: the package lives in `web/node_modules` and this
// script runs from the repository root.
await import('./../web/node_modules/fake-indexeddb/auto/index.mjs');
globalThis.indexedDB = indexedDB;
window.indexedDB = indexedDB;
window.navigator.storage ??= { persist: async () => true, persisted: async () => true,
                               estimate: async () => ({ usage: 1_000_000, quota: 500_000_000_000 }) };

const rulesSource = fs.readFileSync('web/src/rules.js', 'utf8').replace(/^export /gm, '');
const storeSource = fs.readFileSync('web/src/store.js', 'utf8').replace(/^export /gm, '');
const source = fs.readFileSync('web/src/app.js', 'utf8')
  .replace(/^import .*from '\.\/rules\.js';$/gm, rulesSource)
  .replace(/^import .*from '\.\/store\.js';$/gm, storeSource)
  // `catalogue.js` imports the store, which is already inlined above, so its own import line goes
  // and the rest is spliced in under the name `app.js` uses for it.
  .replace(/^import \* as archive from '\.\/catalogue\.js';$/gm,
    'const archive = (() => {' + fs.readFileSync('web/src/catalogue.js', 'utf8')
      .replace(/^import .*$/gm, '')
      .replace(/^export function (\w+)/gm, 'function $1')
      .replace(/^export async function (\w+)/gm, 'async function $1')
    + '\nreturn { toRecords, downloadModland, meta, formats, authors, tracksIn, urlFor, searchTitles, '
    + 'searchAuthors, parseFormats, absentDecoders, playable, onPhone, indexFingerprint, filterIndex, '
    + 'buildRandomTable, drawTrack }; })();')
  .replace(/^import .*$/gm, '')                       // no module loader here
  .replace(/\bawait /g, 'await ');                    // kept: the harness wraps it

console.log('page:');
try {
  window.eval(`(async () => { ${source} \n globalThis.__api = { setQueue, entryFor, render, receive, showPanel, onWorklet, orderLength: () => order.length, playAt, playFromBrowse, renderBrowse, switchTo, markBrowsable, browseTo: (p) => { browsePath = p; return renderBrowse(); }, openRandom, endRandom, removeRandomAt, openAway, endSession, awayState: () => away, choosePlaylist, randomState: () => random, queueNow: () => queue.map((e) => e.url), indexNow: () => index, useRandomSource: (fn) => { randomSource = fn; }, afterOf: (i) => { index = i; return afterCurrent(); }, beforeOf: (i) => { index = i; return beforeCurrent(); } }; })()`);
} catch (error) {
  failures.push(`the script throws on load: ${error.message}`);
  console.log(`  ✗ the script throws on load: ${error.message}`);
}

await new Promise((r) => setTimeout(r, 200));
const $ = (id) => window.document.getElementById(id);

check($('status').textContent.length > 0, 'the status line says something');

// **Hidden means not shown** (`docs/STATUS.md` C38). jsdom lays nothing out, but it does run the
// page's own stylesheet through `getComputedStyle` -- which is enough to catch an element whose
// `display: flex` outranks `hidden`. The Random heading did exactly that and showed over every panel,
// through 211 checks that all read the attribute and never what it did.
{
  const leaking = [...window.document.querySelectorAll('[hidden]')]
    .filter((el) => window.getComputedStyle(el).display !== 'none')
    .map((el) => el.id || el.className || el.tagName);
  check(leaking.length === 0, `every element marked hidden is not displayed${leaking.length ? ` (still showing: ${leaking.join(', ')})` : ''}`);
  // And one that is only hidden later: History's heading hides Random's Filter.
  $('random-filter').hidden = true;
  check(window.getComputedStyle($('random-filter')).display === 'none', "and a button hidden later is too");
  $('random-filter').hidden = false;
}
check(!!window.__api, 'the script finished loading');

if (window.__api) {
  // A queue arriving from the phone, in the shape WebRemote sends.
  window.__api.showPanel('nowplaying');
  check(window.document.querySelectorAll('#fields dd').length === 1
    && window.document.querySelector('#fields dd').textContent.includes('nothing has played'),
    'Now Playing says so before anything has played, rather than opening onto nothing');

  window.__api.showPanel('pair');
  check($('pair').hidden === false, 'the code is up before anything arrives');
  window.__api.receive({
    queue: [
      { url: 'https://modland.com/pub/modules/Protracker/4-Mat/hi%20there.mod', title: 'hi there' },
      { url: 'https://api.modarchive.org/downloads.php?moduleid=42#lotus.mod', title: 'L3_CD6' },
    ],
    index: 0,
  });
  await new Promise((r) => setTimeout(r, 100));

  const rows = window.document.querySelectorAll('#queue li.track');
  check($('pair').hidden === true, 'and the code steps aside once a queue arrives');
  check(rows.length === 2, 'two tracks arrive as two rows');
  check(rows[0]?.querySelector('.title')?.textContent === 'hi there', 'the phone\'s title is used, not the filename');
  check(rows[0]?.querySelector('.meta')?.textContent === 'Modland/Protracker/4-Mat',
    'the subtitle says where the track came from');
  check(rows[1]?.querySelector('.title')?.textContent === 'L3_CD6',
    'a Mod Archive row is named by its title rather than downloads.php');
  check($('count').textContent === '2 tracks', 'the header counts them');
  // Four row states, and the owner met three of them looking alike. A queue that has arrived is
  // *selected*: pointed at, not started, because a browser will not make a sound unasked.
  check(rows[0]?.classList.contains('selected'), 'the row the phone was on is marked selected');
  check(!rows[0]?.classList.contains('playing'), 'and not as playing, because nothing has started');
  check($('playpause').disabled === false, 'play becomes available');
  check($('next').disabled === false, 'next becomes available');

  window.__api.showPanel('paste');
  check($('paste').hidden === false && $('pair').hidden === true, 'the panels switch');
  check($('paste').classList.contains('overlay'), 'and they are dialogs over the page, not sections under it');

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

  const plainRepeatGlyph = $('repeat').querySelector('path').getAttribute('d');
  $('repeat').click();                       // off -> all
  check($('prev').disabled === false, 'repeat-all gives the first track a previous');
  check($('repeat').classList.contains('on'), 'and the button shows it');
  $('repeat').click();                       // all -> one
  check($('repeat').title === 'Repeat one', 'a second press means repeat one');
  check($('repeat').querySelector('path').getAttribute('d') !== plainRepeatGlyph,
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
  // **Two playAt calls a millisecond apart**, which is what a queue arriving from the phone does.
  // The old `start()` returned early on the second and left `node` null; the next line posted to it.
  window.__api.playAt(0);
  window.__api.playAt(0);
  await new Promise((r) => setTimeout(r, 80));
  check($('error').textContent === '', 'two tracks started at once do not race the engine up');

  check(window.document.title.startsWith('hi there'),
    'the tab says what is playing, for a page among twenty');

  // **A local file, handed over rather than fetched.** Its identity is a grant to one app on one
  // phone, so no URL can carry it -- the bytes come with the queue instead.
  const before = posted.length;
  window.__api.receive({
    queue: [{
      url: 'content://com.android.externalstorage.documents/document/primary%3AMusic%2Ftune.mod',
      title: 'a local tune',
      data: 'AAAA',
    }],
    index: 0,
  });
  await new Promise((r) => setTimeout(r, 40));
  check(window.document.querySelector('#queue .meta')?.textContent === 'from the phone',
    'a file from the phone says so rather than showing a document URI');
  window.__api.playAt(0);
  await new Promise((r) => setTimeout(r, 60));
  check(posted.length === before, 'and nothing is fetched for it');
  check($('error').textContent === '', 'and it does not fail');

  // Last, because it replaces the queue everything above was reading -- and into a playlist of
  // his own, because pasting no longer writes into "From the phone" either.
  await window.__api.switchTo('p-paste');
  window.__api.showPanel('paste');
  $('urls').value = 'https://modland.com/pub/modules/AHX/M0d/sundown.ahx';
  $('load').click();
  check($('paste').hidden === true, 'loading closes the paste dialog');
  check(window.document.querySelectorAll('#queue li.track').length === 1, 'and loads what was in it');
  await new Promise((r) => setTimeout(r, 60));   // let that load finish before starting another

  // **A download that will not finish, and the press that calls it off.** The owner's report: press
  // play on something not cached, then ten seconds of a button that still says "play" and cannot be
  // taken back.
  holdTrackFetch = true;
  window.__api.playAt(0);
  await new Promise((r) => setTimeout(r, 80));
  check($('playglyph').getAttribute('d') === 'M6 6h12v12H6z',
    'while a track is fetching, the button offers to stop');
  check($('playpause').title === 'Stop loading', 'and says so');
  $('playpause').click();
  await new Promise((r) => setTimeout(r, 80));
  check($('playglyph').getAttribute('d') !== 'M6 6h12v12H6z', 'pressing it puts the button back');
  check($('sub').textContent === 'stopped', 'and the dock says the load was stopped');
  holdTrackFetch = false;

  // **The load is not over when the fetch is.** The owner met the gap: "opening…" on the dock and a
  // play arrow on the button, which then did something other than what it showed.
  window.__api.playAt(0);
  await new Promise((r) => setTimeout(r, 80));
  // The engine says "ready" in the real page; the harness never runs one, so the wording is the
  // one for an engine that has not answered yet. Either way the bytes have left.
  check($('sub').textContent.includes('opening') || $('sub').textContent.includes('waiting for the engine'),
    'the bytes reach the worklet');
  check($('playglyph').getAttribute('d') === 'M6 6h12v12H6z',
    'and the button still offers to stop while it opens them');
  window.__api.onWorklet({ type: 'failed', reason: 'nothing claimed it' });
  await new Promise((r) => setTimeout(r, 30));
  check(window.document.querySelector('#queue li.track')?.classList.contains('failed'),
    'a refused track is marked in the list');
  check(window.document.querySelector('#fields dd')?.textContent.includes('nothing has played'),
    'and Now Playing stops describing whatever worked last');
  check($('playglyph').getAttribute('d') !== 'M6 6h12v12H6z',
    'a refusal ends the load rather than leaving the button stuck');
  check($('error').textContent === 'nothing claimed it', 'and says what the decoder said');

  // The keys somebody at a desk will try, and the one place they must not fire.
  let played = 0;
  $('playpause').addEventListener('click', () => { played += 1; });
  window.document.body.dispatchEvent(
    new window.KeyboardEvent('keydown', { key: ' ', bubbles: true }));
  check(played === 1, 'space plays');
  $('urls').dispatchEvent(new window.KeyboardEvent('keydown', { key: ' ', bubbles: true }));
  check(played === 1, 'and a space typed into the paste box stays a space');

  // --- volume ---------------------------------------------------------------------------------
  //
  // The page's own level, because a browser tab has none of its own and the phone this mirrors has
  // hardware keys instead. Checked for the same reason the play glyph is: the icon is the only
  // thing that says whether it worked, and the play button already taught what happens when a
  // check reads the wrapper instead of the shape.
  console.log('\nvolume:');
  const LOUD = 'M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z';
  check($('volume').value === '100' && $('volglyph').getAttribute('d') === LOUD,
    'it starts at full and looks it');
  $('mute').click();
  check($('volglyph').getAttribute('d') !== LOUD, 'muting changes the icon, not only the sound');
  $('mute').click();
  check($('volglyph').getAttribute('d') === LOUD, 'and unmuting puts it back');

  window.document.body.dispatchEvent(
    new window.KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
  check($('volume').value === '95', 'down turns it down');
  $('urls').dispatchEvent(new window.KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
  check($('volume').value === '95', 'and an arrow typed into the paste box does not');

  $('volume').value = '0';
  $('volume').dispatchEvent(new window.Event('input'));
  check($('volglyph').getAttribute('d') !== LOUD, 'dragging to zero shows silence without a mute');
  // Muting something already silent would do nothing visible, which is a control that looks broken.
  $('mute').click();
  check($('volume').value === '100', 'and the speaker winds it back up rather than doing nothing');

  // --- a line about one track must not stay under the next (C28) ----------------------------------
  console.log('\nstatus line:');
  $('shuffle').click();                                 // any control writes the line
  check($('status').textContent.startsWith('Shuffle'), 'a control writes the machine\'s line');
  window.__api.onWorklet({
    type: 'opened', describe: 'title\tNext One', duration: 30, subsongs: 1, current: 0,
    canSeek: true, preferredRate: 44100, rate: 44100,
  });
  await new Promise((r) => setTimeout(r, 20));
  check(!$('status').textContent.startsWith('Shuffle'),
    'and opening a track replaces it rather than leaving it standing');
  check($('status').textContent.includes('44100'), 'with what the engine said about this file');
  $('shuffle').click();

  // --- a track from the phone plays more than once (review-round-8 R1) ----------------------------
  //
  // The page transfers the bytes to the worklet, and a transfer detaches the buffer it was given.
  // Handing over the queue's own copy therefore emptied it: the second play sent nothing.
  console.log('\nbytes from the phone:');
  const module16k = Buffer.alloc(16 * 1024, 7).toString('base64');
  window.__api.receive({
    queue: [{ url: 'content://x/1', title: 'From The Phone', file: 'a.mod', data: module16k }],
    index: 0,
  });
  await new Promise((r) => setTimeout(r, 20));
  window.__toWorklet.length = 0;
  await window.__api.playAt(0);
  const first = window.__toWorklet.find((m) => m.type === 'open');
  check(first?.bytes?.byteLength === 16 * 1024, 'the first play hands over sixteen kilobytes');

  window.__toWorklet.length = 0;
  await window.__api.playAt(0);
  const second = window.__toWorklet.find((m) => m.type === 'open');
  check(second?.bytes?.byteLength === 16 * 1024,
    'and so does the second, because the queue kept its own copy');

  // --- what a row and the panel can do with a track (A27) -----------------------------------------
  console.log('\nactions:');
  window.__api.receive({
    queue: [
      { url: 'https://modland.com/pub/modules/Protracker/4-Mat/one.mod', title: 'One', file: 'one.mod' },
      { url: 'content://x/2', title: 'A Local Tune', file: 'local.mod', local: true },
    ],
    index: 0,
  });
  await new Promise((r) => setTimeout(r, 20));
  const menus = [...window.document.querySelectorAll('#queue .rowmenu')];
  check(menus.length === 2, 'every row carries its own menu, including one that cannot play');

  menus[0].click();
  const open = [...window.document.querySelectorAll('#menu button')];
  check($('menu').hidden === false, 'the three dots open it');
  check(open.map((b) => b.textContent).join(',') === 'Save the file,Copy a link,Information',
    'with the three the owner asked for');
  check(open.every((b) => !b.disabled), 'all live for a track with an address');

  menus[1].click();
  const ghostMenu = [...window.document.querySelectorAll('#menu button')];
  check(ghostMenu.every((b) => b.disabled),
    'and all dead for one that stayed on the phone -- no file, no address, nothing to read');

  window.document.body.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  check($('menu').hidden === true, 'escape closes it');

  // The panel acts on what it is describing, so it follows the selection.
  check($('np-save').disabled === false && $('np-link').disabled === false,
    'the panel offers the same three for the track it describes');
  window.__api.receive({ queue: [{ url: 'content://x/1', title: 'Only Local', local: true }], index: 0 });
  await new Promise((r) => setTimeout(r, 20));
  check($('np-save').disabled === true && $('np-link').disabled === true,
    'and goes dead when there is nothing behind them');

  // --- the files that stayed on the phone (A28) ---------------------------------------------------
  //
  // The defect this answers is not the missing music, it is the missing rows: an outside listener's
  // list numbered itself differently from the owner's, and two people cannot talk about that.
  console.log('\nfiles that stayed on the phone:');
  window.__api.receive({
    queue: [
      { url: 'https://modland.com/pub/modules/Protracker/4-Mat/one.mod', title: 'One', file: 'one.mod' },
      { url: 'content://x/2', title: 'A Local Tune', file: 'local.mod', local: true },
      { url: 'https://modland.com/pub/modules/Protracker/4-Mat/three.mod', title: 'Three', file: 'three.mod' },
    ],
    index: 0,
  });
  await new Promise((r) => setTimeout(r, 20));
  const ghostRows = [...window.document.querySelectorAll('#queue li.track')];
  check(ghostRows.length === 3, 'a file that could not travel still takes a place in the list');
  check(ghostRows[1]?.classList.contains('local'), 'and is marked as one that stayed behind');
  check(!ghostRows[1]?.classList.contains('failed'),
    'grey rather than red, because nothing refused it -- it never arrived');
  check(ghostRows[2]?.querySelector('.n')?.textContent === '3',
    'so the numbering matches the phone, which is the whole point');
  check(ghostRows[1]?.onclick === null, 'it does not answer a click');

  // The transport steps over it rather than stopping on a row that can never play.
  check(window.__api.afterOf(0) === 2, 'next skips it');
  check(window.__api.beforeOf(2) === 0, 'and so does previous');

  // A phone paused on one of its own files would otherwise select a row the page cannot start.
  window.__api.receive({
    queue: [
      { url: 'content://x/1', title: 'Local', file: 'a.mod', local: true },
      { url: 'https://modland.com/pub/modules/Protracker/4-Mat/two.mod', title: 'Two', file: 'two.mod' },
    ],
    index: 0,
  });
  await new Promise((r) => setTimeout(r, 20));
  check($('title').textContent === 'Two',
    'and the mark moves off a row the page could never start');

  // --- the tunes inside one file (C23) ------------------------------------------------------------
  //
  // **At the end, and with its own queue.** These press transport buttons and post messages to the
  // worklet, which moves the index and the queue; running them in the middle of the sequence above
  // made four unrelated checks fail, which is a fixture problem rather than a defect and cost ten
  // minutes to see.
  console.log('\nsubsongs:');
  window.__api.setQueue(['https://modland.com/pub/modules/Protracker/4-Mat/a.mod',
                         'https://modland.com/pub/modules/Protracker/4-Mat/b.mod']);
  window.__api.onWorklet({
    type: 'opened', describe: 'title\tThree Tunes', duration: 60, subsongs: 3, current: 0,
    canSeek: true, preferredRate: 44100, rate: 44100,
  });
  await new Promise((r) => setTimeout(r, 20));
  check($('subsongbar').hidden === false, 'a file with several tunes offers the switch');
  check($('allsubsongs').getAttribute('aria-checked') === 'false', 'and it starts off');

  window.__toWorklet.length = 0;
  $('next').click();
  check(!window.__toWorklet.some((m) => m.type === 'subsong'),
    'with it off, next is the queue\'s business and the file is not walked');

  window.__api.onWorklet({
    type: 'opened', describe: 'title\tThree Tunes', duration: 60, subsongs: 3, current: 0,
    canSeek: true, preferredRate: 44100, rate: 44100,
  });
  $('allsubsongs').click();
  check($('allsubsongs').getAttribute('aria-checked') === 'true', 'the switch turns on');
  window.__toWorklet.length = 0;
  $('next').click();
  check(window.__toWorklet.some((m) => m.type === 'subsong' && m.index === 1),
    'and now next asks for the second tune instead of the second file');

  // The worklet answers with the tune's own length and title -- one file, three durations.
  window.__api.onWorklet({
    type: 'subsong', index: 1, duration: 42, describe: 'title\tSecond Tune',
  });
  await new Promise((r) => setTimeout(r, 20));
  check($('np-title').textContent === 'Second Tune', 'and the panel follows it');
  check(window.document.querySelectorAll('.subsong')[1]?.getAttribute('aria-pressed') === 'true',
    'with the right chip marked, from the answer rather than from the click');

  window.__toWorklet.length = 0;
  window.__api.onWorklet({ type: 'ended' });
  await new Promise((r) => setTimeout(r, 20));
  check(window.__toWorklet.some((m) => m.type === 'subsong' && m.index === 2),
    'and the end of a tune moves to the next one inside the file');

  // **The loop that made next play the second tune for ever** (`docs/STATUS.md` C30). The page
  // takes its position from the worklet's answer, and a backend that always answered zero left it
  // asking for tune 2 on every press. The answer is what is checked here, not the request.
  window.__api.onWorklet({ type: 'subsong', index: 2, duration: 30, describe: 'title\tThird' });
  await new Promise((r) => setTimeout(r, 20));
  check(window.document.querySelectorAll('.subsong')[2]?.getAttribute('aria-pressed') === 'true',
    'the chip follows the answer, so the third tune is lit while it plays');
  window.__toWorklet.length = 0;
  $('next').click();
  check(!window.__toWorklet.some((m) => m.type === 'subsong'),
    'and next off the last tune leaves the file rather than replaying it');

  // **Back walks the file too**, which it did not (`docs/STATUS.md` C31). The phone has done this
  // since subsongs existed; the page only ever had the forward half.
  window.__api.onWorklet({ type: 'subsong', index: 2, duration: 30, describe: 'title\tThird' });
  window.__toWorklet.length = 0;
  $('prev').click();
  check(window.__toWorklet.some((m) => m.type === 'subsong' && m.index === 1),
    'back steps to the tune before, not out of the file');

  // And a long press means the opposite of a short one -- past the file -- exactly as
  // `ui/PlayerDock.kt` binds it. This page had the two the wrong way round.
  window.__toWorklet.length = 0;
  $('next').dispatchEvent(new window.Event('pointerdown'));
  await new Promise((r) => setTimeout(r, 600));
  check(!window.__toWorklet.some((m) => m.type === 'subsong'),
    'holding next leaves the file rather than stepping inside it');
  $('next').dispatchEvent(new window.Event('pointerup'));

  $('allsubsongs').click();

  // --- the sliders fill in behind the handle (C21) ------------------------------------------------
  console.log('\nsliders:');
  // A known duration, because the subsong answer above changed it to that tune's own length.
  window.__api.onWorklet({
    type: 'opened', describe: 'title\tOnly', duration: 60, subsongs: 1, current: 0,
    canSeek: true, preferredRate: 44100, rate: 44100,
  });
  window.__api.onWorklet({ type: 'position', seconds: 15 });
  await new Promise((r) => setTimeout(r, 20));
  check($('seek').style.getPropertyValue('--fill') === '25%',
    'a quarter through the tune is a quarter of the track coloured');
  check($('volume').style.getPropertyValue('--fill') === '100%',
    'and the volume slider is painted the same way');

  // --- play, on a track that has reached its end (C24) ---------------------------------------------
  console.log('\nreplay:');
  window.__api.setQueue(['https://modland.com/pub/modules/Protracker/4-Mat/only.mod']);
  window.__api.onWorklet({
    type: 'opened', describe: 'title\tOnly', duration: 10, subsongs: 1, current: 0,
    canSeek: true, preferredRate: 44100, rate: 44100,
  });
  window.__api.onWorklet({ type: 'ended' });
  await new Promise((r) => setTimeout(r, 20));
  check($('playglyph').getAttribute('d') !== 'M6 5h4v14H6zm8 0h4v14h-4z',
    'one track, no repeat: reaching the end stops it');
  window.__toWorklet.length = 0;
  $('playpause').click();
  await new Promise((r) => setTimeout(r, 40));
  check(window.__toWorklet.some((m) => m.type === 'rewind'),
    'and pressing play then starts it again instead of doing nothing');
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

// --- playlists that survive a reload (PLAN_WEB_LIBRARY S2) --------------------------------------
//
// Driven through the page's own store rather than through the DOM, because what S2 promises is not
// a dialog -- it is that the queue comes back. A real IndexedDB is behind it (fake-indexeddb), so a
// broken store fails here rather than on his machine.
{
  console.log('\nplaylists:');
  const store = await import(path.resolve('web/src/store.js'));

  // A handoff writes the phone's playlist, and it is the one that cannot be deleted.
  await store.playlists.save({ id: store.PHONE, name: 'From the phone', tracks: [{ url: 'a', name: 'A' }], index: 0 });
  await store.playlists.remove(store.PHONE);
  check((await store.playlists.get(store.PHONE))?.tracks.length === 1,
    'the phone\'s playlist cannot be deleted — it is a view, not a document');

  await store.playlists.save({ id: 'p-zebra', name: 'Zebra', tracks: [], index: 0 });
  await store.playlists.save({ id: 'p-alpha', name: 'Alpha', tracks: [], index: 0 });
  const names = (await store.playlists.all()).map((p) => p.name);
  check(names[0] === 'From the phone', 'and it sorts first, whatever it is called');
  check(names.slice(1).join(',') === 'Alpha,Zebra', 'with the rest by name');

  await store.playlists.remove('p-zebra');
  check((await store.playlists.all()).length === 2, 'a playlist somebody made can be deleted');

  // The bytes a phone sent are deliberately not kept: they are somebody else's music, they are the
  // largest thing in a queue by far, and a page that hoards them quietly is not what this is.
  await store.playlists.save({
    id: 'p-bytes', name: 'With bytes', index: 0,
    tracks: [{ url: 'x', name: 'X', file: 'x.mod' }],
  });
  const back = await store.playlists.get('p-bytes');
  check(back.tracks[0].data === undefined, 'and a saved track carries no audio with it');

  await store.settings.set('active', 'p-alpha');
  check((await store.settings.get('active')) === 'p-alpha', 'the page remembers which one was showing');
  check((await store.settings.get('nothing', 'fallback')) === 'fallback', 'and answers for what it has never been told');
}

// --- the index, as buckets (PLAN_WEB_LIBRARY S3) ------------------------------------------------
{
  console.log('\ncatalogue:');
  const archive = await import(path.resolve('web/src/catalogue.js'));

  // A slice of Modland's real shape, including the two things that make its paths awkward: an
  // author with a slash in it, and a name that is nothing but punctuation.
  const index = [
    '20000\tProtracker/4-Mat/elysium.mod',
    '30000\tProtracker/4-Mat/another.mod',
    '40000\tProtracker/Jester (Volker Tripp)/elysium.mod',
    '50000\tImpulsetracker/Wayfinder/!!uu !! !!.it',
    '60000\tCoop/Alice & Bob/together.mod',
    '70000\tOctamed/Unknown/x.med',
  ].join('\n');

  // --- the zip, whose tail is what Firefox refused (C33) ----------------------------------------
  //
  // Node's DecompressionStream ignores what follows a deflate stream and Firefox does not, so the
  // owner's failure could not be reproduced by asking node -- which is exactly what had been done.
  // What *can* be checked anywhere is that the member's bounds are computed rather than guessed.
  {
    const zlib = await import('zlib');
    const payload = Buffer.from('12345\tProtracker/4-Mat/elysium.mod\n');
    const deflated = zlib.deflateRawSync(payload);
    const name = Buffer.from('allmods.txt');

    const build = (sizeInLocalHeader) => {
      const local = Buffer.alloc(30);
      local.writeUInt32LE(0x04034b50, 0);
      local.writeUInt16LE(sizeInLocalHeader ? 0 : 0x08, 6);      // bit 3: "the size is elsewhere"
      local.writeUInt32LE(sizeInLocalHeader ? deflated.length : 0, 18);
      local.writeUInt32LE(payload.length, 22);
      local.writeUInt16LE(name.length, 26);
      const central = Buffer.alloc(46);
      central.writeUInt32LE(0x02014b50, 0);
      central.writeUInt32LE(deflated.length, 20);
      central.writeUInt32LE(payload.length, 24);
      central.writeUInt16LE(name.length, 28);
      const eocd = Buffer.alloc(22);
      eocd.writeUInt32LE(0x06054b50, 0);
      eocd.writeUInt16LE(1, 8);
      eocd.writeUInt16LE(1, 10);
      eocd.writeUInt32LE(central.length + name.length, 12);
      eocd.writeUInt32LE(30 + name.length + deflated.length, 16);
      return Buffer.concat([local, name, deflated, central, name, eocd]);
    };

    for (const [where, zip] of [['in the local header', build(true)], ['only in the central directory', build(false)]]) {
      const buffer = zip.buffer.slice(zip.byteOffset, zip.byteOffset + zip.byteLength);
      const { start, end } = archive.memberBounds(buffer);
      const got = zlib.inflateRawSync(Buffer.from(buffer.slice(start, end))).toString();
      check(end === start + deflated.length && got === payload.toString(),
        `the member's bytes are found when its length is ${where}`);
      check(end < zip.byteLength,
        `and the ${zip.byteLength - end} bytes of zip after it are not fed to the decompressor`);
    }
  }

  const { records, tracks, buckets, formats } = archive.toRecords(index);
  // Stored, then searched, through a real IndexedDB -- because search reads by key range and a
  // range query is the one thing a plain object could not have stood in for.
  const { catalogue: store } = await import(path.resolve('web/src/store.js'));
  await store.clear('modland:');
  await store.putAll(records, 100);
  check(tracks === 6, 'every row becomes a track');
  check(buckets === 5, 'and rows sharing a format and author share a bucket');
  check(formats === 4, 'with the formats counted');
  // 5 buckets + 4 author lists + 1 format list + the title shards, one per two-character start.
  const titleShards = records.filter((r) => r.key.startsWith('modland:titles:'));
  check(records.length === 10 + titleShards.length, 'stored as buckets, the lists, and nothing else');
  check(titleShards.length === 5,
    'titles are sharded by their first two characters — el, an, !!, to, x (one is a single letter, padded)');
  const bang = titleShards.find((r) => r.key === 'modland:titles:!!');
  check(bang?.entries[0][0] === '!!uu !! !!.it',
    'and a shard carries the title, its format and its author — no second lookup to play a hit');

  const formatList = records.find((r) => r.key === 'modland:formats').formats.map((f) => f.name);
  check(formatList.join(',') === 'Coop,Impulsetracker,Octamed,Protracker', 'formats come out sorted');
  const fourMat = records.find((r) => r.key === 'modland:tracks:Protracker/4-Mat');
  check(fourMat.tracks.length === 2, 'a bucket holds its own tracks');
  check(fourMat.tracks[0].s === 20000, 'with the size the index gave');

  check(archive.urlFor('Coop', 'Alice & Bob', 'together.mod')
        === 'https://modland.com/pub/modules/Coop/Alice%20%26%20Bob/together.mod',
    'and an author with a space and an ampersand still addresses');

  console.log('\nsearch:');
  const { hits } = await archive.searchTitles('elysium');
  check(hits.length === 2, 'a title is found wherever it lives');
  check(hits.every((h) => h.url.startsWith('https://modland.com/')), 'and comes back playable');
  // The reason every shard is read rather than one: a person typing a name means the middle of it
  // as often as the start.
  const inside = await archive.searchTitles('gether');
  check(inside.hits[0]?.name === 'together.mod', 'including from the middle of a name');
  check((await archive.searchTitles('!!uu')).hits[0]?.name === '!!uu !! !!.it',
    'and a name that is mostly punctuation');
  check((await archive.searchTitles('e')).hits.length === 0, 'one letter is not a search');

  const people = await archive.searchAuthors('mat');
  check(people.length === 1 && people[0].author === '4-Mat', 'an author is found by part of a name');
  check(people[0].format === 'Protracker' && people[0].count === 2,
    'with where they are filed and how much is there');
}

// --- browsing must not rewrite what the phone sent (owner, 2026-09-10) --------------------------
//
// **Shut, and saying so before it is walked into.** The first version of this refused after the
// press: three levels down, a tune chosen, and only then a paragraph explaining that none of it
// counted. He asked for the caption up front, so that is what is checked -- the refusal underneath
// stays, and is checked too, but it is now the second line of defence rather than the first.
if (window.__api) {
  console.log('\nbrowsing and the phone\'s playlist:');
  // The phone's list is showing, which is where a fresh page starts.
  window.__api.receive({
    queue: [{ url: 'https://modland.com/pub/modules/Protracker/4-Mat/one.mod', title: 'One' }],
    index: 0,
  });
  await new Promise((r) => setTimeout(r, 20));
  const before = $('title').textContent;

  check($('tab-browse').getAttribute('aria-disabled') === 'true',
    'the Browse button shows as shut while the phone\'s list is up');
  check(($('tab-browse').title || '').includes('Switch to a playlist of your own'),
    'and its tooltip says what to do about it');

  await window.__api.renderBrowse();
  check($('browsenote').textContent.includes('will not rewrite it'),
    'opening Browse says why, before anything is chosen');
  check($('browsesearch').hidden, 'and offers no search into a list it cannot fill');
  // **What it means, not how many.** This counted one row, which was true until round 8 put
  // History here (and Random, once an index is held): both write into no playlist, so shutting them
  // away from "From the phone" would protect nothing. What must stay true is the point -- the way out
  // comes first, and there is no archive to walk into.
  const offered = [...$('browselist').children].map((li) => li.textContent);
  check(offered[0]?.includes('Make an empty playlist') && !offered.some((t) => t.includes('Modland')),
    'the first row is the way out, and there is no archive to walk into');
  check(offered.slice(1).every((t) => t.includes('Random') || t.includes('History')),
    'and anything else offered is a list that writes into no playlist');
  check($('browselist').textContent.includes('Make an empty playlist'), 'which is what it says');

  window.__api.playFromBrowse(
    [{ url: 'https://modland.com/pub/modules/Protracker/Other/x.mod', name: 'X' }], 0);
  await new Promise((r) => setTimeout(r, 20));
  check($('title').textContent === before, 'and reached anyway, it leaves the queue alone');

  // A playlist of his own, and the same button opens.
  await window.__api.switchTo('p-test');
  check($('tab-browse').getAttribute('aria-disabled') === 'false',
    'switching to a playlist of his own opens Browse again');
  await window.__api.renderBrowse();
  check(!$('browsesearch').hidden, 'search comes back with it');
  await window.__api.switchTo('phone');

  // Pasting is the same act by another door, and it went through it. Found while answering "why
  // can I not find this file", which turned out to be about the index and not about this at all.
  // Re-read rather than reusing `before`: switching away and back reloads the phone's list from
  // storage, which in this harness is empty, so the title has legitimately moved on.
  const beforePaste = $('title').textContent;
  $('urls').value = 'https://modland.com/pub/modules/Protracker/Other/y.mod';
  $('load').click();
  await new Promise((r) => setTimeout(r, 20));
  check($('title').textContent === beforePaste,
    'a pasted address leaves the phone\'s queue alone too');
  check($('pastenote').textContent.includes('Switch to one of your own'), 'and says so');
  $('urls').value = '';
}

// --- only what this browser can play (GOAL.md round 8, item 1) -----------------------------------
//
// The list is the phone's (`SupportedFormatsFileTest` holds them together); what these check is the
// page's half: that it reads the real file, drops exactly what the engine says it lacks, counts what
// it drops, and **says so** -- the owner's condition for leaving anything out at all.
{
  console.log('\nonly what this browser can play:');
  const archive = await import(path.resolve('web/src/catalogue.js'));
  const table = archive.parseFormats(fs.readFileSync('web/src/formats.tsv', 'utf8'));
  const browser = 'openmpt:0.8.9;sc68:3.0.0b;asap:8.0.0;gme:0.6.5;sidplayfp:3.1.1;minimp3:ea99364;zxtune:none';
  const phone = browser.replace(';zxtune:none', '');

  check(table.extensions.size > 100 && table.prefixes.size > 10, 'the real list is read, extensions and prefixes');
  check([...archive.absentDecoders(browser)].join() === 'zxtune', 'the browser engine lacks exactly ZXTune');
  check(archive.absentDecoders(phone).size === 0 && archive.absentDecoders('').size === 0,
    'and absence is only ever what the engine says, never inferred from silence');

  const here = archive.playable(table, archive.absentDecoders(browser));
  const there = archive.playable(table, archive.absentDecoders(phone));
  check(!here('bomb.pt3') && there('bomb.pt3'), 'a Spectrum tune is dropped without ZXTune and kept with it');
  check(!here('warhawk remix.ym'), 'and so is a YM, which the old hand-kept list missed');
  check(here('epic.psm'), 'psm is kept: libopenmpt plays it, whatever ZXTune does');
  check(here('fast.ahx'), 'ahx is kept: HivelyTracker is never named in the fingerprint and is always there');
  check(here('mod.title') && here('ELYSIUM.MOD'), 'an Amiga prefix name and an upper-case extension are both kept');
  check(!here('holiday.jpg') && !archive.onPhone(table)('holiday.jpg'), 'and a photograph is kept by neither');
  check(archive.onPhone(table)('bomb.pt3'), 'the phone would keep the Spectrum tune the browser drops');

  const f = archive.filterIndex([
    '1\tProtracker/4-Mat/elysium.mod', '2\tSpectrum/PT3/x/bomb.pt3', '3\tYM/Hippel/zynaps.ym',
    '4\tPictures/me/holiday.jpg', '', 'no tab here',
  ].join('\n'), here, archive.onPhone(table));
  check(f.total === 4, 'every listed tune is counted, and blank or malformed lines are not');
  check(f.phoneOnly === 2, 'the phone-only ones are counted separately from the unplayable ones');
  check(f.text === '1\tProtracker/4-Mat/elysium.mod', 'and only what plays is kept');

  check(archive.indexFingerprint(browser, table) !== archive.indexFingerprint(phone, table),
    'an index built by another engine reads as another index');
  const grown = archive.parseFormats(fs.readFileSync('web/src/formats.tsv', 'utf8') + '\nextension\tnew\topenmpt\n');
  check(archive.indexFingerprint(browser, table) !== archive.indexFingerprint(browser, grown),
    'and so does one built by another list — the phone lost 5,558 files to that once');

  if (window.__api) {
    const { catalogue: store } = await import(path.resolve('web/src/store.js'));
    await window.__api.switchTo('p-test');

    await store.putAll([{ key: 'modland:meta', tracks: 315294, total: 516107, phoneOnly: 26537,
                          formats: 90, buckets: 32212, fingerprint: 'x' }]);
    await window.__api.renderBrowse();
    check($('browsenote').textContent.includes("holds 315,294 of Modland's 516,107"),
      'Browse says how much of Modland this browser holds');
    check($('browsenote').textContent.includes('26,537 of them play on the phone'),
      'and that the rest is formats it cannot play, many of which the phone can');

    // An index from before the page filtered at all: no counts, every row.
    await store.putAll([{ key: 'modland:meta', tracks: 516107, formats: 339, buckets: 43721, fingerprint: 'x' }]);
    await window.__api.renderBrowse();
    check($('browsenote').textContent.includes('Downloading it again (5.76 MB)'),
      'an index built before the filter says why it should be downloaded again, before it is');

    await store.clear('modland:');
    await window.__api.switchTo('phone');
  }
}

// --- every list behaves like the phone's (GOAL.md round 8, item 2) -------------------------------
//
// jsdom lays nothing out, so where a row ends up cannot be checked here; **whether the page asks to
// scroll, and how**, can. Each call is recorded by a stand-in for `scrollIntoView`.
if (window.__api) {
  console.log('\nlists keep the playing row in view:');
  const scrolls = [];
  const original = window.HTMLElement.prototype.scrollIntoView;
  // **Where the row was, recorded when it is asked for.** `playAt` draws the playlist again once the
  // tune opens, replacing every row -- so an element kept for later is detached by the time it is
  // looked at, and its list reads as none. The first version of this check failed on exactly that.
  window.HTMLElement.prototype.scrollIntoView = function (options) {
    const list = this.parentElement;
    scrolls.push({ list: list?.id, at: list ? [...list.children].indexOf(this) : -1, options });
  };
  const { catalogue: store } = await import(path.resolve('web/src/store.js'));
  try {
    await window.__api.switchTo('p-lists');
    const name = (n) => `t${n}.mod`;
    const url = (n) => `https://modland.com/pub/modules/Protracker/Lister/${name(n)}`;
    window.__api.setQueue([1, 2, 3, 4].map((n) => ({ url: url(n), name: name(n), file: name(n),
                                                     meta: 'Modland/Protracker/Lister' })), 0);
    window.__api.render();
    check(scrolls.length === 0, 'arriving at a list does not scroll it');

    await window.__api.playAt(2);
    const inQueue = scrolls.filter((s) => s.list === 'queue');
    check(inQueue.length === 1 && inQueue[0].at === 2 && inQueue[0].options?.block === 'nearest',
      'a new playing row is revealed once, by the least scroll that shows it');

    scrolls.length = 0;
    window.__api.render();
    check(scrolls.length === 0, 'drawing the list again does not scroll it');

    await store.putAll([{ key: 'modland:tracks:Protracker/Lister',
                          tracks: [1, 2, 3, 4].map((n) => ({ t: name(n), s: 1000 })) }]);
    window.__api.showPanel('browse');
    await window.__api.browseTo(['modland', 'Protracker', 'Lister']);
    const marked = [...$('browselist').children].filter((r) => r.classList.contains('playing'));
    check(marked.length === 1 && marked[0] === $('browselist').children[2],
      'Browse marks the tune that is playing, and only that one');
    check(scrolls.length === 0, 'and opening it does not scroll to it');

    await window.__api.playAt(3);
    const inBrowse = scrolls.filter((s) => s.list === 'browselist');
    check(inBrowse.length === 1 && inBrowse[0].at === 3
          && $('browselist').children[3].classList.contains('playing')
          && !$('browselist').children[2].classList.contains('playing'),
      'when the tune changes with Browse open, Browse moves the mark and follows it by the same rule');

    window.__api.showPanel(null);
    scrolls.length = 0;
    await window.__api.playAt(1);
    check(!scrolls.some((s) => s.list === 'browselist'),
      'and a closed Browse is left alone');
  } finally {
    if (original) window.HTMLElement.prototype.scrollIntoView = original;
    else delete window.HTMLElement.prototype.scrollIntoView;
    await store.clear('modland:');
    await window.__api.switchTo('phone');
  }
}

// --- Random, in the shape the phone has (GOAL.md round 8, item 3) --------------------------------
if (window.__api) {
  console.log('\nrandom:');
  const archive = await import(path.resolve('web/src/catalogue.js'));
  const { catalogue: store, playlists } = await import(path.resolve('web/src/store.js'));
  const settle = (ms = 80) => new Promise((r) => setTimeout(r, ms));

  // One author with one tune, another with three: "uniform over tunes" and "uniform over authors"
  // give the lone author one draw in four and one in two, so the difference is visible.
  const sample = ['1\tProtracker/Solo/only.mod', '1\tProtracker/Trio/a.mod',
                  '1\tProtracker/Trio/b.mod', '1\tProtracker/Trio/c.mod'].join('\n');
  const built = archive.toRecords(sample);
  await store.clear('modland:');
  await store.putAll(built.records, 100);
  await store.putAll([{ key: 'modland:meta', tracks: built.tracks, total: built.tracks, phoneOnly: 0,
                        buckets: built.buckets, formats: built.formats, fingerprint: 'x' }]);
  const table = await archive.buildRandomTable();
  check(table.total === 4, 'the table counts every tune once');
  const draw = async (r) => (await archive.drawTrack(table, () => r))?.name;
  check(await draw(0) === 'only.mod' && await draw(0.3) === 'a.mod' && await draw(0.99) === 'c.mod',
    'a roll is uniform over tunes: the lone author gets one draw in four, not one in two');

  // A known sequence, so the record is predictable: only, a, b, c, only, a, …
  let step = 0;
  const cycle = [0, 0.3, 0.55, 0.8];
  window.__api.useRandomSource(() => cycle[step++ % cycle.length]);

  const one = 'https://example.test/one.mod';
  const two = 'https://example.test/two.mod';
  await window.__api.switchTo('p-random');
  window.__api.setQueue([one, two], 1);
  await settle(700);   // let the playlist's own save land first
  const before = JSON.stringify((await playlists.get('p-random'))?.tracks);

  await window.__api.openRandom();
  await settle();
  check(window.__api.randomState() != null && !$('randomhead').hidden, 'Browse → Random opens the record, with its heading');
  check(window.__api.queueNow().length === 1 && window.__api.indexNow() === 0,
    'and entering plays one, with no second press');
  check($('shuffle').disabled, 'shuffle is shut while the dice runs');
  // The phone hides the chip here; the owner asked for the page's to stay usable (2026-09-11).
  check($('playlistname').textContent === 'Random' && !$('playlistchip').disabled,
    'the playlist chip says where the music is coming from, and stays usable');

  window.__api.onWorklet({ type: 'ended' });
  window.__api.onWorklet({ type: 'ended' });
  await settle();
  check(window.__api.queueNow().length === 2, 'two "ended" in a row advance once — the C35 lesson');

  // Each file the engine opens reports how many tunes it holds. Nothing here opens files, so a
  // count left by an earlier check would stand -- and with "play every tune" on, next would move
  // inside that phantom file instead of along the record.
  window.__api.onWorklet({ type: 'opened', duration: 60, subsongs: 1, current: 0, describe: '' });
  // **A tap, not a bare `.click()`.** `holdToSkipFile` swallows the click that follows a long press
  // and only forgets it on the next `pointerdown` -- so a bare click after an earlier check's long
  // press on the same button is eaten, which is what this check first ran into. A finger always
  // sends `pointerdown` first; the keyboard's arrow and a media key do not, which is `docs/STATUS.md`
  // C36.
  const tap = (button) => {
    button.dispatchEvent(new window.Event('pointerdown'));
    button.dispatchEvent(new window.Event('pointerup'));
    button.click();
  };
  tap($('prev'));
  await settle();
  check(window.__api.indexNow() === 0, 'previous walks back through the record');
  tap($('next'));
  await settle();
  check(window.__api.indexNow() === 1 && window.__api.queueNow().length === 2,
    'next walks the record while there is a row ahead');
  tap($('next'));
  await settle();
  check(window.__api.queueNow().length === 3 && window.__api.indexNow() === 2, 'and rolls only at its end');
  check(new Set(window.__api.queueNow()).size === 3, 'no tune repeats while the pool has others');

  window.__api.removeRandomAt(0);
  check(window.__api.queueNow().length === 2 && window.__api.indexNow() === 1,
    'a row can be removed from the record, and the cursor keeps its tune');

  await settle(700);   // any save a mistake had queued would have landed by now
  check(JSON.stringify((await playlists.get('p-random'))?.tracks) === before,
    'nothing the dice did was written into the playlist');

  // A pick that will not open is walked past and leaves the record, as on the phone.
  const beforeSkip = window.__api.queueNow();
  const failedUrl = beforeSkip[beforeSkip.length - 1];
  window.__api.onWorklet({ type: 'failed', reason: 'nothing claimed it' });
  await settle();
  const afterSkip = window.__api.queueNow();
  check(!afterSkip.slice(0, -1).includes(failedUrl) && afterSkip.length === beforeSkip.length
        && window.__api.indexNow() === afterSkip.length - 1,
    'a pick that will not open is walked past, and leaves the record — it did not play');
  check($('error').textContent === '', 'without leaving a red error behind the music that followed');

  // The bar starts again with the tune, not when the engine first reports a position.
  window.__api.onWorklet({ type: 'opened', duration: 120, subsongs: 1, current: 0, describe: '' });
  window.__api.onWorklet({ type: 'position', seconds: 67 });
  const wasAt = $('elapsed').textContent;
  // A download that never finishes, so "before it is done" is something that can be looked at.
  holdTrackFetch = true;
  tap($('prev'));
  await settle(20);
  check(wasAt === '1:07' && $('elapsed').textContent === '0:00' && Number($('seek').value) === 0,
    'choosing another tune puts the bar back to the start while it is still downloading');
  holdTrackFetch = false;
  $('playpause').click();   // "stop loading", so the held download is called off
  await settle();

  window.__api.endRandom();
  await settle();
  check(window.__api.randomState() == null && $('randomhead').hidden, 'leaving ends the session');
  check(window.__api.queueNow().join() === `${one},${two}` && window.__api.indexNow() === 1,
    'and the playlist is exactly where it was left');
  check(!$('shuffle').disabled && !$('playlistchip').disabled, 'and the transport is the playlist\'s again');

  // Choosing a playlist from the sheet in the middle of a session leaves it for that playlist.
  await window.__api.openRandom();
  await settle();
  window.__api.choosePlaylist('p-random');
  await settle();
  check(window.__api.randomState() == null && window.__api.queueNow().join() === `${one},${two}`,
    'choosing a playlist from the sheet while the dice runs ends the session and shows that playlist');

  // "From the phone" is never written into -- by Browse, the paste box, and now by the dice.
  await window.__api.switchTo('phone');
  const phoneBefore = JSON.stringify((await playlists.get('phone'))?.tracks);
  await window.__api.openRandom();
  window.__api.onWorklet({ type: 'ended' });
  await settle(700);
  check(JSON.stringify((await playlists.get('phone'))?.tracks) === phoneBefore,
    '"From the phone" is left alone while the dice runs over it');

  // A queue arriving from elsewhere ends the session rather than landing in the record.
  window.__api.receive({ queue: [{ url: one, title: 'One' }], index: 0 });
  await settle();
  check(window.__api.randomState() == null && window.__api.queueNow().join() === one,
    'a queue from the phone ends the session and becomes what is showing');

  window.__api.useRandomSource(Math.random);
  await store.clear('modland:');
}

// --- History, the same stream kept (GOAL.md round 8, item 4) -------------------------------------
if (window.__api) {
  console.log('\nhistory:');
  const { catalogue: store, playlists, played } = await import(path.resolve('web/src/store.js'));
  const settle = (ms = 80) => new Promise((r) => setTimeout(r, ms));

  await played.clear();
  const entry = (n, name = `Tune ${n}`) => ({ url: `https://example.test/h${n}.mod`, name, meta: 'm',
                                              file: `h${n}.mod`, replayable: true });
  await played.record(entry(1));
  await played.record(entry(2));
  await played.record(entry(1, 'What it calls itself'));
  let rows = await played.recent();
  check(rows.length === 2, 'one row per tune, not per play');
  check(rows[0].url.endsWith('h1.mod') && rows[0].playCount === 2 && rows[0].name === 'What it calls itself',
    'a replay moves to the top, is counted, and takes the better title');

  await played.record(entry(3), { limit: 2 });
  rows = await played.recent();
  check(rows.length === 2 && !rows.some((r) => r.url.endsWith('h2.mod')), 'past the limit the oldest is forgotten');

  await played.clear();
  check((await played.recent()).length === 0, 'and clearing empties it');

  // Recorded at the one place every play arrives, under the name the tune gives itself.
  await window.__api.switchTo('p-history');
  const h1 = 'https://example.test/list1.mod';
  const h2 = 'https://example.test/list2.mod';
  window.__api.setQueue([h1, h2], 0);
  await settle(700);
  const before = JSON.stringify((await playlists.get('p-history'))?.tracks);
  await window.__api.playAt(1);
  window.__api.onWorklet({ type: 'opened', duration: 10, subsongs: 1, current: 0,
                           describe: 'title\tThe Name It Gives Itself\n', canSeek: true });
  await settle();
  rows = await played.recent();
  check(rows[0]?.url === h2 && rows[0].name === 'The Name It Gives Itself',
    'a play is recorded once the engine has opened it, under its own name');

  window.__api.showPanel('browse');
  await window.__api.browseTo(['history']);
  const first = $('browselist').children[0];
  check(first?.textContent.includes('The Name It Gives Itself'), 'Browse → History lists it');
  check(first?.classList.contains('playing'), 'and marks it, by the same rule as every list');

  first.click();
  await settle();
  check(window.__api.awayState() != null && $('sessiontitle').textContent === 'Playing from your history',
    'playing from History says where it is playing from');
  await settle(700);
  check(JSON.stringify((await playlists.get('p-history'))?.tracks) === before,
    'playing from History leaves the playlist alone');
  window.__api.endSession();
  check(window.__api.awayState() == null && window.__api.queueNow().join() === `${h1},${h2}`,
    'and the way back puts the playlist back as it was');

  // A tune the phone handed over as bytes: kept in History, not offered for a replay it cannot keep.
  await played.record({ url: 'content://phone/owned.mod', name: 'Owned', meta: 'from the phone',
                        file: 'owned.mod', replayable: false });
  window.__api.showPanel('browse');
  await window.__api.browseTo(['history']);
  const gone = [...$('browselist').children].find((li) => li.textContent.includes('Owned'));
  check(gone?.classList.contains('gone') && gone.onclick == null,
    'a tune the page cannot play again is listed, and not offered');

  const clear = [...$('browselist').children].find((li) => li.textContent.includes('Clear the history'));
  clear.click();
  await settle();
  check((await played.recent()).length === 0, 'Clear the history empties it');

  // C37: Browse on "From the phone" with an index downloaded threw, because a helper was read
  // before its declaration. This is that state.
  await window.__api.switchTo('phone');
  await store.putAll([{ key: 'modland:meta', tracks: 3, total: 3, phoneOnly: 0, formats: 1, buckets: 1,
                        fingerprint: 'x' }]);
  let threw = null;
  try { await window.__api.browseTo([]); } catch (error) { threw = error; }
  const labels = [...$('browselist').children].map((li) => li.textContent);
  check(!threw, `Browse on the phone's list with an index held does not throw${threw ? ` (${threw.message})` : ''}`);
  // An index from before the filter: no counts, every row. The owner re-indexed without being asked
  // because this panel -- all of Browse he gets while the phone's list shows -- never said so.
  await store.putAll([{ key: 'modland:meta', tracks: 516107, formats: 339, buckets: 43721, fingerprint: 'x' }]);
  await window.__api.browseTo([]);
  check($('browsenote').textContent.includes('Downloading it again (5.76 MB)'),
    'and an index from before the filter is called out there too, where the owner would see it');
  check(labels.some((t) => t.includes('Random')) && labels.some((t) => t.includes('History')),
    'and still offers Random and History, which write into no playlist');
  await window.__api.browseTo(['history']);
  check($('browsetitle').textContent === 'History', 'History opens even there');

  await store.clear('modland:');
  window.__api.showPanel(null);
}

// --- the rules, from the file the Kotlin tests read (PLAN_WEB_LIBRARY S1) -----------------------
//
// **The point is not that these pass.** It is that they are the same cases `RuleCasesTest.kt`
// drives, so a rule changed on one side and not the other fails on the side that did not change.
// C23, C30 and C31 were each one screen doing what the other did not, and all three were found by
// the owner rather than here.
{
  console.log('\nshared rules:');
  const rules = await import(path.resolve('web/src/rules.js'));
  const text = fs.readFileSync('docs/rules/queue-cases.tsv', 'utf8');

  const groups = {};
  let group = null;
  let header = null;
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    if (line.startsWith('[') && line.endsWith(']')) { group = line.slice(1, -1); header = null; groups[group] = []; continue; }
    const cells = raw.split('\t').map((c) => c.trim());
    if (!header) { header = cells; continue; }
    groups[group].push(Object.fromEntries(header.map((h, i) => [h, cells[i]])));
  }

  const number = (v) => (v === '-' ? null : Number(v));
  const yes = (v) => v === 'yes';
  // A group that is empty is an agreement that quietly stopped being one -- the failure this whole
  // file exists to prevent, wearing a green tick.
  const each = (name, run) => {
    const list = groups[name] ?? [];
    check(list.length > 0, `'${name}' has cases to check`);
    let wrong = 0;
    for (const c of list) if (!run(c)) { wrong++; console.log(`    ✗ ${c.why}`); }
    check(wrong === 0, `${name}: ${list.length} cases from the shared file`);
  };

  each('next', (c) =>
    rules.nextIndex({ tracks: +c.tracks, at: +c.at, repeat: c.repeat }) === number(c.expect));
  each('previous', (c) =>
    rules.previousIndex({ tracks: +c.tracks, at: +c.at, repeat: c.repeat }) === number(c.expect));
  each('subsong', (c) =>
    rules.nextSubsong({ playAll: yes(c.playAll), subsong: +c.subsong, count: +c.count,
                        repeatOne: yes(c.repeatOne) }) === number(c.expect));
  each('playFromEnd', (c) =>
    rules.shouldRestart({ engineFinished: yes(c.engineFinished), position: +c.position,
                          duration: +c.duration }) === yes(c.expect));
  // Random's three: the web's half only, for the reason the file gives next to them.
  each('randomNext', (c) =>
    rules.randomNext({ length: +c.length, at: +c.at }) === (c.expect === 'roll' ? 'roll' : number(c.expect)));
  each('randomPrevious', (c) => rules.randomPrevious({ at: +c.at }) === number(c.expect));
  each('randomFresh', (c) =>
    rules.freshPick({ drawn: c.drawn.split(','), seen: c.seen === '-' ? [] : c.seen.split(',') }) === c.expect);
}

console.log(failures.length ? `\n❌ ${failures.length} failed` : '\n✅ page checks passed');
process.exit(failures.length ? 1 : 0);
