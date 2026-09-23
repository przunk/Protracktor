// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The main thread: fetches bytes, drives the worklet, draws the queue. It never touches audio.

import { nextIndex, previousIndex, nextSubsong, shouldRestart, randomNext, randomPrevious, freshPick, barLength, barTotalText, BAR_LABEL_TEMPLATE, parseNotices, privacyBlocks, fillFromSongDb, recordsPlay, clock, clockTotal, lineScrolls, lineScrollPass } from './rules.js';
import { PHONE, playlists, settings, makePersistent, estimate, played } from './store.js';
import * as archive from './catalogue.js';

const $ = (id) => document.getElementById(id);
const status = (text) => { $('status').textContent = text; };

/**
 * Colours the part of a slider's track that is behind its handle.
 *
 * A range input has no portable way to say this in CSS alone, so the track is a gradient and this
 * moves its stop. Called wherever a value changes — including the places that set `.value`
 * directly, since assigning to it fires no event.
 */
function paint(el) {
  const min = Number(el.min || 0);
  const max = Number(el.max || 100);
  const span = max - min;
  el.style.setProperty('--fill', span > 0 ? `${((Number(el.value) - min) / span) * 100}%` : '0%');
}

let context = null;
let node = null;
/**
 * The one node between the decoder and the speakers.
 *
 * A page has no volume of its own the way a phone does — Android has hardware keys and every app
 * rides the system level, and a browser tab has neither. So the level lives here, and it is the
 * only thing in this file that touches audio without going through the worklet.
 */
let gain = null;
let queue = [];
let index = -1;
/**
 * Play order and the two modes that change it, mirroring `player/PlayQueue.kt`.
 *
 * **The rules are the app's, not invented here**, because a control that behaves differently on the
 * two screens is worse than one that is missing. With shuffle on, *back* returns to the track that
 * was actually played before — there is no other sensible reading of "previous" in a random order.
 * With shuffle off, back means the row above, every time: the list is on screen and a button that
 * disagrees with it looks broken however defensible its bookkeeping.
 */
let shuffle = false;
let repeat = 'off';               // off -> all -> one
let history = [];                 // what was really played, for `previous` under shuffle
let order = [];                   // the permutation `next` walks when shuffle is on
let duration = 0;
// Whether the decoder can move to a position at all; `allowSeeking` adds the other half.
let tuneCanSeek = false;

/**
 * The bar takes a drag only when there is somewhere to drag to: a decoder that can seek **and** a
 * length. A position is asked for as a fraction of the length, and with none the bar would put
 * the thumb at the far end while the time counted up beside it -- the phone's `SeekBar` (`bb385c7`).
 */
function allowSeeking() {
  $('seek').disabled = !(tuneCanSeek && bar().seconds > 0);
}

/** Where the decoder itself ends a tune whose length is unknown (`ends_at`), or 0. */
let endsAt = 0;

/**
 * How far the bar runs: the length, or where playback will stop when nothing knows it, marked
 * approximate -- the phone's `BarLength` (the owner's variant (a)).
 */
const bar = () => barLength({ duration, endsAt, fallback: fallbackSeconds });

/** The number at the bar's end: the length, or `~` and where playback will stop. */
function barTotal() {
  const b = bar();
  return barTotalText(b.seconds, b.approximate);
}

/**
 * Both times beside the bar get the room of `BAR_LABEL_TEMPLATE`, measured in their own font, so
 * the bar is the same length for every tune (A58). A minimum, not a width: a time past 99:59 still
 * shows whole. Measured again once the fonts are in, since a fallback font measures differently.
 */
function holdBarLabels() {
  const row = $('elapsed').parentElement;
  const probe = document.createElement('span');
  probe.textContent = BAR_LABEL_TEMPLATE;
  probe.style.cssText = 'position:absolute;visibility:hidden;white-space:nowrap';
  row.appendChild(probe);
  const width = Math.ceil(probe.getBoundingClientRect().width);
  probe.remove();
  if (!(width > 0)) return;
  for (const id of ['elapsed', 'remaining']) $(id).style.minWidth = `${width}px`;
}
let playing = false;
let seeking = false;
/**
 * Which playlist the queue on screen belongs to.
 *
 * **`PHONE` is not a document.** It is a view of the last thing the phone sent, replaced whole by
 * every handoff and never deleted — the same bargain the phone's own default playlist has, from the
 * other end. Everything else here was made in this browser and is the browser's to keep.
 */
let activePlaylist = PHONE;

/** How many tunes are inside the open file, and which one is sounding. */
let subsongCount = 1;
let currentSubsong = 0;
/**
 * Whether the file's other tunes are part of the queue, mirroring the phone's setting.
 *
 * **One `.kss` holds 256 tunes and one `.sndh` holds three.** With this off, `next` means the next
 * file and the rest of this one is reachable only by tapping a chip; with it on, `next` walks the
 * file first and moves on when it runs out. Kept per browser, like the volume.
 */
let playAllSubsongs = false;
try {
  playAllSubsongs = localStorage.getItem('protracktor.allsubsongs') === '1';
} catch { /* a private window. Off is the safer default: it is what the transport looks like. */ }

/**
 * How long to play a tune whose length nothing knows, in seconds (`docs/STATUS.md` C56).
 *
 * **The page had no such limit at all and the phone did**, which is why a SID played here for ever
 * and ended there: a SID carries no length, the engine reports none, `render` never runs short, and
 * so `ended` was never posted. The phone asks HVSC's database and stops when the position passes
 * what it says; this page has no such database, so this is the whole of its answer for now.
 *
 * Same range and same default as the phone -- three to ten minutes, three by default -- because
 * `docs/SPEC_RANDOM.md` settled that the two players behave alike, and a setting that differs by
 * platform is the kind of difference nobody remembers which way round it goes.
 */
const FALLBACK_MIN_SECONDS = 3 * 60;
const FALLBACK_MAX_SECONDS = 10 * 60;
let fallbackSeconds = FALLBACK_MIN_SECONDS;
/**
 * Whether the clock has already ended the tune now playing.
 *
 * **Not `finished`**, which means something else: "played to its end with nothing after it", the
 * state that turns the play button into "again". A tune the clock moves on from is not finished in
 * that sense at all -- something else is playing a moment later -- and borrowing the flag would
 * leave the transport claiming so for as long as the next open took. Cleared wherever a tune
 * starts, which is `opened` and `subsong`.
 *
 * Named for the clock rather than for the fallback, because it is not only about the fallback: a
 * length from HVSC ends a tune through the same door.
 */
let endedByClock = false;

/**
 * HVSC's lengths for the tune being opened, one per subsong, or empty.
 *
 * **A SID is the only format here whose length comes from outside the file**, and the database that
 * holds it is an optional download -- so this is empty far more often than not, and the fallback
 * length is what answers then.
 */
let openLengths = [];

/**
 * What songdb says about the tune being opened -- author, publisher, album, year -- or null (W5).
 * Read with the bytes, before they go to the worklet, and used to fill the gaps in what the tune
 * says about itself, never to overwrite it (`rules.fillFromSongDb`).
 */
let openMetadata = null;

/** Hashes the file only when there is a database to look it up in: no metadata, no hashing. */
async function songMetadataFor(bytes) {
  try {
    if (!(await archive.songMetadataMeta())?.tunes) return null;
    return await archive.songMetadataFor(new Uint8Array(bytes));
  } catch {
    return null;
  }
}

/** A tune's fields, completed from songdb where it said nothing. */
const withSongDb = (fields) => fillFromSongDb(fields, openMetadata, (f) => releaseYear(f) !== '');

/**
 * The stored lengths for a file, if it is one HVSC could know about.
 *
 * The name is the filter and the hash is the lookup. `.sid`, `.psid` and `.rsid` are the three
 * `formats.tsv` files to `sidplayfp`, and HVSC is a C64 collection: nothing else can be in it, so
 * nothing else is hashed.
 */
async function songLengthsFor(name, bytes) {
  const dot = String(name ?? '').toLowerCase().lastIndexOf('.');
  const extension = dot >= 0 ? name.toLowerCase().slice(dot + 1) : '';
  if (!['sid', 'psid', 'rsid'].includes(extension)) return [];
  try {
    return (await archive.songLengthsFor(new Uint8Array(bytes))) ?? [];
  } catch {
    // A lookup that fails is a length we do not have, which is the normal case anyway.
    return [];
  }
}
try {
  const stored = Number(localStorage.getItem('protracktor.fallback'));
  // NaN fails every comparison, so an absent or damaged value keeps the default without a test of
  // its own.
  if (stored >= FALLBACK_MIN_SECONDS && stored <= FALLBACK_MAX_SECONDS) fallbackSeconds = stored;
} catch { /* a private window */ }
/**
 * Whether the current track has played to its end with nothing after it.
 *
 * **Play then means "again", not "resume".** A transport whose button offers to play and does
 * nothing is worse than one that is disabled (`docs/STATUS.md` C24).
 */
let finished = false;
/**
 * The fetch of the track being loaded, so it can be called off.
 *
 * **Pressing play on something not cached starts a download**, and on a slow connection that is ten
 * seconds during which the button said "play" and a second press would have started the same
 * download again. A press while loading means "not now", and the only honest answer is to abort.
 */
let loading = null;
/**
 * Which row is doing what: `null`, `'loading'`, `'playing'` or `'failed'`.
 *
 * **Separate from `index`**, because they answer different questions. `index` is which track the
 * page is *on*; this is whether that track has actually started. Conflated, they light a row as
 * though it were playing while a fetch is still running.
 */
let rowState = null;
/** Set when the bytes go to the worklet, cleared when it answers. See the watchdog in `playAt`. */
let openWatchdog = null;

/**
 * Everything a queue entry needs, from a URL alone.
 *
 * The name matters and is not decoration: four backends choose a loader by the file's extension,
 * so a URL whose last segment is lost would arrive as a nameless blob and be declined by all of
 * them (`docs/PLAN_HANDOFF.md` §5a).
 */
/**
 * A row for a file that could not travel.
 *
 * It holds a place and a name and nothing else: no address, so nothing can try to fetch it, and
 * `local` so the transport steps over it rather than stopping on a row that can never play.
 */
function ghost(name) {
  return { url: null, name: name || 'a file on the phone', local: true, meta: 'on the phone — not sent' };
}

function entryFor(url) {
  let name = url;
  try {
    const parsed = new URL(url, location.href);
    // **The fragment first, and that is not a nicety.** The Mod Archive addresses a file as
    // `downloads.php?moduleid=123#tune.mod`: the path is a script, the query is a number, and the
    // *fragment* is the only place the filename appears. Reading the path gives "downloads.php" for
    // every one of them -- which is not merely an ugly row, because four backends identify a format
    // by its extension and would be handed a name that has none.
    const fromHash = parsed.hash ? decodeURIComponent(parsed.hash.slice(1)) : '';
    name = fromHash || decodeURIComponent(parsed.pathname.split('/').pop()) || url;
  } catch { /* a malformed URL is still worth showing; it will fail loudly when played */ }
  return { url, name };
}

let starting = null;

/**
 * Brings the engine up, once.
 *
 * **`if (context) return` would be a race.** `context` is assigned before the awaits that follow,
 * so a second call while the first is still loading the wasm returns immediately with `node` still
 * null — and the next line posts to it. `TypeError: node is null`, from two `playAt` calls a
 * millisecond apart, which is exactly what a queue arriving from the phone produces. Holding the
 * promise makes the second caller wait for the first rather than overtake it.
 */
function start() {
  starting ??= begin();
  return starting;
}

async function begin() {
  // **A worklet needs a secure context**, and that is not a detail on this page: `localhost` counts,
  // `https://` counts, and a plain `http://192.168.x.x` does not. Without this the failure is
  // `audioWorklet` being undefined three lines down, which reads as a broken engine rather than as
  // the wrong address (`docs/PLAN_HANDOFF.md` §4).
  if (!isSecureContext) {
    $('error').textContent =
      'audio needs https or localhost — this address cannot start a decoder';
    status('open this page on localhost, or through a tunnel with https');
    throw new Error('insecure context');
  }
  // Created on a click, because a browser will not let audio start without one. The *first* tune
  // pushed to a fresh tab therefore cannot play by itself, and saying so beats looking broken
  // (`docs/PLAN_HANDOFF.md` §4).
  // **44,100, and not because it is traditional.** Six of the seven backends emulate a machine with
  // a fixed clock and produce 44.1 kHz whatever they are asked for. A browser's default context is
  // usually 48 kHz and there is no resampler between a worklet and the speakers, so those samples
  // would play 8.8% fast and about a semitone and a half sharp -- audible as a tune that sounds
  // quicker than it should. Android asks the backend and tells Oboe, which resamples; this is the
  // same answer by the only route a page has.
  context = new AudioContext({ sampleRate: 44100 });
  status('loading the engine…');
  // Born suspended: no click has reached this page yet -- a link opened from another app. Chrome
  // does not even run the worklet until the context does, so this is said now rather than when a
  // tune opens, which there it would not do -- the page would fetch and then sit silent.
  if (context.state === 'suspended') waitForTouch();

  const wasm = await fetch('../vendor/engine.wasm').then((r) => r.arrayBuffer());
  await context.audioWorklet.addModule('./processor.js');
  node = new AudioWorkletNode(context, 'protracktor', {
    numberOfInputs: 0,
    outputChannelCount: [2],
    processorOptions: { wasmBinary: wasm },
  });
  // Through the gain, not straight to the speakers. Created here rather than at load, because
  // there is no AudioContext to create it in until the first click.
  gain = context.createGain();
  gain.gain.value = amplitude();
  node.connect(gain);
  gain.connect(context.destination);
  node.port.onmessage = (event) => onWorklet(event.data);
}

/**
 * Whether the engine has said hello.
 *
 * **The one fact the page needed and did not have.** A track posted to a worklet whose engine is
 * still compiling is queued there and answered later; a track posted to one that never compiled is
 * queued for ever, and the screen sits on "opening…" with nothing to say. Knowing which of those is
 * happening is the difference between a bug report and a shrug.
 */
let engineReady = false;

/** Which decoders this build carries. An index is only as good as the set that filtered it. */
let engineFingerprint = '';

/**
 * Resolves once the engine has said which decoders it has.
 *
 * **Downloading the index needs that answer**, and `start()` does not wait for it: it builds the
 * worklet and returns, and the hello arrives later. Filtering before it arrived would keep every
 * Spectrum row, because nothing would yet say ZXTune is missing. Bounded, because an engine that
 * never compiles would otherwise leave the download waiting in silence.
 */
const readyWaiters = [];
function whenEngineReady(ms = 30000) {
  if (engineReady) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('the engine did not start')), ms);
    readyWaiters.push(() => { clearTimeout(timer); resolve(); });
  });
}

function onWorklet(message) {
  switch (message.type) {
    case 'ready':
      engineReady = true;
      // Recorded when an index is built and compared when one is read, exactly as the phone does.
      engineFingerprint = message.backends;
      status(`engine ready — ${message.backends}`);
      readyWaiters.splice(0).forEach((resolve) => resolve());
      reconsiderStoredIndexes();
      break;
    case 'opened': {
      loading = null;
      rowState = 'playing';
      render();
      $('seek').dataset.opened = '1';
      clearTimeout(openWatchdog);
      // **The engine first, HVSC only when the engine has nothing.** A format that knows its own
      // length knows it better than a lookup on a hash could; a SID has none to know.
      duration = message.duration > 0
        ? message.duration
        : (openLengths[message.current ?? 0] ?? openLengths[0] ?? 0);
      finished = false;
      endedByClock = false;
      subsongCount = message.subsongs ?? 1;
      currentSubsong = message.current ?? 0;
      const fields = withSongDb(describeFields(message.describe));
      endsAt = Number(fields.ends_at) || 0;
      allowSeeking();
      dockFields = fields;
      // **Recorded here and only here.** The playlist's plays, Browse's, Random's and History's
      // own replays all arrive at this one message -- History's to be left out by `recordPlay`
      // (A56) -- so there is one recording path rather than one per list -- and it is after the engine opened the file, so what
      // is recorded is a tune that played, under the name it gives itself.
      recordPlay(queue[index], fields);
      if (random) random.failures = 0;
      if (fields.title) $('title').textContent = fields.title;
      renderNowPlaying(fields, subsongCount, currentSubsong);
      publishToSystem(queue[index], fields);
      // A backend that wants a rate this context cannot give would play sharp and say nothing.
      // Today they all want 44,100; if one ever does not, this says so instead of transposing it.
      if (message.preferredRate > 0 && message.preferredRate !== message.rate) {
        status(`⚠ this decoder wants ${message.preferredRate} Hz and the page is running at ` +
               `${message.rate} Hz — it will play ${(message.rate / message.preferredRate).toFixed(3)}× fast`);
      } else {
        // **Overwritten, not left standing.** This line is the machine talking about the track in
        // front of it, so it has to be replaced when the track changes -- otherwise "Tune 2 of
        // 2" from a `.sndh` two files ago sits under a tune that has one (`docs/STATUS.md` C28).
        // A sentence about the wrong file is worse than no sentence.
        status(`${message.rate} Hz` + (subsongCount > 1 ? ` · ${subsongCount} tunes in this file` : ''));
      }
      $('sub').textContent = describeLine(fields);
      tuneCanSeek = !!message.canSeek;
      allowSeeking();
      $('error').textContent = '';
      // **Open, and silent until the page is touched.** A link opened from another app starts the
      // tune with no click on this page, and a browser keeps the audio suspended until there is one
      // -- so the button says play, which is the press that makes the sound, rather than pause,
      // which would have stopped a tune nobody could hear yet.
      if (context?.state === 'suspended') {
        setPlaying(false);
        waitForTouch();
        break;
      }
      setPlaying(true);
      break;
    }
    case 'failed':
      loading = null;
      clearTimeout(openWatchdog);
      // A fresh Random pick that will not open is walked past, as on the phone.
      if (random && index === queue.length - 1 && skipFailedPick(message.reason)) break;
      rowState = 'failed';
      render();
      // Errors stay above the transport. The status line moved into Now Playing because it is the
      // machine talking to itself; a decoder refusing a file is the machine talking to the listener.
      $('error').textContent = explainFailure(message.reason, queue[index]);
      $('sub').textContent = '—';
      // **And Now Playing stops describing the last file that worked.** A refusal that leaves
      // the panel showing another track's fields reads as the wrong tune playing.
      renderNothingPlaying();
      setPlaying(false);
      break;
    case 'seeked':
      if (seekNext !== null) {
        // A newer place was asked for while this one ran: go there now, spinner and all.
        node?.port.postMessage({ type: 'seek', seconds: seekNext });
        seekNext = null;
        break;
      }
      seekLanded();
      $('elapsed').textContent = clock(message.seconds);
      break;
    case 'position':
      // Not while a seek is under way: a position sent before it landed would pull the bar back.
      if (!seeking && !seekPending) {
        const range = bar().seconds;
        $('seek').value = range > 0 ? Math.round((message.seconds / range) * 1000) : 0;
        paint($('seek'));
        $('elapsed').textContent = clock(message.seconds);
        $('remaining').textContent = barTotal();
      }
      // **A tune ends when its length says so, whoever supplied the length.**
      //
      // Not `duration <= 0`, which would assume that a format knowing its own length says so by
      // running out of audio. That is true of a tracker module and **false of the one format this
      // was written for**: a SID never runs out, it loops. Gated on an unknown duration, this
      // would stop firing for exactly the tunes it exists to stop, the moment HVSC's lengths gave
      // them a duration.
      //
      // The engine's own length still wins over the fallback; what changed is that a known length
      // is now a reason to stop rather than a reason not to look.
      const limit = duration > 0 ? duration : fallbackSeconds;
      if (limit > 0 && !endedByClock && message.seconds >= limit) {
        endedByClock = true;
        trackEnded();
      }
      break;
    // A subsong is a different tune: its own length, often its own title.
    case 'subsong': {
      finished = false;
      endedByClock = false;
      currentSubsong = message.index;
      // HVSC times every subsong separately, so switching tune switches length too.
      duration = message.duration > 0 ? message.duration : (openLengths[message.index] ?? 0);
      {
        const described = describeFields(message.describe);
        endsAt = Number(described.ends_at) || 0;
      }
      allowSeeking();
      const fields = withSongDb(describeFields(message.describe));
      if (fields.title) $('title').textContent = fields.title;
      $('sub').textContent = describeLine(fields);
      renderNowPlaying(fields, subsongCount, currentSubsong);
      setPlaying(true);
      break;
    }
    case 'described': {
      const resolve = describePending.get(message.id);
      if (resolve) { describePending.delete(message.id); resolve(message); }
      break;
    }
    case 'ended':
      trackEnded();
      break;
  }
}

