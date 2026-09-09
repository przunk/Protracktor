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
  constructor() { this.port = { onmessage: null, postMessage: (m) => window.__toWorklet.push(m) }; }
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

const source = fs.readFileSync('web/src/app.js', 'utf8')
  .replace(/^import .*$/gm, '')                       // no module loader here
  .replace(/\bawait /g, 'await ');                    // kept: the harness wraps it

console.log('page:');
try {
  window.eval(`(async () => { ${source} \n globalThis.__api = { setQueue, entryFor, render, receive, showPanel, onWorklet, orderLength: () => order.length, playAt, afterOf: (i) => { index = i; return afterCurrent(); }, beforeOf: (i) => { index = i; return beforeCurrent(); } }; })()`);
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

  // Last, because it replaces the queue everything above was reading.
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

console.log(failures.length ? `\n❌ ${failures.length} failed` : '\n✅ page checks passed');
process.exit(failures.length ? 1 : 0);
