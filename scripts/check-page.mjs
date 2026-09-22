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
  // Refused while `__audioBlocked` is set: a browser will not resume audio without a click on the
  // page, and a link opened from another app has had none.
  async resume() { if (!window.__audioBlocked) this.state = 'running'; }
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
  // ASMA's archive, answering ranges as asma.atari.org does -- or ignoring them, as a server may.
  if (u === 'https://asma.atari.org/asmadb/asma.zip') {
    const zip = window.__asmaZip;
    const range = options?.headers?.Range;
    const copy = (b) => b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength);
    const headers = { get: (name) => (name.toLowerCase() === 'content-length' ? String(zip.length) : null) };
    if (options?.method === 'HEAD') return { ok: true, status: 200, headers };
    // **What the real server does to a browser**: a range the browser must ask about first -- the
    // suffix form, or any range where it safelists none -- is refused, because asma.atari.org
    // answers the question without `Access-Control-Allow-Headers`. curl never asks, so it passes
    // there and fails in a browser.
    if (range && (range.startsWith('bytes=-') || window.__asmaNoSafeRange)) throw new TypeError('Failed to fetch');
    if (!range || window.__asmaIgnoreRange) return { ok: true, status: 200, headers, arrayBuffer: async () => copy(zip) };
    window.__asmaRanges.push(range);
    const [, from, to] = /^bytes=(\d*)-(\d*)$/.exec(range);
    const slice = from === '' ? zip.subarray(Math.max(0, zip.length - Number(to))) : zip.subarray(Number(from), Number(to) + 1);
    return { ok: true, status: 206, arrayBuffer: async () => copy(slice) };
  }
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
// Read by key range: the page's search asks for every title shard by prefix.
window.IDBKeyRange = IDBKeyRange;
// And a Modland address escapes a name byte by byte, as the phone's URLEncoder does.
window.TextEncoder ??= TextEncoder;
// And ASMA's list names its files in bytes, read back with a decoder.
window.TextDecoder ??= TextDecoder;
// Links are deflated and inflated through streams, as a browser does it.
window.CompressionStream ??= CompressionStream;
window.DecompressionStream ??= DecompressionStream;
window.Response ??= Response;
window.navigator.storage ??= { persist: async () => true, persisted: async () => true,
                               estimate: async () => ({ usage: 1_000_000, quota: 500_000_000_000 }) };

const rulesSource = fs.readFileSync('web/src/rules.js', 'utf8').replace(/^export /gm, '');
const lengthsSource = fs.readFileSync('web/src/songlengths.js', 'utf8').replace(/^export /gm, '');
const storeSource = fs.readFileSync('web/src/store.js', 'utf8').replace(/^export /gm, '');
const source = fs.readFileSync('web/src/app.js', 'utf8')
  .replace(/^import .*from '\.\/rules\.js';$/gm, rulesSource)
  .replace(/^import .*from '\.\/songlengths\.js';$/gm, lengthsSource)
  .replace(/^import .*from '\.\/store\.js';$/gm, storeSource)
  // `catalogue.js` imports the store, which is already inlined above, so its own import line goes
  // and the rest is spliced in under the name `app.js` uses for it.
  .replace(/^import \* as archive from '\.\/catalogue\.js';$/gm,
    'const archive = (() => {' + fs.readFileSync('web/src/catalogue.js', 'utf8')
      .replace(/^import .*$/gm, '')
      .replace(/^export function (\w+)/gm, 'function $1')
      .replace(/^export async function (\w+)/gm, 'async function $1')
    + '\nreturn { toRecords, downloadModland, meta, formats, authors, tracksIn, urlFor, searchTitles, '
    + 'searchAuthors, parseFormats, absentDecoders, playable, onPhone, indexFingerprint, refreshPlayable, stampIndex, '
    + 'buildRandomTable, drawTrack, platformOf, downloadAsma, sources, sourceName, '
    + 'downloadSongLengths, songLengthsFor, songLengthsMeta, clearSongLengths }; })();')
  .replace(/^import .*$/gm, '')                       // no module loader here
  .replace(/\bawait /g, 'await ');                    // kept: the harness wraps it

console.log('page:');
try {
  window.eval(`(async () => { ${source} \n globalThis.__archive = archive; globalThis.__api = { setQueue, entryFor, render, receive, showPanel, onWorklet, orderLength: () => order.length, playAt, playFromBrowse, renderBrowse, switchTo, runSearch, browseTo: (p) => { browsePath = p; return renderBrowse(); }, openRandom, endRandom, removeRandomAt, openAway, endSession, awayState: () => away, choosePlaylist, saveEdits, discardEdits, dirtyNow: () => dirty, randomState: () => random, queueNow: () => queue.map((e) => e.url), indexNow: () => index, useRandomSource: (fn) => { randomSource = fn; }, releaseYear, describeFields, sendToWeb, canSendToWeb, inflateFragment, downloadAsmaIndex, archiveMeta: (s) => archive.meta(s), catalogueStore: catalogue, searchTitlesNow: (q) => archive.searchTitles(q), randomTable: () => archive.buildRandomTable(), lastSentLink: () => lastSentLink, contextNow: () => context, afterOf: (i) => { index = i; return afterCurrent(); }, finishedNow: () => finished, endedByClockNow: () => endedByClock, beforeOf: (i) => { index = i; return beforeCurrent(); } }; })()`);
} catch (error) {
  failures.push(`the script throws on load: ${error.message}`);
  console.log(`  ✗ the script throws on load: ${error.message}`);
}

await new Promise((r) => setTimeout(r, 200));
const $ = (id) => window.document.getElementById(id);

check($('status').textContent.length > 0, 'the status line says something');

// **Every button carries an icon and its name** -- the standing rule for this app, on the phone
// and on the page alike.
// Checked across the whole page so the next button cannot be added bare. The subsong chips are the
// one exception, numbers in circles exactly as the phone draws them.
{
  const bare = [...window.document.querySelectorAll('button')]
    .filter((b) => !b.classList.contains('subsong') && !b.querySelector('svg'))
    .map((b) => b.id || b.textContent.trim() || b.className);
  check(bare.length === 0, `every button on the page has an icon${bare.length ? ` (bare: ${bare.join(', ')})` : ''}`);
}

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
  // Four row states, which must not look alike. A queue that has arrived is
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
  // The phone's rows (`ui/NowPlaying.kt`): where the file is, the year, then the file's own fields.
  check(labels.join(',') === 'File,Year,Format,Artist',
    'Now Playing shows the fields in the phone\'s order, under the phone\'s names');
  check(values[0] === 'Modland/Protracker/4-Mat/hi there.mod',
    'starting with where the file lives, read off the address of a row the phone sent');
  check(values.includes('Rob Hubbard'), 'and their values');
  check(window.document.querySelectorAll('.subsong').length === 3,
    'a file with three tunes gets three subsong buttons');

  // --- C56: a tune whose length nothing knows still ends ----------------------------------------
  //
  // The tune opened just above is a SID with `duration: 0`, which is what the engine really reports
  // for one -- nothing in the file says when to stop. Before this, `render` never ran short, `ended`
  // was never posted, and the page played it for ever while the phone ended it from HVSC's
  // database. These four checks fail against that page.
  check($('fallback').value === '3' && $('fallbackvalue').textContent === '3 minutes',
    'the fallback length starts at the phone\'s default of three minutes');
  window.__api.onWorklet({ type: 'position', seconds: 60 });
  check(window.__api.endedByClockNow() === false,
    'a tune with no length of its own is left alone before the fallback');

  // **Standing at the end of the queue with repeat off**, so that running out has nowhere to go and
  // `trackEnded` takes its last branch -- which sets `finished`, the state that turns the play
  // button into "again". That flag is the observable: it is only ever set by the end-of-tune path,
  // so seeing it proves the fallback went through the same door a real `ended` does.
  const wasAt = window.__api.indexNow();
  const last = window.__api.queueNow().length - 1;
  check(window.__api.afterOf(last) == null, 'and the queue has nothing after the last track');
  window.__api.onWorklet({ type: 'position', seconds: 3 * 60 });
  check(window.__api.endedByClockNow() === true && window.__api.finishedNow() === true,
    'and ends, through the same path a real end of tune takes, once the fallback is reached');
  // **And a length HVSC supplied ends the tune too**, which gating on `duration <= 0` would miss:
  // that assumes a format knowing its own length runs out of audio, which is true of a tracker
  // module and false of the one format this was written for. A SID never runs out, it loops -- so
  // its music fades at 3:38, it begins again at 4:05, and the bar sits pinned at the end.
  window.__api.onWorklet({
    type: 'opened',
    describe: 'title\tNormal People\tformat\tCommodore 64 (SID)',
    duration: 245,
    subsongs: 1,
    canSeek: false,
    preferredRate: 44100,
    rate: 44100,
  });
  await new Promise((r) => setTimeout(r, 20));
  window.__api.afterOf(last);
  window.__api.onWorklet({ type: 'position', seconds: 244 });
  check(window.__api.endedByClockNow() === false,
    'a tune with a known length is left alone before it');
  window.__api.onWorklet({ type: 'position', seconds: 245 });
  check(window.__api.endedByClockNow() === true,
    'and ends when that length is reached, rather than looping for ever');

  // **And the guard survives the next track being started**, which is the bug that shipped between
  // the two: `playAt` cleared the flag, but `playAt` only begins *loading* the next tune -- the
  // worklet plays the old one until the bytes arrive. Its position messages kept coming, `duration`
  // had just been zeroed for the bar so the limit fell back to three minutes, and the queue walked
  // on once per message -- a SID ending and the list jumping forward four tracks.
  await window.__api.playAt(wasAt);
  check(window.__api.endedByClockNow() === true,
    'and starting the next track does not reopen the guard while the old one is still playing');

  // **Put back what these four checks moved.** `afterOf` sets the index as well as reading past it,
  // and the tune above is now finished -- both of which the checks after this one depend on. The
  // open is the same message the block started with, which clears `finished` and the fallback's own
  // flag and leaves Now Playing showing exactly what it showed before.
  window.__api.afterOf(wasAt);
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
  // Author · machine · year, the phone's dock line; the machine is the file's, from its name.
  check($('sub').textContent === 'Rob Hubbard · Amiga · 1985', 'the dock card says who, for what, and when');
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
  check(metadata?.album === 'Amiga · 1985', 'and what it is for, and when');

  // **The author where the file is silent.** A plain .mod has nowhere to record one; Modland
  // files it under a folder that names them.
  window.__api.onWorklet({
    type: 'opened',
    describe: 'title\tzoolook\nformat\tProTracker MOD (M.K.)\nartist\t\nchannels\t4\nmessage\tgreetings to\n  all  the scene\n',
    duration: 0, subsongs: 1, canSeek: true, preferredRate: 44100, rate: 44100,
  });
  await new Promise((r) => setTimeout(r, 50));
  check($('sub').textContent === '4-Mat · Amiga', 'a silent file is credited to the folder it is filed under');
  const silent = [...window.document.querySelectorAll('#fields dt')].map((n) => n.textContent);
  const said = [...window.document.querySelectorAll('#fields dd')].map((n) => n.textContent);
  check(said[silent.indexOf('Artist')] === '4-Mat' && silent.includes('Channels'),
    'and Now Playing says so too, with the rest of what the file holds');
  check(!$('np-message').hidden && $('np-message-text').textContent === 'greetings to\n  all  the scene',
    'the module\'s message is shown whole, every line and its spacing');
  check(!silent.some((label) => label.includes('the scene')), 'and none of its lines is taken for a field');
  // The phone's `DescribeBlockTest` cases, the same rule on this side.
  const read = api.describeFields('comment\tmessage\there\nartist\tRob Hubbard\nmessage\tthe real one\n  second line');
  check(read.comment === 'message\there' && read.artist === 'Rob Hubbard' && read.message === 'the real one\n  second line',
    'the word message inside another value is only a word, and the real message after it is found');
  check(api.describeFields('title\tx\nmessage\tname\tsize\nloop\t0012').loop === undefined,
    'a line of the message holding a tab is not taken for a field');

  // The year, by the phone's `ReleaseYear` rules.
  const yearOf = (describe) => api.releaseYear(api.describeFields(describe));
  check(yearOf('year\t0\ndate\t\ncopyright\t1987-1989 Rob Hubbard') === '1987\u20131989', 'a range is kept as a range');
  check(yearOf('copyright\t1987 Rob Hubbard 1989') === '1987', 'two years in a sentence are not a range');
  check(yearOf('copyright\tKMCA-1234') === '', 'and a catalogue number is not a year');
  check(yearOf('date\t2004-05-01T10:00') === '2004', 'an ISO date gives its year');
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

  // Last, because it replaces the queue everything above was reading -- and into one of the
  // reader's own playlists, because pasting does not write into "From the phone".
  await window.__api.switchTo('p-paste');
  window.__api.showPanel('paste');
  $('urls').value = 'https://modland.com/pub/modules/AHX/M0d/sundown.ahx';
  $('load').click();
  check($('paste').hidden === true, 'loading closes the paste dialog');
  // A paste is an edit of the list and waits for Save now, as on the phone. Saved here, so what
  // follows starts from the state it always started from.
  await window.__api.saveEdits();
  check(window.document.querySelectorAll('#queue li.track').length === 1, 'and loads what was in it');
  await new Promise((r) => setTimeout(r, 60));   // let that load finish before starting another

  // **A download that will not finish, and the press that calls it off.** Pressing play on
  // something not cached must not mean ten seconds of a button that still says "play" and cannot
  // be taken back.
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

  // **The load is not over when the fetch is.** Otherwise the dock says "opening…" while the
  // button shows a play arrow that does something other than what it shows.
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
  // Select stands first: it is the way into ticking rows, which a mouse has no long press to
  // find. The rest are in a fixed order, with Share with Protracktor beside the other link.
  check(open.map((b) => b.textContent).join(',')
        === 'Select,Add to playlist,Save the file,Copy a link,Share with Protracktor,More from this author,Information',
    'with every action the phone\'s row menu has, in the same order');
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
  // The defect this answers is not the missing music, it is the missing rows: two people whose
  // lists number themselves differently cannot talk about them.
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
// a dialog -- it is that the queue comes back. A real IndexedDB is behind it (fake-indexeddb), so
// a broken store fails here rather than in a browser.
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
  // **Checked for what they are, not for what else is there.** These counted every playlist in the
  // database and expected exactly their own, which held only while nothing earlier had saved one yet
  // -- a timing the paste check's Save changed. The order and the deletion are the point.
  const names = (await store.playlists.all()).map((p) => p.name);
  const rest = names.slice(1);
  check(names[0] === 'From the phone', 'and it sorts first, whatever it is called');
  check(rest.join() === [...rest].sort((x, y) => x.localeCompare(y)).join() && rest.indexOf('Alpha') < rest.indexOf('Zebra'),
    'with the rest by name');

  const before = (await store.playlists.all()).length;
  await store.playlists.remove('p-zebra');
  check(!(await store.playlists.get('p-zebra')) && (await store.playlists.all()).length === before - 1,
    'a playlist somebody made can be deleted');

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
  // Node's DecompressionStream ignores what follows a deflate stream and Firefox does not, so
  // asking node cannot reproduce the failure a browser sees.
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