/**
 * What happens when a tune runs out.
 *
 * **Its own function because there are two ways to run out**, and they must behave identically: the
 * engine returning no audio, and the fallback limit for a tune whose length nothing knows
 * (`docs/STATUS.md` C56). Inlined in the `ended` case, the second one would have been a copy of
 * this, and a copy is where repeat, shuffle, subsongs and Random start to disagree with each other.
 * The phone calls one `handleTrackEnded` for the same reason.
 */
function trackEnded() {
  // **The file before the queue**, when the listener asked for that. Repeat-one is checked
  // first and deliberately: it means "this tune again", and a file's other tunes are not it.
  const inside = nextSubsong({
    playAll: playAllSubsongs, subsong: currentSubsong,
    count: subsongCount, repeatOne: repeat === 'one',
  });
  if (inside != null) {
    node?.port.postMessage({ type: 'subsong', index: inside });
    return;
  }
  if (random) {
    // Repeat-one first, as on the phone: the end of a tune under it plays the tune again. The
    // next *button* does not ask -- on the phone it rolls on regardless.
    if (repeat === 'one' && index >= 0) { playAt(index); return; }
    const step = randomNext({ length: queue.length, at: index });
    if (step === 'roll') rollRandom(); else playAt(step);
    return;
  }
  // What a queue is for, and where the modes actually show: repeat-one plays it again, shuffle
  // takes the next of the permutation, repeat-all wraps, and off stops.
  const next = afterCurrent();
  if (next == null) {
    // Nothing follows, so the button now means "again" rather than "resume".
    finished = true;
    setPlaying(false);
  } else if (next === index && repeat === 'one') playAt(index);
  else playAt(next);
}

/**
 * Asks every stored index again what it can offer, now that the engine has said what it is.
 *
 * **What a format learnt since the download costs: one local pass** (`docs/ROADMAP_FORMATS.md`
 * step 0). An index holds every row its archive lists, so this is a question already answered by
 * rows that are here — where it used to mean fetching Modland's 5.76 MB again on every device.
 *
 * Only when the stamp actually moved, and only for indexes written since step 0: an older one is
 * missing rows no local pass can supply, and Browse offers it the one download that ends that.
 *
 * Quiet, and deliberately: nothing the listener did caused this and nothing they can see changes
 * unless a format really did arrive. Failures are swallowed for the same reason — a browser with
 * no index has nothing to reconsider, and saying so would be noise.
 */
async function reconsiderStoredIndexes() {
  try {
    const table = await formatsReady();
    const current = archive.indexFingerprint(engineFingerprint, table);
    const isPlayable = archive.playable(table, absentHere());
    for (const source of archive.sources()) {
      const held = await archive.meta(source);
      if (!held?.complete || held.fingerprint === current) continue;
      await archive.refreshPlayable(source, isPlayable);
      await archive.stampIndex(source, current);
    }
    if (!$('browse').hidden) await renderBrowse();
  } catch { /* no index, or no engine yet; either way there is nothing to reconsider */ }
}

/**
 * The engine's `describe` block, which is tab-separated key/value lines.
 *
 * The dock's card wants one line of it; Now Playing wants all of it. Parsed once, used twice.
 */
function describeFields(describe) {
  // **The message is the last field and the only one with lines in it** -- the engine writes it
  // last for that reason, and a module's message is its greetings, laid out for a tracker's screen.
  // Split with everything else, all but its first line became keys of their own and were lost.
  // At the start of a line only, and the first such line: the word inside another value -- a
  // comment reading "message<TAB>…" -- is not the field, and must not hide the real one after it.
  // The phone's `DescribeBlock` reads the block by the same rule.
  const at = describe.startsWith('message\t') ? 0
    : describe.indexOf('\nmessage\t') < 0 ? -1 : describe.indexOf('\nmessage\t') + 1;
  const head = at >= 0 ? describe.slice(0, at) : describe;
  const fields = Object.fromEntries(
    head.split('\n').filter(Boolean).map((line) => {
      const tab = line.indexOf('\t');
      return tab < 0 ? [line, ''] : [line.slice(0, tab), line.slice(tab + 1)];
    })
  );
  if (head !== describe) fields.message = describe.slice(at + 'message\t'.length).replace(/\s+$/, '');
  return fields;
}

/**
 * Who wrote it: what the file says, or failing that the folder it is filed under -- the phone's
 * `TrackRef.displayAuthor`. A plain `.mod` has nowhere to record an author, and Modland files it
 * under one anyway: `Modland/Protracker/Jogeir Liljedahl`.
 */
function authorOf(entry, fields) {
  const said = fields.artist?.trim() || fields.composer?.trim();
  if (said) return said;
  const where = whereOf(entry);
  // Only a path names a folder; "The Mod Archive" or a bare host name is a source, not a person.
  if (!where.includes('/')) return '';
  return where.slice(where.lastIndexOf('/') + 1).split(' · ').pop().trim();
}

/**
 * The year a tune came out, from whichever field its format keeps it in -- the phone's
 * `ReleaseYear`, rule for rule: `year`, then `date`, then `copyright`; a year is 1970..2099, so a
 * catalogue number in a copyright line is not one; and two years joined by nothing but a dash are
 * a range, kept as one.
 */
/** The unit separator the engine joins names with, so a list stays one line of the block. */
const NAME_SEPARATOR = String.fromCharCode(0x1f);

/** A names value as its names, empty ones inside kept; none when every one is blank. */
function namesOf(value) {
  const names = (value ?? '').split(NAME_SEPARATOR);
  return names.some((name) => name.trim()) ? names : [];
}

/**
 * Instrument and sample names, **folded**, and only where there is something to read
 * (`docs/PLAN_INSTRUMENT_NAMES.md`): a MOD's 31 sample names are where its author wrote, and a SID
 * has none. One list where the two say the same. Numbered as a tracker numbers them; a fresh
 * `<details>` for every tune, so each starts closed.
 */
function renderNames(fields) {
  const box = $('np-names');
  box.replaceChildren();
  const instruments = namesOf(fields.instrument_names);
  const samples = namesOf(fields.sample_names);
  const lists = [];
  if (instruments.length) lists.push(['Instrument names', instruments]);
  if (samples.length && samples.join(NAME_SEPARATOR) !== instruments.join(NAME_SEPARATOR)) {
    lists.push(['Sample names', samples]);
  }
  for (const [label, names] of lists) {
    const details = document.createElement('details');
    const summary = document.createElement('summary');
    summary.innerHTML = iconSvg(ICON.expand);
    summary.append(`${label} (${names.length})`);
    const digits = Math.max(2, String(names.length).length);
    const pre = document.createElement('pre');
    pre.textContent = names.map((name, i) => `${String(i + 1).padStart(digits, '0')} ${name}`).join('\n');
    details.append(summary, pre);
    box.append(details);
  }
}

function releaseYear(fields) {
  for (const key of ['year', 'date', 'copyright']) {
    const value = fields[key]?.trim() ?? '';
    if (!value || value === '0') continue;
    const years = [...value.matchAll(/(?<!\d)(19[7-9]\d|20\d\d)(?!\d)/g)];
    if (!years.length) continue;
    const [first, second] = years;
    if (second) {
      const between = value.slice(first.index + 4, second.index).trim();
      if (['-', '–', '—'].includes(between)) return `${first[0]}\u2013${second[0]}`;
    }
    return first[0];
  }
  return '';
}

/**
 * Where a row came from and what its file is called. A row the phone sent, or one pasted, carries
 * neither -- only an address and, from the phone, the tune's title -- so both are read off the
 * address, the way the playlist row's second line already is.
 */
function whereOf(entry) { return entry?.meta ?? (entry?.url ? sourceOf(entry.url) : ''); }
function fileOf(entry) { return entry?.file || (entry?.url ? entryFor(entry.url).name : entry?.name) || ''; }

/**
 * The archive folder a tune came from, as a Browse path, or null: the phone's "Show neighbours",
 * which is "more from this author". Read from where the row says it lives --
 * `Modland/Protracker/4-Mat`, `ASMA/Composers/Aki` -- which a pasted Modland address gives as well,
 * since `sourceOf` builds the same line from the URL. A file from the phone has no folder here.
 */
function authorFolderOf(entry) {
  if (!entry || entry.local) return null;
  const [label, format, ...author] = (whereOf(entry) || '').split('/');
  const source = archive.sources().find((s) => archive.sourceName(s) === label);
  const who = author.join('/');
  return source && format && who ? [source, format, who] : null;
}

/** Opens Browse on that folder: what else this author left in the archive. */
/**
 * Where a jump put Browse, while Browse is still there (W9). **A jump is a place you were put, not
 * one you walked to**: More from this author lands three levels deep without passing through them,
 * so the first Back leaves Browse for where you were, rather than climbing a hierarchy you never
 * climbed into -- the phone's `arrivedByJump`. Any other Back, or walking elsewhere, ends it.
 */
let jumpedTo = null;
const arrivedByJump = () => !!jumpedTo && jumpedTo.join('\u0000') === browsePath.join('\u0000');

async function showAuthorFolder(entry) {
  const folder = authorFolderOf(entry);
  if (!folder) return;
  const digressing = !!random;
  $('browsesearch').value = '';
  browsePath = folder;
  jumpedTo = folder.slice();
  showPanel('browse');
  // **The dice stands aside as the folder opens, not when something in it is played**
  // (`docs/SPEC_RANDOM.md` §3). Doing it when something is played instead lets next roll another
  // pick, lets Back climb the archive, and leaves the way back existing only by accident.
  if (digressing) {
    const [source, format, author] = folder;
    const tracks = (await archive.tracksIn(format, author, source)).map(plain);
    openDigression(tracks, tracks.findIndex((track) => track.url === entry.url));
  }
  await renderBrowse();
  // **On screen, not somewhere below the fold.** You came here from that tune, and an author with
  // eighty of them would otherwise open at the top with no sign of the one you were listening to.
  revealRow(markPlayingIn($('browselist'), entry.url));
}

/**
 * The author's folder becomes what next and previous walk, **without restarting anything**.
 *
 * The tune playing is the tune the jump was made from, and it is in this folder — so the session
 * changes underneath it and the music does not notice. The dice waits inside the new session with
 * its record and its cursor (`docs/BACKLOG.md` A41).
 */
function openDigression(tracks, at) {
  if (!random || !tracks.length) return;
  // A change the playlist was still waiting to save is its own; written before the swap.
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null; saveQueue(); }
  away = {
    stash: random.stash,
    kind: 'browse',
    dice: { session: random, queue, index, history, order },
    author: browsePath[2] ?? '',
  };
  random = null;
  queue = tracks.slice();
  index = Math.max(0, at);
  history = [index];
  order = [];
  if (shuffle) reshuffle(index);
  showSessionView('browse');
  render();
}

/** The machine the file is for, from its name, or '' -- the phone's `Platforms.forFileName`. */
function machineOf(entry) {
  return archive.platformOf(formatTable, fileOf(entry)) ?? '';
}

/**
 * The dock's second line, the phone's: **author · machine · year**. With no author the machine
 * stands in for it rather than joining it, and with no machine either the format does -- "MOD ·
 * Amiga" would say one thing twice.
 */
function describeLine(fields, entry = queue[index]) {
  const author = authorOf(entry, fields);
  const machine = machineOf(entry);
  return [author || machine || fields.format, author ? machine : '', releaseYear(fields)]
    .filter(Boolean).join(' · ') || '—';
}

/**
 * Now Playing: the phone's rows, in the phone's order (`ui/NowPlaying.kt`). The year first, being
 * the one fact about the tune rather than about the file; the machine's own vocabulary last.
 */
const FIELD_ORDER = [
  ['format', 'Format'], ['tracker', 'Tracker'], ['artist', 'Artist'], ['album', 'Album'],
  ['publisher', 'Publisher'], ['composer', 'Composer'], ['hardware', 'Hardware'],
  ['channels', 'Channels'], ['patterns', 'Patterns'], ['instruments', 'Instruments'],
  ['samples', 'Samples'], ['subsongs', 'Subsongs'],
];

/**
 * `web/src/formats.tsv`: every name a catalogue index keeps, and which decoders open it.
 *
 * **The same list the phone indexes by** (`SupportedFormatsFileTest` holds the two together), read
 * once and kept. A hand-kept list drifts: one here lacked `ym` and `vtx`, listed `psm`, which
 * libopenmpt plays, and put the cost at 3,639 files where the measurement says 26,537.
 */
let formatTable = null;
let formatTableLoading = null;

function formatsReady() {
  formatTableLoading ??= fetch('./formats.tsv')
    .then((response) => {
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return response.text();
    })
    .then((text) => (formatTable = archive.parseFormats(text)))
    // Forgotten on failure, so the next caller asks again instead of inheriting a rejection for ever.
    .catch((error) => { formatTableLoading = null; throw error; });
  return formatTableLoading;
}

/** What the engine in hand cannot open, from its own fingerprint. */
function absentHere() { return archive.absentDecoders(engineFingerprint); }

function explainFailure(reason, entry) {
  // A name the phone keeps and this engine cannot open: say so, rather than the decoder's own
  // "nothing claimed it", which is true and useless when the phone plays the file perfectly.
  if (formatTable && entry?.name && archive.onPhone(formatTable)(entry.name)
      && !archive.playable(formatTable, absentHere())(entry.name)) {
    return `${entry.name}: this browser build has no decoder for this format — it plays on the phone`;
  }
  return reason;
}

/**
 * Now Playing, before anything has played.
 *
 * A panel that expands onto an empty list looks identical to one that did not open at all. A
 * screen with nothing to say has to say that.
 */
function renderNothingPlaying() {
  $('np-title').textContent = queue[index]?.name || 'Nothing playing';
  $('np-message').hidden = true;
  $('np-names').replaceChildren();
  const list = $('fields');
  list.replaceChildren();
  const dt = document.createElement('dt');
  dt.textContent = '—';
  const dd = document.createElement('dd');
  dd.textContent = 'nothing has played yet';
  list.append(dt, dd);
  $('subsongs').replaceChildren();
  $('subsongbar').hidden = true;
}

function renderNowPlaying(fields, subsongs, current, entry = queue[index]) {
  $('np-title').textContent = fields.title || entry?.name || 'Nothing playing';
  const list = $('fields');
  list.replaceChildren();
  const rows = [];
  // Where it came from and what the file is called, which the title stops showing once the tune's
  // own name has been read out of it.
  if (entry) rows.push(['File', [whereOf(entry), fileOf(entry)].filter(Boolean).join('/')]);
  const year = releaseYear(fields);
  if (year) rows.push(['Year', year]);
  // The author where the file is silent, as the phone fills it from its song database: the folder
  // Modland files the tune under. What the file says always wins.
  const known = { ...fields, artist: authorOf(entry, fields) };
  for (const [key, label] of FIELD_ORDER) {
    const value = known[key]?.trim();
    if (value && value !== '0') rows.push([label, value]);
  }
  for (const [label, value] of rows) {
    const dt = document.createElement('dt');
    dt.textContent = label;
    const dd = document.createElement('dd');
    dd.textContent = value;
    list.append(dt, dd);
  }
  // Monospaced, as on the phone: these were written for a tracker's fixed-width screen, and the
  // alignment is part of what they say.
  $('np-message').hidden = !fields.message?.trim();
  $('np-message-text').textContent = fields.message ?? '';
  renderNames(fields);

  // **Subsongs are not decoration.** One `.kss` holds 256 tunes and one `.sndh` holds three; a
  // player that only ever plays the first is playing a fraction of the file (`docs/PLAN_FORMATS.md`).
  // The switch that decides what `next` means, shown only where it decides anything.
  $('subsongbar').hidden = subsongs <= 1;
  $('allsubsongs').setAttribute('aria-checked', String(playAllSubsongs));

  const strip = $('subsongs');
  strip.replaceChildren();
  if (subsongs > 1) {
    for (let i = 0; i < subsongs; i++) {
      const button = document.createElement('button');
      button.className = 'subsong';
      button.textContent = String(i + 1);
      button.setAttribute('aria-pressed', String(i === current));
      // The worklet answers with the tune's own length and title, and that answer redraws these
      // chips — so this only asks. Marking the pressed one here as well would be a second opinion
      // about which tune is playing.
      button.onclick = () => node?.port.postMessage({ type: 'subsong', index: i });
      strip.append(button);
    }
  }
}

/**
 * The operating system's own media controls.
 *
 * **The phone has this and the browser can too.** `PlaybackService` publishes a `MediaSession` so
 * the notification, the lock screen and a headset button work; `navigator.mediaSession` is the same
 * idea, and it is what makes a keyboard's play key reach a tab buried among twenty others. Without
 * it the page is a thing you must find before you can pause it.
 *
 * Guarded, because it is absent in older browsers and the page must not care.
 */
function publishToSystem(entry, fields) {
  if (!('mediaSession' in navigator)) return;
  try {
    navigator.mediaSession.metadata = new window.MediaMetadata({
      title: fields?.title || entry?.name || 'Protracktor',
      artist: authorOf(entry, fields ?? {}),
      album: [machineOf(entry) || fields?.format, releaseYear(fields ?? {})].filter(Boolean).join(' · '),
    });
    navigator.mediaSession.setActionHandler('play', () => $('playpause').click());
    navigator.mediaSession.setActionHandler('pause', () => $('playpause').click());
    navigator.mediaSession.setActionHandler('nexttrack', () => $('next').click());
    navigator.mediaSession.setActionHandler('previoustrack', () => $('prev').click());
  } catch { /* an older browser; the page works without it */ }
}

/** The tab's name, so a page among twenty says what it is playing. */
function nameTheTab(entry) {
  document.title = entry ? `${entry.name} — Protracktor` : 'Protracktor';
}

async function playAt(next) {
  try {
    await start();
  } catch (error) {
    // `start` has already said what went wrong; this stops the queue walking on regardless.
    setPlaying(false);
    $('sub').textContent = '—';
    return;
  }
  index = next;
  if (history[history.length - 1] !== next) history.push(next);
  remember();
  const entry = queue[index];
  render();
  followPlaying();
  // **The bar starts again with the tune**, not when the engine first reports a position. The phone
  // zeroes it the instant a track is chosen; left alone, the page's bar stays on the last tune's
  // 1:07 through the whole of the next download.
  duration = 0;
  endsAt = 0;
  openLengths = [];
  // **`endedByClock` is deliberately NOT cleared here**, and clearing it here was a bug that
  // skipped four tracks at a time (`docs/STATUS.md` C59).
  //
  // This function starts *loading* the next tune; the worklet goes on playing the **old** one until
  // the new bytes arrive and open, which is a fetch away. Its position messages keep coming in that
  // gap, still carrying the old tune's clock -- and `duration` has just been zeroed for the bar, so
  // the limit falls back to three minutes and every one of those messages is past it. Cleared here,
  // the guard reopened and the queue walked on once per message until the fetch finished.
  //
  // It is cleared where a new tune actually starts: `opened` and `subsong`.
  $('seek').value = 0;
  paint($('seek'));
  seekLanded();
  $('elapsed').textContent = clock(0);
  $('remaining').textContent = clockTotal(0);
  nameTheTab(entry);
  $('title').textContent = entry.name;
  $('sub').textContent = 'fetching…';
  $('error').textContent = '';

  let bytes;
  loading?.abort();
  const abort = new AbortController();
  loading = abort;
  rowState = 'loading';
  render();
  setPlaying(false);
  try {
    if (entry.data) {
      // **Handed over rather than fetched.** A file on the phone has no address a browser could
      // open, so the phone sends the bytes with the queue -- which it can do because it is there at
      // the moment of transfer, and because a tracker module is kilobytes (`docs/PLAN_WEB.md` §8).
      //
      // **A copy, and that is not tidiness** (`docs/review-round-8.md` R1). The message below
      // *transfers* the buffer, which detaches it here -- so handing over `entry.data` itself made
      // the queue's own copy zero bytes long, and the second play of that row sent nothing and
      // threw. Measured: one transfer, `byteLength` 0. A `.slice` of twenty kilobytes is not worth
      // reasoning about; a queue that empties itself as it plays is.
      $('sub').textContent = 'from the phone…';
      bytes = entry.data.slice(0);
    } else {
      // A Random pick read ahead was fetched while the tune before it played, which is the point of
      // reading ahead: a pick that has not been fetched is a gap between tracks. Used once.
      const ready = random?.bytes.get(entry.url);
      if (ready) random.bytes.delete(entry.url);
      bytes = ready ? await ready : null;
      if (!bytes) {
        $('sub').textContent = 'fetching…';
        const response = await fetch(entry.url, { signal: abort.signal });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        bytes = await response.arrayBuffer();
      }
    }
  } catch (e) {
    // **Only the load that is still current clears the flag.** Two quick track changes overlap:
    // the older fetch finishes after the newer one has started, and clearing unconditionally would
    // wipe the newer controller -- leaving a download nobody can call off and a button that lies
    // about what it will do.
    if (loading === abort) { loading = null; setPlaying(false); }
    if (e.name === 'AbortError') { $('sub').textContent = 'stopped'; return; }
    if (random && index === queue.length - 1 && skipFailedPick(e.message)) return;
    // A fetch that fails here is usually CORS or a network that blocks the archive, and those are
    // different problems from a file the decoders refuse. Say which.
    $('error').textContent = `could not fetch it: ${e.message}`;
    $('sub').textContent = '—';
    return;
  }
  // **The load is not over when the fetch is.** The bytes still have to reach the worklet and be
  // opened. In that gap the dock says "opening…" while the button would otherwise be back to a
  // play arrow, and pressing it would do something else entirely. The controller stays until the
  // worklet answers, so "stop" means the whole operation and the glyph agrees with it.
  //
  // Handed over, and the page now waits for the worklet to say `opened` or `failed`. It says which
  // it is waiting for, because "fetching…" left standing after the fetch finished is a lie.
  $('sub').textContent = engineReady
    ? `${(bytes.byteLength / 1024).toFixed(0)} KB — opening…`
    : `${(bytes.byteLength / 1024).toFixed(0)} KB — waiting for the engine…`;

  // **Asked before the bytes are handed over, because handing them over detaches them** -- the
  // postMessage below transfers the buffer, and a moment later this page does not have it. The
  // phone hashes at the same point and for the same reason (`docs/STATUS.md` C56).
  //
  // Only for the three names HVSC could possibly know. Hashing every file would cost a pass over a
  // five-megabyte MP3 to learn that a C64 database has never heard of it.
  openLengths = await songLengthsFor(entry.file ?? entry.name, bytes);
  openMetadata = await songMetadataFor(bytes);

  node.port.postMessage({ type: 'open', bytes, name: entry.file ?? entry.name }, [bytes]);
  setPlaying(false);
  announceGesture();

  // **A worklet that never answers is the failure with no symptom.** It has happened twice on this
  // page -- once when a message was dropped before the engine had compiled, once when the engine
  // did not load at all -- and both times the screen simply sat there. Ten seconds is far past any
  // honest open; after that the page says so rather than waiting for ever.
  clearTimeout(openWatchdog);
  openWatchdog = setTimeout(function expire() {
    if (loading !== abort) return;
    // Waiting for a touch is not a worklet that never answered: Chrome runs none until then.
    if (context?.state === 'suspended') { openWatchdog = setTimeout(expire, 10_000); return; }
    loading = null;
    setPlaying(false);
    $('error').textContent = engineReady
      ? 'the decoder took the file and never answered — check the browser console'
      : 'the engine never finished loading, so nothing can be opened — check the browser console';
    $('sub').textContent = '—';
  }, 10_000);
}

