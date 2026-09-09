// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The main thread: fetches bytes, drives the worklet, draws the queue. It never touches audio.

const $ = (id) => document.getElementById(id);
const status = (text) => { $('status').textContent = text; };
const clock = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;

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
let playing = false;
let seeking = false;
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
 * page is *on*; this is whether that track has actually started. The owner met them conflated: he
 * pressed play, the row lit up as though it were playing, and a fetch was still running.
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
 * **`if (context) return` was a race and the owner found it.** `context` is assigned before the
 * awaits that follow, so a second call while the first was still loading the wasm returned
 * immediately with `node` still null — and the next line posted to it. `TypeError: node is null`,
 * from two `playAt` calls a millisecond apart, which is exactly what a queue arriving from the
 * phone produces. Holding the promise makes the second caller wait for the first rather than
 * overtake it.
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
  // would play 8.8% fast and about a semitone and a half sharp -- audible as "it sounds quicker
  // than I remember", which is exactly how this was found. Android has always asked the backend and
  // told Oboe, which resamples; this is the same answer by the only route a page has.
  context = new AudioContext({ sampleRate: 44100 });
  status('loading the engine…');

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

function onWorklet(message) {
  switch (message.type) {
    case 'ready':
      engineReady = true;
      engineHasZxTune = !message.backends.includes('zxtune:none');
      status(`engine ready — ${message.backends}`);
      break;
    case 'opened': {
      loading = null;
      rowState = 'playing';
      render();
      $('seek').dataset.opened = '1';
      clearTimeout(openWatchdog);
      duration = message.duration;
      finished = false;
      subsongCount = message.subsongs ?? 1;
      currentSubsong = message.current ?? 0;
      const fields = describeFields(message.describe);
      if (fields.title) $('title').textContent = fields.title;
      renderNowPlaying(fields, subsongCount, currentSubsong);
      publishToSystem(queue[index], fields);
      // A backend that wants a rate this context cannot give would play sharp and say nothing.
      // Today they all want 44,100; if one ever does not, this says so instead of transposing it.
      if (message.preferredRate > 0 && message.preferredRate !== message.rate) {
        status(`⚠ this decoder wants ${message.preferredRate} Hz and the page is running at ` +
               `${message.rate} Hz — it will play ${(message.rate / message.preferredRate).toFixed(3)}× fast`);
      }
      $('sub').textContent = describeLine(fields);
      $('seek').disabled = !message.canSeek;
      $('error').textContent = '';
      setPlaying(true);
      break;
    }
    case 'failed':
      loading = null;
      clearTimeout(openWatchdog);
      rowState = 'failed';
      render();
      // Errors stay above the transport. The status line moved into Now Playing because it is the
      // machine talking to itself; a decoder refusing a file is the machine talking to the listener.
      $('error').textContent = explainFailure(message.reason, queue[index]);
      $('sub').textContent = '—';
      // **And Now Playing stops describing the last file that worked.** The owner saw a refusal
      // leave the panel showing another track's fields, which reads as the wrong tune playing.
      renderNothingPlaying();
      setPlaying(false);
      break;
    case 'position':
      if (!seeking) {
        $('seek').value = duration > 0 ? Math.round((message.seconds / duration) * 1000) : 0;
        paint($('seek'));
        $('elapsed').textContent = clock(message.seconds);
        $('remaining').textContent = clock(duration);
      }
      break;
    // A subsong is a different tune: its own length, often its own title.
    case 'subsong': {
      finished = false;
      currentSubsong = message.index;
      duration = message.duration;
      const fields = describeFields(message.describe);
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
    case 'ended': {
      // **The file before the queue**, when the listener asked for that. Repeat-one is checked
      // first and deliberately: it means "this tune again", and a file's other tunes are not it.
      if (repeat !== 'one' && playAllSubsongs && currentSubsong + 1 < subsongCount) {
        node?.port.postMessage({ type: 'subsong', index: currentSubsong + 1 });
        break;
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
      break;
    }
  }
}

/**
 * The engine's `describe` block, which is tab-separated key/value lines.
 *
 * The dock's card wants one line of it; Now Playing wants all of it. Parsed once, used twice.
 */
function describeFields(describe) {
  return Object.fromEntries(
    describe.split('\n').filter(Boolean).map((line) => {
      const tab = line.indexOf('\t');
      return tab < 0 ? [line, ''] : [line.slice(0, tab), line.slice(tab + 1)];
    })
  );
}

function describeLine(fields) {
  return [fields.format, fields.artist, fields.tracker].filter(Boolean).join(' · ') || '—';
}

/**
 * Now Playing: the same fields the phone shows, in the same order.
 *
 * The order is not alphabetical and is not the engine's; it is `ui/NowPlaying.kt`'s — what a
 * listener asks first comes first, and the machine's own vocabulary comes last.
 */
const FIELD_ORDER = ['title', 'artist', 'format', 'tracker', 'year', 'publisher', 'album', 'comment'];

/**
 * The ZX Spectrum formats, which the browser build does not carry.
 *
 * **ZXTune is compiled out of the web engine and that is deliberate**: it does not build under
 * Emscripten, and patching it means forking a library `ARCHITECTURE` §3 says we do not fork
 * (`docs/PLAN_WEB.md` §14). The cost is 3,639 Modland files of 516,107 — and a refusal that says
 * "no decoder claimed the file", which is true and useless when the phone plays it perfectly.
 */
const ZX_FORMATS = new Set(['pt3', 'pt2', 'pt1', 'stc', 'st1', 'st3', 'asc', 'as0', 'sqt', 'stp', 'psm', 'ftc', 'gtr']);

function explainFailure(reason, entry) {
  const extension = (entry?.name ?? '').toLowerCase().split('.').pop();
  if (ZX_FORMATS.has(extension) && !engineHasZxTune) {
    return `${entry.name}: the ZX Spectrum decoder is not in the browser build — this one plays on the phone`;
  }
  return reason;
}

/** Read from the engine's own fingerprint rather than assumed, so a future build that has it is believed. */
let engineHasZxTune = false;

/**
 * Now Playing, before anything has played.
 *
 * The owner said he could not expand it. It expanded — onto an empty list, which looks identical to
 * a panel that did not open. A screen with nothing to say has to say that.
 */
function renderNothingPlaying() {
  $('np-title').textContent = queue[index]?.name || 'Nothing playing';
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

function renderNowPlaying(fields, subsongs, current) {
  $('np-title').textContent = fields.title || queue[index]?.name || 'Nothing playing';
  const list = $('fields');
  list.replaceChildren();
  const shown = FIELD_ORDER.filter((key) => fields[key]);
  for (const key of shown) {
    const dt = document.createElement('dt');
    dt.textContent = key;
    const dd = document.createElement('dd');
    dd.textContent = fields[key];
    list.append(dt, dd);
  }
  if (!shown.length) {
    const dt = document.createElement('dt');
    dt.textContent = '—';
    const dd = document.createElement('dd');
    dd.textContent = 'the file says nothing about itself';
    list.append(dt, dd);
  }

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
      artist: fields?.artist || '',
      album: [fields?.format, fields?.year].filter(Boolean).join(' · '),
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
  const entry = queue[index];
  render();
  followPlaying();
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
      $('sub').textContent = 'from the phone…';
      bytes = entry.data;
    } else {
      $('sub').textContent = 'fetching…';
      const response = await fetch(entry.url, { signal: abort.signal });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      bytes = await response.arrayBuffer();
    }
  } catch (e) {
    // **Only the load that is still current clears the flag.** Two quick track changes overlap:
    // the older fetch finishes after the newer one has started, and clearing unconditionally would
    // wipe the newer controller -- leaving a download nobody can call off and a button that lies
    // about what it will do.
    if (loading === abort) { loading = null; setPlaying(false); }
    if (e.name === 'AbortError') { $('sub').textContent = 'stopped'; return; }
    // A fetch that fails here is usually CORS or a network that blocks the archive, and those are
    // different problems from a file the decoders refuse. Say which.
    $('error').textContent = `could not fetch it: ${e.message}`;
    $('sub').textContent = '—';
    return;
  }
  // **The load is not over when the fetch is.** The bytes still have to reach the worklet and be
  // opened, and the owner met exactly the gap that leaves: the dock said "opening…", the button had
  // gone back to a play arrow, and pressing it did something else entirely. The controller stays
  // until the worklet answers, so "stop" means the whole operation and the glyph agrees with it.
  //
  // Handed over, and the page now waits for the worklet to say `opened` or `failed`. It says which
  // it is waiting for, because "fetching…" left standing after the fetch finished is a lie.
  $('sub').textContent = engineReady
    ? `${(bytes.byteLength / 1024).toFixed(0)} KB — opening…`
    : `${(bytes.byteLength / 1024).toFixed(0)} KB — waiting for the engine…`;
  node.port.postMessage({ type: 'open', bytes, name: entry.file ?? entry.name }, [bytes]);
  setPlaying(false);
  announceGesture();

  // **A worklet that never answers is the failure with no symptom.** It has happened twice on this
  // page -- once when a message was dropped before the engine had compiled, once when the engine
  // did not load at all -- and both times the screen simply sat there. Ten seconds is far past any
  // honest open; after that the page says so rather than waiting for ever.
  clearTimeout(openWatchdog);
  openWatchdog = setTimeout(() => {
    if (loading !== abort) return;
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
  if (context.state === 'suspended') {
    status('Press play — a browser will not start audio until this page is clicked.');
  }
}

const PLAY_GLYPH = 'M8 5v14l11-7z';
const PAUSE_GLYPH = 'M6 5h4v14H6zm8 0h4v14h-4z';
const STOP_GLYPH = 'M6 6h12v12H6z';

/**
 * Keeps the playing row on screen.
 *
 * The phone has a button for this and the owner asked twice for it to be *subtle* (`GOAL.md`
 * round 2, item 1). A page has room to simply do it: a queue of fifty is a scrollbar, and a
 * playing row three screens up is the same complaint in a different medium.
 */
function followPlaying() {
  const row = $('queue').children[index];
  if (row?.scrollIntoView) row.scrollIntoView({ block: 'nearest' });
}

function setPlaying(on) {
  playing = on;
  if ('mediaSession' in navigator) navigator.mediaSession.playbackState = on ? 'playing' : 'paused';
  // Three states, not two: a track being fetched is not "paused", and a button that would restart
  // the same download is the one press nobody wants twice.
  $('playglyph').setAttribute('d', loading ? STOP_GLYPH : on ? PAUSE_GLYPH : PLAY_GLYPH);
  $('playpause').title = loading ? 'Stop loading' : on ? 'Pause' : 'Play';
  $('playpause').disabled = queue.length === 0;
  // Asked of the modes rather than of the position, exactly as `PlayerState.canGoNext` is: under
  // repeat-all the last track does have a next, and under shuffle the row above is not the previous.
  $('prev').disabled = beforeCurrent() == null;
  // Available while the *file* has more in it too, not only the queue: with the switch on, that is
  // what the button will do.
  $('next').disabled = afterCurrent() == null && !(playAllSubsongs && hasNextSubsong());
  $('shuffle').disabled = queue.length === 0;
  $('repeat').disabled = queue.length === 0;
  $('shuffle').classList.toggle('on', shuffle);
  $('repeat').classList.toggle('on', repeat !== 'off');
  // The panel's actions are about the track it is describing, so they go dead with it.
  const entry = queue[index];
  $('np-show').disabled = !entry;
  $('np-save').disabled = !entry || !!entry.local;
  $('np-link').disabled = !entry || !entry.url;
}

/**
 * The list, in the app's shape: a position, a title, and a subtitle that says where it came from.
 *
 * `ui/PlaylistScreen.kt` puts the source path in the subtitle and tints the playing row with
 * `primary`; both are reproduced here. The drag handle, the selection mode and the overflow menu
 * are phone-only and deliberately absent (`GOAL.md` round 7, item 3).
 */
function render() {
  const list = $('queue');
  list.replaceChildren(...queue.map((entry, i) => {
    const li = document.createElement('li');
    li.className = i === index && rowState ? `track ${rowState}` : 'track';
    // Grey rather than red: `.failed` means a decoder refused this, and nothing here broke -- the
    // bytes simply never left the phone. A different state, and one that does not answer a click.
    if (entry.local) li.classList.add('local');

    const n = document.createElement('span');
    n.className = 'n';
    n.textContent = String(i + 1);

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
    if (!entry.local) li.onclick = () => playAt(i);
    return li;
  }));
  $('count').textContent = queue.length
    ? `${queue.length} track${queue.length === 1 ? '' : 's'}`
    : 'nothing yet';
}

/**
 * The bytes of a row, from wherever they are.
 *
 * A paired queue carries them; a link does not, so they are fetched. Either way this is what the
 * two actions that need bytes are built on, and it is why they are disabled for a row that stayed
 * on the phone: there is nothing to go and get.
 */
async function bytesOf(entry) {
  if (entry.data) return entry.data;
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
    status('Link copied');
  } catch {
    // A browser that refuses the clipboard without a gesture it recognises, or an insecure context.
    // Showing the address is worse than copying it and much better than silence.
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
    const answer = new Promise((resolve) => describePending.set(id, resolve));
    node.port.postMessage({ type: 'describe', id, name: entry.file || entry.name, bytes }, [bytes]);
    const described = await answer;
    if (!described.ok) { status(explainFailure(described.reason, entry)); return; }
    renderNowPlaying(describeFields(described.describe), described.subsongs, -1);
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
    ['Save the file', () => saveFile(entry), !entry.local],
    ['Copy a link', () => copyLink(entry), !!entry.url],
    ['Information', () => informAbout(entry), !entry.local],
  ];
  for (const [label, act, enabled] of items) {
    const button = document.createElement('button');
    button.textContent = label;
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

/** What `next` should play, or null at the end. */
function afterCurrent() {
  if (repeat === 'one' && index >= 0) return index;
  if (shuffle) {
    const at = order.indexOf(index);
    if (at >= 0 && at + 1 < order.length) return order[at + 1];
    if (repeat === 'all') { reshuffle(null); return order[0] ?? null; }
    return null;
  }
  const ahead = seek(index + 1, 1);
  if (ahead != null) return ahead;
  return repeat === 'all' ? seek(0, 1) : null;
}

/** What `previous` should play, or null. */
function beforeCurrent() {
  if (shuffle) {
    // The history holds what was really played; the cursor is its end because the page only ever
    // walks backwards from now.
    const at = history.lastIndexOf(index);
    return at > 0 ? history[at - 1] : null;
  }
  const back = seek(index - 1, -1);
  if (back != null) return back;
  return repeat === 'all' && queue.length ? seek(queue.length - 1, -1) : null;
}

/**
 * A queue arrives.
 *
 * **It does not start playing.** The owner scanned a code and music began, which is startling on a
 * page nobody has touched: on the phone he pressed something, in the browser he did not. The list
 * appears, the dock names what is first, and the next press is his.
 *
 * @param at which track to select, if the sender knows. Selected, not played.
 */
function setQueue(urls, at = 0) {
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
  $('pair').hidden = which !== 'pair';
  $('paste').hidden = which !== 'paste';
  $('nowplaying').hidden = which !== 'nowplaying';
  $('expand').style.transform = which === 'nowplaying' ? 'rotate(180deg)' : '';
  $('tab-pair').setAttribute('aria-pressed', String(which === 'pair'));
  $('tab-paste').setAttribute('aria-pressed', String(which === 'paste'));
  if (which === 'paste') $('urls').focus();
}
$('nowcard').onclick = () => showPanel($('nowplaying').hidden ? 'nowplaying' : null);
$('tab-pair').onclick = () => showPanel($('pair').hidden ? 'pair' : null);
$('tab-paste').onclick = () => showPanel($('paste').hidden ? 'paste' : null);

// A dialog closes on its own button, on the scrim behind it, and on Escape. All three, because
// people reach for all three and a dialog that only answers one of them feels stuck.
for (const overlay of document.querySelectorAll('.overlay')) {
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay || event.target.hasAttribute('data-close')) showPanel(null);
  });
}
addEventListener('keydown', (event) => {
  if (event.key === 'Escape') showPanel(null);
});

$('load').onclick = () => {
  const urls = $('urls').value.split('\n').map((s) => s.trim()).filter(Boolean);
  if (urls.length) setQueue(urls);
  showPanel(null);
};
$('playpause').onclick = async () => {
  // Nothing loaded yet, but a track is selected: this press is the one that starts it. That is the
  // gesture a browser insists on, and it is why a queue arriving does not play by itself.
  if (!loading && !$('seek').dataset.opened && index >= 0 && !playing) {
    playAt(index);
    return;
  }
  // **It reached the end and nothing followed, so this press means "again".** The bytes are still
  // in the worklet, so it rewinds rather than fetching them a second time.
  if (finished) {
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
$('prev').onclick = () => { const p = beforeCurrent(); if (p != null) playAt(p); };

/** Whether there is another tune inside the open file. */
const hasNextSubsong = () => currentSubsong + 1 < subsongCount;

function skipSubsong() {
  if (!hasNextSubsong()) return false;
  node?.port.postMessage({ type: 'subsong', index: currentSubsong + 1 });
  status(`Tune ${currentSubsong + 2} of ${subsongCount}`);
  return true;
}

/**
 * Next, and what it means depends on the switch — exactly as it does on the phone.
 *
 * With "play every tune in this file" on, the file is walked before the queue moves; with it off,
 * `next` is always the next file and the tunes inside are reached by their chips or by holding.
 */
$('next').onclick = () => {
  if (playAllSubsongs && skipSubsong()) return;
  const n = afterCurrent();
  if (n != null) playAt(n);
};

/**
 * Holding next skips *within* the file, whatever the switch says.
 *
 * The phone's dock does this and the reason carries over: the chips are behind a panel, and
 * wanting the next tune of a `.sndh` is not a reason to open one. `pointerdown` rather than
 * `mousedown`, so a finger works; the click that follows is swallowed, or the press would count
 * twice.
 */
let holdTimer = null;
let held = false;
$('next').addEventListener('pointerdown', () => {
  held = false;
  holdTimer = setTimeout(() => { held = skipSubsong(); }, 500);
});
for (const event of ['pointerup', 'pointercancel', 'pointerleave']) {
  $('next').addEventListener(event, () => clearTimeout(holdTimer));
}
$('next').addEventListener('click', (event) => {
  if (held) { event.stopImmediatePropagation(); event.preventDefault(); held = false; }
}, true);

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
$('np-save').onclick = () => { const e = queue[index]; if (e) saveFile(e); };
$('np-link').onclick = () => { const e = queue[index]; if (e) copyLink(e); };

$('seek').oninput = () => { seeking = true; paint($('seek')); };
$('seek').onchange = () => {
  seeking = false;
  node?.port.postMessage({ type: 'seek', seconds: (Number($('seek').value) / 1000) * duration });
};

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
  try {
    const packed = Uint8Array.from(atob(raw.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
    const stream = new Blob([packed]).stream().pipeThrough(new DecompressionStream('deflate'));
    const text = await new Response(stream).text();
    const lines = text.split('\n').map((s) => s.trim()).filter(Boolean);
    const base = 'https://modland.com/pub/modules/';
    // `address` or `address<tab>title`. The title is sent only when the address does not already
    // carry it -- which is most of Modland and none of The Mod Archive, whose URLs are a script and
    // a number (`QueueLink.withTitle`).
    setQueue(lines.map((line) => {
      // **A file that stayed on the phone**, sent as a name so its place in the list survives.
      // Its bytes are a storage grant to one app on one device and could never have come; the
      // *position* could, and two people cannot talk about a list that numbers itself differently
      // at each end (`docs/BACKLOG.md` A28).
      if (line.startsWith('phone:')) return ghost(line.slice('phone:'.length));
      const [address, title] = line.split('\t');
      const url = address.includes('://') ? address
        : base + address.split('/').map(encodeURIComponent).join('/');
      const entry = entryFor(url);
      return title ? { ...entry, name: title } : entry;
    }));
    const ghosts = lines.filter((l) => l.startsWith('phone:')).length;
    status(`${lines.length - ghosts} tracks from the link` +
           (ghosts ? `, and ${ghosts} that stayed on the phone` : ''));
  } catch (e) {
    status(`the link could not be read: ${e.message}`);
  }
}

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
    $('pairnote').textContent = 'Pairing needs the local server; open this page through it.';
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

// Open on the code: on a fresh page the first useful act is to point a phone at it. It closes
// itself the moment a queue arrives.
renderNothingPlaying();
showPanel('pair');

status('ready — press Play or load some URLs');
pair();
fromFragment();

// **A link opened in a tab that already has this page does not reload it.** Only the fragment
// changes, and the browser fires `hashchange` instead -- so without this, sending a second queue
// from the phone to an open tab appeared to do nothing at all until the owner pressed reload.
addEventListener('hashchange', fromFragment);