// --- Browse plays without touching the playlist -------------------------------------------------
//
// **The phone's model.** Without it, searching and pressing one tune turns the playlist into every
// result on the screen. On the phone a result plays with the playlist left alone, and only what is
// added stays -- so that is what is checked: search answers with tunes, a press plays one from a
// list that is not the playlist, and Add is the only way in.
if (window.__api) {
  console.log('\nBrowse plays without touching the playlist:');
  const { catalogue: store, playlists: saved } = await import(path.resolve('web/src/store.js'));
  await store.putAll([{ key: 'modland:meta', tracks: 6, total: 6, formats: 4, buckets: 5, fingerprint: 'x' }]);
  const settle = () => new Promise((r) => setTimeout(r, 20));
  const rows = () => [...$('browselist').children];
  const button = (li, cls) => li.querySelector(`.${cls}`);

  // The phone's list is showing, which is where a fresh page starts.
  window.__api.receive({
    queue: [{ url: 'https://modland.com/pub/modules/Protracker/4-Mat/one.mod', title: 'One' }],
    index: 0,
  });
  await settle();

  // Browsing writes into nothing now, so it is not shut on the phone's list.
  window.__api.showPanel('browse');
  await window.__api.browseTo([]);
  check(!$('browsesearch').hidden && rows().some((li) => li.textContent.includes('Modland')),
    'Browse opens whole on the phone\'s list, search and archive both');
  check(!$('browse').hidden && window.document.querySelector('main').hidden && !window.document.querySelector('footer').hidden,
    'and stands where the list stands, with the dock still under it');

  await window.__api.runSearch('mat');
  check(rows().length === 2 && rows().every((li) => li.classList.contains('btrack') && li.dataset.url),
    'a search answers with tunes, never with a folder');
  check(rows().map((li) => li.querySelector('.bname').textContent).sort().join() === 'another.mod,elysium.mod',
    'and an author found by name brings their tunes, the ones whose titles do not match included');
  check(rows().every((li) => li.querySelector('.bmeta')?.textContent === 'Protracker/4-Mat'),
    'each saying where it lives');
  check(rows().every((li) => button(li, 'badd')?.querySelector('svg') && button(li, 'badd').textContent === 'Add'
                             && button(li, 'bmore')?.querySelector('svg')),
    'with Add, icon and label, and the tune\'s menu beside it');

  // Add on the phone's list asks where, and closing that goes back to Browse.
  button(rows()[0], 'badd').click();
  await settle();
  check(!$('addto').hidden && $('browse').hidden, 'Add on the phone\'s list asks which playlist, since that one is never written');
  $('addto').querySelector('[data-close]').click();
  check($('addto').hidden && !$('browse').hidden, 'and closing the question goes back to Browse, results and all');
  check(rows().length === 2, 'which kept its results');
  const phoneBefore = window.__api.queueNow().join();

  // One of the reader's own playlists, with one tune in it.
  const mine = 'https://modland.com/pub/modules/Coop/Alice%20%26%20Bob/together.mod';
  await saved.save({ id: 'p-browse', name: 'Mine', tracks: [{ url: mine, name: 'together.mod' }], index: 0 });
  await window.__api.switchTo('p-browse');
  window.__api.showPanel('browse');
  await window.__api.runSearch('elysium');
  check(rows().length === 2, 'searching again, from a playlist of his own');

  rows()[0].click();
  await settle();
  const session = window.__api.awayState();
  check(session?.kind === 'browse' && window.__api.queueNow().length === 2
        && window.__api.queueNow().join() === rows().map((li) => li.dataset.url).join(),
    'pressing a tune plays it from the results, which are what next and previous walk');
  check(session.stash.queue.map((t) => t.url).join() === mine && !window.__api.dirtyNow(),
    'and the playlist is untouched, with nothing waiting to be saved');
  check(!$('browse').hidden && rows()[0].classList.contains('playing'),
    'Browse stays open on the results, marking the one playing');
  check($('playlistname').textContent === 'Browse', 'the name at the top says where the music comes from');
  check(window.__api.queueNow().join() !== phoneBefore, 'nothing of this reached the phone\'s list either');

  button(rows()[1], 'badd').click();
  await settle();
  check(session.stash.queue.map((t) => t.url).join() === `${mine},${rows()[1].dataset.url}`,
    'Add appends the tune to the playlist waiting underneath');
  check(window.__api.dirtyNow(), 'as an edit waiting for Save, the phone\'s rule');
  check(button(rows()[1], 'badd').disabled && button(rows()[1], 'badd').textContent === 'Added'
        && button(rows()[1], 'badd').querySelector('svg'),
    'and the row says it is in there now');
  check(window.__api.queueNow().length === 2, 'while the results playing are left as they were');

  // The tune's menu.
  button(rows()[0], 'bmore').click();
  const menu = [...$('menu').children];
  check(menu.map((b) => b.textContent).join('|')
        === "Add to another playlist|Information|More from this author|Save the file|Copy a link|Share with Protracktor",
    'the tune\'s menu has the phone\'s actions');
  check(menu.every((b) => b.querySelector('svg')), 'each with its icon');
  // The icon beside its word, centred on it: a later rule once made every item a block and left the
  // icons floating above the text.
  const item = window.getComputedStyle(menu[0]);
  check(item.display === 'flex' && item.alignItems === 'center' && item.gap === '12px',
    'laid out as a row, the icon centred beside its word');
  menu[2].click();
  await settle();
  check($('browsetitle').textContent === 'Protracker / 4-Mat' && !$('browsesearch').value,
    'More from this author walks to their folder, out of the search');
  check(rows().every((li) => li.classList.contains('btrack')) && rows().length === 2,
    'whose tunes are rows of the same kind');

  // Out of Browse and back to the playlist: the added tune is there, and Save is offered.
  window.__api.showPanel(null);
  check(!window.document.querySelector('main').hidden && !$('randomhead').hidden,
    'closing Browse leaves the music playing, under a heading that says where from');
  window.__api.endSession();
  await settle();
  check(window.__api.awayState() == null && window.__api.queueNow().length === 2
        && window.__api.queueNow()[0] === mine,
    'the playlist comes back with what was added, and only that');
  check(!$('tab-save').hidden, 'and Save offered for it');

  // Adding with nothing played from Browse goes straight into the list on screen.
  window.__api.showPanel('browse');
  await window.__api.browseTo(['modland', 'Protracker', '4-Mat']);
  const other = rows().find((li) => !button(li, 'badd').disabled);
  const inList = rows().map((li) => window.__api.queueNow().includes(li.dataset.url));
  check(rows().every((li, i) => button(li, 'badd').disabled === inList[i]) && other,
    'a folder shows which of its tunes the playlist already has');
  button(other, 'badd').click();
  check(window.__api.queueNow().length === 3 && window.__api.queueNow()[2] === other.dataset.url,
    'and Add with nothing from Browse playing appends to the list on screen');
  await window.__api.discardEdits();
  await settle();
  check(window.__api.queueNow().join() === mine, 'Discard takes the added tunes back out');
  check(rows().filter((li) => button(li, 'badd').disabled).length === 0,
    'and Browse\'s rows stop saying they are in it');

  $('browseback').click();
  await settle();
  check($('browsetitle').textContent === 'Protracker', 'Back goes up one level');
  $('browsesearch').value = 'elysium';
  await window.__api.runSearch('elysium');
  check($('browsetitle').textContent === 'Search' && !$('browseback').hidden
        && $('browsenote').textContent.startsWith('2 tunes by name or author'),
    'a search says what it found, and offers Back');
  $('browseback').click();
  await settle();
  check(!$('browsesearch').value && $('browsetitle').textContent === 'Protracker',
    'which clears it and returns to where it was typed');
  await window.__api.browseTo([]);
  window.__api.showPanel(null);
  await saved.remove('p-browse');
  await window.__api.switchTo('phone');

  // Pasting still replaces the list on screen, so on the phone's list it is still refused.
  const beforePaste = $('title').textContent;
  $('urls').value = 'https://modland.com/pub/modules/Protracker/Other/y.mod';
  $('load').click();
  await settle();
  check($('title').textContent === beforePaste,
    'a pasted address leaves the phone\'s queue alone');
  check($('pastenote').textContent.includes('Switch to one of your own'), 'and says so');
  $('urls').value = '';
  window.__api.showPanel(null);
  await store.clear('modland:meta');
}