/**
 * Says out loud that a browser will not start audio by itself.
 *
 * **This is the failure that looks like a broken feature.** A queue arrives from the phone, the page
 * fills in, the decoder opens the file — and nothing is heard, because an `AudioContext` created
 * without a click starts suspended and `process()` is never called. `docs/PLAN_HANDOFF.md` §4 listed
 * this as one of four things that would look like bugs, and then the page did not mention it.
 *
 * The resume is attempted anyway: if the tab has ever been clicked, it succeeds and there is
 * nothing to say.
 */
async function announceGesture() {
  if (!context) return;
  if (context.state === 'suspended') {
    try { await context.resume(); } catch { /* needs a gesture; the message below is the answer */ }
  }
  if (context.state === 'suspended') waitForTouch();
}

const PLAY_GLYPH = 'M8 5v14l11-7z';
const PAUSE_GLYPH = 'M6 5h4v14H6zM14 5h4v14h-4z';
const STOP_GLYPH = 'M6 6h12v12H6z';

/**
 * Keeps the playing row on screen.
 *
 * The phone has a button for this, and it has to be subtle. A page has room to simply do it: a
 * queue of fifty is a scrollbar, and a playing row three screens up is the same complaint in a
 * different medium.
 */
function followPlaying() {
  // Not while rows are being ticked: a list that moves under a working finger fights it.
  if (!selected.size) revealRow($('queue').children[index]);
  // Browse too, when it is open on the tune: the phone keeps every list of tracks in step, and a
  // folder somebody is reading while the music moves on is the list that most needs it.
  if (!$('browse').hidden) revealRow(markPlayingIn($('browselist'), queue[index]?.url));
}

/**
 * Puts a row on screen by the least scroll that shows it, and does nothing if it is already there.
 *
 * **The phone's `KeepRowInView`, which a browser has natively.** `block: 'nearest'` is exactly the
 * rule: never while the row is visible, one row's worth when it is not, off the top to the top
 * edge and off the bottom to the bottom edge. One function for every list, because a second copy
 * would have to get all four right again.
 *
 * The phone's worst defect cannot happen here: its dock was drawn over the list, so a row behind it
 * counted as visible. The page's dock is `main`'s sibling in the column, not a layer over it, so
 * nothing a list considers on screen can be covered.
 */
function revealRow(row) {
  if (row?.scrollIntoView) row.scrollIntoView({ block: 'nearest' });
}

/** The address of what is playing, or null. A tune only *selected* by the phone is not playing. */
function playingUrl() {
  return index >= 0 && rowState && rowState !== 'selected' ? queue[index]?.url ?? null : null;
}

/**
 * Marks the row of [url] in a list of tracks and answers it. **Marks only** — arriving at a list
 * never scrolls it, which on the phone was a decision rather than a default: every return to a list
 * would otherwise throw you back to the playing row and lose the place you were reading.
 */
function markPlayingIn(list, url = playingUrl()) {
  let found = null;
  const fetching = rowState === 'loading';
  for (const row of list.children) {
    const playing = !!url && row.dataset.url === url;
    row.classList.toggle('playing', playing);
    // **And breathes while its tune is being fetched** (`docs/WISHLIST.md` B32), here as in the
    // playlist: these are the lists a tune is most often started from.
    row.classList.toggle('loading', playing && fetching);
    if (playing && !found) found = row;
  }
  return found;
}

function setPlaying(on) {
  playing = on;
  // Offered for anything playing that is not the playlist: a dice pick, a Browse result, a tune
  // sent by link. Deciding to keep it is what you do after hearing it.
  $('nowkeep').hidden = !(random || away) || !queue[index] || activePlaylist === PHONE;
  if ('mediaSession' in navigator) navigator.mediaSession.playbackState = on ? 'playing' : 'paused';
  // Three states, not two: a track being fetched is not "paused", and a button that would restart
  // the same download is the one press nobody wants twice.
  // Play, not stop, while the page waits for a touch: the press it wants is the one that plays.
  const stopping = loading && !firstTouch;
  $('playglyph').setAttribute('d', stopping ? STOP_GLYPH : on ? PAUSE_GLYPH : PLAY_GLYPH);
  $('playpause').title = stopping ? 'Stop loading' : on ? 'Pause' : 'Play';
  $('playpause').disabled = queue.length === 0;
  // Asked of the modes rather than of the position, exactly as `PlayerState.canGoNext` is: under
  // repeat-all the last track does have a next, and under shuffle the row above is not the previous.
  $('prev').disabled = beforeCurrent() == null && !(playAllSubsongs && hasPreviousSubsong());
  // Available while the *file* has more in it too, not only the queue: with the switch on, that is
  // what the button will do.
  $('next').disabled = afterCurrent() == null && !(playAllSubsongs && hasNextSubsong());
  // Shut while the dice runs: shuffle reorders the playlist, and Random plays from neither.
  $('shuffle').disabled = queue.length === 0 || !!random;
  $('repeat').disabled = queue.length === 0;
  $('shuffle').classList.toggle('on', shuffle);
  $('repeat').classList.toggle('on', repeat !== 'off');
  // The panel's actions are about the track it is describing, so they go dead with it.
  const entry = queue[index];
  $('np-show').disabled = !entry;
  $('np-folder').disabled = !authorFolderOf(entry);
  $('np-save').disabled = !entry || !!entry.local;
  $('np-link').disabled = !entry || !entry.url;
}

/**
 * The list, in the app's shape: a position, a title, and a subtitle that says where it came from.
 *
 * `ui/PlaylistScreen.kt` puts the source path in the subtitle and tints the playing row with
 * `primary`; both are reproduced here. The drag handle, the selection mode and the overflow menu
 * are phone-only and deliberately absent.
 */
/**
 * The empty playlist's offer (W11), the phone's `EmptyPlaylist`: **nothing is said until it is known
 * what is held**, then Browse when a catalogue is here and the download when none is. Asked again
 * whenever the list or the panels change; a later ask supersedes an earlier one still reading.
 */
let emptyAsked = 0;
async function renderEmpty() {
  const asked = ++emptyAsked;
  const box = $('emptyplaylist');
  const showing = () => !queue.length && !random && !away;
  if (!showing()) { box.hidden = true; return; }
  let held = false;
  try {
    const [modland, asma] = await Promise.all([archive.meta('modland'), archive.meta('asma')]);
    held = !!(modland?.tracks || asma?.tracks);
  } catch { /* nothing readable is nothing held */ }
  if (asked !== emptyAsked || !showing()) { if (!showing()) box.hidden = true; return; }
  $('emptybody').textContent = held
    ? 'Browse the catalogues this browser holds, send tunes from your phone with the pairing code, '
      + 'or paste some links.'
    : 'Nothing to browse yet. Download a catalogue and half a million tracks are yours to look '
      + 'through, offline — or send tunes from your phone with the pairing code.';
  const action = $('emptyaction');
  action.innerHTML = iconSvg(held ? ICON.cloud : ICON.download) + (held ? 'Browse' : 'Get some music to browse');
  action.onclick = async () => {
    browsePath = [];
    showPanel('browse');
    await renderBrowse();
  };
  box.hidden = false;
}

function render() {
  renderEmpty();
  const list = $('queue');
  // Browse's Add buttons say whether a tune is in this list, and this is where the list changed.
  for (const row of $('browselist').children) row.repaintAdd?.();
  // And its rows follow what is playing and what is being fetched, since the list on screen during
  // a digression is Browse's rather than this one.
  if (!$('browse').hidden) markPlayingIn($('browselist'));
  if (selected.size) selected = new Set([...selected].filter((entry) => queue.includes(entry)));
  list.replaceChildren(...queue.map((entry, i) => {
    const li = document.createElement('li');
    li.className = i === index && rowState ? `track ${rowState}` : 'track';
    // Grey rather than red: `.failed` means a decoder refused this, and nothing here broke -- the
    // bytes simply never left the phone. A different state, and one that does not answer a click.
    if (entry.local) li.classList.add('local');

    const n = document.createElement('span');
    n.className = 'n';
    n.textContent = String(i + 1);
    // **While ticking, the box stands where the number was** -- the same slot, so the row does not
    // move when selection starts under the finger that started it (the phone's reason too).
    if (selected.size && !entry.local) {
      const tick = document.createElement('input');
      tick.type = 'checkbox';
      tick.className = 'tick';
      tick.checked = selected.has(entry);
      tick.setAttribute('aria-label', `Select ${entry.name}`);
      n.replaceChildren(tick);
    }
    li.classList.toggle('ticked', selected.has(entry));

    const text = document.createElement('div');
    text.className = 'text';
    const title = document.createElement('div');
    title.className = 'title';
    title.textContent = entry.name;
    const meta = document.createElement('div');
    meta.className = 'meta';
    meta.textContent = entry.meta ?? sourceOf(entry.url);
    text.append(title, meta);

    const menu = document.createElement('button');
    menu.className = 'rowmenu';
    menu.title = 'More';
    menu.setAttribute('aria-label', `More for ${entry.name}`);
    menu.innerHTML = '<svg viewBox="0 0 24 24"><path d="M12 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 2a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm0 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"/></svg>';
    // Stopped here, or opening the menu would also start the track underneath it.
    menu.onclick = (event) => { event.stopPropagation(); openRowMenu(entry, menu); };

    li.append(n, text, menu);
    if (!entry.local) {
      li.onclick = () => {
        if (swallowRowClick) { swallowRowClick = false; return; }
        if (selected.size) toggleSelected(entry); else playAt(i);
      };
      holdToSelect(li, entry);
    }
    return li;
  }));
  updateSelectBar();
  $('count').textContent = queue.length
    ? `${queue.length} track${queue.length === 1 ? '' : 's'}`
    : 'nothing yet';
  if (random) $('count').textContent = `${queue.length} played at random`;
  // What the session is, not always History's words: a tune sent here said "1 from your history".
  if (away) {
    $('count').textContent = `${queue.length} ${{ history: 'from your history', browse: 'from Browse', link: 'sent to you' }[away.kind] ?? ''}`.trim();
  }
}

/**
 * The bytes of a row, from wherever they are.
 *
 * A paired queue carries them; a link does not, so they are fetched. Either way this is what the
 * two actions that need bytes are built on, and it is why they are disabled for a row that stayed
 * on the phone: there is nothing to go and get.
 */
async function bytesOf(entry) {
  // A copy for the same reason `playAt` takes one: the callers hand these to the worklet, which
  // transfers them, and a transfer detaches whatever it was given (`docs/review-round-8.md` R1).
  if (entry.data) return entry.data.slice(0);
  if (!entry.url) return null;
  const response = await fetch(entry.url);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.arrayBuffer();
}

/**
 * Hands the file to the browser to save.
 *
 * A blob and a click, which is the only way a page can offer a file. The name matters more than it
 * looks: it is what the person ends up with on disk, and `entry.file` is the decoder's name for it
 * where there is one.
 */
