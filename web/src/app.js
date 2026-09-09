// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The main thread: fetches bytes, drives the worklet, draws the queue. It never touches audio.

const $ = (id) => document.getElementById(id);
const status = (text) => { $('status').textContent = text; };
const clock = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;

let context = null;
let node = null;
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

/**
 * Everything a queue entry needs, from a URL alone.
 *
 * The name matters and is not decoration: four backends choose a loader by the file's extension,
 * so a URL whose last segment is lost would arrive as a nameless blob and be declined by all of
 * them (`docs/PLAN_HANDOFF.md` §5a).
 */
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

async function start() {
  if (context) return;
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
  node.connect(context.destination);
  node.port.onmessage = (event) => onWorklet(event.data);
}

function onWorklet(message) {
  switch (message.type) {
    case 'ready':
      status(`engine ready — ${message.backends}`);
      break;
    case 'opened': {
      duration = message.duration;
      const fields = describeFields(message.describe);
      if (fields.title) $('title').textContent = fields.title;
      renderNowPlaying(fields, message.subsongs ?? 1, 0);
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
      $('error').textContent = message.reason;
      setPlaying(false);
      break;
    case 'position':
      if (!seeking) {
        $('seek').value = duration > 0 ? Math.round((message.seconds / duration) * 1000) : 0;
        $('elapsed').textContent = clock(message.seconds);
        $('remaining').textContent = clock(duration);
      }
      break;
    case 'ended': {
      // What a queue is for, and where the modes actually show: repeat-one plays it again, shuffle
      // takes the next of the permutation, repeat-all wraps, and off stops.
      const next = afterCurrent();
      if (next == null) setPlaying(false);
      else if (next === index && repeat === 'one') playAt(index);
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

function renderNowPlaying(fields, subsongs, current) {
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
  const strip = $('subsongs');
  strip.replaceChildren();
  if (subsongs > 1) {
    for (let i = 0; i < subsongs; i++) {
      const button = document.createElement('button');
      button.className = 'subsong';
      button.textContent = String(i + 1);
      button.setAttribute('aria-pressed', String(i === current));
      button.onclick = () => {
        node?.port.postMessage({ type: 'subsong', index: i });
        for (const other of strip.children) other.setAttribute('aria-pressed', 'false');
        button.setAttribute('aria-pressed', 'true');
        setPlaying(true);
      };
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
  await start();
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
  try {
    $('sub').textContent = 'fetching…';
    const response = await fetch(entry.url);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    bytes = await response.arrayBuffer();
  } catch (e) {
    // A fetch that fails here is usually CORS or a network that blocks the archive, and those are
    // different problems from a file the decoders refuse. Say which.
    $('error').textContent = `could not fetch it: ${e.message}`;
    $('sub').textContent = '—';
    return;
  }
  // Handed over, and the page now waits for the worklet to say `opened` or `failed`. It says which
  // it is waiting for, because "fetching…" left standing after the fetch finished is a lie.
  $('sub').textContent = `${(bytes.byteLength / 1024).toFixed(0)} KB — opening…`;
  node.port.postMessage({ type: 'open', bytes, name: entry.name }, [bytes]);
  announceGesture();
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
  $('playglyph').setAttribute('d', on ? PAUSE_GLYPH : PLAY_GLYPH);
  $('playpause').title = on ? 'Pause' : 'Play';
  $('playpause').disabled = queue.length === 0;
  // Asked of the modes rather than of the position, exactly as `PlayerState.canGoNext` is: under
  // repeat-all the last track does have a next, and under shuffle the row above is not the previous.
  $('prev').disabled = beforeCurrent() == null;
  $('next').disabled = afterCurrent() == null;
  $('shuffle').disabled = queue.length === 0;
  $('repeat').disabled = queue.length === 0;
  $('shuffle').classList.toggle('on', shuffle);
  $('repeat').classList.toggle('on', repeat !== 'off');
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
    li.className = i === index ? 'track playing' : 'track';

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

    li.append(n, text);
    li.onclick = () => playAt(i);
    return li;
  }));
  $('count').textContent = queue.length
    ? `${queue.length} track${queue.length === 1 ? '' : 's'}`
    : 'nothing yet';
}

/** "Modland/Protracker/4-Mat", the way the phone's subtitle reads. */
function sourceOf(url) {
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

/** A fresh permutation. A new lap is a new shuffle; replaying one order forever is not shuffle. */
function reshuffle(keep) {
  order = queue.map((_, i) => i);
  for (let i = order.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [order[i], order[j]] = [order[j], order[i]];
  }
  if (keep != null) {
    // What is playing keeps playing: reordering what comes next is not a reason to interrupt now.
    order = [keep, ...order.filter((i) => i !== keep)];
  }
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
  if (index + 1 < queue.length) return index + 1;
  return repeat === 'all' ? 0 : null;
}

/** What `previous` should play, or null. */
function beforeCurrent() {
  if (shuffle) {
    // The history holds what was really played; the cursor is its end because the page only ever
    // walks backwards from now.
    const at = history.lastIndexOf(index);
    return at > 0 ? history[at - 1] : null;
  }
  if (index > 0) return index - 1;
  return repeat === 'all' && queue.length ? queue.length - 1 : null;
}

function setQueue(urls) {
  // Entries already built keep their names; bare strings become entries here. The paste box gives
  // strings, a link gives entries with the phone's own titles.
  queue = urls.map((u) => (typeof u === 'string' ? entryFor(u) : u));
  index = -1;
  history = [];
  if (shuffle) reshuffle(null);
  render();
  setPlaying(false);
  if (queue.length) playAt(0);
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
  await start();
  if (context.state === 'suspended') await context.resume();
  setPlaying(!playing);
  node.port.postMessage({ type: playing ? 'play' : 'pause' });
};
$('prev').onclick = () => { const p = beforeCurrent(); if (p != null) playAt(p); };
$('next').onclick = () => { const n = afterCurrent(); if (n != null) playAt(n); };

const REPEAT_GLYPH = 'M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z';
const REPEAT_ONE_GLYPH = 'M7 7h10v3l4-4-4-4v3H5v6h2V7zm6 10H7v-3l-4 4 4 4v-3h10v-6h-2v4zm-2-6h-1l-2 1v1h1.5V15H11v-4z';

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
$('seek').oninput = () => { seeking = true; };
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
      const [address, title] = line.split('\t');
      const url = address.includes('://') ? address
        : base + address.split('/').map(encodeURIComponent).join('/');
      const entry = entryFor(url);
      return title ? { ...entry, name: title } : entry;
    }));
    status(`${lines.length} tracks from the link`);
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
  setQueue(message.queue.map((row) => ({
    ...entryFor(row.url),
    name: row.title || entryFor(row.url).name,
  })));
  status(`${message.queue.length} tracks from the phone`);
  // The phone says which one it was on. Starting anywhere else would be the handoff losing the one
  // thing a listener actually cares about.
  if (message.index > 0 && message.index < message.queue.length) playAt(message.index);
  announceGesture();
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
  }[event.key];
  if (!act) return;
  event.preventDefault();
  act();
});

// Open on the code: on a fresh page the first useful act is to point a phone at it. It closes
// itself the moment a queue arrives.
showPanel('pair');

status('ready — press Play or load some URLs');
pair();
fromFragment();

// **A link opened in a tab that already has this page does not reload it.** Only the fragment
// changes, and the browser fires `hashchange` instead -- so without this, sending a second queue
// from the phone to an open tab appeared to do nothing at all until the owner pressed reload.
addEventListener('hashchange', fromFragment);