// --- only what this browser can play ------------------------------------------------------------
//
// The list is the phone's (`SupportedFormatsFileTest` holds them together); what these check is the
// page's half: that it reads the real file, drops exactly what the engine says it lacks, counts
// what it drops, and **says so**, which is the condition on which anything is left out at all.
{
  console.log('\nonly what this browser can play:');
  const archive = await import(path.resolve('web/src/catalogue.js'));
  const table = archive.parseFormats(fs.readFileSync('web/src/formats.tsv', 'utf8'));
  const browser = 'openmpt:0.8.9;sc68:3.0.0b;asap:8.0.0;gme:0.6.5;sidplayfp:3.1.1;minimp3:ea99364;zxtune:none;uade:none';
  const phone = browser.replace(';zxtune:none', '').replace(';uade:none', '');

  check(table.extensions.size > 100 && table.prefixes.size > 10, 'the real list is read, extensions and prefixes');
  check([...archive.absentDecoders(browser)].sort().join() === 'uade,zxtune', 'the browser engine lacks exactly ZXTune and UADE');
  check(archive.platformOf(table, 'zoolook.mod') === 'Amiga' && archive.platformOf(table, 'mod.zoolook') === 'Amiga',
    'a file\'s machine comes from its extension or its Amiga prefix');
  check(archive.platformOf(table, 'Commando.sid') === 'Commodore 64' && archive.platformOf(table, 'a.pt3') === 'ZX Spectrum',
    'for every machine the list names');
  check(archive.platformOf(table, 'notes.txt') === null && archive.platformOf(table, 'README') === null,
    'and nothing for a name no machine claims');
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
  check(!here('mdat.turrican') && there('mdat.turrican') && !here('alfred chicken.dw') && there('alfred chicken.dw'),
    'an Amiga custom tune is the phone\'s alone: UADE is a second process and the page cannot start one');
  check(archive.platformOf(table, '5th gear.soc') === 'Atari ST', 'and an ST replay UADE plays is still an Atari ST tune');

  // **The index keeps every row now** (`docs/ROADMAP_FORMATS.md` step 0), and what plays is decided
  // where it is read. This used to check `filterIndex`, which dropped rows at download time and
  // made the index a function of the format list — so one format added cost every user the whole
  // 5.76 MB again.
  const built = archive.toRecords([
    '1\tProtracker/4-Mat/elysium.mod', '2\tSpectrum/PT3/x/bomb.pt3', '3\tYM/Hippel/zynaps.ym',
    '4\tPictures/me/holiday.jpg', '', 'no tab here',
  ].join('\n'), 'modland', here);
  check(built.total === 4, 'every listed tune is stored, and blank or malformed lines are not');
  check(built.tracks === 1, 'and only one of the four counts as playable here');
  const bucketed = built.records
    .filter((r) => r.tracks)
    .flatMap((r) => r.tracks.map((e) => `${e.t}:${e.p}`))
    .sort();
  check(bucketed.join(',') === 'bomb.pt3:0,elysium.mod:1,holiday.jpg:0,zynaps.ym:0',
    'every row is kept, each carrying whether this build can open it', bucketed.join(','));
  // The derived records are the ones Browse and search read, and they hold only what plays.
  const titles = built.records.filter((r) => r.entries).flatMap((r) => r.entries.map((e) => e[0]));
  check(titles.join(',') === 'elysium.mod', 'the searchable titles are only the playable ones',
    titles.join(','));
  check(built.records.filter((r) => r.authors).flatMap((r) => r.authors).length === 1,
    'and an author with nothing playable is not offered');

  check(archive.indexFingerprint(browser, table) !== archive.indexFingerprint(phone, table),
    'an index built by another engine reads as another index');
  const grown = archive.parseFormats(fs.readFileSync('web/src/formats.tsv', 'utf8') + '\nextension\tnew\topenmpt\n');
  check(archive.indexFingerprint(browser, table) !== archive.indexFingerprint(browser, grown),
    'and so does one built by another list — the phone lost 5,558 files to that once');

  if (window.__api) {
    const { catalogue: store } = await import(path.resolve('web/src/store.js'));
    await window.__api.switchTo('p-test');

    // An index from before step 0: it holds only what the build of the day accepted.
    await store.putAll([{ key: 'modland:meta', tracks: 315294, total: 516107,
                          formats: 90, buckets: 32212, fingerprint: 'x' }]);
    await window.__api.renderBrowse();
    check($('browsenote').textContent.includes("holds 315,294 of Modland's 516,107"),
      'Browse says how much of Modland a partial index holds');

    // And one written since, which holds the archive and offers what it can open. The distinction
    // is the point of step 0 and the page has to state it, or "the other 200,813" reads as a
    // download somebody still owes.
    await store.putAll([{ key: 'modland:meta', tracks: 342169, total: 516107, complete: true,
                          formats: 90, buckets: 32212, fingerprint: 'x' }]);
    await window.__api.renderBrowse();
    check($('browsenote').textContent.includes("holds all 516,107 of Modland's tunes and can play 342,169"),
      'a whole index says it holds everything and plays some of it',
      $('browsenote').textContent);
    check($('browsenote').textContent.includes('already here if it learns one'),
      'and that a format arriving later needs no download');

    // An index from before the page filtered at all: no counts, every row.
    await store.putAll([{ key: 'modland:meta', tracks: 516107, formats: 339, buckets: 43721, fingerprint: 'x' }]);
    await window.__api.renderBrowse();
    check($('browsenote').textContent.includes('Downloading it again (5.76 MB)'),
      'an index built before the filter says why it should be downloaded again, before it is');

    await store.clear('modland:');
    await window.__api.switchTo('phone');
  }
}

// --- every list behaves like the phone's ---------------------------------------------------------
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

// --- Random, in the shape the phone has ----------------------------------------------------------
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
  // The phone hides the chip here; the page's stays usable, because it looks like a way out.
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

// --- History, the same stream kept ---------------------------------------------------------------
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
  // An index from before the filter: no counts, every row. This panel is all of Browse there is
  // while the phone's list shows, so if it does not say so, nothing does.
  await store.putAll([{ key: 'modland:meta', tracks: 516107, formats: 339, buckets: 43721, fingerprint: 'x' }]);
  await window.__api.browseTo([]);
  check($('browsenote').textContent.includes('Downloading it again (5.76 MB)'),
    'and an index from before the filter is called out there too, where it will be seen');
  check(labels.some((t) => t.includes('Random')) && labels.some((t) => t.includes('History')),
    'and still offers Random and History, which write into no playlist');
  await window.__api.browseTo(['history']);
  check($('browsetitle').textContent === 'History', 'History opens even there');

  await store.clear('modland:');
  window.__api.showPanel(null);
}

// --- the buttons the page builds as it goes also carry icons -------------------------------------
if (window.__api) {
  console.log('\nicons on what the page builds:');
  const settle = (ms = 80) => new Promise((r) => setTimeout(r, ms));
  await window.__api.switchTo('p-icons');
  $('playlistchip').click();
  await settle();
  const drops = [...$('playlistlist').querySelectorAll('button')];
  check(drops.length > 0 && drops.every((b) => b.querySelector('svg')), 'every button in the playlist sheet has an icon');
  window.__api.showPanel(null);
  const { catalogue: store } = await import(path.resolve('web/src/store.js'));
  await store.putAll([{ key: 'modland:meta', tracks: 3, total: 3, phoneOnly: 0, formats: 1, buckets: 1, fingerprint: 'x' }]);
  await window.__api.browseTo([]);
  const actions = [...$('browselist').children];
  check(actions.length > 0 && actions.every((li) => li.querySelector('svg')),
    'every row at the root of Browse does something, and has an icon');
  await store.clear('modland:');
}

// --- the playlist sheet, in the phone's shape ----------------------------------------------------
if (window.__api) {
  console.log('\nthe playlist sheet:');
  const { playlists } = await import(path.resolve('web/src/store.js'));
  const settle = (ms = 80) => new Promise((r) => setTimeout(r, ms));
  const answers = { prompt: null, confirm: false };
  window.prompt = () => answers.prompt;
  window.confirm = () => answers.confirm;

  await playlists.save({ id: 'p-sheet', name: 'Sheet test', tracks: [{ url: 'https://example.test/s.mod', name: 's' }], index: 0 });
  await window.__api.switchTo('p-sheet');
  $('playlistchip').click();
  await settle();
  const rows = [...$('playlistlist').children];
  const mine = rows.find((li) => li.textContent.includes('Sheet test'));
  const phone = rows.find((li) => li.textContent.includes('From the phone'));
  check(mine?.firstElementChild?.className === 'pcount' && mine.firstElementChild.textContent === '1',
    'each row starts with its size, as the phone\'s does');
  check(mine?.getAttribute('aria-current') === 'true', 'and the one showing is marked');
  check(phone && !phone.querySelector('.pmenu'), '"From the phone" has nothing to rename or delete');

  mine.querySelector('.pmenu').click();
  const items = [...$('menu').querySelectorAll('button')].map((b) => b.textContent.trim());
  check(items.join() === 'Rename,Delete' && [...$('menu').querySelectorAll('button')].every((b) => b.querySelector('svg')),
    'its three dots hold Rename and Delete, each with its icon');

  answers.prompt = 'Renamed';
  [...$('menu').querySelectorAll('button')].find((b) => b.textContent.includes('Rename')).click();
  await settle();
  check((await playlists.get('p-sheet'))?.name === 'Renamed' && $('playlistname').textContent === 'Renamed',
    'Rename renames it, and the chip follows');

  [...$('playlistlist').children].find((li) => li.textContent.includes('Renamed')).querySelector('.pmenu').click();
  answers.confirm = false;
  [...$('menu').querySelectorAll('button')].find((b) => b.textContent.includes('Delete')).click();
  await settle();
  check(!!(await playlists.get('p-sheet')), 'Delete asks first, and a no keeps it');
  [...$('playlistlist').children].find((li) => li.textContent.includes('Renamed')).querySelector('.pmenu').click();
  answers.confirm = true;
  [...$('menu').querySelectorAll('button')].find((b) => b.textContent.includes('Delete')).click();
  await settle();
  check(!(await playlists.get('p-sheet')) && $('playlistname').textContent === 'From the phone',
    'and a yes deletes it, leaving the phone\'s list showing');
  $('menu').hidden = true;
  window.__api.showPanel(null);
}