async function saveFile(entry) {
  try {
    const bytes = await bytesOf(entry);
    if (!bytes) { status('that one stayed on the phone — there is nothing here to save'); return; }
    const url = URL.createObjectURL(new Blob([bytes], { type: 'application/octet-stream' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = entry.file || entry.name || 'tune';
    a.click();
    // Revoked on a timer rather than immediately: the click is asynchronous inside the browser and
    // revoking in the same tick has been known to hand the user a zero-byte file.
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
    status(`Saved ${a.download}`);
  } catch (e) {
    status(`could not save it: ${e.message}`);
  }
}

/**
 * Copies the track's own address.
 *
 * **The track's, not the page's.** A link to this page carries the whole queue and is what the
 * phone already sends; what is useful from a row is the one file, at an address anybody can open —
 * which for Modland and The Mod Archive is a real URL and for a row that stayed on the phone is
 * nothing at all.
 */
async function copyLink(entry) {
  if (!entry.url) { status('that one stayed on the phone — it has no address'); return; }
  try {
    await navigator.clipboard.writeText(entry.url);
    showNote('Link copied');
  } catch {
    // A browser that refuses the clipboard without a gesture it recognises, or an insecure context.
    // Showing the address is worse than copying it and much better than silence.
    showNote('The browser would not copy it — the address is in Now Playing');
    status(entry.url);
  }
}

/** Scrolls the list to a row and marks it, which is what the phone's "Show in playlist" does. */
function showInPlaylist(i) {
  const row = $('queue').children[i];
  if (!row) return;
  row.scrollIntoView({ block: 'center', behavior: 'smooth' });
  showPanel(null);
}

/**
 * Describes a file that is not playing, through the worklet.
 *
 * Opens a **second** decoder handle beside the one making sound, describes it and closes it. The
 * cost is opening a decoder on the audio thread; the median module here is 20 KB and that is
 * nothing, but a very large file would be the thing to blame if this ever glitches.
 */
let describeAsk = 0;
const describePending = new Map();
async function informAbout(entry) {
  showPanel('nowplaying');
  $('np-title').textContent = entry.name;
  $('fields').replaceChildren();
  status('reading it…');
  try {
    const bytes = await bytesOf(entry);
    if (!bytes) { status('that one stayed on the phone — there is nothing to read'); return; }
    await start();
    const id = ++describeAsk;
    // **With the watchdog `playAt` has.** A worklet that takes a message and never answers is a
    // failure this page has met twice, and without a deadline this promise is simply never settled:
    // the panel sits on "reading it…" for ever (`docs/review-round-8.md` R5).
    const answer = new Promise((resolve) => {
      const timer = setTimeout(() => { describePending.delete(id); resolve(null); }, 10_000);
      describePending.set(id, (message) => { clearTimeout(timer); resolve(message); });
    });
    node.port.postMessage({ type: 'describe', id, name: entry.file || entry.name, bytes }, [bytes]);
    const described = await answer;
    if (!described) { status('the decoder took the file and never answered'); return; }
    if (!described.ok) { status(explainFailure(described.reason, entry)); return; }
    renderNowPlaying(describeFields(described.describe), described.subsongs, -1, entry);
    $('np-title').textContent = describeFields(described.describe).title || entry.name;
    status(`${entry.name} — not playing, just described`);
  } catch (e) {
    status(`could not read it: ${e.message}`);
  }
}

/** "Modland/Protracker/4-Mat", the way the phone's subtitle reads. */
function sourceOf(url) {
  // A document URI is a grant to one app on one phone; showing it would be showing plumbing.
  if (url.startsWith('content://')) return 'from the phone';
  try {
    const parsed = new URL(url, location.href);
    if (parsed.hostname.endsWith('modland.com')) {
      const path = decodeURIComponent(parsed.pathname.replace('/pub/modules/', ''));
      return `Modland/${path.split('/').slice(0, -1).join('/')}`;
    }
    if (parsed.hostname === 'asma.atari.org' && parsed.pathname.startsWith('/asma/')) {
      const path = decodeURIComponent(parsed.pathname.replace('/asma/', ''));
      return ['ASMA', ...path.split('/').slice(0, -1)].join('/');
    }
    if (parsed.hostname.endsWith('modarchive.org')) return 'The Mod Archive';
    return parsed.hostname;
  } catch {
    return '';
  }
}

/** Whether row [i] holds music this page can actually play. */
const playable = (i) => i >= 0 && i < queue.length && !queue[i].local;

/**
 * The row's own menu, anchored under its three dots.
 *
 * **The two that need bytes are disabled for a row that stayed on the phone**, and the third with
 * them: there is no file to save, no address to copy and nothing to read. A menu that offers three
 * things and does none of them is worse than one with three things greyed.
 */
function openRowMenu(entry, anchor) {
  const menu = $('menu');
  menu.replaceChildren();
  const items = [
    // A mouse has no long press to discover, so the way into ticking rows is here as well.
    ['Select', () => startSelecting(entry), !entry.local, ICON.check],
    // **One row, one press**, as the phone's row menu has it: ticking the row first would be a
    // gesture meant for many rows spent on one.
    ['Add to playlist', () => openAddTo([plain(entry)]), !entry.local, ICON.playlistAdd],
    ['Save the file', () => saveFile(entry), !entry.local, ICON.save],
    ['Copy a link', () => copyLink(entry), !!entry.url, ICON.link],
    ['Share with Protracktor', () => sendToWeb(entry), canSendToWeb(entry), ICON.web],
    // On every list, as on the phone, not in Browse alone.
    ['More from this author', () => showAuthorFolder(entry), !!authorFolderOf(entry), ICON.folder],
    ['Information', () => informAbout(entry), !entry.local, ICON.info],
  ];
  // Pruning the record before keeping the rest, as the phone's rows allow.
  if (random) items.push(['Remove from this list', () => removeRandomAt(queue.indexOf(entry)), true, ICON.remove]);
  // **A playlist of the user's own can lose a row**, as on the phone. "From the phone" is what
  // the phone sent and is never edited here; a session's list is not a playlist.
  if (!random && !away && activePlaylist !== PHONE) {
    items.push(['Remove from this playlist', () => removeFromPlaylist(queue.indexOf(entry)), true, ICON.remove]);
  }
  showMenu(items, anchor);
}

/**
 * Draws a menu under [anchor] from `[label, act, enabled, icon]` rows. Shared by a track's three dots
 * and a playlist's, so the two menus cannot come to look or behave differently.
 */
function showMenu(items, anchor) {
  const menu = $('menu');
  menu.replaceChildren();
  for (const [label, act, enabled, icon] of items) {
    const button = document.createElement('button');
    // An icon and its name, never the name alone -- the phone's menu has both, and so must this.
    button.innerHTML = iconSvg(icon);
    button.append(label);
    button.disabled = !enabled;
    button.onclick = () => { closeRowMenu(); act(); };
    menu.append(button);
  }
  const box = anchor.getBoundingClientRect();
  menu.hidden = false;
  // Placed after it is shown, because a hidden element measures zero and would be pinned to the
  // top left on the first open of every session.
  const height = menu.offsetHeight;
  menu.style.left = `${Math.max(8, box.right - menu.offsetWidth)}px`;
  menu.style.top = `${box.bottom + height > innerHeight ? Math.max(8, box.top - height) : box.bottom}px`;
}

function closeRowMenu() { $('menu').hidden = true; }
addEventListener('click', (event) => {
  if (!$('menu').hidden && !$('menu').contains(event.target)) closeRowMenu();
});
addEventListener('keydown', (event) => { if (event.key === 'Escape') closeRowMenu(); });
addEventListener('scroll', closeRowMenu, true);

/**
 * Writes the queue on screen into whichever playlist it belongs to.
 *
 * **Debounced, because the things that call it are the transport.** Moving to the next track is a
 * write, and a listening session is hundreds of them; a playlist is a few hundred rows and writing
 * it on every one would be work nobody asked for. Half a second is under a person's notice and far
 * above a button press.
 */
let saveTimer = null;
function remember() {
  // **The dice's record is never a playlist**, "From the phone" least of all.
  if (random || away) return;
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => { saveTimer = null; if (!random && !away) saveQueue(); }, 500);
}

/**
 * Writes the queue into its playlist, **reading everything before the first `await`**.
 *
 * Reading the name first and the tracks after awaiting it is harmless while the queue only ever
 * holds one playlist, and a hole the moment Random swaps it: a save still pending when the dice
 * takes over would write the dice's picks into the playlist it had just left.
 */
async function saveQueue() {
  // **Edits wait for Save**, as on the phone. While the list differs from the one on disk
  // nothing is written, the place in it included --
  // it would be a place in a list that was never saved.
  if (dirty) return;
  const id = activePlaylist;
  // Inside Random or History the playlist waits in the stash, and that is what Save means.
  const source = (random ?? away)?.stash ?? { queue, index };
  // The bytes a phone sent are deliberately **not** kept. They are a copy of a file that lives
  // somewhere else, they are the largest thing in the queue by far, and a page that quietly hoards
  // somebody's music is not what this is.
  const tracks = source.queue.map(({ url, name, meta, local, file }) => ({ url, name, meta, local, file }));
  const at = source.index;
  try {
    const name = id === PHONE ? 'From the phone' : (await playlists.get(id))?.name ?? 'Playlist';
    await playlists.save({ id, name, tracks, index: at });
    await settings.set('active', id);
  } catch (e) {
    status(`could not save the playlist: ${e.message}`);
  }
}

/** A fresh permutation. A new lap is a new shuffle; replaying one order forever is not shuffle. */
function reshuffle(keep) {
  // Rows that cannot play are not in the permutation at all, which is simpler and stricter than
  // dealing them out and skipping them later.
  order = queue.map((_, i) => i).filter(playable);
  for (let i = order.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [order[i], order[j]] = [order[j], order[i]];
  }
  if (keep != null) {
    // What is playing keeps playing: reordering what comes next is not a reason to interrupt now.
    order = [keep, ...order.filter((i) => i !== keep)];
  }
}

/** The next row from [from] in [step] that can play, or null. Never wraps; the callers decide that. */
function seek(from, step) {
  for (let i = from; i >= 0 && i < queue.length; i += step) if (playable(i)) return i;
  return null;
}

/**
 * Random, as the phone has it (`docs/PLAN_RANDOM.md`).
 *
 * **A second queue, explicitly transient.** The page plays from `queue`, so while the dice runs
 * `queue` *is* the record -- which lets every row keep the actions, the marking and item 2's
 * scrolling that the playlist has -- and the playlist that was showing waits in `stash` until the
 * session ends. `remember()` refuses to write while this is set, so no playlist ever receives a pick.
 *
 * The record is what has played. Three picks are decided and fetched ahead of it and not shown: a
 * fetching strategy, not a promise.
 */
let random = null;
const RANDOM_AHEAD = 3;   // the phone's READ_AHEAD
const RANDOM_DRAWS = 4;   // the phone's OVERDRAW: drawn wide, repeats dropped
const RANDOM_FAILURES = 8; // the phone's maxFailedRandomPicks
/** Replaceable so the checks can roll a known sequence; the page always uses `Math.random`. */
let randomSource = Math.random;

/**
 * The heading over a list the page is playing from without it being a playlist -- Random's record,
 * or History (item 4). One heading for both, because they are one idea: music playing from
 * somewhere the playlist is not, and a way back to it.
 */
const SESSION = {
  random: { title: 'Playing at random', chip: 'Random', icon: () => ICON.dice },
  history: { title: 'Playing from your history', chip: 'History', icon: () => ICON.history },
  // The phone's "Playing from search", widened to every Browse list: a folder plays the same way.
  browse: { title: 'Playing from Browse', chip: 'Browse', icon: () => ICON.search },
  // A tune sent here as a link (Share with Protracktor): shown to you, not yet yours.
  link: { title: 'Playing a tune sent to you', chip: 'Sent', icon: () => ICON.web },
};

function showSessionView(kind) {
  $('randomhead').hidden = !kind;
  $('randomfilter').hidden = true;
  $('randomnote').hidden = true;
  // **A digression says whose folder it is**, in the shape the dice's own heading has (`docs/BACKLOG.md`
  // A41): the dice is not over, it is waiting, and the way back is the button beside these words.
  const digressing = kind === 'browse' && !!away?.dice;
  if (kind) {
    const { title, icon } = SESSION[kind];
    $('sessiontitle').textContent = digressing ? 'Browsing author' : title;
    $('sessionicon').innerHTML = iconSvg(digressing ? ICON.detour : icon())
      .replace(/^<svg[^>]*>|<\/svg>$/g, '');
  }
  // Back to the dice rather than out to the playlist, while there is a dice to go back to.
  $('random-leave').innerHTML = iconSvg(digressing ? ICON.dice : ICON.playlist)
    + (digressing ? 'Random' : 'Playlist');
  // The line under the heading: what the dice picks from, or whose folder this is -- the same shape
  // for both, rather than a name after a dash.
  if (digressing) $('randomscope').textContent = away.author;
  $('randomscope').hidden = kind !== 'random' && !digressing;
  $('random-filter').hidden = kind !== 'random';
  // **The chip stays usable**, because it looks like a way out and ought to be one. The phone
  // hides it here; the page lets it name where you are and choose where to go,
  // and choosing a playlist ends the session on the way (`choosePlaylist`).
  if (kind) $('playlistname').textContent = digressing ? 'Browsing' : SESSION[kind].chip;
  setDirty(dirty);
}

function showRandomView(on) { showSessionView(on ? 'random' : null); }

/** Browse → Random. Closes the panel first, and starts the session inside the same click. */
function enterRandomFromBrowse() {
  showPanel(null);
  openRandom();
}

/**
 * Opens a session: a new record, and a tune playing with no second press.
 *
 * **The engine is started before anything is awaited**, because a browser allows sound only from
 * inside a click -- and a pick has to be read from IndexedDB and fetched before it can play.
 */
async function openRandom() {
  const engine = start().catch(() => null);
  // A change the playlist was still waiting to save is its own; written now, before the swap.
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null; saveQueue(); }
  // Entering again starts a new record but goes back to the same playlist when it ends.
  const stash = random?.stash ?? away?.stash ?? { queue, index, history, order, name: $('playlistname').textContent };
  away = null;
  loading?.abort();
  loading = null;
  random = { stash, ahead: [], table: null, filling: null, advancing: false, bytes: new Map() };
  queue = [];
  index = -1;
  history = [];
  order = [];
  rowState = null;
  showRandomView(true);
  render();
  await engine;
  await rollRandom();
}

/**
 * Tops the picks read ahead up to three: drawn uniformly over tunes, a tune the session has had
 * passed over unless the pool has run out, and each one's bytes fetched as soon as it is decided.
 */
function fillAhead() {
  const r = random;
  if (!r) return Promise.resolve();
  r.filling ??= (async () => {
    r.table ??= await archive.buildRandomTable();
    while (random === r && r.ahead.length < RANDOM_AHEAD && r.table.total > 0) {
      const seen = new Set([...queue, ...r.ahead].map((entry) => entry.url));
      const drawn = [];
      for (let i = 0; i < RANDOM_DRAWS; i++) {
        const track = await archive.drawTrack(r.table, randomSource);
        if (track) drawn.push(track);
      }
      if (!drawn.length) break;
      const url = freshPick({ drawn: drawn.map((track) => track.url), seen });
      const pick = drawn.find((track) => track.url === url);
      r.ahead.push(pick);
      r.bytes.set(pick.url, fetch(pick.url)
        .then((response) => (response.ok ? response.arrayBuffer() : null))
        .catch(() => null));
    }
  })().finally(() => { r.filling = null; });
  return r.filling;
}

/**
 * Adds a pick to the end of the record and plays it.
 *
 * **The C35 lesson, in the first line.** On the phone a track's end could be acted on once per poll
 * tick while the next pick was being chosen, and five picks went by in a second. Here the gap is
 * IndexedDB and a fetch. The flag is raised before the first `await`, so a second "ended" -- or a
 * second press of next -- finds a roll already under way and does nothing.
 */
async function rollRandom() {
  const r = random;
  if (!r || r.advancing) return;
  r.advancing = true;
  let pick;
  try {
    await fillAhead();
    pick = r.ahead.shift();
  } finally {
    r.advancing = false;
  }
  if (random !== r) return;   // left while it was deciding
  if (!pick) {
    $('randomnote').textContent = 'Nothing to pick from. Download the Modland index in Browse first.';
    $('randomnote').hidden = false;
    return;
  }
  queue.push(pick);
  // The next picks are decided while this one plays.
  fillAhead().catch(() => {});
  await playAt(queue.length - 1);
}

/**
 * Whether the list on screen differs from the one on disk -- the phone's `dirty`.
 *
 * Saving works as it does on the phone. Removing a row, or replacing the list from Browse or the
 * paste box, changes what is on screen and not what is stored;
 * **Save** writes it, **Discard** reads the stored one back, and a switch with edits still waiting
 * asks which. Where playback has got to is the app's own business and is saved as it goes -- but
 * only while the list is the saved one, because a place in an unsaved list means nothing on disk.
 * "From the phone" is never edited, so it is never dirty.
 */
let dirty = false;
let unsavedAnswer = null;

function setDirty(value) {
  dirty = value;
  // The phone's rule: Save and Discard only while there is something to save, and not while the
  // screen is showing a session rather than the playlist.
  const show = dirty && !random && !away;
  $('tab-save').hidden = !show;
  $('tab-discard').hidden = !show;
}

async function saveEdits() {
  setDirty(false);
  await saveQueue();
  status('Saved');
}

/** Reads the stored playlist back, throwing the edits away. */
async function discardEdits() {
  setDirty(false);
  await switchTo(activePlaylist);
  status('Changes discarded');
}

/**
 * Asks about edits that a switch would throw away, and answers whether to go on.
 *
 * True at once when there is nothing unsaved. Otherwise the phone's dialog: Save them or Discard
 * them, both of which go on; closing it keeps editing, which does not.
 */
function settleUnsaved() {
  if (!dirty) return Promise.resolve(true);
  return new Promise((resolve) => {
    unsavedAnswer = resolve;
    $('unsaved').hidden = false;
  });
}

function answerUnsaved(go) {
  $('unsaved').hidden = true;
  const resolve = unsavedAnswer;
  unsavedAnswer = null;
  resolve?.(go);
}

/** The phone's icons, the same paths, for controls the page builds rather than declares. */
const ICON = {
  save: 'M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z',
  link: 'M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z',
  info: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z',
  remove: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  rename: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.9959.9959 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  expand: 'M12 8l-6 6 1.4 1.4L12 10.8l4.6 4.6L18 14z',
  web: 'M19 4H5c-1.11 0-2 .9-2 2v12c0 1.1.89 2 2 2h4v-2H5V8h14v10h-4v2h4c1.1 0 2-.9 2-2V6c0-1.1-.89-2-2-2zm-7 6l-4 4h3v6h2v-6h3l-4-4z',
  playlistAdd: 'M14 10H2v2h12v-2zm0-4H2v2h12V6zm4 8v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM2 16h8v-2H2v2z',
  search: 'M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  // A digression: the way you were going, and the turn you took off it.
  detour: 'M3 5h7a5 5 0 0 1 5 5v6h3l-4.5 5.5L9 16h3v-6a2 2 0 0 0-2-2H3V5z',
  // The way back to the playlist, the same glyph the heading's button was drawn with.
  playlist: 'M3 9h10v2H3V9zm0-4h10v2H3V5zm0 8h6v2H3v-2zm11-1v6l5-3-5-3z',
  folder: 'M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z',
  more: 'M12 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 2a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm0 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4z',
  check: 'M19 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.11 0 2-.9 2-2V5c0-1.1-.89-2-2-2zm-9 14l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z',
  download: 'M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z',
  // A tick in a disc: this set is here. The phone's `Downloaded`.
  downloaded: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM10 17l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z',
  // Fetch again what is already here. The phone's `Refresh`.
  refresh: 'M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
  cloud: 'M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z',
  // Hollow shapes on the phone, so they need the even-odd rule to keep their holes.
  dice: { d: 'M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM5 5v14h14V5H5zM7.2 8.5a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0zM14.2 8.5a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0zM10.7 12a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0zM7.2 15.5a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0zM14.2 15.5a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0z', hollow: true },
  history: { d: 'M5,4 L19,4 A2,2 0 0,1 21,6 L21,19 A2,2 0 0,1 19,21 L5,21 A2,2 0 0,1 3,19 L3,6 A2,2 0 0,1 5,4 Z M5.5,9.5 L18.5,9.5 L18.5,18.5 L5.5,18.5 Z M7,2 L9,2 L9,4 L7,4 Z M15,2 L17,2 L17,4 L15,4 Z M8,12 L11,12 L11,15 L8,15 Z', hollow: true },
};
const iconSvg = (icon) => {
  const { d, hollow } = typeof icon === 'string' ? { d: icon, hollow: false } : icon;
  return `<svg viewBox="0 0 24 24" aria-hidden="true"><path${hollow ? ' fill-rule="evenodd"' : ''} d="${d}"/></svg>`;
};

/**
 * Takes a row out of one of the user's own playlists, with the way back offered.
 *
 * The phone's rules (`PlaybackController.removeTracks`): **no question first** -- undo costs nothing
 * when it was meant -- and **removing what is playing stops it** rather than starting something
 * else, because no reading of "remove" asks for a different tune. The page saves as it goes where
 * the phone waits for Save, so the undo is the whole of the safety net here.
 */
let lastRemoval = null;
let undoTimer = null;

function removeFromPlaylist(at) { removeRows([at]); }

/**
 * Takes any number of rows out as **one edit, with one undo** -- the phone's `removeTracks`: what
 * changed for a group is that undo has to bring all of it back, each to where it was.
 */
function removeRows(ats) {
  if (random || away || activePlaylist === PHONE) return;
  const sorted = [...new Set(ats)].filter((at) => at >= 0 && at < queue.length).sort((x, y) => x - y);
  if (!sorted.length) return;
  const current = queue[index];
  const removed = sorted.map((at) => ({ at, track: queue[at] }));
  const wasCurrent = sorted.includes(index);
  for (let k = sorted.length - 1; k >= 0; k--) queue.splice(sorted[k], 1);
  if (wasCurrent) {
    stopForLeaving();
    index = Math.min(sorted[0], queue.length - 1);
    rowState = 'selected';
    setPlaying(false);
    $('title').textContent = queue[index]?.name ?? 'Nothing playing';
    $('sub').textContent = queue[index] ? 'press play' : '—';
  } else {
    index = queue.indexOf(current);
  }
  // The shuffle and the back-stack both hold indices, and every one past a gap has moved.
  history = index >= 0 ? [index] : [];
  if (shuffle) reshuffle(index >= 0 ? index : null);
  lastRemoval = { removed, playlist: activePlaylist, wasCurrent, current };
  setDirty(true);
  render();
  showUndo(removed.length === 1 ? `Removed ${removed[0].track.name}` : `Removed ${removed.length} tracks`);
}

function undoRemoval() {
  const removal = lastRemoval;
  hideUndo();
  if (!removal || removal.playlist !== activePlaylist || random || away) return;
  const current = queue[index];
  // In ascending order of where they were, so each lands where it stood before the others went.
  for (const { at, track } of removal.removed) queue.splice(Math.min(at, queue.length), 0, track);
  if (removal.wasCurrent) {
    index = queue.indexOf(removal.current);
    rowState = 'selected';
    $('title').textContent = removal.current?.name ?? 'Nothing playing';
    $('sub').textContent = 'press play';
  } else {
    index = queue.indexOf(current);
  }
  history = index >= 0 ? [index] : [];
  if (shuffle) reshuffle(index >= 0 ? index : null);
  // Still an edit, as on the phone: undo restores the rows, not the saved state.
  setDirty(true);
  render();
}

/**
 * Ticking rows, as the phone has it: a long press starts it, a tap then ticks rather than plays,
 * and a bar says what can be done with what is ticked. Held by entry rather than by index, so a row
 * that moves keeps its tick; a row that leaves the list takes its tick with it (`render`).
 */
let selected = new Set();
let pendingAdd = null;
/**
 * The click that follows a long press, which must not also tick the row off again. **Kept outside
 * the row**, because the press re-draws the list and the click lands on the new one; and forgotten
 * at the next press, so an unfinished one cannot eat an unrelated click -- `docs/STATUS.md` C36's
 * trap, avoided here.
 */
let swallowRowClick = false;

function holdToSelect(row, entry) {
  let timer = null;
  row.addEventListener('pointerdown', () => {
    swallowRowClick = false;
    timer = setTimeout(() => { swallowRowClick = true; startSelecting(entry); }, 500);
  });
  for (const event of ['pointerup', 'pointercancel', 'pointerleave']) {
    row.addEventListener(event, () => clearTimeout(timer));
  }
}

function startSelecting(entry) {
  selected.add(entry);
  render();
}

function toggleSelected(entry) {
  if (selected.has(entry)) selected.delete(entry); else selected.add(entry);
  render();
}

function clearSelection() {
  if (!selected.size) return;
  selected = new Set();
  render();
}

function updateSelectBar() {
  $('selectbar').hidden = !selected.size;
  $('selectcount').textContent = `${selected.size} selected`;
  // Delete only where rows can go: one of the user's own playlists, not the phone's, not a
  // session's list.
  $('sel-delete').hidden = !!(random || away) || activePlaylist === PHONE;
}

/**
 * The playlists tracks can go to: the user's own, never the phone's, and not the one they are in.
 * Ticked
 * rows by default; Browse passes the tune it was asked about, and is gone back to afterwards.
 */
let addFromBrowse = false;
async function openAddTo(tracks = queue.filter((entry) => selected.has(entry) && !entry.local), { browse = false } = {}) {
  if (!tracks.length) return;
  pendingAdd = tracks;
  addFromBrowse = browse;
  // Ticked rows are already in the playlist on screen unless a session is showing; a tune from
  // Browse is in none of them.
  const inSession = browse || !!(random || away);
  const targets = (await playlists.all())
    .filter((p) => p.id !== PHONE && (inSession || p.id !== activePlaylist));
  const list = $('addtolist');
  list.replaceChildren();
  for (const target of targets) {
    const li = document.createElement('li');
    const count = document.createElement('div');
    count.className = 'pcount';
    count.textContent = String(target.tracks?.length ?? 0);
    const name = document.createElement('div');
    name.className = 'pname';
    name.textContent = target.name;
    li.append(count, name);
    li.onclick = () => addTracksTo(target.id);
    list.append(li);
  }
  $('addtonote').textContent = `${tracks.length} track${tracks.length === 1 ? '' : 's'} to add.`
    + (targets.length ? '' : ' There is no other playlist yet — make one.');
  showPanel('addto');
}

/**
 * Appends the ticked tracks to a playlist, or to a new one. **What is already there is not added
 * twice** -- the phone's `appendTracks` rule. Another playlist is written at once, as the phone
 * writes it; the one a session was started from is an edit of it and waits for Save like any other.
 */
async function addTracksTo(id, newName = null) {
  const tracks = pendingAdd ?? [];
  pendingAdd = null;
  if (id && id === activePlaylist) {
    addToShowing(tracks);
  } else {
    const target = id ? await playlists.get(id) : { id: `p${Date.now().toString(36)}`, name: newName, tracks: [], index: 0 };
    const have = new Set((target.tracks ?? []).map((t) => t.url));
    const adding = tracks.filter((t) => !have.has(t.url)).map(plain);
    await playlists.save({ ...target, tracks: [...(target.tracks ?? []), ...adding] });
    status(`Added ${adding.length} to ${target.name}` + (adding.length < tracks.length ? `, ${tracks.length - adding.length} already there` : ''));
  }
  closeAddTo();
  clearSelection();
}

/** Out of the picker, and back to Browse if that is where it was opened from. */
function closeAddTo() {
  const back = addFromBrowse;
  addFromBrowse = false;
  pendingAdd = null;
  showPanel(back ? 'browse' : null);
}

const plain = ({ url, name, meta, local, file }) => ({ url, name, meta, local, file });

/**
 * Appends to the playlist that is showing -- or waiting under a session -- and answers how many
 * went in. **An edit, waiting for Save like every other** (the phone's rule); what is already there
 * is not added twice (the phone's `appendTracks`).
 */
function addToShowing(tracks) {
  const session = random ?? away;
  const target = session ? session.stash.queue : queue;
  const name = session ? session.stash.name : $('playlistname').textContent;
  const have = new Set(target.map((t) => t.url));
  const adding = tracks.filter((t) => !have.has(t.url)).map(plain);
  target.push(...adding);
  if (adding.length) {
    setDirty(true);
    if (!session) render();
  }
  status(`Added ${adding.length} to ${name}` + (adding.length < tracks.length ? `, ${tracks.length - adding.length} already there` : ''));
  return adding.length;
}

/** Whether the playlist that is showing, or waiting under a session, holds [url] already. */
function showingHas(url) {
  return ((random ?? away)?.stash.queue ?? queue).some((t) => t.url === url);
}

// --- what the page carries, and what it keeps (W4) -----------------------------------------------
//
// The files are staged beside the engine by `scripts/stage-web-legal.mjs`: the licence table, the
// texts its `web` rows name, and the privacy policy. Fetched when asked for, never before.

/** Says, in the sheet, that something the page should carry is not there -- never a blank sheet. */
function legalMissing(what) {
  const p = document.createElement('p');
  p.textContent = `${what} is not beside this page's engine. Run scripts/stage-web-legal.mjs, or build the engine again.`;
  $('legalbody').replaceChildren(p);
}

/**
 * Points Source code at the exact version this page was built from (the GPL's offer of source), when
 * the staging recorded one; the repository's front page otherwise.
 */
async function pointSourceAtBuild() {
  try {
    const build = await fetch('../vendor/legal/build.json').then((r) => (r.ok ? r.json() : null));
    if (build?.commit) $('open-source').href = `https://github.com/przunk/protracktor/tree/${build.commit}`;
  } catch { /* the front page is still the source */ }
}

async function renderLicences() {
  $('legaltitle').textContent = 'Open-source licences';
  $('legalback').hidden = true;
  const table = await fetch('../vendor/notices/components.tsv').then((r) => (r.ok ? r.text() : null)).catch(() => null);
  if (table === null) { legalMissing('The licence table'); return; }
  const body = $('legalbody');
  body.replaceChildren();
  for (const component of parseNotices(table, 'web')) {
    const row = document.createElement('button');
    row.className = 'component';
    row.textContent = component.name;
    const detail = document.createElement('small');
    detail.textContent = [component.version, component.licence].filter((s) => s && s !== '—').join(' · ');
    row.append(detail);
    row.onclick = () => renderComponent(component);
    body.append(row);
  }
}

/** One component's licence files, as written. Back returns to the list. */
async function renderComponent(component) {
  $('legaltitle').textContent = component.name;
  const back = $('legalback');
  back.hidden = false;
  back.onclick = () => renderLicences();
  const body = $('legalbody');
  body.replaceChildren();
  for (const file of component.files) {
    const text = await fetch(`../vendor/${file}`).then((r) => (r.ok ? r.text() : null)).catch(() => null);
    const pre = document.createElement('pre');
    pre.textContent = text ?? `(${file.slice(file.lastIndexOf('/') + 1)} is missing beside the engine)`;
    body.append(pre);
  }
  body.scrollTop = 0;
}

async function renderPrivacy() {
  $('legaltitle').textContent = 'Privacy policy';
  $('legalback').hidden = true;
  const markdown = await fetch('../vendor/legal/privacy-policy.md').then((r) => (r.ok ? r.text() : null)).catch(() => null);
  if (markdown === null) { legalMissing('The privacy policy'); return; }
  const body = $('legalbody');
  body.replaceChildren();
  let list = null;
  for (const block of privacyBlocks(markdown)) {
    if (block.kind === 'bullet') {
      if (!list) { list = document.createElement('ul'); body.append(list); }
      const li = document.createElement('li');
      li.textContent = block.text;
      list.append(li);
      continue;
    }
    list = null;
    const el = document.createElement(block.kind === 'heading' ? 'h3' : 'p');
    el.textContent = block.text;
    body.append(el);
  }
  body.scrollTop = 0;
}

/**
 * The page's settings, drawn each time they open, behind the gear left of shuffle. **What is in
 * them is what the page already knew and nobody could read**: which decoders this browser's engine
 * carries -- the reason a Spectrum tune plays on the phone and not here -- which archives are
 * indexed, and how much the browser is holding.
 */
async function renderSettings() {
  const list = $('settingsfields');
  list.replaceChildren();
  const row = (label, value) => {
    const dt = document.createElement('dt');
    dt.textContent = label;
    const dd = document.createElement('dd');
    dd.textContent = value;
    list.append(dt, dd);
  };
  row('Decoders in this build', engineFingerprint || 'the engine has not started yet');
  // **Storage in this browser** (W10), the phone's storage section: each download, what it holds,
  // and a way to let go of it -- nothing downloaded here is undeletable, and saying how much there
  // is without saying how to be rid of it is half an answer. Asked before, because each can be
  // fetched again but not in a moment; answered at the press, and said once (C76, C77).
  const heading = document.createElement('dt');
  heading.className = 'storagehead';
  heading.textContent = 'Storage in this browser';
  list.append(heading);
  const INDEX_GONE = 'It can be downloaded again from Browse. Until then, this catalogue cannot be browsed or '
    + 'searched, and its tunes will not play.';
  const sets = [];
  for (const source of archive.sources()) {
    const held = await archive.meta(source);
    sets.push({
      label: `${archive.sourceName(source)} index`,
      held: held?.tracks ? `${held.tracks.toLocaleString()} tunes` : null,
      absent: 'not downloaded — Browse offers it',
      consequence: INDEX_GONE,
      remove: () => archive.forgetIndex(source),
    });
  }
  const lengths = await archive.songLengthsMeta();
  const metadata = await archive.songMetadataMeta();
  const songTunes = (lengths?.tunes ?? 0) + (metadata?.tunes ?? 0);
  sets.push({
    label: 'Song metadata',
    held: songTunes ? `${songTunes.toLocaleString()} tunes` : null,
    absent: 'not downloaded — Browse offers it; until then a SID stops at the length below',
    consequence: 'It can be downloaded again from Browse. Until then tunes show no length, author or '
      + 'year unless the file itself says.',
    remove: async () => { await archive.clearSongLengths(); await archive.clearSongMetadata(); },
  });
  for (const set of sets) {
    const dt = document.createElement('dt');
    dt.textContent = set.label;
    const dd = document.createElement('dd');
    if (set.held) {
      dd.append(`${set.held} · `);
      const remove = document.createElement('button');
      remove.className = 'plain';
      remove.innerHTML = `${iconSvg(ICON.remove)}Delete`;
      remove.onclick = async () => {
        if (!confirm(`Delete the ${set.label}?\n\n${set.consequence}`)) return;
        dd.textContent = 'deleting…';
        await set.remove();
        showNote(`${set.label} deleted`);
        await renderSettings();
      };
      dd.append(remove);
    } else {
      dd.textContent = set.absent;
    }
    list.append(dt, dd);
  }

  const { usage, quota } = await estimate();
  row('Stored here', usage
    ? `${(usage / 1e6).toFixed(1)} MB of ${(quota / 1e9).toFixed(0)} GB this browser offered`
    : 'nothing yet');
}

/** Six seconds, the length of a Material snackbar with an action. */
function showUndo(text) {
  $('snacktext').textContent = text;
  $('snackundo').hidden = false;
  $('snackbar').hidden = false;
  clearTimeout(undoTimer);
  undoTimer = setTimeout(hideUndo, 6000);
}

/**
 * A snackbar with nothing to press, for a moment's confirmation -- "Link copied". **The status
 * line cannot carry these**: it lives in Now Playing, which is usually folded, so what it says
 * about a copy is said to nobody. Two and a half seconds: long enough to read three words, short
 * enough not to sit over the dock.
 */
function showNote(text) {
  $('snacktext').textContent = text;
  $('snackundo').hidden = true;
  $('snackbar').hidden = false;
  clearTimeout(undoTimer);
  undoTimer = setTimeout(hideUndo, 2500);
}

function hideUndo() {
  clearTimeout(undoTimer);
  lastRemoval = null;
  $('snackbar').hidden = true;
}

/**
 * Walks past a Random pick that would not open, and answers whether it did.
 *
 * The index keeps only formats this engine can play, but a file can still be refused, packed, or
 * gone from the server -- and the phone
 * has always walked past those (`skipFailedRandomPick`), because a pick nobody chose should not stop
 * the music. **The pick leaves the record**: the record is what has played, and this did not.
 *
 * Bounded, as on the phone: eight in a row and it stops and says so, rather than spinning through an
 * index that has somehow filled with things it cannot open.
 */
function skipFailedPick(reason) {
  const r = random;
  if (!r) return false;
  r.failures = (r.failures ?? 0) + 1;
  if (r.failures > RANDOM_FAILURES) {
    r.failures = 0;
    status('Several picks in a row would not open. Stopping here.');
    return false;
  }
  const failed = queue.splice(index, 1)[0];
  index--;
  rowState = index >= 0 ? 'selected' : null;
  render();
  status(`skipped ${failed?.name ?? 'a pick'} — ${reason}`);
  $('error').textContent = '';
  rollRandom();
  return true;
}

/**
 * Drops a row from the record. Not a queue edit -- pruning what you are looking at before keeping
 * the rest. The one playing may go and goes on playing; the cursor row then says "selected" rather
 * than claim a tune that is no longer in the list.
 */
function removeRandomAt(at) {
  if (!random || at < 0 || at >= queue.length) return;
  const wasPlaying = at === index;
  queue.splice(at, 1);
  if (at <= index) index--;
  if (wasPlaying) rowState = 'selected';
  render();
}

/**
 * Writes one play into the history. Never fatal: a page that cannot keep a history still plays.
 *
 * A tune the phone handed over as bytes cannot be played again after a reload -- the bytes are
 * never kept (`saveQueue` says why). It is recorded anyway, so "what was that" still has an answer,
 * and marked, so History does not offer a replay it cannot deliver.
 */
function recordPlay(entry, fields) {
  if (!entry?.url) return;
  // A play History itself started leaves History as it is (A56, `rules.recordsPlay`).
  if (!recordsPlay({ walkingResults: away !== null, fromHistory: away?.kind === 'history' })) return;
  played.record({
    url: entry.url,
    name: fields.title || entry.name,
    meta: entry.meta ?? sourceOf(entry.url),
    file: entry.file ?? entry.name,
    replayable: !entry.data && !entry.local && /^https?:\/\//.test(entry.url),
  }).catch(() => {});
}

/**
 * Playing from a Browse list or from History: **transient, like Random, and for the same reason**
 * -- it writes into no playlist (`docs/BACKLOG.md` A33). Without it, searching and pressing one
 * tune replaces the playlist with every result on the screen. The phone has never done that: its
 * results become the queue while you are in them and the playlist is left alone.
 */
let away = null;

function openAway(tracks, at, kind = 'history') {
  if (!tracks.length) return;
  // A change the playlist was still waiting to save is its own; written before the swap.
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null; saveQueue(); }
  const stash = random?.stash ?? away?.stash ?? { queue, index, history, order, name: $('playlistname').textContent };
  // **The dice waits underneath** (`docs/BACKLOG.md` A41). Playing a tune from an author's folder
  // does not end the session: the record and the cursor are kept, and the way back is the
  // heading's button. A session opened from another session inherits whatever was already waiting.
  const dice = random ? { session: random, queue, index, history, order } : away?.dice ?? null;
  const author = dice ? (away?.author ?? browsePath[2] ?? '') : null;
  random = null;
  loading?.abort();
  loading = null;
  away = { stash, kind, dice, author };
  queue = tracks.slice();
  index = Math.min(Math.max(at, 0), queue.length - 1);
  history = [index];
  order = [];
  if (shuffle) reshuffle(index);
  rowState = 'selected';
  showSessionView(kind);
  render();
  // Called inside the click, so the `start()` inside it may make sound.
  playAt(index);
}

