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
  try { name = decodeURIComponent(new URL(url, location.href).pathname.split('/').pop()) || url; }
  catch { /* a malformed URL is still worth showing; it will fail loudly when played */ }
  return { url, name };
}

async function start() {
  if (context) return;
  // Created on a click, because a browser will not let audio start without one. The *first* tune
  // pushed to a fresh tab therefore cannot play by itself, and saying so beats looking broken
  // (`docs/PLAN_HANDOFF.md` §4).
  context = new AudioContext();
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
  node.port.postMessage({ type: 'open', bytes, name: entry.name }, [bytes]);
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
}

function setQueue(urls) {
  queue = urls.map(entryFor);
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
    setQueue(lines.map((line) => (line.includes('://') ? line : base + line.split('/').map(encodeURIComponent).join('/'))));
    status(`${lines.length} tracks from the link`);
  } catch (e) {
    status(`the link could not be read: ${e.message}`);
  }
}

status('ready — press Play or load some URLs');
fromFragment();
