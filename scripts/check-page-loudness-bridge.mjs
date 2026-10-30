// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { Worker } from 'node:worker_threads';
import EngineModule from '../web/vendor/engine.mjs';
import { stereoFixture, openFixture, renderPcm } from './fixtures/stereo.mjs';
import { nonzeroAudibleNsf } from './fixtures/loudness.mjs';
import { LOUDNESS_CACHE_KEY } from '../web/src/loudness-cache.js';
import { recordProfile } from '../web/src/loudness-profiles.js';

const until = async (predicate, timeout = 10000) => {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) { if (await predicate()) return true; await new Promise((done) => setTimeout(done, 5)); }
  return false;
};

/** Replace browser transports/output only; load the unchanged app, processor, worker and WASM. */
export async function prepare({ window }) {
  const bytes = stereoFixture('correlated'), wasm = fs.readFileSync('web/vendor/engine.wasm');
  const copy = (data) => data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength);
  const bridge = window.__loudnessBridge = { hold: false, held: [], holdDecoded: false, decoded: [],
    threads: new Set(), processor: null, commands: [], replies: [], workers: 0 };
  const fetch = window.fetch;
  window.fetch = (url, options) => {
    if (String(url).endsWith('engine.wasm')) return Promise.resolve({ ok: true, arrayBuffer: async () => copy(wasm) });
    if (String(url).startsWith('https://example.test/loudness-bridge/'))
      return Promise.resolve({ ok: true, arrayBuffer: async () => copy(String(url).endsWith('.nsf') ? nonzeroAudibleNsf() : bytes) });
    return fetch(url, options);
  };
  window.Worker = class {
    constructor(url, options) {
      assert(String(url).endsWith('/loudness-worker.js')); assert.equal(options.type, 'module');
      this.thread = new Worker(new URL('./fixtures/loudness-worker-node.mjs', import.meta.url));
      bridge.workers++; bridge.threads.add(this.thread);
      this.thread.on('message', (data) => {
        const deliver = () => this.onmessage?.({ data });
        if (bridge.hold) bridge.held.push(deliver); else deliver();
      });
      this.thread.on('error', (error) => this.onerror?.(error));
      this.thread.on('messageerror', (error) => this.onmessageerror?.(error));
    }
    postMessage(data, transfer) { this.thread.postMessage(data, transfer); }
    terminate() { bridge.threads.delete(this.thread); void this.thread.terminate(); }
  };
  window.AudioWorkletNode = class {
    constructor(_context, _name, options) {
      window.__workletOptions = options;
      let Processor;
      this.port = { onmessage: null, postMessage: (message, transfer) => {
        const data = structuredClone(message, transfer ? { transfer } : {});
        bridge.commands.push(data); window.__toWorklet.push(data);
        queueMicrotask(() => bridge.processor.port.onmessage({ data }));
      } };
      const port = this.port;
      const scope = { EngineModule, performance, sampleRate: 44100,
        AudioWorkletProcessor: class { constructor() {
          this.port = { postMessage: (message) => {
            bridge.replies.push(structuredClone(message));
            const deliver = () => queueMicrotask(() => port.onmessage?.({ data: structuredClone(message) }));
            if (bridge.holdDecoded && message.type === 'decoded') bridge.decoded.push(deliver); else deliver();
          } };
        } }, registerProcessor: (_name, type) => { Processor = type; } };
      const source = fs.readFileSync('web/src/processor.js', 'utf8').replace("import EngineModule from '../vendor/engine.mjs';", '');
      vm.runInNewContext(source, scope); bridge.processor = new Processor(options);
    }
    connect() {}
  };
  bridge.callback = (frames = 2048) => {
    const output = [new Float32Array(frames), new Float32Array(frames)];
    assert.equal(bridge.processor.process([], [output]), true);
    const pcm = new Float32Array(frames * 2);
    for (let n = 0; n < frames; n++) { pcm[n * 2] = output[0][n]; pcm[n * 2 + 1] = output[1][n]; }
    return pcm;
  };
  bridge.cleanup = async () => {
    bridge.processor?.close();
    await Promise.all([...bridge.threads].map((thread) => thread.terminate())); bridge.threads.clear();
  };
}