/** Stops what is playing on the way out of a session: nothing it chose should outlive it. */
function stopForLeaving() {
  loading?.abort();
  loading = null;
  clearTimeout(openWatchdog);
  node?.port.postMessage({ type: 'close' });
  delete $('seek').dataset.opened;
  finished = false;
}

/** The playlist exactly where it was left -- nothing ever wrote to it. */
function restoreStash(stash) {
  ({ queue, index, history, order } = stash);
  rowState = 'selected';
  showSessionView(null);
  $('playlistname').textContent = stash.name;
  render();
  setPlaying(false);
  const entry = queue[index];
  $('title').textContent = entry?.name ?? 'Nothing playing';
  $('sub').textContent = entry ? 'press play' : '—';
  nameTheTab(entry);
}

/**
 * Leaves Random: **playback stops**, the record goes -- what played is in the history -- and the
 * playlist is exactly where it was left.
 */
function endRandom() {
  if (!random) return;
  const { stash } = random;
  random = null;
  stopForLeaving();
  restoreStash(stash);
}

function endAway() {
  if (!away) return;
  const { stash } = away;
  away = null;
  stopForLeaving();
  restoreStash(stash);
}

/** The heading's way back, whichever session it is heading. */
function endSession() { if (random) endRandom(); else endAway(); }

/**
 * Back to the dice that was waiting (`docs/BACKLOG.md` A41).
 *
 * **Paused, on the pick it was on**, rather than starting it: coming back from a digression
 * should not put music on unasked. Play resumes that tune; next rolls. What played during the
 * digression is in the history, as everything played here is.
 */
function resumeDice() {
  const dice = away?.dice;
  if (!dice) return;
  away = null;
  stopForLeaving();
  random = dice.session;
  ({ queue, index, history, order } = dice);
  rowState = 'selected';
  showSessionView('random');
  render();
  setPlaying(false);
  const entry = queue[index];
  $('title').textContent = entry?.name ?? 'Nothing playing';
  $('sub').textContent = entry ? 'press play' : '—';
  nameTheTab(entry);
  status('Back to the dice — press play for the tune it was on, or next for another.');
}

/** A queue arrived from elsewhere: nothing to restore, the new queue wins. */
function dropSession() {
  const session = random ?? away;
  if (!session) return;
  random = null;
  away = null;
  showSessionView(null);
  $('playlistname').textContent = session.stash.name;
}


/**
 * What `next` should play, or null at the end.
 *
 * **The rule comes from `rules.js`, which `docs/rules/queue-cases.tsv` checks against the phone's.**
 * What is left here is the two things that cannot be shared: the shuffled order, whose permutation
 * differs between the two runtimes by construction, and stepping over rows that stayed on the phone,
 * which only this side has.
 */
function afterCurrent() {
  // Random answers with the next row or a roll, and ignores repeat and shuffle: repeat-one is checked
  // where a tune *ends*, not here, and shuffle reorders a playlist the dice does not play from.
  if (random) return randomNext({ length: queue.length, at: index });
  if (repeat === 'one' && index >= 0) return index;
  if (shuffle) {
    const at = order.indexOf(index);
    if (at >= 0 && at + 1 < order.length) return order[at + 1];
    if (repeat === 'all') { reshuffle(null); return order[0] ?? null; }
    return null;
  }
  const candidate = nextIndex({ tracks: queue.length, at: index, repeat });
  if (candidate == null) return null;
  const ahead = seek(candidate, 1);
  return ahead ?? (repeat === 'all' ? seek(0, 1) : null);
}

/** What `previous` should play, or null. */
function beforeCurrent() {
  if (random) return randomPrevious({ at: index });
  if (shuffle) {
    // The history holds what was really played; the cursor is its end because the page only ever
    // walks backwards from now.
    const at = history.lastIndexOf(index);
    return at > 0 ? history[at - 1] : null;
  }
  const candidate = previousIndex({ tracks: queue.length, at: index, repeat });
  if (candidate == null) return null;
  const back = seek(candidate, -1);
  return back ?? (repeat === 'all' && queue.length ? seek(queue.length - 1, -1) : null);
}

/**
 * A queue arrives.
 *
 * **It does not start playing.** Music beginning on a page nobody has touched is startling: the
 * press that sent the queue happened on the phone, not here. The list appears, the dock names what
 * is first, and the next press is the reader's.
 *
 * @param at which track to select, if the sender knows. Selected, not played.
 */
function setQueue(urls, at = 0) {
  // A queue arriving -- a playlist switched to, the phone's handoff, a tune played from Browse --
  // replaces whatever was showing, and that includes the dice's record.
  dropSession();
  // Another list arriving makes an undo for the last one meaningless.
  hideUndo();
  delete $('seek').dataset.opened;
  // Selected, not playing: the queue points here and nothing has started. A list with no mark at
  // all leaves the dock naming a track the list does not admit to.
  rowState = 'selected';
  // Entries already built keep their names; bare strings become entries here. The paste box gives
  // strings, a link gives entries with the phone's own titles.
  queue = urls.map((u) => (typeof u === 'string' ? entryFor(u) : u));
  index = queue.length ? Math.min(Math.max(at, 0), queue.length - 1) : -1;
  // The phone's place in the list may be one of its own files. Selecting it would leave the dock
  // naming something the page can never start, so the mark moves to the first row that can play.
  if (index >= 0 && !playable(index)) index = seek(index, 1) ?? seek(0, 1) ?? -1;
  history = index >= 0 ? [index] : [];
  if (shuffle) reshuffle(index >= 0 ? index : null);
  render();
  setPlaying(false);
  remember();
  const entry = queue[index];
  if (entry) {
    $('title').textContent = entry.name;
    $('sub').textContent = 'press play';
    nameTheTab(entry);
  }
}

/**
 * The two interruptions and the one panel.
 *
 * Pair and Paste are dialogs over the page; Now Playing is a panel under the queue, because it is
 * something you read alongside the list rather than instead of it.
 */
function showPanel(which) {
  if (!$('unsaved').hidden) answerUnsaved(false);
  $('browse').hidden = which !== 'browse';
  // Browse stands where the list stands, with the dock still under it.
  document.querySelector('main').hidden = which === 'browse';
  $('tab-browse').setAttribute('aria-pressed', String(which === 'browse'));
  $('pair').hidden = which !== 'pair';
  $('paste').hidden = which !== 'paste';
  $('playlists').hidden = which !== 'playlists';
  $('addto').hidden = which !== 'addto';
  $('settings').hidden = which !== 'settings';
  $('legal').hidden = which !== 'legal';
  $('nowplaying').hidden = which !== 'nowplaying';
  $('expand').style.transform = which === 'nowplaying' ? 'rotate(180deg)' : '';
  $('tab-pair').setAttribute('aria-pressed', String(which === 'pair'));
  $('tab-paste').setAttribute('aria-pressed', String(which === 'paste'));
  if (which === 'paste') $('urls').focus();
  // Back to the list: a download made meanwhile may have changed what the empty list offers.
  if (!which) renderEmpty();
}
/**
 * The playlist sheet: what there is, which one is showing, and what may be done to it.
 *
 * Drawn fresh each time it opens rather than kept in step, because it is a dialog somebody opens
 * for a moment and the alternative is a second copy of the truth.
 */
async function renderPlaylists() {
  const list = $('playlistlist');
  list.replaceChildren();
  const all = await playlists.all();
  // **"From the phone" is always a choice**, stored or not. It is written only once the phone sends
  // something, so a fresh browser -- a phone opening a shared tune -- drew an empty sheet under a
  // page whose own heading has just named that list.
  if (!all.some((p) => p.id === PHONE)) all.unshift({ id: PHONE, name: 'From the phone', tracks: [] });
  for (const playlist of all) {
    const li = document.createElement('li');
    li.setAttribute('aria-current', String(playlist.id === activePlaylist));

    // The size first, as the phone has it: which of these has anything in it (BACKLOG A21).
    const count = document.createElement('div');
    count.className = 'pcount';
    count.textContent = String(playlist.tracks?.length ?? 0);
    const name = document.createElement('div');
    name.className = 'pname';
    name.textContent = playlist.name;
    li.append(count, name);

    // Everything that can be done *to* a playlist, behind the same three dots a track row uses.
    // "From the phone" has none: it is not something anybody made, and it is not renamed or deleted.
    if (playlist.id !== PHONE) {
      const more = document.createElement('button');
      more.className = 'pmenu';
      more.innerHTML = iconSvg(ICON.more);
      more.setAttribute('aria-label', `More for ${playlist.name}`);
      more.onclick = (event) => { event.stopPropagation(); openPlaylistMenu(playlist, more); };
      li.append(more);
    }

    li.onclick = () => choosePlaylist(playlist.id);
    list.append(li);
  }

  const { usage, quota } = await estimate();
  $('storageline').textContent = usage
    ? `This browser is holding ${(usage / 1e6).toFixed(1)} MB of ${(quota / 1e9).toFixed(0)} GB it offered.`
    : 'Nothing stored yet.';
}

/** A playlist's own menu: the phone's Rename and Delete, with their icons. */
function openPlaylistMenu(playlist, anchor) {
  showMenu([
    ['Rename', () => renamePlaylist(playlist), true, ICON.rename],
    ['Delete', () => deletePlaylist(playlist), true, ICON.remove],
  ], anchor);
}