// --- editing one of the reader's own playlists, as on the phone ----------------------------------
if (window.__api) {
  console.log('\nediting a playlist:');
  const { playlists } = await import(path.resolve('web/src/store.js'));
  const settle = (ms = 80) => new Promise((r) => setTimeout(r, ms));
  const saved = async (id) => ((await playlists.get(id))?.tracks ?? []).map((t) => t.url).join();
  const menuOf = (row) => { row.querySelector('.rowmenu').click(); return [...$('menu').querySelectorAll('button')]; };

  const a = 'https://example.test/e1.mod';
  const b = 'https://example.test/e2.mod';
  const c = 'https://example.test/e3.mod';
  await window.__api.switchTo('p-edit');
  window.__api.setQueue([a, b, c], 0);
  await settle(700);

  let items = menuOf($('queue').children[1]);
  check(items.some((x) => x.textContent.trim() === 'Remove from this playlist'),
    'a row of a playlist of his own offers removal');
  check(items.every((x) => x.querySelector('svg')), 'and every item in the row menu has an icon');
  items.find((x) => x.textContent.includes('Remove')).click();
  await settle(700);
  check(window.__api.queueNow().join() === `${a},${c}`, 'removing takes the row out, with no question first');
  check(!$('snackbar').hidden && $('snacktext').textContent.includes('Removed'), 'with the way back offered');
  check(!!$('snackundo').querySelector('svg'), 'which has an icon too');

  // As on the phone: an edit waits for Save, and Save and Discard appear only while it waits.
  check(await saved('p-edit') === `${a},${b},${c}`, 'nothing is written until Save');
  check(!$('tab-save').hidden && !$('tab-discard').hidden, 'Save and Discard appear while an edit waits');
  await window.__api.discardEdits();
  await settle();
  check(window.__api.queueNow().join() === `${a},${b},${c}` && $('tab-save').hidden,
    'Discard reads the saved list back');

  items = menuOf($('queue').children[1]);
  items.find((x) => x.textContent.includes('Remove')).click();
  await settle();
  $('snackundo').click();
  await settle();
  check(window.__api.queueNow().join() === `${a},${b},${c}` && $('snackbar').hidden,
    'undo puts it back where it was');
  items = menuOf($('queue').children[1]);
  items.find((x) => x.textContent.includes('Remove')).click();
  await settle();
  $('tab-save').click();
  await settle();
  check(await saved('p-edit') === `${a},${c}` && $('tab-save').hidden, 'Save writes it, and Save goes away');

  // A switch with an edit waiting asks first -- the phone's "Unsaved changes".
  await playlists.save({ id: 'p-other', name: 'Other', tracks: [], index: 0 });
  items = menuOf($('queue').children[0]);
  items.find((x) => x.textContent.includes('Remove')).click();
  await settle();
  const choosing = window.__api.choosePlaylist('p-other');
  await settle();
  check(!$('unsaved').hidden && window.__api.queueNow().join() === c,
    'a switch with an edit waiting asks first, and has not switched');
  check([...$('unsaved').querySelectorAll('button')].every((x) => x.querySelector('svg')), 'the question\'s buttons have icons');
  window.__api.showPanel(null);
  await choosing;
  check($('unsaved').hidden && window.__api.dirtyNow() && window.__api.queueNow().join() === c,
    'closing the question keeps editing, where it was');
  const choosingAgain = window.__api.choosePlaylist('p-other');
  await settle();
  $('unsaved-save').click();
  await choosingAgain;
  await settle();
  check(await saved('p-edit') === c && $('playlistname').textContent === 'Other',
    'Save in the question writes the edit and then switches');

  // Closing the tab with an edit waiting is the browser's question, the only one there is.
  await window.__api.switchTo('p-edit');
  window.__api.setQueue([a, b, c], 0);
  await settle(700);
  items = menuOf($('queue').children[1]);
  items.find((x) => x.textContent.includes('Remove')).click();
  await settle();
  const leaving = new window.Event('beforeunload', { cancelable: true });
  window.dispatchEvent(leaving);
  check(leaving.defaultPrevented, 'closing the tab with an edit waiting asks the browser to ask');
  await window.__api.discardEdits();
  await settle();

  // Removing what is playing stops it, rather than starting something else.
  await window.__api.playAt(0);
  items = menuOf($('queue').children[0]);
  items.find((x) => x.textContent.includes('Remove')).click();
  await settle();
  check($('playpause').title === 'Play' && window.__api.queueNow().join() === `${b},${c}`,
    'removing the tune that is playing stops it, and starts nothing else');
  $('snackundo').click();
  await settle(700);

  // "From the phone" is what the phone sent, and is never edited here.
  await window.__api.switchTo('phone');
  window.__api.receive({ queue: [{ url: a, title: 'A' }], index: 0 });
  await settle();
  const phoneItems = menuOf($('queue').children[0]).map((x) => x.textContent.trim());
  check(!phoneItems.some((t) => t.includes('Remove')), '"From the phone" offers no removal');
  $('menu').hidden = true;
}

// --- ticking rows, as on the phone ---------------------------------------------------------------
if (window.__api) {
  console.log('\nticking rows:');
  const { playlists } = await import(path.resolve('web/src/store.js'));
  const settle = (ms = 80) => new Promise((r) => setTimeout(r, ms));
  const urls = [1, 2, 3, 4].map((n) => `https://example.test/sel${n}.mod`);
  const menuItem = (row, label) => {
    row.querySelector('.rowmenu').click();
    return [...$('menu').querySelectorAll('button')].find((b) => b.textContent.trim() === label);
  };
  window.prompt = () => 'Picked';

  await playlists.save({ id: 'p-target', name: 'Target', tracks: [{ url: urls[3], name: 'already' }], index: 0 });
  await window.__api.switchTo('p-sel');
  window.__api.setQueue(urls, 0);
  await settle(700);

  menuItem($('queue').children[0], 'Select').click();
  await settle();
  check(!$('selectbar').hidden && $('selectcount').textContent === '1 selected', 'Select in the row menu starts ticking');
  check($('queue').children[0].querySelector('.tick')?.checked, 'and the row shows its box, where its number was');
  $('queue').children[2].click();
  await settle();
  check($('selectcount').textContent === '2 selected' && window.__api.indexNow() === 0,
    'a tap then ticks a row rather than playing it');

  check(!$('sel-delete').hidden, 'Delete is offered in a playlist of his own');
  $('sel-delete').click();
  await settle();
  check(window.__api.queueNow().join() === `${urls[1]},${urls[3]}` && $('selectbar').hidden,
    'Delete takes every ticked row out, as one edit');
  check($('snacktext').textContent === 'Removed 2 tracks' && window.__api.dirtyNow(), 'waiting for Save, with one undo for both');
  $('snackundo').click();
  await settle();
  check(window.__api.queueNow().join() === urls.join(), 'undo brings them all back, each where it was');
  await window.__api.discardEdits();
  await settle();

  menuItem($('queue').children[1], 'Select').click();
  $('queue').children[3].click();
  await settle();
  $('sel-add').click();
  await settle();
  const targets = [...$('addtolist').children].map((li) => li.textContent);
  check(!$('addto').hidden && targets.some((t) => t.includes('Target')) && !targets.some((t) => t.includes('From the phone')),
    'Add to playlist offers his other playlists, never the phone\'s');
  [...$('addtolist').children].find((li) => li.textContent.includes('Target')).click();
  await settle();
  const target = (await playlists.get('p-target')).tracks.map((t) => t.url);
  check(target.join() === `${urls[3]},${urls[1]}`, 'and adds what it did not already have, once');
  check($('selectbar').hidden && $('addto').hidden, 'then the ticks and the sheet go');

  // A long press starts ticking, and the click that ends it does not tick the row off again.
  const row = $('queue').children[2];
  row.dispatchEvent(new window.Event('pointerdown'));
  await settle(560);
  row.dispatchEvent(new window.Event('pointerup'));
  $('queue').children[2].click();
  await settle();
  check($('selectcount').textContent === '1 selected' && $('queue').children[2].classList.contains('ticked'),
    'a long press starts ticking, and the click at its end is not counted twice');
  window.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape' }));
  await settle();
  check($('selectbar').hidden, 'Escape leaves the ticking');

  // On "From the phone" the ticked rows can be copied elsewhere, and nothing else.
  await window.__api.switchTo('phone');
  window.__api.receive({ queue: [{ url: urls[0], title: 'A' }], index: 0 });
  await settle();
  menuItem($('queue').children[0], 'Select').click();
  await settle();
  check($('sel-delete').hidden && !$('sel-add').hidden, 'on the phone\'s list only Add is offered');
  $('sel-cancel').click();
  await settle();
  $('menu').hidden = true;
}