export async function verify({ window, check }) {
  const bridge = window.__loudnessBridge, api = window.__api, $ = (id) => window.document.getElementById(id);
  try {
    assert(api); api.showPanel('settings');
    check(!$('settings-normalization').checked && api.contextNow() === null, 'normalization starts Off without creating audio');
    $('settings-normalization').click();
    check($('settings-normalization').checked && api.contextNow() === null, 'the real Settings switch opts in without autoplay');
    const track = (file) => ({ url: `https://example.test/loudness-bridge/${file}`, file, name: file });
    await api.storeNow.save({ id: 'loudness-first', name: 'First', tracks: [track('first.mod')], index: 0 });
    await api.choosePlaylist('loudness-first');
    $('queue').querySelector('li.track').click(); window.document.body.click();
    assert(await until(() => bridge.processor?.handle && !bridge.processor.awaitingPreparation && $('playpause').title === 'Pause'));
    check(bridge.workers > 0 && bridge.commands.some((m) => m.type === 'prepared' && m.profile),
      'the actual row tap reaches decoder, separate native worker and first-PCM preparation');
    const engine = bridge.processor.engine, handle = bridge.processor.handle;
    check(engine._pt_position(handle) === 0, 'no PCM or playback position advances before the prepared callback');
    const known = (await api.settingsNow.get(LOUDNESS_CACHE_KEY) ?? []).map(recordProfile).find(Boolean);
    assert(known?.complete);
    const rawHandle = openFixture(engine, stereoFixture('correlated'));
    try {
      const raw = renderPcm(engine, rawHandle, 44100, 2048), pcm = bridge.callback();
      const gain = Math.fround(Math.min(10 ** (Math.min(12, Math.max(-36, -18 - known.lufs)) / 20), Math.fround(.794328235) / known.peak));
      check(raw.some((v) => Math.abs(v) > .01) && pcm.every((v, n) => Math.abs(v - raw[n] * gain) < 1e-7),
        'the first real app/worklet PCM uses the prepared native trim without an unnormalized burst');
    } finally { engine._pt_close(rawHandle); }
    assert(await until(async () => await window.__played.count() === 1));
    $('playpause').click(); assert(await until(() => !bridge.processor.playing));
    const paused = engine._pt_position(handle); $('settings-normalization').click();
    assert(await until(() => !bridge.processor.normalizationEnabled));
    check(bridge.callback().every((v) => v === 0) && engine._pt_position(handle) === paused,
      'a live normalization switch preserves the paused decoder and its position');
    check(bridge.threads.size === 0, 'turning normalization Off releases the idle analysis worker without closing live audio');
    $('settings-normalization').click(); assert(await until(() => bridge.processor.normalizationEnabled));
    await api.storeNow.save({ id: 'loudness-second', name: 'Second', tracks: [track('second.mod')], index: 0 });
    await api.choosePlaylist('loudness-second'); bridge.hold = true;
    const oldRequest = bridge.commands.filter((row) => row.type === 'open').at(-1).requestId;
    $('queue').querySelector('li.track').click(); window.document.body.click();
    assert(await until(() => bridge.held.length > 0 && bridge.processor.awaitingPreparation));
    check(bridge.replies.findLast((row) => row.type === 'closed')?.requestId === oldRequest
      && bridge.replies.findLast((row) => row.type === 'loudness' && row.reason === 'close')?.requestId === oldRequest,
      'old close/profile notifications retain their own request identity before the new open');
    check(bridge.callback().every((value) => value === 0), 'an actual callback stays silent while startup preparation is pending');
    check(await window.__played.count() === 1 && engine._pt_position(bridge.processor.handle) === 0,
      'a privately decoded file awaiting measurement neither plays nor enters History');
    $('playpause').click(); assert(await until(() => bridge.processor.handle === 0));
    const before = bridge.commands.filter((m) => m.type === 'prepared').length;
    bridge.hold = false; for (const release of bridge.held.splice(0)) release();
    await new Promise((done) => setTimeout(done, 20));
    check(await window.__played.count() === 1 && bridge.commands.filter((m) => m.type === 'prepared').length === before,
      'Stop rejects a late real worker result without recording or reopening the cancelled file');
    $('playpause').click(); window.document.body.click();
    assert(await until(() => bridge.processor?.handle && !bridge.processor.awaitingPreparation && $('playpause').title === 'Pause'));
    assert(await until(async () => await window.__played.count() === 2));
    check(bridge.callback().some((v) => Math.abs(v) > .001), 'an explicit replay after cancellation prepares and renders successfully');
    await api.storeNow.save({ id: 'loudness-nonzero', name: 'Nonzero initial tune', tracks: [track('nonzero.nsf')], index: 0 });
    await api.choosePlaylist('loudness-nonzero');
    const beforeNonzero = bridge.commands.filter((row) => row.type === 'prepared').length;
    $('queue').querySelector('li.track').click(); window.document.body.click();
    assert(await until(() => bridge.commands.filter((row) => row.type === 'prepared').length > beforeNonzero
      && bridge.processor?.handle && !bridge.processor.awaitingPreparation && $('playpause').title === 'Pause'));
    const prepared = bridge.commands.filter((row) => row.type === 'prepared').at(-1);
    check(prepared.current === 1 && !!prepared.profile && engine._pt_current_subsong(bridge.processor.handle) === 1,
      'the actual GME decoder selects a nonzero audible tune and prepares that exact subsong before PCM');
    check(bridge.callback().some((v) => Math.abs(v) > .001), 'the prepared nonzero GME tune renders audible PCM');
    assert(await until(async () => await window.__played.count() === 3));
    await api.storeNow.save({ id: 'loudness-timeout', name: 'Unanswered decoder', tracks: [track('timeout.mod')], index: 0 });
    await api.choosePlaylist('loudness-timeout'); bridge.holdDecoded = true;
    $('queue').querySelector('li.track').click(); window.document.body.click();
    assert(await until(() => bridge.decoded.length > 0));
    // Exercise the real ten-second watchdog, not a direct call to its timeout handler.
    const expired = await until(() => $('error').textContent.includes('never answered'), 15000);
    check(expired && bridge.processor.handle === 0 && $('playpause').title === 'Play',
      'the actual open deadline cancels private preparation and closes the unanswered decoder');
    bridge.holdDecoded = false; for (const release of bridge.decoded.splice(0)) release();
    await new Promise((done) => setTimeout(done, 100));
    check(bridge.processor.handle === 0 && await window.__played.count() === 3,
      'a late decoded reply after the deadline cannot start playback or enter History');
    check(!window.__browserErrors.length, 'the real ESM/worker/worklet bridge reports no browser-script errors');
  } finally { await bridge.cleanup(); }
}