async function renamePlaylist(playlist) {
  const name = prompt('Call it what?', playlist.name)?.trim();
  if (!name || name === playlist.name) return;
  await playlists.save({ ...(await playlists.get(playlist.id)), name });
  if (activePlaylist === playlist.id) {
    // Inside Random or History the chip names the session; the name waits in the stash for later.
    const session = random ?? away;
    if (session) session.stash.name = name; else $('playlistname').textContent = name;
  }
  renderPlaylists();
}

/**
 * Deletes a playlist -- **asking first**, where removing a track does not. The phone makes the same
 * distinction on purpose: undoing a deleted playlist from a snackbar that lives six seconds is not
 * an escape route, and asking is.
 */
async function deletePlaylist(playlist) {
  if (!confirm(`Delete “${playlist.name}”? Its ${playlist.tracks?.length ?? 0} tracks go with it.`)) return;
  await playlists.remove(playlist.id);
  // Its unsaved edits went with it; there is nothing left to ask about.
  if (activePlaylist === playlist.id) { setDirty(false); choosePlaylist(PHONE); }
  renderPlaylists();
}

/**
 * The sheet's answer to a playlist being chosen. Out of Random or History first -- playback stops,
 * as leaving by the heading's button does -- and then the playlist chosen, not the one left behind.
 */
async function choosePlaylist(id) {
  endSession();
  if (!(await settleUnsaved())) return;
  switchTo(id);
  showPanel(null);
}

/** Loads a playlist into the queue. Selected, not started — a switch is not a press of play. */
async function switchTo(id) {
  setDirty(false);
  const playlist = await playlists.get(id);
  activePlaylist = id;
  $('playlistname').textContent = playlist?.name ?? (id === PHONE ? 'From the phone' : 'Playlist');
  setQueue(playlist?.tracks ?? [], playlist?.index ?? 0);
  await settings.set('active', id);
}

/*
  Browsing an archive.

  **Four levels and one way through them**, so the state is a stack rather than a router: `[]` is
  the catalogue, `[format]` its authors, `[format, author]` its tracks. Back pops. The phone's
  `BrowseNavigation.kt` is the same three fields and the same reason -- what a person walks down
  they expect to walk back up.
*/
let browsePath = [];
let searchTimer = null;
/** Bumped by every search and every redraw, so a slow answer cannot land on a newer screen. */
let searchAsked = 0;
const SEARCH_LIMIT = 300;

async function renderBrowse() {
  // Whatever a search still running would draw is no longer wanted.
  searchAsked++;
  const list = $('browselist');
  const note = $('browsenote');
  list.replaceChildren();
  // The dice's own heading, in the screen a digression happens in.
  $('browsedigression').hidden = !away?.dice;
  if (away?.dice) {
    $('browsedigression').innerHTML = iconSvg(ICON.detour)
      + `<div class="text"><div class="title">Browsing author</div><div class="meta"></div></div>`;
    $('browsedigression').querySelector('.meta').textContent = away.author;
  }
  $('browseback').hidden = browsePath.length === 0;
  note.textContent = '';
  // No search inside a digression: it is one author's folder and the way out is Back (the phone
  // shows no field here either). Close goes with it — a second way out, to somewhere else, beside
  // the one that leads back to the dice.
  $('browsesearch').hidden = !!away?.dice;
  $('browseclose').hidden = !!away?.dice;

  // **Declared before anything uses it**, because the "From the phone" branch below calls it: a
  // `const` read before its declaration throws when Browse is opened on the phone's list with an
  // index downloaded, and jsdom does not reach that state (`docs/STATUS.md` C37).
  // An icon for the rows that do something -- Random, History, a download -- as the phone's Browse
  // rows have one; rows that are data (a format, an author, a tune) are drawn as the phone draws them.
  const row = (name, count, onclick, icon = null) => {
    const li = document.createElement('li');
    const label = document.createElement('div');
    label.className = 'bname';
    label.textContent = name;
    const number = document.createElement('div');
    number.className = 'bcount';
    number.textContent = count == null ? '' : count.toLocaleString();
    li.append(label, number);
    if (icon) li.insertAdjacentHTML('afterbegin', iconSvg(icon));
    li.onclick = onclick;
    list.append(li);
  };
  // A row of the root: its name over one line of what it is, as the phone's DomainRow.
  const domainRow = (name, detail, onclick, icon) => {
    const li = document.createElement('li');
    li.className = 'bdomain';
    const text = document.createElement('div');
    text.className = 'btext';
    const label = document.createElement('div');
    label.className = 'bname';
    label.textContent = name;
    const meta = document.createElement('div');
    meta.className = 'bmeta';
    meta.textContent = detail;
    text.append(label, meta);
    li.append(text);
    li.insertAdjacentHTML('afterbegin', iconSvg(icon));
    li.onclick = onclick;
    list.append(li);
  };

  if (browsePath.length === 0) {
    // **The phone's Browse** (W8): where the music is, then the two ways through it that are not
    // places -- the dice and the record of what played -- then search. No local folders: a browser
    // cannot list a phone's storage, and the page's ways in for those are the pairing code and
    // pasted links.
    $('browsetitle').textContent = 'Browse';
    domainRow('Online catalogues', 'Browse the archives — indexed once, then browsable offline', async () => {
      browsePath = ['catalogues'];
      await renderBrowse();
    }, ICON.cloud);
    domainRow('Random', 'Play something from the indexed catalogues', enterRandomFromBrowse, ICON.dice);
    domainRow('History', 'Tunes you have played, most recent first', openHistory, ICON.history);
    domainRow('Search', 'Across the catalogues this browser holds', () => $('browsesearch').focus(), ICON.search);
    return;
  }

  if (browsePath[0] === 'catalogues') {
    await renderCatalogues(list, note);
    return;
  }

  if (browsePath[0] === 'history') {
    $('browsetitle').textContent = 'History';
    const rows = await played.recent();
    if (!rows.length) {
      note.textContent = 'Nothing played yet. What the page plays is kept here — the last 500 tunes, one row each.';
      return;
    }
    note.textContent = `${rows.length} tune${rows.length === 1 ? '' : 's'}, most recent first.`;
    const tracks = rows.filter((r) => r.replayable)
      .map(({ url, name, meta, file }) => ({ url, name, meta, file }));
    for (const r of rows) {
      const at = tracks.findIndex((t) => t.url === r.url);
      if (at >= 0) {
        trackRow(list, tracks[at], [r.meta?.replace('Modland/', ''), r.playCount > 1 ? `played ${r.playCount} times` : '']
          .filter(Boolean).join(' · '), () => playFromBrowse(tracks, at, 'history'));
        continue;
      }
      row(r.name, r.playCount > 1 ? `×${r.playCount}` : '', null);
      const li = list.lastElementChild;
      li.dataset.url = r.url;
      li.classList.add('gone');
      li.title = "Played from the phone's copy — the page does not keep those";
    }
    markPlayingIn(list);
    row('Clear the history', null, async () => { await played.clear(); await renderBrowse(); }, ICON.remove);
    return;
  }

  // `[archive, group, author]`: Modland's groups are formats, ASMA's are its sections.
  const source = browsePath[0];
  if (browsePath.length === 1) {
    $('browsetitle').textContent = archive.sourceName(source);
    for (const { name, count } of await archive.formats(source)) {
      row(name, count, async () => { browsePath = [source, name]; await renderBrowse(); });
    }
    return;
  }

  if (browsePath.length === 2) {
    const format = browsePath[1];
    $('browsetitle').textContent = format;
    for (const { name, count } of await archive.authors(format, source)) {
      row(name || '(no author)', count, async () => {
        browsePath = [source, format, name];
        await renderBrowse();
      });
    }
    return;
  }

  const [, format, author] = browsePath;
  $('browsetitle').textContent = `${format} / ${author || '(no author)'}`;
  const tracks = await archive.tracksIn(format, author, source);
  tracks.forEach((track, i) => {
    // **The whole author is what next and previous walk**, which is what the phone does: a person
    // who opened a folder and pressed a tune meant that folder, not that one file.
    trackRow(list, track, `${Math.round(track.size / 1024).toLocaleString()} KB`, () => playFromBrowse(tracks, i), { folder: false });
  });
  markPlayingIn(list);
}

/**
 * One tune in a Browse list: pressed, it plays; beside it, **Add** and the tune's menu -- the phone's
 * `BrowseTrackRow`, where finding out what something is comes before deciding to keep it.
 *
 * Add goes to the playlist that is showing (or waiting under a session) and waits for Save there.
 * "From the phone" is never written to, so with it showing Add asks which playlist instead.
 */
function trackRow(list, track, meta, onplay, { folder = true } = {}) {
  const li = document.createElement('li');
  li.className = 'btrack';
  li.dataset.url = track.url;
  const text = document.createElement('div');
  text.className = 'btext';
  const name = document.createElement('div');
  name.className = 'bname';
  name.textContent = track.name;
  text.append(name);
  if (meta) {
    const where = document.createElement('div');
    where.className = 'bmeta';
    where.textContent = meta;
    text.append(where);
  }
  const add = document.createElement('button');
  add.className = 'badd';
  const paintAdd = () => {
    const there = activePlaylist !== PHONE && showingHas(track.url);
    add.disabled = there;
    add.innerHTML = iconSvg(there ? ICON.check : ICON.playlistAdd);
    add.append(there ? 'Added' : 'Add');
    const into = (random ?? away)?.stash.name ?? $('playlistname').textContent;
    add.title = there ? `Already in ${into}`
      : activePlaylist === PHONE ? 'Add to one of your playlists' : `Add to ${into}`;
  };
  paintAdd();
  li.repaintAdd = paintAdd;
  add.onclick = (event) => {
    event.stopPropagation();
    if (activePlaylist === PHONE) { openAddTo([plain(track)], { browse: true }); return; }
    addToShowing([track]);
    paintAdd();
  };
  const more = document.createElement('button');
  more.className = 'bmore';
  more.innerHTML = iconSvg(ICON.more);
  more.setAttribute('aria-label', `More for ${track.name}`);
  more.onclick = (event) => {
    event.stopPropagation();
    openBrowseMenu(track, more, folder);
  };
  li.append(text, add, more);
  li.onclick = onplay;
  list.append(li);
}

/** A Browse tune's menu: the phone's, less sharing, which a page does as saving and copying. */
function openBrowseMenu(track, anchor, folder) {
  const items = [
    ['Add to another playlist', () => openAddTo([plain(track)], { browse: true }), true, ICON.playlistAdd],
    ['Information', () => informAbout(track), true, ICON.info],
  ];
  // Where the tune lives, and what else is there. Pointless from inside that very folder.
  if (folder && authorFolderOf(track)) {
    items.push(['More from this author', () => showAuthorFolder(track), true, ICON.folder]);
  }
  items.push(['Save the file', () => saveFile(track), true, ICON.save]);
  items.push(['Copy a link', () => copyLink(track), true, ICON.link]);
  items.push(['Share with Protracktor', () => sendToWeb(track), canSendToWeb(track), ICON.web]);
  showMenu(items, anchor);
}

/** Browse → History. */
async function openHistory() {
  browsePath = ['history'];
  await renderBrowse();
}

/**
 * Plays a tune found by browsing, **without touching the playlist**. The list it came from is
 * what next and previous walk while you are in it; Browse stays open so the next tune
 * can be tried, and Add is how anything gets kept. The phone's `playFromResults`.
 */
function playFromBrowse(tracks, at, kind = 'browse') {
  openAway(tracks.map(plain), at, kind);
}

/**
 * Why a stored index should be downloaded again, or nothing if it need not be.
 *
 * An index is filtered by the list and the decoders it was built with, so one built by another set
 * holds the wrong rows and looks current -- the phone learnt this by losing 60,572 C64 tunes. One
 * built before the page filtered at all has no `total`, and holds a third this browser cannot open.
 */
async function staleSentence(held) {
  if (!held?.tracks) return '';
  const table = await formatsReady().catch(() => null);
  // **Only an index from before step 0 can be stale this way.** One written since holds every row
  // the archive lists, so a format added later is answered from what is already stored — there is
  // nothing to fetch and nothing to say.
  const partial = !held.complete;
  const moved = held.total === undefined
    || (table && engineFingerprint && held.fingerprint !== archive.indexFingerprint(engineFingerprint, table));
  return partial && moved
    ? 'This index was built for a different set of formats than this page now plays, and was built '
      + 'before indexes kept everything. Downloading it again (5.76 MB) is the last time that will '
      + 'be needed: what it stores then no longer depends on which formats this build can open.'
    : '';
}

/**
 * How much of Modland this browser holds, and why the rest is not here, in one sentence.
 *
 * Said out loud in Browse and in search, which is the condition on which leaving anything out of
 * the index is acceptable at all. Silent for an index that predates the counts.
 */
function holding({ tracks, total, complete } = {}) {
  if (!total || !tracks) return '';
  const rest = total - tracks;
  if (rest <= 0) return `This browser holds all ${total.toLocaleString()} of Modland's tunes.`;
  // **"Holds" and "offers" became different numbers** at `docs/ROADMAP_FORMATS.md` step 0: the
  // index keeps the whole archive and the page offers what it can open. Said that way round
  // because it is the useful half — a format arriving later needs no download, and the sentence
  // should not imply one.
  const kept = complete
    ? `This browser holds all ${total.toLocaleString()} of Modland's tunes and can play `
      + `${tracks.toLocaleString()} of them. `
    : `This browser holds ${tracks.toLocaleString()} of Modland's ${total.toLocaleString()} tunes. `;
  return `${kept}The other ${rest.toLocaleString()} are in formats it cannot open`
    + (complete ? ' yet — they are already here if it learns one.' : '.');
}

async function downloadIndex() {
  const note = $('browsenote');
  await downloadStarted('modland');
  try {
    // Which formats to keep depends on which decoders this engine has, and only the engine can say.
    // Started here if nothing has played yet -- this is a click, so a browser allows the audio.
    if (!engineReady) {
      note.textContent = 'starting the engine, to ask which formats this browser can play…';
      await start();
      await whenEngineReady();
    }
    const table = await formatsReady();
    const result = await archive.downloadModland({
      fingerprint: archive.indexFingerprint(engineFingerprint, table),
      // **The verdict, not the filter.** Every row the archive lists is stored; this decides which
      // of them this build offers (`docs/ROADMAP_FORMATS.md` step 0), and a build that learns a
      // format re-decides the stored rows instead of fetching them again.
      isPlayable: archive.playable(table, absentHere()),
      onProgress: (p) => {
        note.textContent = p.stage === 'storing'
          ? `storing ${p.done.toLocaleString()} of ${p.total.toLocaleString()}…`
          : `${p.stage}…`;
      },
    });
    await downloadEnded('modland');
    note.textContent = `${result.formats} formats. ${holding(result)}`;
  } catch (e) {
    await downloadEnded('modland');
    note.textContent = `the index could not be downloaded: ${e.message}`;
  }
}

/** Browse → Download the ASMA list: 0.85 MB of the archive's own directory (`archive.downloadAsma`). */
async function downloadAsmaIndex() {
  const note = $('browsenote');
  await downloadStarted('asma');
  try {
    if (!engineReady) {
      note.textContent = 'starting the engine, to ask which formats this browser can play…';
      await start();
      await whenEngineReady();
    }
    const table = await formatsReady();
    const result = await archive.downloadAsma({
      fingerprint: archive.indexFingerprint(engineFingerprint, table),
      isPlayable: archive.playable(table, absentHere()),
      onProgress: (p) => {
        note.textContent = p.stage === 'storing'
          ? `storing ${p.done.toLocaleString()} of ${p.total.toLocaleString()}…`
          : `${p.stage}…`;
      },
    });
    await downloadEnded('asma');
    note.textContent = `ASMA: ${result.tracks.toLocaleString()} tunes in ${result.formats} sections.`;
  } catch (e) {
    await downloadEnded('asma');
    note.textContent = `the ASMA list could not be downloaded: ${e.message}`;
  }
}

/**
 * Browse → SID song lengths: HVSC's hand-timed durations (`archive.downloadSongLengths`).
 *
 * **The one download here that is not a list of tunes.** A SID is a program and has no length in
 * it; HVSC's is a person's stopwatch, 61,157 of them, and without it the page played every SID
 * until the fallback in Settings stopped it (`docs/STATUS.md` C56).
 *
 * No engine and no format table, unlike the two above: this is keyed on the MD5 of a file, so
 * nothing about it depends on what this build can decode.
 */
/**
 * Downloads one of the databases the page takes whole, saying how far it has got. [what] names it
 * in the sentence that ends it, [run] is the archive's download.
 */
async function downloadDatabase(what, run) {
  const note = $('browsenote');
  try {
    const result = await run({
      onProgress: (p) => {
        if (p.stage === 'fetching' && p.total) {
          note.textContent = `fetching ${(p.done / 1e6).toFixed(1)} of ${(p.total / 1e6).toFixed(1)} MB…`;
        } else if (p.stage === 'storing') {
          note.textContent = `storing ${p.done} of ${p.total}…`;
        } else {
          note.textContent = `${p.stage}…`;
        }
      },
    });
    note.textContent = `${what}: ${result.tunes.toLocaleString()} tunes.`;
    return true;
  } catch (e) {
    note.textContent = `the ${what.toLowerCase()} could not be downloaded: ${e.message}`;
    return false;
  }
}

/**
 * **Song metadata, one press** (the phone's A52 D1): everything the page can use of what a file
 * cannot say about itself -- HVSC's SID lengths and songdb's author, publisher, album and year --
 * under one row and one tick. Each part is still its own download underneath, so one that fails
 * says which.
 */
async function downloadSongMetadata() {
  await downloadStarted(SONG_METADATA);
  const lengths = await downloadDatabase('SID song lengths', archive.downloadSongLengths);
  const said = $('browsenote').textContent;
  const metadata = await downloadDatabase('Song metadata', archive.downloadSongMetadata);
  await downloadEnded(SONG_METADATA);
  if (lengths && metadata) $('browsenote').textContent = `${said} ${$('browsenote').textContent}`;
}

// --- the catalogues, as the phone draws them (W8) ------------------------------------------------

/** The key the grouped "Song metadata" download runs under, beside the catalogues' own names. */
const SONG_METADATA = 'song-metadata';

/** What is downloading now, by key: a catalogue's name, or `SONG_METADATA`. */
const downloading = new Set();

/** Redraws the catalogues if they are on screen; the list stays, only its rows change. */
async function redrawCatalogues() {
  if (!$('browse').hidden && browsePath[0] === 'catalogues') await renderBrowse();
}
async function downloadStarted(key) { downloading.add(key); await redrawCatalogues(); }
async function downloadEnded(key) { downloading.delete(key); await redrawCatalogues(); }

/**
 * Whether a downloadable set is here, as the row's first mark: a tick in a disc, in the accent
 * colour, or a dimmed cloud. Shape, colour and the line under it all say the same thing, so none of
 * them carries it alone -- the phone's `HeldIcon`.
 */
function heldMark(held) {
  const span = document.createElement('span');
  span.className = held ? 'bheld yes' : 'bheld';
  span.innerHTML = iconSvg(held ? ICON.downloaded : ICON.cloud);
  return span;
}

/**
 * The one button of a downloadable row, drawn as what it will do: **Download** when the set is not
 * here, **Refresh** when it is -- two marks, because one arrow for both made one button look like
 * two offers merged. While it runs, a spinner and what it is doing, in the button's place.
 */
function downloadButton(key, held, label, onclick) {
  if (downloading.has(key)) {
    const busy = document.createElement('span');
    busy.className = 'bbusy';
    busy.innerHTML = '<span class="spinner" aria-hidden="true"></span>indexing…';
    return busy;
  }
  const button = document.createElement('button');
  button.className = 'bdl';
  button.innerHTML = iconSvg(held ? ICON.refresh : ICON.download);
  button.setAttribute('aria-label', label);
  button.title = label;
  button.onclick = (event) => { event.stopPropagation(); onclick(); };
  return button;
}

/** A row that may be downloaded: the held mark, a name over its detail, and its one button. */
function heldRow(list, { name, detail, warning = '', held, key, label, ondownload, onopen = null }) {
  const li = document.createElement('li');
  li.className = 'bheldrow';
  const text = document.createElement('div');
  text.className = 'btext';
  const title = document.createElement('div');
  title.className = 'bname';
  title.textContent = name;
  text.append(title);
  // **Nothing drawn before it is known** (the phone's C74): a detail of null is "not read yet".
  if (detail != null) {
    const meta = document.createElement('div');
    meta.className = 'bmeta';
    meta.textContent = detail;
    text.append(meta);
  }
  if (warning) {
    const warn = document.createElement('div');
    warn.className = 'bmeta bwarn';
    warn.textContent = warning;
    text.append(warn);
  }
  li.append(heldMark(held), text, downloadButton(key, held, label, ondownload));
  // Only openable once it is here: an empty catalogue opened teaches nothing about why.
  if (onopen) li.onclick = onopen; else li.classList.add('closed');
  list.append(li);
  return li;
}