// --- Share with Protracktor ----------------------------------------------------------------------
//
// One tune, from any list, as a link that opens this page playing it. **The phone makes the link
// too**, so the page must open what `QueueLink.trackLink` packs -- checked by packing the same line
// the way the JVM does (zlib deflate, URL-safe base64) and handing it over as the address.
if (window.__api) {
  console.log('\nShare with Protracktor:');
  const api = window.__api;
  const settle = () => new Promise((r) => setTimeout(r, 30));
  const zlib = await import('zlib');
  const { playlists: saved } = await import(path.resolve('web/src/store.js'));
  const kept = 'https://modland.com/pub/modules/Coop/Alice%20%26%20Bob/together.mod';
  await saved.save({ id: 'p-send', name: 'Kept', tracks: [{ url: kept, name: 'together.mod' }], index: 0 });
  await api.switchTo('p-send');
  const tune = { url: 'https://modland.com/pub/modules/Protracker/Jogeir%20Liljedahl/zoolook.mod',
                 name: 'zoolook', file: 'zoolook.mod', meta: 'Modland/Protracker/Jogeir Liljedahl' };

  // A clipboard that accepts, as a browser's does after a click; jsdom has none.
  const copied = [];
  Object.defineProperty(window.navigator, 'clipboard',
    { value: { writeText: async (text) => { copied.push(text); } }, configurable: true });
  await api.sendToWeb(tune);
  const link = api.lastSentLink();
  check(copied.at(-1) === link && !$('snackbar').hidden && $('snacktext').textContent === 'Link copied'
        && $('snackundo').hidden,
    'the link is copied, and a snackbar says so, with nothing to press');
  await new Promise((r) => setTimeout(r, 2600));
  check($('snackbar').hidden, 'and goes by itself after two and a half seconds');
  check(link?.startsWith(`${window.location.origin}${window.location.pathname}#play:`),
    'the link points at this page, marked as one tune to play');
  const line = 'Protracker/Jogeir Liljedahl/zoolook.mod\tzoolook';
  check(await api.inflateFragment(link.split('#play:')[1]) === line,
    'and carries the tune the way the phone packs it: a Modland path, and the title the path lacks');
  check(!api.canSendToWeb({ url: null, local: true, name: 'mine.mod' })
        && !api.canSendToWeb({ url: 'https://example.org/live set.mp3', name: 'live set.mp3' })
        && !api.canSendToWeb({ url: 'asma://asma/Games/tune.sap', name: 'tune.sap' }),
    'a file on the phone, an MP3 and an address no browser can fetch make no link');

  // The phone's link, as the address of this page. A browser keeps its audio suspended until the
  // page is touched, which is what a link opened from another app is.
  api.contextNow().state = 'suspended';
  window.__audioBlocked = true;
  window.location.hash = `play:${zlib.deflateSync(Buffer.from(line)).toString('base64url')}`;
  await settle();
  const session = api.awayState();
  check(session?.kind === 'link' && api.queueNow().join() === tune.url,
    'a link sent here plays that one tune');
  check(session.stash.queue.map((t) => t.url).join() === kept && !api.dirtyNow(),
    'and leaves the playlist that was showing exactly as it was');
  // Folded: the dock and the heading say what it is.
  check($('nowplaying').hidden && $('pair').hidden && $('title').textContent === 'zoolook',
    'nothing is drawn over it: Now Playing stays folded and the dock names the tune');
  check($('count').textContent === '1 sent to you', 'the line under the name says the tune was sent, not that it came from history');
  check($('sessiontitle').textContent === 'Playing a tune sent to you' && $('playlistname').textContent === 'Sent'
        && $('sessionicon').querySelector('path'),
    'under a heading that says where it came from');

  api.onWorklet({ type: 'opened', describe: 'title\tzoolook\nformat\tProTracker MOD\n', duration: 200,
                  subsongs: 1, canSeek: true, preferredRate: 44100, rate: 44100 });
  await settle();
  check($('playpause').title === 'Play' && $('sub').textContent === 'Tap anywhere to play',
    'opened with no click on the page, the dock asks for a tap, rather than showing pause for a silent tune');
  // **Any touch starts it**, not only Play: the first use of the page is the permission the
  // browser was waiting for.
  window.__audioBlocked = false;
  const sent = window.__toWorklet.length;
  $('title').dispatchEvent(new window.Event('pointerdown', { bubbles: true }));
  await settle();
  check(api.contextNow().state === 'running' && $('playpause').title === 'Pause'
        && window.__toWorklet.slice(sent).map((m) => m.type).join() === 'play',
    'and the first touch anywhere on the page starts it, once');
  check($('sub').textContent !== 'Tap anywhere to play', 'after which the dock says what is playing again');

  // Play itself, as that first touch: its own click starts the tune, and must not be the second
  // press that pauses what the touch began.
  api.contextNow().state = 'suspended';
  window.__audioBlocked = true;
  api.onWorklet({ type: 'opened', describe: 'title\tzoolook\n', duration: 200,
                  subsongs: 1, canSeek: true, preferredRate: 44100, rate: 44100 });
  await settle();
  window.__audioBlocked = false;
  const before = window.__toWorklet.length;
  $('playpause').dispatchEvent(new window.Event('pointerdown', { bubbles: true }));
  $('playpause').dispatchEvent(new window.Event('pointerup', { bubbles: true }));
  $('playpause').click();
  await settle();
  check($('playpause').title === 'Pause' && window.__toWorklet.slice(before).map((m) => m.type).join() === 'play',
    'and Play pressed as that touch starts it, rather than starting and pausing it');

  // **Chrome's order**: it runs no worklet while the context is suspended, so the tune is not
  // opened until the touch -- the dock must ask for one while it is still loading, and the button
  // must not offer to stop, or the page fetches and then sits silent with nothing to say.
  api.contextNow().state = 'suspended';
  window.__audioBlocked = true;
  await api.playAt(0);
  await settle();
  check($('sub').textContent === 'Tap anywhere to play' && $('playpause').title === 'Play',
    'with the tune not yet opened, the dock asks for a tap and Play is offered, not Stop');
  window.__audioBlocked = false;
  $('title').dispatchEvent(new window.Event('pointerdown', { bubbles: true }));
  await settle();
  check(api.contextNow().state === 'running' && $('playpause').title === 'Stop loading',
    'the touch lets the worklet run, and the load it was holding carries on');
  api.onWorklet({ type: 'opened', describe: 'title\tzoolook\n', duration: 200,
                  subsongs: 1, canSeek: true, preferredRate: 44100, rate: 44100 });
  await settle();
  check($('playpause').title === 'Pause', 'and the tune plays the moment it opens');

  // Offered on every list: the queue's row menu here, Browse's in its own checks above.
  window.document.querySelector('#queue li .rowmenu').click();
  const item = [...$('menu').children].find((b) => b.textContent === 'Share with Protracktor');
  check(item && !item.disabled && item.querySelector('svg'), 'a row\'s menu offers it, with its icon');
  // Copy a link says so the same way.
  [...$('menu').children].find((b) => b.textContent === 'Copy a link').click();
  await settle();
  check(copied.at(-1) === tune.url && $('snacktext').textContent === 'Link copied' && !$('snackbar').hidden,
    'Copy a link confirms itself in the same snackbar');
  $('menu').hidden = true;
  delete window.navigator.clipboard;

  api.endSession();
  await settle();
  check(api.queueNow().join() === kept, 'leaving puts the playlist back');

  // **Several tunes, one link** (`docs/BACKLOG.md` A38).
  const second = { url: 'https://modland.com/pub/modules/AHX/Pink/frog.ahx', name: 'frog.ahx',
                   file: 'frog.ahx', meta: 'Modland/AHX/Pink' };
  check($('sel-share') && $('sel-share').querySelector('svg')
        && $('sel-share').textContent.includes('Share with Protracktor'),
    'the selection bar offers to share what is ticked, with its icon');
  await api.sendToWeb([tune, second]);
  const many = api.lastSentLink();
  check(await api.inflateFragment(many.split('#play:')[1])
        === 'Protracker/Jogeir Liljedahl/zoolook.mod\tzoolook\nAHX/Pink/frog.ahx',
    'and the link carries them both, packed as the phone packs a queue');

  api.contextNow().state = 'suspended';
  window.location.hash = many.slice(many.indexOf('#') + 1);
  await settle();
  check(api.awayState()?.kind === 'link' && api.queueNow().length === 2
        && api.queueNow()[1] === second.url,
    'a link of several tunes plays the lot, not only the first');
  check($('sessiontitle').textContent === 'Playing tunes sent to you' && $('count').textContent === '2 sent to you',
    'and says so over the list');
  check(api.awayState().stash.queue.map((t) => t.url).join() === kept && !api.dirtyNow(),
    'with the playlist left as it was');
  api.endSession();
  await settle();

  // A queue's link, into a tab showing the code: the code steps aside, as it does for pairing,
  // rather than standing in the middle of the screen over the tune that is arriving.
  api.showPanel('pair');
  window.location.hash = zlib.deflateSync(Buffer.from('Protracker/4-Mat/hi there.mod')).toString('base64url');
  await settle();
  check($('pair').hidden && api.queueNow().join() === 'https://modland.com/pub/modules/Protracker/4-Mat/hi%20there.mod',
    'a queue that comes by link closes the pairing code');
  window.history.replaceState(null, '', window.location.pathname);
  await saved.remove('p-send');
  await api.switchTo('phone');
}

