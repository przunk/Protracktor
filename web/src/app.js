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
    case 'opened':
      duration = message.duration;
      // A backend that wants a rate this context cannot give would play sharp and say nothing.
      // Today they all want 44,100; if one ever does not, this says so instead of transposing it.
      if (message.preferredRate > 0 && message.preferredRate !== message.rate) {
        status(`⚠ this decoder wants ${message.preferredRate} Hz and the page is running at ` +
               `${message.rate} Hz — it will play ${(message.rate / message.preferredRate).toFixed(3)}× fast`);
      }
      $('sub').textContent = describeLine(message.describe);
      $('seek').disabled = !message.canSeek;
      $('error').textContent = '';
      setPlaying(true);
      break;
    case 'failed':
      $('error').textContent = message.reason;
      setPlaying(false);
      break;
    case 'position':
      if (!seeking) {
        $('seek').value = duration > 0 ? Math.round((message.seconds / duration) * 1000) : 0;
        $('time').textContent = `${clock(message.seconds)} / ${clock(duration)}`;
      }
      break;
    case 'ended':
      // What a queue is for. The last track stops rather than wrapping, which is what the phone
      // does with repeat off.
      if (index + 1 < queue.length) playAt(index + 1); else setPlaying(false);
      break;
  }
}

/** The engine's describe block is tab-separated key/value lines; the row wants one line. */
function describeLine(describe) {
  const fields = Object.fromEntries(
    describe.split('\n').filter(Boolean).map((line) => line.split('\t'))
  );
  return [fields.format, fields.artist, fields.tracker].filter(Boolean).join(' · ') || '—';
}

async function playAt(next) {
  await start();
  index = next;
  const entry = queue[index];
  render();
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
    status('▶ press Play — a browser will not start audio until this page is clicked');
    $('playpause').textContent = 'Play ▶';
  }
}

function setPlaying(on) {
  playing = on;
  $('playpause').textContent = on ? 'Pause' : 'Play';
  $('playpause').disabled = queue.length === 0;
  $('prev').disabled = index <= 0;
  $('next').disabled = index + 1 >= queue.length;
}

function render() {
  const list = $('queue');
  list.replaceChildren(...queue.map((entry, i) => {
    const li = document.createElement('li');
    if (i === index) li.className = 'playing';
    const n = document.createElement('span'); n.className = 'n'; n.textContent = String(i + 1);
    const name = document.createElement('span'); name.className = 'name'; name.textContent = entry.name;
    li.append(n, name);
    li.onclick = () => playAt(i);
    return li;
  }));
  $('paste').hidden = queue.length > 0;
  // Shrunk rather than hidden. It is the way a second playlist arrives, so it has to stay on
  // screen; full size next to a playing queue is a poster for something already done.
  $('pair').className = queue.length > 0 ? 'small' : '';
}

function setQueue(urls) {
  // Entries already built keep their names; bare strings become entries here. The paste box gives
  // strings, a link gives entries with the phone's own titles.
  queue = urls.map((u) => (typeof u === 'string' ? entryFor(u) : u));
  index = -1;
  render();
  setPlaying(false);
  if (queue.length) playAt(0);
}

$('load').onclick = () => {
  const urls = $('urls').value.split('\n').map((s) => s.trim()).filter(Boolean);
  if (urls.length) setQueue(urls);
};
$('playpause').onclick = async () => {
  await start();
  if (context.state === 'suspended') await context.resume();
  setPlaying(!playing);
  node.port.postMessage({ type: playing ? 'play' : 'pause' });
};
$('prev').onclick = () => index > 0 && playAt(index - 1);
$('next').onclick = () => index + 1 < queue.length && playAt(index + 1);
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

status('ready — press Play or load some URLs');
pair();
fromFragment();

// **A link opened in a tab that already has this page does not reload it.** Only the fragment
// changes, and the browser fires `hashchange` instead -- so without this, sending a second queue
// from the phone to an open tab appeared to do nothing at all until the owner pressed reload.
addEventListener('hashchange', fromFragment);