async function renderCatalogues(list, note) {
  $('browsetitle').textContent = 'Online catalogues';
  // Everything is read first, then drawn at once: no row appears and then changes its mind.
  const held = {};
  for (const source of archive.sources()) held[source] = await archive.meta(source);
  const lengths = await archive.songLengthsMeta();
  const metadata = await archive.songMetadataMeta();
  if (browsePath[0] !== 'catalogues') return;
  list.replaceChildren();

  const sizes = { modland: '5.76 MB', asma: '0.85 MB' };
  const about = {
    modland: 'Modland is half a million tunes; its index is kept in this browser, and browsing is then offline.',
    asma: 'ASMA is 6,335 Atari 8-bit tunes; each is fetched from ASMA when it plays.',
  };
  note.textContent = [
    held.modland?.tracks ? holding(held.modland) : about.modland,
    held.asma?.tracks ? '' : about.asma,
  ].filter(Boolean).join(' ');

  for (const source of archive.sources()) {
    const here = held[source];
    const name = archive.sourceName(source);
    heldRow(list, {
      name,
      detail: here?.tracks
        ? `${here.tracks.toLocaleString()} tunes`
        : `Not indexed yet — tap the arrow to download its index (${sizes[source] ?? 'a download'})`,
      warning: source === 'modland' ? await staleSentence(here) : '',
      held: !!here?.tracks,
      key: source,
      label: here?.tracks ? `Update the index for ${name}` : `Download the index for ${name}`,
      ondownload: source === 'asma' ? downloadAsmaIndex : downloadIndex,
      onopen: here?.tracks ? async () => { browsePath = [source]; await renderBrowse(); } : null,
    });
  }

  // **One row for what files cannot say about themselves** (the phone's A52 D1): the SID lengths
  // and songdb's metadata, one tick for both. No replay routines row: neither sc68's nor UADE's can
  // reach a browser (`docs/PLAN_WEB_PARITY.md`).
  const complete = !!(lengths?.tunes && metadata?.tunes);
  const group = heldRow(list, {
    name: 'Song metadata',
    detail: complete
      ? `${(lengths.tunes + metadata.tunes).toLocaleString()} tunes`
      : 'Not downloaded — no lengths, authors or years (5.2 + 14.8 MB)',
    held: complete,
    key: SONG_METADATA,
    label: complete ? 'Update the song metadata' : 'Download the song metadata',
    ondownload: downloadSongMetadata,
  });
  group.classList.add('groupstart');
}

/**
 * Searching the index.
 *
 * **Tunes, never folders.** A name typed may be a tune's or an author's, as on the phone, whose
 * search matches both and answers
 * with tracks either way: an author found here brings their tunes, not a row to walk into.
 * Debounced, and it says how long it took: reading 1,663 title shards is a real amount of work and
 * a number is more honest than a spinner.
 */
async function runSearch(query) {
  const list = $('browselist');
  const note = $('browsenote');
  const asked = ++searchAsked;
  if (query.trim().length < 2) { browsePath = []; await renderBrowse(); return; }
  // Every archive held, one list of results: somebody typing a name does not know which has it.
  const held = [];
  for (const source of archive.sources()) {
    const known = await archive.meta(source);
    if (known?.tracks) held.push({ source, ...known });
  }
  if (!held.length) { note.textContent = 'Download an index first.'; return; }

  note.textContent = 'searching…';
  const started = performance.now();
  const answers = await Promise.all(held.map(({ source }) => Promise.all([
    archive.searchAuthors(query, 100, source), archive.searchTitles(query, 200, source),
  ])));
  const people = answers.flatMap(([authors]) => authors);
  const found = answers.flatMap(([, { hits }]) => hits);
  const seen = new Set(found.map((t) => t.url));
  let full = answers.some(([, { capped }]) => capped);
  for (const { source, format, author } of people) {
    if (found.length >= SEARCH_LIMIT) { full = true; break; }
    for (const track of await archive.tracksIn(format, author, source)) {
      if (seen.has(track.url)) continue;
      seen.add(track.url);
      found.push(track);
    }
  }
  if (found.length > SEARCH_LIMIT) { found.length = SEARCH_LIMIT; full = true; }
  // A slower search for "zo" must not draw over the one for "zool" that finished first.
  if (asked !== searchAsked) return;
  $('browsetitle').textContent = 'Search';
  $('browseback').hidden = false;
  list.replaceChildren();
  found.forEach((track, i) => {
    trackRow(list, track, track.meta.replace('Modland/', ''), () => playFromBrowse(found, i));
  });
  markPlayingIn(list);

  const ms = Math.round(performance.now() - started);
  // Where it looked, every time, so "nothing matched" cannot be read as "Modland has no such tune"
  // when the tune is in a format this browser does not index.
  const tunes = held.reduce((sum, { tracks }) => sum + tracks, 0);
  const among = `among the ${tunes.toLocaleString()} tunes this browser can play`
    + ` (${held.map(({ source }) => archive.sourceName(source)).join(' and ')})`;
  note.textContent = found.length
    ? `${found.length}${full ? '+' : ''} tunes by name or author ${among}, in ${ms} ms.`
    : `nothing matched ${among}, in ${ms} ms.`
      + (held.some(({ complete }) => complete)
        ? ' Formats this build cannot open are indexed but not offered.'
        : ' Formats it cannot play are not in an index built before this browser kept everything.');
}

$('browsesearch').oninput = () => {
  clearTimeout(searchTimer);
  const query = $('browsesearch').value;
  searchTimer = setTimeout(() => runSearch(query), 250);
};

$('tab-browse').onclick = async () => {
  if (!$('browse').hidden) { showPanel(null); return; }
  browsePath = [];
  jumpedTo = null;
  showPanel('browse');
  await renderBrowse();
};
$('browseback').onclick = async () => {
  // Out of a search, back to where it was typed; otherwise one level up. **Except above the folder
  // a digression came from** (`docs/BACKLOG.md` A41): there the way back is the dice, not the
  // archive, which is what "one level of digression" means.
  if ($('browsesearch').value) { $('browsesearch').value = ''; searchAsked++; } else if (
    away?.dice && browsePath.length <= 3
  ) {
    resumeDice();
    showPanel(null);
    return;
  } else if (arrivedByJump()) {
    // Out of Browse in one press, wherever the jump was made from (W-D4 a, as the phone does).
    jumpedTo = null;
    showPanel(null);
    return;
  } else browsePath = browsePath.slice(0, -1);
  jumpedTo = null;
  await renderBrowse();
};
$('browseclose').onclick = () => showPanel(null);

$('playlistchip').onclick = () => {
  renderPlaylists();
  showPanel($('playlists').hidden ? 'playlists' : null);
};

/**
 * Makes an empty playlist and switches to it. Answers whether one was made.
 *
 * Kept apart from the sheet's button that calls it, so "what a new playlist is" has one home.
 */
async function newPlaylist() {
  if (!(await settleUnsaved())) return false;
  const name = prompt('Call it what?', 'New playlist');
  if (!name) return false;
  const id = `p${Date.now().toString(36)}`;
  await playlists.save({ id, name, tracks: [], index: 0 });
  activePlaylist = id;
  $('playlistname').textContent = name;
  await settings.set('active', id);
  // Emptied on purpose: the point of a new list is to put something in it, and leaving the previous
  // queue on screen under a new name is the opposite of empty.
  setQueue([], 0);
  status(`${name} — empty. Browse for something to put in it.`);
  return true;
}

$('newlist').onclick = async () => { if (await newPlaylist()) renderPlaylists(); };

$('saveas').onclick = async () => {
  if (!queue.length) { status('there is nothing in the queue to save'); return; }
  const name = prompt('Call it what?', 'My playlist');
  if (!name) return;
  const id = `p${Date.now().toString(36)}`;
  await playlists.save({ id, name, tracks: queue.map(({ url, name: n, meta, local, file }) => ({ url, name: n, meta, local, file })), index });
  // The edits went into the new playlist; the old one stays as it was saved.
  setDirty(false);
  // **Saved out of Random or History, the list becomes the playlist**, and the session is over:
  // leaving it would otherwise put back the playlist that was showing before, under this one's name.
  dropSession();
  activePlaylist = id;
  $('playlistname').textContent = name;
  await settings.set('active', id);
  renderPlaylists();
  status(`Saved as ${name}`);
};

$('nowcard').onclick = () => showPanel($('nowplaying').hidden ? 'nowplaying' : null);
// Keeping what is playing, without leaving what it is playing from — the phone's `keepTransient`.
$('nowkeep').onclick = (event) => {
  event.stopPropagation();
  const entry = queue[index];
  if (entry) addToShowing([plain(entry)]);
};
$('tab-settings').onclick = async () => {
  const opening = $('settings').hidden;
  if (opening) { await renderSettings(); await pointSourceAtBuild(); }
  showPanel(opening ? 'settings' : null);
};
$('open-notices').onclick = () => renderLicences().then(() => showPanel('legal'));
$('open-privacy').onclick = () => renderPrivacy().then(() => showPanel('legal'));
$('pair-privacy').onclick = () => renderPrivacy().then(() => showPanel('legal'));
$('tab-pair').onclick = () => showPanel($('pair').hidden ? 'pair' : null);
$('tab-paste').onclick = () => showPanel($('paste').hidden ? 'paste' : null);

// A dialog closes on its own button, on the scrim behind it, and on Escape. All three, because
// people reach for all three and a dialog that only answers one of them feels stuck.
for (const overlay of document.querySelectorAll('.overlay')) {
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay || event.target.hasAttribute('data-close')) {
      if (overlay.id === 'addto') closeAddTo(); else showPanel(null);
    }
  });
}
addEventListener('keydown', (event) => {
  if (event.key !== 'Escape') return;
  if (!$('addto').hidden) closeAddTo(); else showPanel(null);
});

$('load').onclick = () => {
  const urls = $('urls').value.split('\n').map((s) => s.trim()).filter(Boolean);
  if (!urls.length) { showPanel(null); return; }
  // **"From the phone" is not written into**, and pasting writes into the playlist that is
  // showing, and the next handoff replaces "From the phone" wholesale -- so the paste would be
  // thrown away, silently, and until then the screen would show a queue the phone does not have.
  // The same holds for every function that writes into the showing list.
  if (activePlaylist === PHONE) {
    $('pastenote').textContent = 'This would replace what the phone sent. Switch to one of your '
      + 'own playlists first, or make an empty one — the name at the top left opens them.';
    return;
  }
  $('pastenote').textContent = '';
  setDirty(true);
  setQueue(urls);
  showPanel(null);
};
$('playpause').onclick = async () => {
  // Waiting for the page's first use: this press is it, whatever the tune is doing meanwhile --
  // while it is still loading, the button would otherwise mean "stop".
  if (firstTouch) { await firstTouch({ target: document.body }); return; }
  // Nothing loaded yet, but a track is selected: this press is the one that starts it. That is the
  // gesture a browser insists on, and it is why a queue arriving does not play by itself.
  if (!loading && !$('seek').dataset.opened && index >= 0 && !playing) {
    playAt(index);
    return;
  }
  // **It reached the end and nothing followed, so this press means "again".** The bytes are still
  // in the worklet, so it rewinds rather than fetching them a second time.
  if (shouldRestart({ engineFinished: finished, position: 0, duration: 0 })) {
    finished = false;
    if (context?.state === 'suspended') await context.resume();
    node?.port.postMessage({ type: 'rewind' });
    setPlaying(true);
    return;
  }
  // While something is being fetched, this button means "stop waiting".
  if (loading) {
    loading.abort();
    loading = null;
    clearTimeout(openWatchdog);
    // It may already have the bytes and be opening them; tell it to drop what it has.
    node?.port.postMessage({ type: 'close' });
    setPlaying(false);
    $('sub').textContent = 'stopped';
    status('Stopped loading.');
    return;
  }
  await start();
  if (context.state === 'suspended') await context.resume();
  setPlaying(!playing);
  node.port.postMessage({ type: playing ? 'play' : 'pause' });
};
/*
  The transport, and **the phone is the specification** — both halves of it, which this did not have.

  Short press: with "play every tune in this file" on, the tunes inside are walked before the queue
  moves, forwards *and backwards*. `previous` did not do that at all, so on a `.sndh` back left the
  file (`docs/STATUS.md` C31).

  Long press: **past the file**, to the next or previous one, whatever is left inside this one. This
  page had it the other way round — a long press stepped *within* the file — which is the opposite
  of `ui/PlayerDock.kt`, where the long click is `onNextFile` and `onPreviousFile`. A transport whose
  gestures mean opposite things on the two screens is worse than one that is missing them.
*/

/** Whether there is another tune inside the open file, ahead or behind. */
const hasNextSubsong = () => currentSubsong + 1 < subsongCount;
const hasPreviousSubsong = () => currentSubsong > 0;

function goToSubsong(index) {
  node?.port.postMessage({ type: 'subsong', index });
  status(`Tune ${index + 1} of ${subsongCount}`);
}

/** The next file, past whatever is left inside this one. What a long press means. */
function nextFile() {
  const n = afterCurrent();
  if (n === 'roll') rollRandom();
  else if (n != null) playAt(n);
}

function previousFile() {
  const p = beforeCurrent();
  if (p != null) playAt(p);
}

$('random-filter').onclick = () => { $('randomfilter').hidden = !$('randomfilter').hidden; };
$('snackundo').onclick = () => undoRemoval();
$('tab-save').onclick = () => saveEdits();
$('sel-add').onclick = () => openAddTo();
$('sel-share').onclick = () => {
  const tracks = queue.filter((entry) => selected.has(entry));
  clearSelection();
  sendToWeb(tracks.map(plain));
};
$('sel-delete').onclick = () => {
  const ats = queue.map((entry, i) => (selected.has(entry) ? i : -1)).filter((i) => i >= 0);
  selected = new Set();
  removeRows(ats);
};
$('sel-cancel').onclick = () => clearSelection();
$('addto-new').onclick = async () => {
  const name = prompt('Call it what?', 'New playlist')?.trim();
  if (name) addTracksTo(null, name);
};
// Escape leaves the ticking first, the way Back does on the phone -- before it closes anything else.
addEventListener('keydown', (event) => { if (event.key === 'Escape' && selected.size) clearSelection(); });
$('tab-discard').onclick = () => discardEdits();
$('unsaved-save').onclick = async () => { await saveEdits(); answerUnsaved(true); };
$('unsaved-discard').onclick = () => { setDirty(false); answerUnsaved(true); };
// **Closing the tab** is the one switch the page cannot ask about in its own words; the browser's
// question is the only one available, and losing edits silently is worse than a plain dialog.
addEventListener('beforeunload', (event) => {
  if (!dirty) return;
  event.preventDefault();
  event.returnValue = '';
});
$('random-leave').onclick = () => (away?.dice ? resumeDice() : endSession());

$('next').onclick = () => {
  if (playAllSubsongs && hasNextSubsong()) { goToSubsong(currentSubsong + 1); return; }
  nextFile();
};

$('prev').onclick = () => {
  if (playAllSubsongs && hasPreviousSubsong()) { goToSubsong(currentSubsong - 1); return; }
  previousFile();
};

/**
 * A long press on either skips the rest of the file.
 *
 * `pointerdown` rather than `mousedown`, so a finger works; the click that follows is swallowed, or
 * the press would count twice. Half a second, which is what Android's own long-press timeout is.
 */
function holdToSkipFile(button, act) {
  let timer = null;
  let held = false;
  button.addEventListener('pointerdown', () => {
    held = false;
    timer = setTimeout(() => { held = true; act(); }, 500);
  });
  for (const event of ['pointerup', 'pointercancel', 'pointerleave']) {
    button.addEventListener(event, () => clearTimeout(timer));
  }
  button.addEventListener('click', (event) => {
    if (held) { event.stopImmediatePropagation(); event.preventDefault(); held = false; }
  }, true);
}
holdToSkipFile($('next'), nextFile);
holdToSkipFile($('prev'), previousFile);

/**
 * The fallback length slider (`docs/STATUS.md` C56).
 *
 * Minutes on the control, seconds in the variable: the phone's slider has one notch per minute and
 * this one must not be able to produce a value that one cannot.
 */
function showFallback() {
  const minutes = Math.round(fallbackSeconds / 60);
  $('fallback').value = String(minutes);
  $('fallbackvalue').textContent = `${minutes} ${minutes === 1 ? 'minute' : 'minutes'}`;
}
$('fallback').oninput = () => {
  const minutes = Number($('fallback').value);
  fallbackSeconds = Math.min(FALLBACK_MAX_SECONDS, Math.max(FALLBACK_MIN_SECONDS, minutes * 60));
  showFallback();
  try { localStorage.setItem('protracktor.fallback', String(fallbackSeconds)); } catch { /* private window */ }
  // A tune already past the new limit ends at the next position message, which is what somebody
  // dragging the slider down is asking for -- so the flag is cleared rather than left standing.
  endedByClock = false;
};
showFallback();

$('allsubsongs').onclick = () => {
  playAllSubsongs = !playAllSubsongs;
  $('allsubsongs').setAttribute('aria-checked', String(playAllSubsongs));
  try { localStorage.setItem('protracktor.allsubsongs', playAllSubsongs ? '1' : '0'); } catch { /* private window */ }
  setPlaying(playing);
  status(playAllSubsongs ? 'Next walks this file first' : 'Next moves to the next file');
};

const REPEAT_GLYPH = 'M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z';
const REPEAT_ONE_GLYPH = 'M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z';

$('shuffle').onclick = () => {
  shuffle = !shuffle;
  if (shuffle) reshuffle(index >= 0 ? index : null);
  setPlaying(playing);
  status(shuffle ? 'Shuffle on' : 'Shuffle off');
};

$('repeat').onclick = () => {
  repeat = repeat === 'off' ? 'all' : repeat === 'all' ? 'one' : 'off';
  // **The shape carries the mode, not just the tint**, so it survives being read without colour --
  // the same rule the dock follows on the phone (AGENTS.md §8).
  $('repeat').querySelector('path').setAttribute('d', repeat === 'one' ? REPEAT_ONE_GLYPH : REPEAT_GLYPH);
  $('repeat').title = repeat === 'one' ? 'Repeat one' : repeat === 'all' ? 'Repeat all' : 'Repeat off';
  setPlaying(playing);
  status(`Repeat ${repeat}`);
};
/*
  Volume. The slider is a **percentage of loudness, not of amplitude** -- halving the amplitude of
  a signal does not sound half as loud, so a linear slider spends its top half doing almost nothing
  and its bottom quarter doing everything. Squaring is the cheap approximation everyone uses and it
  is close enough that the middle of the slider sounds like the middle.
*/
const VOLUME_ON = 'M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z';
const VOLUME_OFF = 'M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51A8.8 8.8 0 0 0 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3 3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06a8.9 8.9 0 0 0 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4 9.91 6.09 12 8.18V4z';

/** 0…100, kept per browser. Not a preference anybody else needs to know about. */
let level = 100;
let muted = false;
try {
  // **The raw string first.** `Number(null)` is 0, not NaN, so reading the value straight into a
  // number made a first-time visitor arrive muted -- which the page checks caught before anybody
  // opened it. Nothing stored means nothing stored, and that is full volume.
  const stored = localStorage.getItem('protracktor.volume');
  const value = stored === null ? NaN : Number(stored);
  if (Number.isFinite(value) && value >= 0 && value <= 100) level = value;
} catch { /* a private window, or site data turned off. The default is a fine answer. */ }

/** What the gain node should be set to, given the slider and the mute. */
const amplitude = () => (muted ? 0 : (level / 100) ** 2);

function applyVolume() {
  $('volume').value = String(level);
  paint($('volume'));
  const silent = muted || level === 0;
  $('volglyph').setAttribute('d', silent ? VOLUME_OFF : VOLUME_ON);
  $('mute').title = silent ? 'Unmute' : 'Mute';
  // `setTargetAtTime` rather than an assignment: a gain that jumps clicks, and a slider dragged
  // across produces a hundred jumps. 15 ms is under a frame and above the click.
  if (gain) gain.gain.setTargetAtTime(amplitude(), context.currentTime, 0.015);
  try { localStorage.setItem('protracktor.volume', String(level)); } catch { /* see above */ }
}

function setVolume(value) {
  level = Math.max(0, Math.min(100, Math.round(value)));
  // Moving the slider away from zero is the same gesture as unmuting, and leaving it silent would
  // look like the control had stopped working.
  if (level > 0) muted = false;
  applyVolume();
  status(`Volume ${level}%`);
}

$('volume').oninput = () => setVolume(Number($('volume').value));
$('mute').onclick = () => {
  // Muting at zero would do nothing visible, so it winds back up instead -- which is what a
  // speaker icon means when the sound is already off.
  if (level === 0) { level = 100; muted = false; } else { muted = !muted; }
  applyVolume();
  status(muted || level === 0 ? 'Muted' : `Volume ${level}%`);
};
applyVolume();