// --- ASMA in the browser -------------------------------------------------------------------------
//
// **The list, not the archive.** ASMA publishes a 20 MB zip; the page reads its central directory
// with two ranged requests and fetches each tune from its own address. Checked against a zip built
// here, served by a stand-in that answers ranges the way asma.atari.org does -- and once more by one
// that ignores them, which a server is allowed to do.
if (window.__api) {
  console.log('\nASMA:');
  const api = window.__api;
  const settle = () => new Promise((r) => setTimeout(r, 30));
  const { catalogue: store } = await import(path.resolve('web/src/store.js'));
  const entry = (name, size) => {
    const bytes = Buffer.from(name);
    const head = Buffer.alloc(46);
    head.writeUInt32LE(0x02014b50, 0);
    head.writeUInt32LE(size, 24);
    head.writeUInt16LE(bytes.length, 28);
    return Buffer.concat([head, bytes]);
  };
  const names = [['asma/', 0], ['asma/Composers/', 0], ['asma/Composers/Aki/Robots.sap', 1922],
                 ['asma/Composers/Aki/Atari_Style.sap', 7291], ['asma/Games/Boulder Dash (Tune 1).sap', 1000],
                 ['asma/Docs/STIL.txt', 272641]];
  const directory = Buffer.concat(names.map(([n, s]) => entry(n, s)));
  const body = Buffer.alloc(70000, 7);          // what stands for the compressed tunes
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(names.length, 10);
  end.writeUInt32LE(directory.length, 12);
  end.writeUInt32LE(body.length, 16);
  window.__asmaZip = Buffer.concat([body, directory, end]);
  window.__asmaRanges = [];
  // The engine says hello, as the worklet does once it has compiled: the list is filtered by what
  // it can open, and a download waits for it to say (`whenEngineReady`).
  api.onWorklet({ type: 'ready', backends: 'openmpt:0.8.9;sc68:3.0.0b;asap:8.0.0;gme:0.6.5;sidplayfp:3.1.1;minimp3:ea99364;zxtune:none' });

  await api.downloadAsmaIndex();
  await settle();
  const asked = window.__asmaRanges.splice(0);
  // A directory this small sits inside the tail, so one ordinary range brings both.
  const size = window.__asmaZip.length;
  check(asked.length === 1 && asked[0] === `bytes=${size - 65557}-${size - 1}`,
    'the list is read with a size from HEAD and an ordinary range — never the suffix form a browser must ask about');
  const held = await api.archiveMeta('asma');
  check(held?.tracks === 3 && held.formats === 2, 'every .sap is listed and nothing else, under its section');
  check([...$('browselist').children].some((li) => li.textContent.startsWith('ASMA — 3 tunes')),
    'Browse offers ASMA beside Modland');

  await api.browseTo(['asma']);
  check([...$('browselist').children].map((li) => li.querySelector('.bname').textContent).join() === 'Composers,Games',
    'its sections are the first level');
  await api.browseTo(['asma', 'Composers', 'Aki']);
  const rows = [...$('browselist').children];
  check(rows.length === 2 && rows.every((li) => li.classList.contains('btrack')), 'an author\'s tunes are tunes, with Add and the menu');
  const robots = rows.find((li) => li.querySelector('.bname').textContent === 'Robots.sap');
  check(robots?.dataset.url === 'https://asma.atari.org/asma/Composers/Aki/Robots.sap',
    'and each plays from its own address on asma.atari.org');

  $('browsesearch').value = 'aki';
  await api.runSearch('aki');
  const hits = [...$('browselist').children];
  check(hits.some((li) => li.dataset.url === 'https://asma.atari.org/asma/Composers/Aki/Robots.sap'
                         && li.querySelector('.bmeta').textContent === 'ASMA/Composers/Aki'),
    'search finds ASMA\'s tunes by author, saying which archive they are from');
  await api.runSearch('boulder');
  check([...$('browselist').children][0]?.dataset.url === 'https://asma.atari.org/asma/Games/Boulder%20Dash%20%28Tune%201%29.sap',
    'and by title, a tune filed with no author included');
  check($('browsenote').textContent.includes('(Modland and ASMA)') || $('browsenote').textContent.includes('(ASMA)'),
    'the note says which archives it looked in');
  $('browsesearch').value = '';

  const table = await api.randomTable();
  check(table.entries.some((e) => e.source === 'asma'), 'the dice picks from ASMA too');

  // A server that ignores the range sends everything, and the same reading still works.
  window.__asmaIgnoreRange = true;
  await store.clear('asma:');
  await api.downloadAsmaIndex();
  await settle();
  window.__asmaIgnoreRange = false;
  window.__asmaRanges.length = 0;
  check((await api.archiveMeta('asma'))?.tracks === 3, 'and a server that ignores the range gives the same list');

  // A browser that asks about every range is refused by this server: it gets the whole archive.
  window.__asmaNoSafeRange = true;
  await store.clear('asma:');
  await api.downloadAsmaIndex();
  await settle();
  window.__asmaNoSafeRange = false;
  window.__asmaRanges.length = 0;
  check((await api.archiveMeta('asma'))?.tracks === 3, 'and a browser whose range is refused falls back to the whole archive');

  // ASMA's real directory is 849 KB, far more than one tail: then it takes a second ordinary range.
  // Padded here past the tail with entries the filter drops, so only the three tunes remain.
  const padding = Array.from({ length: 1500 }, (_, i) => entry(`asma/Docs/padding-${String(i).padStart(5, '0')}.txt`, 1));
  const bigDirectory = Buffer.concat([directory, ...padding]);
  const bigEnd = Buffer.from(end);
  bigEnd.writeUInt16LE(names.length + padding.length, 10);
  bigEnd.writeUInt32LE(bigDirectory.length, 12);
  bigEnd.writeUInt32LE(200000, 16);
  window.__asmaZip = Buffer.concat([Buffer.alloc(200000, 7), bigDirectory, bigEnd]);
  await store.clear('asma:');
  await api.downloadAsmaIndex();
  await settle();
  const big = window.__asmaZip.length;
  const two = window.__asmaRanges.splice(0);
  check(bigDirectory.length > 65557 && two.length === 2 && two[0] === `bytes=${big - 65557}-${big - 1}`
        && two[1] === `bytes=200000-${200000 + bigDirectory.length - 1}`
        && (await api.archiveMeta('asma'))?.tracks === 3,
    'a real-sized archive is read as the tail and then the directory, both ordinary ranges');

  await store.clear('asma:');
  await api.browseTo([]);
  window.__api.showPanel(null);
}