// The panel's actions act on whatever is playing, which is the one thing the panel is about.
$('np-show').onclick = () => { if (index >= 0) showInPlaylist(index); };
$('np-folder').onclick = () => showAuthorFolder(queue[index]);
$('np-save').onclick = () => { const e = queue[index]; if (e) saveFile(e); };
$('np-link').onclick = () => { const e = queue[index]; if (e) copyLink(e); };

// --- the now-playing lines scroll when they do not fit (A54) --------------------------------------
//
// The title and the line under it are written from a dozen places, so they are watched rather than
// each write being taught to measure: whenever either's text changes, it is measured once, and a
// line wider than its box is wrapped in a span that moves -- two seconds still, then left at the
// phone's pace until its end shows, and round again (`rules.lineScrollPass`). A line that fits is
// left alone, with its `…` for the moment it no longer does.
const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)') ?? null;

function fitLine(el, again = false) {
  const text = el.textContent;
  // Our own wrapping is a change too; the same text already running is not a new line.
  if (!again && el.scrollRun?.text === text) return;
  el.scrollRun?.animation?.cancel();
  el.scrollRun = null;
  if (el.firstElementChild?.classList.contains('run')) el.textContent = text;
  el.classList.remove('scrolling');
  const isStatus = el.id === 'sub' && loading !== null;
  if (!lineScrolls({ animationsOn: !reducedMotion?.matches, isStatus })) return;
  const pass = lineScrollPass(el.scrollWidth - el.clientWidth);
  if (!pass) return;
  const run = document.createElement('span');
  run.className = 'run';
  run.textContent = text;
  el.scrollRun = { text };
  el.replaceChildren(run);
  el.classList.add('scrolling');
  if (typeof run.animate !== 'function') return;
  el.scrollRun.animation = run.animate([
    { transform: 'translateX(0)', offset: 0 },
    { transform: 'translateX(0)', offset: pass.pauseShare },
    { transform: `translateX(${-pass.distance}px)`, offset: 1 },
  ], { duration: pass.duration, iterations: Infinity });
}

for (const id of ['title', 'sub']) {
  const el = $(id);
  new MutationObserver(() => fitLine(el)).observe(el, { childList: true, characterData: true, subtree: true });
}
// A narrower window, or motion turned down while playing, asks every line again.
const refitLines = () => { fitLine($('title'), true); fitLine($('sub'), true); };
window.addEventListener('resize', refitLines);
reducedMotion?.addEventListener?.('change', refitLines);

$('seek').oninput = () => { seeking = true; paint($('seek')); };
$('seek').onchange = () => {
  seeking = false;
  // **The bar stays where it was let go** until the worklet says the seek has landed, and a seek
  // that takes long enough to be seen waiting shows a spinner in place of the elapsed time -- the
  // phone's `SeekProgress` (Q11). A SID seeks by running its machine there, for seconds.
  const target = (Number($('seek').value) / 1000) * bar().seconds;
  // **The latest seek wins** (Q11). The worklet runs a seek to its end before it reads its next
  // message, so a click while one runs is not sent: it is remembered, and only the last one goes
  // when the running one lands. Ten clicks in a second are two seeks, not ten.
  if (seekPending) { seekNext = target; return; }
  seekPending = true;
  clearTimeout(seekSpinner);
  seekSpinner = setTimeout(() => {
    if (!seekPending) return;
    $('elapsed').classList.add('seeking');
    $('elapsed').setAttribute('aria-label', 'Seeking');
  }, SEEK_SPINNER_AFTER_MS);
  node?.port.postMessage({ type: 'seek', seconds: target });
};

/** A seek asked for while another ran: only the latest is kept, and sent when that one lands. */
let seekNext = null;

/** Whether a seek is under way, and the timer that shows its spinner if it takes a while. */
let seekPending = false;
let seekSpinner = null;
/** A seek shorter than this shows nothing, as on the phone. */
const SEEK_SPINNER_AFTER_MS = 300;

/** The seek has landed, or the tune it was made on is gone: the time comes back. */
function seekLanded() {
  seekPending = false;
  seekNext = null;
  clearTimeout(seekSpinner);
  $('elapsed').classList.remove('seeking');
  $('elapsed').removeAttribute('aria-label');
}

/**
 * A queue handed over in the URL's fragment.
 *
 * The fragment is **never sent to the server**, so the page's own host learns nothing about what is
 * being played -- which is what makes the first handoff design need no server at all
 * (`docs/PLAN_HANDOFF.md` §3 H1). Fifty tracks compress to under two thousand characters.
 */
async function fromFragment() {
  const raw = location.hash.slice(1);
  if (!raw) return;
  // **One tune to play**, not a queue to take over: Share with Protracktor (`QueueLink.PLAY_PREFIX`).
  const one = raw.startsWith(PLAY_PREFIX);
  try {
    const lines = (await inflateFragment(one ? raw.slice(PLAY_PREFIX.length) : raw))
      .split('\n').map((s) => s.trim()).filter(Boolean);
    // `address` or `address<tab>title`. The title is sent only when the address does not already
    // carry it -- which is most of Modland and none of The Mod Archive, whose URLs are a script and
    // a number (`QueueLink.withTitle`).
    const entries = lines.map((line) => {
      // **A file that stayed on the phone**, sent as a name so its place in the list survives.
      // Its bytes are a storage grant to one app on one device and could never have come; the
      // *position* could, and two people cannot talk about a list that numbers itself differently
      // at each end (`docs/BACKLOG.md` A28).
      if (line.startsWith('phone:')) return ghost(line.slice('phone:'.length));
      const [address, title] = line.split('\t');
      const url = address.includes('://') ? address
        : MODLAND_FILES + address.split('/').map(encodeURIComponent).join('/');
      const entry = entryFor(url);
      return title ? { ...entry, name: title } : entry;
    });
    if (one) { playSentTune(entries.filter((e) => !e.local)); return; }
    // The code steps aside for a queue that came by link, as it does for one that came by pairing
    // (`applyReceive`) -- in a tab that was already open on it, `hashchange` brings us here.
    if (!$('pair').hidden) showPanel(null);
    setQueue(entries);
    activePlaylist = PHONE;
    $('playlistname').textContent = 'From the phone';
    const ghosts = lines.filter((l) => l.startsWith('phone:')).length;
    status(`${lines.length - ghosts} tracks from the link` +
           (ghosts ? `, and ${ghosts} that stayed on the phone` : ''));
  } catch (e) {
    status(`the link could not be read: ${e.message}`);
  }
}

const PLAY_PREFIX = 'play:';
const MODLAND_FILES = 'https://modland.com/pub/modules/';

/** URL-safe base64 of zlib deflate, back to text -- `QueueLink.encode`, from the other side. */
async function inflateFragment(fragment) {
  const packed = Uint8Array.from(atob(fragment.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
  return new Response(new Response(packed).body.pipeThrough(new DecompressionStream('deflate'))).text();
}

async function deflateFragment(text) {
  const bytes = new Uint8Array(await new Response(
    new Response(new TextEncoder().encode(text)).body.pipeThrough(new CompressionStream('deflate'))
  ).arrayBuffer());
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * A tune somebody sent here to be played (`QueueLink.trackLink`): **played, not filed**. It goes
 * through the session a Browse result plays through, so whatever list this page was showing is
 * left alone -- the link may well have come from somebody else, and a tune shown to you is not
 * one you asked to keep. **Now Playing stays folded**: the dock names the tune
 * and the heading says where it came from, and a panel over both was one more thing to close.
 */
function playSentTune(tracks) {
  if (!tracks.length) { status('the link names nothing this page can play'); return; }
  openAway(tracks, 0, 'link');
  // The heading names what arrived: one tune, or the list of them (`docs/BACKLOG.md` A38).
  if (tracks.length > 1) $('sessiontitle').textContent = 'Playing tunes sent to you';
  showPanel(null);
  status(tracks.length === 1
    ? `${tracks[0].name} — sent to this player`
    : `${tracks.length} tunes — sent to this player`);
}

/**
 * Starts the tune waiting on a suspended context at the first touch or key **anywhere** on the page
 * rather than only at Play. Straight away is the browser's to allow, not the page's: it keeps
 * audio suspended until the page is used, and a site given leave to
 * autoplay never gets here. What the page can do is make any use of it count, not just Play.
 *
 * **Play itself and the space bar are left alone**, because they already start it: taking the
 * pointerdown first would make their click the second press, and pause what had just begun.
 */
let firstTouch = null;
let dockFields = null;
const TAP_HINT = 'Tap anywhere to play';

/**
 * Waiting for the page to be used, **said in the dock**: the status line lives in Now Playing,
 * which is folded, so a sentence there was one nobody read while the dock sat on "fetching…".
 */
function waitForTouch() {
  startOnFirstTouch();
  $('sub').textContent = TAP_HINT;
  status('A browser starts no sound until the page is touched.');
  setPlaying(playing);
}

function startOnFirstTouch() {
  if (firstTouch) return;
  const events = ['pointerdown', 'keydown', 'click'];
  firstTouch = async (event) => {
    if ($('playpause').contains(event.target) || event.key === ' ') return;
    try { await context.resume(); } catch { return; }
    if (context.state !== 'running' || !firstTouch) return;
    for (const type of events) removeEventListener(type, firstTouch, true);
    firstTouch = null;
    const opened = !!$('seek').dataset.opened && !loading;
    // Opened already (Firefox runs the worklet while suspended): this touch is the play. Not yet
    // (Chrome does not): the open waiting in the worklet now runs, and `opened` starts it.
    if (opened && !playing) {
      setPlaying(true);
      node.port.postMessage({ type: 'play' });
    } else {
      setPlaying(playing);
    }
    if ($('sub').textContent === TAP_HINT) {
      $('sub').textContent = opened && dockFields ? describeLine(dockFields) : 'opening…';
    }
    status('Playing');
  };
  for (const type of events) addEventListener(type, firstTouch, true);
}

/** Whether a row can go as a one-tune link: `QueueLink.canSend`, the page's side of it. */
function canSendToWeb(entry) {
  return !!entry?.url && !entry.local && /^https?:\/\//.test(entry.url)
    && !/\.mp3$/i.test(fileOf(entry)) && !/\.mp3$/i.test(entry.name ?? '');
}

/**
 * Share with Protracktor: the link that opens this page playing [entry], shared where the browser
 * can share and copied where it cannot. **Packed exactly as the phone packs it**, so a link from
 * either side opens the same way (`QueueLink.withTitle`: Modland as a path, the title only when
 * the address does not already say it).
 */
async function sendToWeb(entries) {
  const tunes = (Array.isArray(entries) ? entries : [entries]).filter(canSendToWeb);
  if (!tunes.length) { status('none of those have an address another browser could open'); return; }
  const lines = tunes.map((entry) => {
    const address = entry.url.startsWith(MODLAND_FILES)
      ? entry.url.slice(MODLAND_FILES.length).split('/').map(decodeURIComponent).join('/')
      : entry.url;
    const file = address.slice(address.lastIndexOf('/') + 1).split('#')[0].split('?')[0];
    const title = entry.name?.trim() ?? '';
    return !title || title.toLowerCase() === file.toLowerCase() ? address : `${address}\t${title}`;
  });
  const entry = tunes[0];
  const link = `${location.origin}${location.pathname}#${PLAY_PREFIX}${await deflateFragment(lines.join('\n'))}`;
  lastSentLink = link;
  try {
    const named = tunes.length === 1 ? entry.name : `${tunes.length} tunes`;
    if (navigator.share) { await navigator.share({ title: `${named} — Protracktor web`, url: link }); return; }
  } catch (error) {
    if (error?.name === 'AbortError') return;   // the share sheet was closed; nothing to say
  }
  try {
    await navigator.clipboard.writeText(link);
    showNote('Link copied');
  } catch {
    showNote('The browser would not copy it — the link is in Now Playing');
    status(link);
  }
}
let lastSentLink = null;

/**
 * The pairing panel: a code that is always there, and a loop that is always asking.
 *
 * **A page cannot expose an endpoint**, which is the fact this design turns on. The phone does not
 * post a playlist "to the page"; it posts to the server the page is listening to, and the QR
 * carries that server's address. The room id is 128 bits and travels only in the code on screen;
 * nothing flows back to the phone, so a stolen code buys a noise in somebody's tab.
 *
 * **Long polling, not an event stream, and that was measured rather than chosen.** A Cloudflare
 * quick tunnel buffers `text/event-stream`: the server reported delivering and this page received
 * nothing, through anti-buffering headers and two kilobytes of padding
 * (`docs/PLAN_HANDOFF.md` §5c). A complete HTTP response is the one thing every proxy forwards.
 */
async function pair() {
  // **The room outlives a reload**, which is what lets the phone scan once and send many times. It
  // is this browser's identity, kept here and nowhere else.
  let id = localStorage.getItem('protracktor.room');
  if (!id || !/^[0-9a-f]{32}$/.test(id)) {
    id = Array.from(crypto.getRandomValues(new Uint8Array(16)))
      .map((b) => b.toString(16).padStart(2, '0')).join('');
    localStorage.setItem('protracktor.room', id);
  }

  let base;
  try {
    base = (await fetch('/pair/host').then((r) => r.json())).base;
  } catch {
    // **Said, not left blank**: a page on a static host (GitHub Pages, say) has no pairing service
    // at all -- everything else works there, and this is the one thing that cannot.
    $('pairnote').textContent = 'This page is served without its pairing service, so a phone cannot '
      + 'send to it here. A page run with scripts/serve-web.mjs has one.';
    $('pairurl').textContent = '';
    $('qr').replaceChildren();
    return;
  }
  const post = `${base}/pair/${id}`;
  $('pairurl').textContent = post;

  const qr = qrcode(0, 'M');
  qr.addData(post);
  qr.make();
  $('qr').innerHTML = qr.createTableTag(5, 0);

  const ready = 'Scan this with Protracktor on your phone to send it a playlist.';
  $('pairnote').textContent = ready;

  let since = 0;
  let failures = 0;
  for (;;) {
    try {
      // A little above the server's own hold, so a request that is answered normally is never the
      // one that times out here -- and one that vanishes into a dead connection still ends.
      const abort = new AbortController();
      const bell = setTimeout(() => abort.abort(), 35_000);
      const answer = await fetch(`/pair/${id}/next?since=${since}`, { signal: abort.signal });
      clearTimeout(bell);
      if (!answer.ok) throw new Error(`HTTP ${answer.status}`);
      const body = await answer.json();
      failures = 0;
      $('pairnote').textContent = ready;
      if (typeof body.seq === 'number') since = body.seq;
      if (body.message) receive(JSON.parse(body.message));
    } catch (error) {
      // **Backing off matters more here than it looks.** A page left open on a laptop that has lost
      // the server would otherwise ask several times a second for as long as the tab is open --
      // which is how a small convenience becomes somebody's battery.
      failures += 1;
      const wait = Math.min(1000 * 2 ** Math.min(failures, 4), 15_000);
      $('pairnote').textContent =
        `Not reaching the pairing service — trying again in ${Math.round(wait / 1000)}s.`;
      await new Promise((resume) => setTimeout(resume, wait));
    }
  }
}

/** A queue from the phone. */
function receive(message) {
  if (!message.queue?.length) return;
  // A queue from the phone replaces what is showing, so edits not yet written are asked about first,
  // as a switch asks. Kept editing, the queue is not loaded -- and the page says so.
  if (dirty) {
    settleUnsaved().then((go) => {
      if (go) applyReceive(message);
      else status('A queue arrived from the phone and was not loaded, to keep your unsaved changes.');
    });
    return;
  }
  applyReceive(message);
}

function applyReceive(message) {
  setDirty(false);
  // The code did its job, so it stops standing in front of the music.
  if (!$('pair').hidden) showPanel(null);
  // The phone says which one it was on, and that is where the list opens -- selected rather than
  // started, so nothing makes a noise until somebody asks.
  setQueue(
    message.queue.map((row) => {
      // The phone marks what it could not send: a file whose bytes stayed there, or one too big
      // for the message. Drawn greyed in its own place rather than as a row that fails on the
      // first touch — the same answer a link's `phone:` line gets (`docs/BACKLOG.md` A28).
      if (row.local) return ghost(row.title || row.file);
      return {
        ...entryFor(row.url),
        name: row.title || entryFor(row.url).name,
        // The decoder is handed this, not the title: four backends choose a loader by extension.
        file: row.file || undefined,
        // Base64 in, bytes out, once -- decoding at play time would do it again on every replay.
        data: row.data ? Uint8Array.from(atob(row.data), (c) => c.charCodeAt(0)).buffer : undefined,
      };
    }),
    message.index ?? 0,
  );
  // A handoff is always the phone's playlist, whatever was showing. It replaces it whole.
  activePlaylist = PHONE;
  $('playlistname').textContent = 'From the phone';
  const stranded = message.queue.filter((row) => row.local).length;
  status(`${message.queue.length - stranded} tracks from the phone — press play` +
         (stranded ? `, and ${stranded} that stayed on it` : ''));
}

/**
 * The keys somebody at a desk will try.
 *
 * Space for play, arrows for the transport — and nothing clever. They are ignored while a field has
 * focus, because a space typed into the paste box must be a space; that is the bug every page with
 * shortcuts ships once.
 */
addEventListener('keydown', (event) => {
  const target = event.target;
  if (target instanceof HTMLTextAreaElement || target instanceof HTMLInputElement) return;
  if (event.metaKey || event.ctrlKey || event.altKey) return;
  const act = {
    ' ': () => $('playpause').click(),
    ArrowRight: () => $('next').click(),
    ArrowLeft: () => $('prev').click(),
    // Up and down for volume, because that is what they do everywhere else and because the slider
    // is the one control that disappears on a narrow screen. Five per press: twenty presses from
    // silence to full is a fair trade against having to aim at an 84-pixel slider.
    ArrowUp: () => setVolume(level + 5),
    ArrowDown: () => setVolume(level - 5),
    m: () => $('mute').click(),
  }[event.key];
  if (!act) return;
  event.preventDefault();
  act();
});

// **No pinch to zoom**, in the two places the viewport and `touch-action` do not
// reach: Safari's own gesture events, which ignore `user-scalable=no`, and a touchpad's pinch on a
// computer, which a browser delivers as a wheel with Ctrl held.
addEventListener('gesturestart', (event) => event.preventDefault());
addEventListener('wheel', (event) => { if (event.ctrlKey) event.preventDefault(); }, { passive: false });

// Open on the code: on a fresh page the first useful act is to point a phone at it. It closes
// itself the moment a queue arrives.
//
// **Not when the page was opened by a link.** A link is the queue already arriving, and the code
// would sit in the middle of the screen while it did -- the hash is read after an await, so the
// first thing anybody opening a tune sent to them would see is a QR code meant for somebody else.
renderNothingPlaying();
holdBarLabels();
document.fonts?.ready.then(holdBarLabels);
if (!location.hash.slice(1)) showPanel('pair');

status('ready — press Play or load some URLs');
pair();

/**
 * What was showing last time.
 *
 * **After `pair()` and before `fromFragment()`, and the order is the whole of it.** A link in the
 * address bar is somebody asking for *that* queue right now; anything restored from storage is
 * what they were doing yesterday, and yesterday must not win. So the restore runs first and the
 * fragment, if there is one, replaces it.
 */
// Read early so a refusal can be explained with it, and never fatal: without it the page still
// plays; it only cannot filter an index or say why a file will not open.
formatsReady().catch(() => {});

(async () => {
  try {
    await makePersistent();
    const id = await settings.get('active', PHONE);
    const playlist = await playlists.get(id);
    // **An empty playlist is restored too.** Requiring tracks holds only while every playlist
    // arrives full from the phone; one made empty on purpose would be dropped on reload, putting
    // "From the phone" back in front of the reader -- and that is the one list Browse will not
    // write into, so the tab they had just unblocked shuts again.
    if (playlist && id !== PHONE) {
      activePlaylist = id;
      $('playlistname').textContent = playlist.name;
      setQueue(playlist.tracks ?? [], playlist.index ?? 0);
      showPanel(null);
      status(playlist.tracks?.length
        ? `${playlist.name} — where you left it`
        : `${playlist.name} — empty. Browse for something to put in it.`);
    }
  } catch (e) {
    // A private window, storage turned off, a second tab holding an old version. The page works
    // without any of this and saying so is better than a dialog nobody can act on.
    status(`this browser is not keeping playlists: ${e.message}`);
  }
  fromFragment();
})();

// **A link opened in a tab that already has this page does not reload it.** Only the fragment
// changes, and the browser fires `hashchange` instead -- so without this, sending a second queue
// from the phone to an open tab appears to do nothing at all until the tab is reloaded.
addEventListener('hashchange', fromFragment);