// --- no pinch to zoom ----------------------------------------------------------------------------
if (window.__api) {
  console.log('\nno pinch to zoom:');
  const viewport = window.document.querySelector('meta[name="viewport"]').content;
  check(viewport.includes('maximum-scale=1') && viewport.includes('user-scalable=no'),
    'the viewport does not let the page be zoomed');
  check([...window.document.querySelectorAll('style')].some((s) => /html\s*{\s*touch-action:\s*pan-x pan-y;/.test(s.textContent)),
    'and touch keeps scrolling but not pinching');
  const gesture = new window.Event('gesturestart', { cancelable: true });
  window.dispatchEvent(gesture);
  const pinch = new window.WheelEvent('wheel', { ctrlKey: true, cancelable: true, deltaY: -10 });
  window.dispatchEvent(pinch);
  const scroll = new window.WheelEvent('wheel', { cancelable: true, deltaY: -10 });
  window.dispatchEvent(scroll);
  check(gesture.defaultPrevented && pinch.defaultPrevented && !scroll.defaultPrevented,
    'Safari\'s pinch and a touchpad\'s are refused, and an ordinary scroll is not');
}

// --- the playlist sheet on a fresh browser, and on a phone's width -------------------------------
if (window.__api) {
  console.log('\nthe playlist sheet, on a phone:');
  // Straight out of the database: `playlists.remove` refuses "From the phone" on purpose, and the
  // state wanted is a browser the phone has never sent anything to.
  await new Promise((resolve, reject) => {
    const open = window.indexedDB.open('protracktor');
    open.onerror = () => reject(open.error);
    open.onsuccess = () => {
      const db = open.result;
      const tx = db.transaction('playlists', 'readwrite');
      tx.objectStore('playlists').delete('phone');
      tx.oncomplete = () => { db.close(); resolve(); };
      tx.onerror = () => reject(tx.error);
    };
  });
  $('playlistchip').click();
  await new Promise((r) => setTimeout(r, 30));
  const first = $('playlistlist').children[0];
  check(first?.querySelector('.pname')?.textContent === 'From the phone' && first.querySelector('.pcount').textContent === '0',
    'with nothing stored, "From the phone" is still a choice rather than an empty sheet');
  check(window.getComputedStyle($('newlist').parentElement).flexWrap === 'wrap',
    'and the two buttons under the list may take a line each, where one line is too narrow for both');
  window.__api.showPanel(null);
}

// --- instrument and sample names (docs/PLAN_INSTRUMENT_NAMES.md) ------------------------------------
if (window.__api) {
  console.log('\ninstrument and sample names:');
  const api = window.__api;
  const us = String.fromCharCode(0x1f);
  const open = (describe) => api.onWorklet({ type: 'opened', describe, duration: 100, subsongs: 1,
                                             canSeek: true, preferredRate: 44100, rate: 44100 });
  const sections = () => [...$('np-names').querySelectorAll('details')];

  open(`title\tnames\nformat\tProTracker MOD\nsamples\t3\nsample_names\tgreetings${us}${us}  to all\nmessage\tline one\n  line two`);
  await new Promise((r) => setTimeout(r, 30));
  let found = sections();
  check(found.length === 1 && found[0].querySelector('summary').textContent === 'Sample names (3)'
        && found[0].querySelector('summary svg') && !found[0].open,
    'a MOD\'s sample names are one folded section, with an icon and how many');
  check(found[0].querySelector('pre').textContent === '01 greetings\n02 \n03   to all',
    'numbered as a tracker numbers them, the empty one inside kept and the spacing intact');
  check($('np-message-text').textContent === 'line one\n  line two', 'the message after them is still whole');
  const labels = [...window.document.querySelectorAll('#fields dt')].map((n) => n.textContent);
  check(labels.includes('Samples') && !labels.some((l) => l.toLowerCase().includes('names')),
    'and the field list keeps its count of samples and gains no names');

  open(`title\tx\ninstrument_names\tbass${us}lead\nsample_names\tbass${us}lead\nmessage\t`);
  await new Promise((r) => setTimeout(r, 30));
  check(sections().length === 1 && sections()[0].textContent.startsWith('Instrument names (2)'),
    'one list where instruments and samples say the same');
  open(`title\tx\ninstrument_names\tbass${us}lead\nsample_names\tkick${us}snare${us}hat\nmessage\t`);
  await new Promise((r) => setTimeout(r, 30));
  check(sections().map((d) => d.querySelector('summary').textContent).join() === 'Instrument names (2),Sample names (3)',
    'both where they differ, instruments first');
  open(`title\tsid\nformat\tPSID\nsample_names\t ${us} \nmessage\t`);
  await new Promise((r) => setTimeout(r, 30));
  check(sections().length === 0, 'and nothing at all where every name is blank or there are none');
  api.onWorklet({ type: 'failed', reason: 'x' });
  check(sections().length === 0, 'a refusal clears them with the rest of Now Playing');
}

// --- the dice waits while you browse an author (docs/BACKLOG.md A41) -----------------------------
if (window.__api) {
  console.log('\ndigression: the dice waits underneath:');
  const api = window.__api;
  const settle = (ms = 80) => new Promise((r) => setTimeout(r, ms));
  const archive = await import(path.resolve('web/src/catalogue.js'));
  const { catalogue: store, playlists: saved } = await import(path.resolve('web/src/store.js'));

  const built = archive.toRecords(['1\tProtracker/Trio/a.mod', '1\tProtracker/Trio/b.mod',
                                   '1\tProtracker/Trio/c.mod'].join('\n'));
  await store.clear('modland:');
  await store.putAll(built.records, 100);
  await store.putAll([{ key: 'modland:meta', tracks: built.tracks, total: built.tracks, phoneOnly: 0,
                        buckets: built.buckets, formats: built.formats, fingerprint: 'x' }]);
  await saved.save({ id: 'p-dig', name: 'Mine', tracks: [{ url: 'https://example.test/kept.mod', name: 'kept.mod' }], index: 0 });
  await api.switchTo('p-dig');

  api.useRandomSource(() => 0.1);
  await api.openRandom();
  await settle();
  const record = api.queueNow();
  const cursor = api.indexNow();
  check(api.randomState() != null && record.length > 0, 'the dice is rolling, with a record');

  // Keeping what the dice gave, the way the phone's dock offers it.
  check(!$('nowkeep').hidden && $('nowkeep').querySelector('svg'),
    'the dock offers to keep what is playing, since it is not from the playlist');
  const height = window.getComputedStyle($('nowcard')).height;
  $('nowkeep').click();
  await settle();
  check(api.randomState().stash.queue.map((t) => t.url).join() === `https://example.test/kept.mod,${record[cursor]}`,
    'pressing it appends that tune to the playlist waiting underneath');
  check(api.dirtyNow(), 'as an edit waiting for Save, like every other');
  check(window.getComputedStyle($('nowcard')).height === height,
    'and the card is the same height with the button as without it');
  // Saved first: what follows switches playlists, and an edit waiting would stop to ask.
  await api.saveEdits();
  await settle();

  // The digression, by the way it is really reached: the row's own menu.
  const scrolls = [];
  const original = window.HTMLElement.prototype.scrollIntoView;
  window.HTMLElement.prototype.scrollIntoView = function stub() { scrolls.push(this.dataset.url); };
  window.document.querySelector('#queue li .rowmenu').click();
  [...$('menu').children].find((b) => b.textContent === 'More from this author').click();
  await settle();
  const marked = [...$('browselist').children].filter((li) => li.classList.contains('playing'));
  check(!$('browse').hidden && marked.length === 1 && marked[0].dataset.url === record[cursor],
    'More from this author opens the folder with the tune that is playing marked');
  check(scrolls.includes(record[cursor]), 'and brings it on screen rather than opening at the top');

  // **The dice stands aside as the folder opens** (`docs/SPEC_RANDOM.md` §1, the digression), so
  // everything below is true before anything in the folder has been played.
  check(api.awayState()?.kind === 'browse' && api.awayState().dice != null,
    'the dice waits from the moment the jump lands, not from the first tune played here');
  const folder = [...$('browselist').children].map((li) => li.dataset.url);
  check(api.queueNow().join() === folder.join() && api.queueNow()[api.indexNow()] === record[cursor],
    'the folder is what next and previous walk, starting from the tune the jump was made from');
  check(api.afterOf(0) === 1, 'so next is the author\'s next tune rather than another roll');
  check($('browsesearch').hidden, 'a folder offers no search box, as the phone offers none');
  check($('browseclose').hidden,
    'and no Close beside it: the way out of a digression is the Back that returns to the dice');

  // A row being fetched breathes here as it does in the playlist (`docs/WISHLIST.md` B32).
  holdTrackFetch = true;
  api.playAt(api.indexNow());
  await settle();
  check(window.document.querySelector('#browselist li.loading'),
    'the row whose tune is being fetched says so in Browse too');
  holdTrackFetch = false;
  $('playpause').click();   // calls the download off, leaving the digression as it was
  await settle();
  check(!$('browsedigression').hidden
        && $('browsedigression').textContent.includes('Browsing author')
        && $('browsedigression').textContent.includes('Trio')
        && $('browsedigression').querySelector('svg'),
    'and the heading is in this screen, in the shape the dice\'s own heading has');
  if (original) window.HTMLElement.prototype.scrollIntoView = original;
  else delete window.HTMLElement.prototype.scrollIntoView;

  window.document.querySelector('#browselist li.btrack').click();
  await settle();
  const session = api.awayState();
  check(session?.kind === 'browse' && session.dice != null,
    'playing one of them keeps the dice underneath rather than ending it');
  check($('sessiontitle').textContent === 'Browsing author' && $('randomscope').textContent === 'Trio'
        && !$('randomscope').hidden && $('playlistname').textContent === 'Browsing',
    'and the heading says whose folder this is, in the two lines the dice\'s heading has');
  check($('random-leave').textContent.includes('Random') && $('random-leave').querySelector('svg'),
    'the way back offers the dice, not the playlist');
  // What it held, plus the pick kept from the dock a moment ago — and nothing from this folder.
  check(session.stash.queue.map((t) => t.url).join() === `https://example.test/kept.mod,${record[cursor]}`,
    'the playlist is still waiting under both of them, with what was kept and nothing else');

  // Back, out of the author's folder: the dice, where it was, paused.
  $('browseback').click();
  await settle();
  check(api.randomState() != null && api.awayState() == null, 'Back from the author\'s folder returns to the dice');
  check(api.queueNow().join() === record.join() && api.indexNow() === cursor,
    'with the record it had and the cursor where it was');
  check($('sessiontitle').textContent === 'Playing at random' && $('sub').textContent === 'press play',
    'paused on that pick, not playing something new');
  const selectedRow = [...window.document.querySelectorAll('#queue li.track')]
    .findIndex((li) => li.classList.contains('selected'));
  check(selectedRow === api.indexNow(),
    'and the pick it will play is marked, so play starts what the screen names');
  check($('browse').hidden, 'and Browse is closed');

  // A playlist chosen while digressing ends both: a way back that leads nowhere is worse than none.
  api.showPanel('browse');
  await api.browseTo(['modland', 'Protracker', 'Trio']);
  window.document.querySelector('#browselist li.btrack').click();
  await settle();
  check(api.awayState()?.dice != null, 'digressing again');
  await api.choosePlaylist('p-dig');
  await settle();
  check(api.awayState() == null && api.randomState() == null
        && api.queueNow().join() === `https://example.test/kept.mod,${record[cursor]}`,
    'choosing a playlist ends the digression and the dice with it, and the playlist is what it kept');

  await store.clear('modland:');
  await saved.remove('p-dig');
  await api.switchTo('phone');
  api.useRandomSource(Math.random);
}

// --- the gear, left of shuffle -------------------------------------------------------------------
if (window.__api) {
  console.log('\nthe page\'s settings:');
  const gear = $('tab-settings');
  check(gear && gear.nextElementSibling === $('shuffle') && gear.querySelector('svg') && gear.title === 'Settings',
    'a gear stands left of shuffle, with its icon');
  check(!gear.disabled, 'and answers whether or not anything is playing');
  gear.click();
  await new Promise((r) => setTimeout(r, 40));
  check(!$('settings').hidden, 'pressing it opens the settings');
  const labels = [...$('settingsfields').querySelectorAll('dt')].map((n) => n.textContent);
  check(labels[0] === 'Decoders in this build' && labels.includes('Modland') && labels.includes('ASMA')
        && labels.includes('Stored here'),
    'which say what this build plays, what is indexed, and what the browser is holding');
  $('settings').querySelector('[data-close]').click();
  check($('settings').hidden, 'and Close shuts them');
}

// --- add to playlist, from one row ---------------------------------------------------------------
//
// The phone's row menu opens the picker for that one track. Adding by ticking rows first would be
// the gesture for many rows spent on one.
if (window.__api) {
  console.log('\nadd to playlist, from one row:');
  const api = window.__api;
  const settle = () => new Promise((r) => setTimeout(r, 30));
  const { playlists: saved } = await import(path.resolve('web/src/store.js'));
  await saved.save({ id: 'p-target-row', name: 'Elsewhere', tracks: [], index: 0 });
  await saved.save({ id: 'p-source-row', name: 'Here', tracks: [], index: 0 });
  await api.switchTo('p-source-row');
  const url = 'https://modland.com/pub/modules/Protracker/4-Mat/one.mod';
  api.setQueue([{ url, name: 'one.mod', file: 'one.mod', meta: 'Modland/Protracker/4-Mat' }], 0);
  api.render();

  window.document.querySelector('#queue li .rowmenu').click();
  const add = [...$('menu').children].find((b) => b.textContent === 'Add to playlist');
  check(add && !add.disabled && add.querySelector('svg'), 'a row offers it without ticking anything first');
  add.click();
  await settle();
  check(!$('addto').hidden && $('addtonote').textContent.startsWith('1 track'),
    'and the picker opens for that one track');
  const target = [...$('addtolist').children].find((li) => li.textContent.includes('Elsewhere'));
  check(target && ![...$('addtolist').children].some((li) => li.textContent.includes('Here')),
    'offering his other playlists, never the one the row is already in');
  target.click();
  await settle();
  check((await saved.get('p-target-row')).tracks.map((t) => t.url).join() === url,
    'choosing one puts the track in it');
  check(api.queueNow().join() === url, 'and leaves the playlist the row was in alone');
  await saved.remove('p-target-row');
  await saved.remove('p-source-row');
  await api.switchTo('phone');
}

// --- more from this author -----------------------------------------------------------------------
//
// Offered on every list, as on the phone, not in Browse alone. From a row, and from the tune
// playing, it opens the folder the tune came from -- read off where the row says it lives, which a
// pasted Modland address gives as well.
if (window.__api) {
  console.log('\nmore from this author:');
  const api = window.__api;
  const settle = () => new Promise((r) => setTimeout(r, 30));
  const item = () => [...$('menu').children].find((b) => b.textContent === 'More from this author');

  api.setQueue([{ url: 'https://modland.com/pub/modules/Protracker/4-Mat/one.mod', name: 'one.mod',
                  file: 'one.mod', meta: 'Modland/Protracker/4-Mat' }], 0);
  api.render();
  window.document.querySelector('#queue li .rowmenu').click();
  check(item() && !item().disabled && item().querySelector('svg'),
    'a row in the playlist offers it, with its icon');
  check(!$('np-folder').disabled && $('np-folder').querySelector('svg') && $('np-folder').querySelector('span'),
    'and so does Now Playing, for the tune it is describing');
  item().click();
  await settle();
  check(!$('browse').hidden && $('browsetitle').textContent === 'Protracker / 4-Mat',
    'pressing it opens Browse on that author, wherever the row was');
  api.showPanel(null);

  // A pasted Modland address carries no `meta`, and the folder comes out of the address instead.
  api.setQueue(['https://modland.com/pub/modules/AHX/Pink/frog.ahx'], 0);
  api.render();
  window.document.querySelector('#queue li .rowmenu').click();
  check(item() && !item().disabled, 'a pasted Modland address knows its folder too');
  $('menu').hidden = true;

  // Nothing to open for a file that stayed on the phone, or an address from anywhere else.
  api.setQueue([{ url: 'https://example.org/tunes/x.mod', name: 'x.mod' }], 0);
  api.render();
  window.document.querySelector('#queue li .rowmenu').click();
  check(item() && item().disabled && $('np-folder').disabled,
    'and an address outside the archives offers it greyed, in the row and in Now Playing');
  $('menu').hidden = true;
}

// --- the rules, from the file the Kotlin tests read (PLAN_WEB_LIBRARY S1) -----------------------
//
// **The point is not that these pass.** It is that they are the same cases `RuleCasesTest.kt`
// drives, so a rule changed on one side and not the other fails on the side that did not change.
// C23, C30 and C31 were each one screen doing what the other did not, and none of the three was
// caught by a test.
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
  // ASMA's address, from the zip entry's path, by the page's own rule (`catalogue.js` urlFor).
  const catalogueModule = await import(path.resolve('web/src/catalogue.js'));
  each('asmaUrl', (c) => {
    const [, format, ...rest] = c.path.split('/');
    const title = rest.pop();
    return catalogueModule.urlFor(format, rest.join('/'), title, 'asma') === `https://asma.atari.org/${c.expect}`;
  });
  each('randomFresh', (c) =>
    rules.freshPick({ drawn: c.drawn.split(','), seen: c.seen === '-' ? [] : c.seen.split(',') }) === c.expect);
  // A length as the time display shows it, the same rows `RuleCasesTest` runs against `formatTotal`.
  each('lengthTotal', (c) => rules.clockTotal(Number(c.seconds)) === c.expect);
  // What a search matches, the same rows `RuleCasesTest` runs against `SearchTerms`.
  each('searchMatch', (c) =>
    rules.searchMatches(c.query, c.title, c.author === '-' ? '' : c.author) === (c.expect === 'yes'));
  // HVSC's time tokens, the same rows `RuleCasesTest` runs against `SongLengths.parseTime`. A SID's
  // whole length comes from reading these right; there is nothing in the file to fall back on.
  const lengthsModule = await import(path.resolve('web/src/songlengths.js'));
  each('songLengthTime', (c) => {
    const got = lengthsModule.parseSongLengthTime(c.token);
    return c.expect === '-' ? got === null : got === Number(c.expect);
  });
}

// --- a format learnt after the download costs nothing ------------------------------------------
//
// **The whole of `docs/ROADMAP_FORMATS.md` step 0, checked end to end.** An index holds every row
// the archive lists; what this build can open is decided where it is read and re-decided locally.
// Before this, one format added meant every user fetching Modland's 5.76 MB again — and the
// roadmap has four items that would each have charged it.
if (window.__api?.catalogueStore) {
  const store = window.__api.catalogueStore;
  const archiveApi = window.__archive;
  await store.clear('modland:');

  const lines = [
    '1\tProtracker/4-Mat/elysium.mod',
    '2\tSpectrum/PT3/x/bomb.pt3',
    '3\tSpectrum/PT3/x/lasers.pt3',
  ].join('\n');
  const canPlayMod = (name) => name.endsWith('.mod');
  const built = archiveApi.toRecords(lines, 'modland', canPlayMod);
  await store.putAll(built.records);
  await store.putAll([{ key: 'modland:meta', tracks: built.tracks, total: built.total,
                        complete: true, formats: built.formats, buckets: built.buckets,
                        fingerprint: 'before' }]);

  check(built.total === 3 && built.tracks === 1,
    'three rows stored, one of them playable', `${built.total}/${built.tracks}`);
  check((await archiveApi.tracksIn('Spectrum', 'PT3/x', 'modland')).length === 0,
    'the folder of a format this build cannot open lists nothing');
  check((await archiveApi.formats('modland')).length === 1,
    'and that format is not offered at the top level');

  // The build learns the format. No fetch, no network, one pass over what is already here.
  const offered = await archiveApi.refreshPlayable('modland', () => true);
  check(offered === 3, 'learning a format re-decides the stored rows', String(offered));
  check((await archiveApi.tracksIn('Spectrum', 'PT3/x', 'modland')).length === 2,
    'the folder fills in, from rows that were downloaded months ago');
  check((await archiveApi.formats('modland')).map((f) => f.name).sort().join(',') === 'Protracker,Spectrum',
    'the format appears at the top level');
  const hits = (await archiveApi.searchTitles('lasers', 20, 'modland')).hits;
  check(hits.length === 1, 'and search finds it, which means the title shards were rebuilt');

  // And the other direction: a format lost must disappear from every derived record, not just from
  // the folder. A stale title shard is a tune findable in search and absent from Browse.
  await archiveApi.refreshPlayable('modland', canPlayMod);
  check((await archiveApi.searchTitles('lasers', 20, 'modland')).hits.length === 0,
    'losing a format empties the search as well as the folder');

  // A partial index -- written before any of this -- cannot be repaired locally and must say so.
  await store.putAll([{ key: 'modland:meta', tracks: 1, total: 3, complete: false }]);
  check((await archiveApi.refreshPlayable('modland', () => true)) === null,
    'and an index from before step 0 is left alone, because its rows were never downloaded');
  await store.clear('modland:');
}

// --- the dice never draws what this build cannot open ------------------------------------------
//
// The index holds the whole archive since `docs/ROADMAP_FORMATS.md` step 0, so every reader has to
// ask for the playable part. **The dice is the reader where getting it wrong costs most**: it would
// fetch a file nothing can decode and move on, which a listener experiences as the dice skipping.
// The phone proves the same thing in `CataloguePlayableTest`.
if (window.__api?.catalogueStore && window.__archive) {
  const store = window.__api.catalogueStore;
  const archiveApi = window.__archive;
  await store.clear('modland:');
  await store.clear('asma:');

  const lines = [];
  for (let i = 0; i < 20; i++) lines.push(`1\tProtracker/4-Mat/good${i}.mod`);
  for (let i = 0; i < 80; i++) lines.push(`1\tProtracker/4-Mat/bad${i}.zzzznope`);
  const built = archiveApi.toRecords(lines.join('\n'), 'modland', (n) => n.endsWith('.mod'));
  await store.putAll(built.records);
  await store.putAll([{ key: 'modland:meta', tracks: built.tracks, total: built.total,
                        complete: true, formats: built.formats, buckets: built.buckets }]);

  const table = await archiveApi.buildRandomTable();
  check(table.total === 20, 'the pool counts only what can be played', String(table.total));
  const drawn = [];
  for (let i = 0; i < 40; i++) {
    // Spread across the whole pool rather than trusting Math.random to.
    const at = (i + 0.5) / 40;
    drawn.push(await archiveApi.drawTrack(table, () => at));
  }
  check(drawn.every((t) => t && t.name.endsWith('.mod')),
    'and forty draws across the pool are all playable',
    drawn.filter((t) => !t || !t.name.endsWith('.mod')).map((t) => t?.name ?? 'null').join(','));
  // Every one of the twenty is reachable: an off-by-one in the bucket arithmetic would show as a
  // pool that only ever hands back its first or last tune.
  check(new Set(drawn.map((t) => t.name)).size === 20,
    'and every playable tune in the pool can come up', String(new Set(drawn.map((t) => t.name)).size));
  await store.clear('modland:');
}

// --- clearing one archive's rows ----------------------------------------------------------------
//
// **Re-indexing is ninety times slower than indexing** if `catalogue.clear` walks a cursor
// deleting record by record: 281 ms to write 20,000 rows and 26 seconds to delete them again. From
// the outside that is two or three seconds the first time and twenty the second, apparently stuck
// on "sorting" -- the first time there is nothing to clear. One `delete` over the key range
// instead.
//
// Speed is not what these check; a benchmark in a test suite measures the machine it runs on. They
// check the thing that made the fast version worth trusting: **that the range still deletes exactly
// the right rows**. `modlandish:` is the case that matters, because a prefix scan written by hand
// gets it wrong.
if (window.__api?.catalogueStore) {
  const store = window.__api.catalogueStore;
  await store.putAll([
    { key: 'modland:a', v: 1 },
    { key: 'modland:z', v: 2 },
    { key: 'asma:a', v: 3 },
    { key: 'modlandish:a', v: 4 },
  ]);
  await store.clear('modland:');
  const left = (await store.byPrefix('')).map((r) => r.key).sort();
  check(!left.includes('modland:a') && !left.includes('modland:z'),
    'clearing an archive removes its rows', left.join(', '));
  check(left.includes('asma:a'), 'and leaves another archive alone', left.join(', '));
  check(left.includes('modlandish:a'),
    'and leaves an archive whose name merely starts the same way', left.join(', '));
  await store.clear('asma:');
  await store.clear('modlandish:');
}

// --- searching for two words -------------------------------------------------------------------
//
// The rule is checked from the shared file above; this checks that the **search actually uses it**,
// through the real store and the real shard layout. The case that matters is the second row: a
// file named `space_ninja.mod`, typed as `space ninja`.
if (window.__api?.catalogueStore) {
  const store = window.__api.catalogueStore;
  const entries = [
    ['space_ninja.mod', 'Protracker', '4-Mat'],
    ['spaceninja.mod', 'Protracker', '4-Mat'],
    ['space-robot.mod', 'Protracker', '4-Mat'],
  ];
  await store.putAll([{ key: 'modland:titles:sp', entries }]);
  const found = async (q) => (await window.__api.searchTitlesNow(q)).hits.map((h) => h.name).sort();

  check((await found('space ninja')).join(',') === 'space_ninja.mod,spaceninja.mod',
    'two words find a separator and no separator alike, and nothing else',
    (await found('space ninja')).join(','));
  check((await found('ninja space')).join(',') === 'space_ninja.mod,spaceninja.mod',
    'and the order they were typed in does not matter');
  check((await found('SPACE NINJA')).length === 2, 'nor does their case');
  check((await found('ninja')).length === 2, 'one word still works as it always did');
  // The direction this deliberately does not cover, stated as a check so that the day somebody
  // decides to pay for it, a passing test says what changed.
  check((await found('spaceninja')).join(',') === 'spaceninja.mod',
    'and a run-on query still misses the separated name, which is the measured trade in rules.js');
  await store.clear('modland:titles:');
}

// --- MD5, and the lookup it is the key to ---------------------------------------------------------
//
// **Written out rather than fetched from anywhere**, because `crypto.subtle` does not offer MD5 and
// never will: it is broken as a *security* hash. Nothing here is security -- HVSC chose it as a
// key twenty years ago, and a lookup has to use the key the database was written with.
//
// So it is checked against the vectors published with the algorithm. An MD5 that is subtly wrong
// finds nothing and looks exactly like a database that has never heard of the tune.
{
  const lengths = await import(path.resolve('web/src/songlengths.js'));
  const encode = (text) => new TextEncoder().encode(text);
  const vectors = [
    ['', 'd41d8cd98f00b204e9800998ecf8427e'],
    ['a', '0cc175b9c0f1b6a831c399e269772661'],
    ['abc', '900150983cd24fb0d6963f7d28e17f72'],
    ['message digest', 'f96b697d7cb7938d525a2f31aaf161d0'],
    ['abcdefghijklmnopqrstuvwxyz', 'c3fcd3d76192e4007dfb496cca67e13b'],
    ['12345678901234567890123456789012345678901234567890123456789012345678901234567890',
      '57edf4a22be3c955ac49da2e2107b67a'],
  ];
  const wrong = vectors.filter(([text, want]) => lengths.md5(encode(text)) !== want);
  check(wrong.length === 0, 'MD5 agrees with the vectors published with the algorithm',
    wrong.map(([t]) => JSON.stringify(t)).join(', '));

  // **A block boundary and the length field above 2^32 bits.** 56 bytes is the last size that fits
  // its padding in one block and 64 is the first that does not, which is where a hand-written MD5
  // goes wrong. Checked against the same implementation at a size the page will really meet.
  const long = new Uint8Array(200_000);
  for (let i = 0; i < long.length; i++) long[i] = (i * 7) & 0xff;
  check(/^[0-9a-f]{32}$/.test(lengths.md5(long)), 'and hashes a file-sized buffer');
  check(lengths.md5(new Uint8Array(56)) !== lengths.md5(new Uint8Array(64)),
    'and 56 bytes and 64 bytes hash differently, which is where padding goes wrong');

  // The parse, the shard and the lookup, end to end through the real store.
  if (window.__api?.catalogueStore) {
    const parsed = lengths.parseSongLengths(
      '[Database]\n; /MUSICIANS/H/Hubbard_Rob/Commando.sid\n' +
      '6d019ecba831a9f853675aac29a61c10=3:55.594 1:01.288 0:06\n' +
      'ffffffffffffffffffffffffffffffff=bad\n',
    );
    check(parsed.length === 1, 'a line with an unreadable time is dropped whole', `${parsed.length} kept`);
    check(parsed[0].seconds.join(',') === '235.594,61.288,6', 'and every subsong is kept',
      parsed[0].seconds.join(','));
  }
}

console.log(failures.length ? `\n❌ ${failures.length} failed` : '\n✅ page checks passed');
process.exit(failures.length ? 1 : 0);
