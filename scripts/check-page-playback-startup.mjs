// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { deflateSync } from 'node:zlib';
import { zipFixture } from './fixtures/zip.mjs';
import { zipDirectory } from '../web/src/zip-archive.js';

const globalTracks = ['a', 'b'].map((name) => ({ url: `https://example.test/playback-startup/${name}.mod`, name }));
const cases = {
  'stereo-narrow': { raw: { stereoMix: 'narrow' }, shuffle: false, repeat: 'off', all: false, stereo: 'narrow' },
  'stereo-mono': { raw: { stereoMix: 'mono' }, shuffle: false, repeat: 'off', all: false, stereo: 'mono' },
  'stereo-malformed': { raw: { stereoMix: 'future-mode' }, shuffle: false, repeat: 'off', all: false },
  'stereo-denied': { raw: {}, shuffle: false, repeat: 'off', all: false },
  'stereo-loading': { raw: { stereoMix: 'narrow' }, shuffle: false, repeat: 'off', all: false, stereo: 'narrow' },
  defaults: { raw: {}, shuffle: false, repeat: 'off', all: false },
  cache: { raw: {}, shuffle: false, repeat: 'off', all: false },
  asma: { raw: {}, shuffle: false, repeat: 'off', all: false },
  empty: { raw: { shuffle: '1', repeat: 'one', allsubsongs: '1' }, shuffle: true, repeat: 'one', all: true },
  malformed: { raw: { shuffle: 'true', repeat: 'future-mode', allsubsongs: 'yes' }, shuffle: false, repeat: 'off', all: false },
  one: { raw: { shuffle: '1', repeat: 'one', allsubsongs: '1' }, shuffle: true, repeat: 'one', all: true },
  all: { raw: { shuffle: '0', repeat: 'all', allsubsongs: '1' }, shuffle: false, repeat: 'all', all: true },
  legacy: { raw: { allsubsongs: '1' }, shuffle: false, repeat: 'off', all: true },
  denied: { raw: {}, shuffle: false, repeat: 'off', all: false },
  'prefetch-always': { raw: { cacheAhead: 'always' }, shuffle: false, repeat: 'off', all: false, folderMode: 'always' },
  'prefetch-off': { raw: { cacheAhead: 'off' }, shuffle: false, repeat: 'off', all: false, folderMode: 'off' },
  'prefetch-denied': { raw: {}, shuffle: false, repeat: 'off', all: false, folderMode: 'wifi' },
  delayed: { raw: { shuffle: '1', repeat: 'one' }, shuffle: true, repeat: 'one', all: false },
  'delayed-failure': { raw: { shuffle: '1', repeat: 'one' }, shuffle: true, repeat: 'one', all: false },
  fragment: { raw: { shuffle: '1', repeat: 'all' }, shuffle: true, repeat: 'all', all: false },
};
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const until = async (fn) => { for (let n = 0; n < 200; n++) { if (fn()) return true; await wait(10); } return false; };

/** Seed through the production store before the common page entry; no alternate app bootstrap. */
export async function prepare({ window, rulesSource, storeSource, name }) {
  const choice = cases[name]; assert(choice, `Unknown startup case: ${name}`);
  const tracks = name === 'asma' ? [{ url: 'https://example.test/playback-startup/a.mod', name: 'a' },
    { url: 'https://asma.atari.org/asma/Games/start.sap', name: 'start.sap' }] : [...globalTracks];
  for (const [key, value] of Object.entries(choice.raw)) window.localStorage.setItem(`protracktor.${key}`, value);
  const store = window.eval(`(() => { ${rulesSource} ${storeSource}; return { playlists, settings, musicCache, asmaArchive }; })()`);
  await store.playlists.save({ id: 'startup-list', name: 'Stored playlist', tracks, index: 1 });
  await store.playlists.save({ id: 'newer-list', name: 'Newer choice', tracks: [tracks[0]], index: 0 });
  if (name !== 'empty') await store.settings.set('active', 'startup-list');
  if (name === 'asma') {
    const bytes = zipFixture([{name:'asma/Games/start.sap',body:new Uint8Array([21,22,23,24])}]);
    await store.asmaArchive.replace({id:'asma',schema:1,body:await new window.Response(bytes).blob(),entries:zipDirectory(bytes),tracks:1,total:1,at:1},
      [{key:'asma:meta',tracks:1,total:1,complete:true}],{ticket:await store.asmaArchive.ticket()});
  }
  if (name === 'cache') await store.musicCache.write(tracks[1].url, await new window.Response(new Uint8Array([21, 22, 23, 24])).blob());
  const state = window.__startup = { writes: [], preferenceWrites: [], notices: [], choice, tracks,
    initialRepeatGlyph: window.document.querySelector('#repeat path').getAttribute('d') };
  const fetch = window.fetch; state.trackRequests = 0;
  window.fetch = (...args) => { if (tracks.some((track) => track.url === String(args[0]))) state.trackRequests++;
    if (name === 'stereo-loading' && String(args[0]).endsWith('engine.wasm')) return fetch(...args).then(async (reply) => {
      await new Promise((resolve) => { state.releaseEngine = resolve; }); return reply;
    });
    return fetch(...args); };
  // Cold module evaluation can outlast a snackbar. Observe its visible lifetime, not hidden text.
  const bar = window.document.getElementById('snackbar'), caption = window.document.getElementById('snacktext');
  const notices = new window.MutationObserver(() => {
    if (!bar.hidden && caption.textContent) state.notices.push(caption.textContent);
  });
  notices.observe(bar, { attributes: true, attributeFilter: ['hidden'], childList: true, characterData: true, subtree: true });
  // Native startup enforces fetched-cache budgets; user documents still initialize read-only.
  const transaction = IDBDatabase.prototype.transaction;
  IDBDatabase.prototype.transaction = function (stores, mode, ...rest) {
    if (mode === 'readwrite') state.writes.push(...(typeof stores === 'string' ? [stores] : stores));
    return transaction.call(this, stores, mode, ...rest);
  };
  const set = window.Storage.prototype.setItem, get = window.Storage.prototype.getItem;
  window.Storage.prototype.setItem = function (key, value) {
    if (/^protracktor\.(shuffle|repeat|allsubsongs|cacheAhead|stereoMix)$/.test(key)) state.preferenceWrites.push([key, value]);
    return set.call(this, key, value);
  };
  if (name === 'denied') window.Storage.prototype.getItem = function (key) {
    if (/^protracktor\.(shuffle|repeat|allsubsongs)$/.test(key)) throw new Error('playback preference read refused');
    return get.call(this, key);
  };
  if (name === 'stereo-denied') window.Storage.prototype.getItem = function (key) {
    if (key === 'protracktor.stereoMix') throw new Error('stereo mix preference read refused');
    return get.call(this, key);
  };
  if (name === 'prefetch-denied') window.Storage.prototype.getItem = function (key) {
    if (key === 'protracktor.cacheAhead') throw new Error('folder download preference read refused');
    return get.call(this, key);
  };
  if (name.startsWith('delayed')) window.navigator.storage.persisted = () => new Promise((resolve) => { state.release = () => resolve(true); });
  if (name === 'fragment') window.location.hash = deflateSync('https://example.test/explicit-link.mod').toString('base64url');
  state.cleanup = () => { notices.disconnect(); IDBDatabase.prototype.transaction = transaction; window.Storage.prototype.setItem = set; window.Storage.prototype.getItem = get; };
}

export async function verify({ window, check, name }) {
  const api = window.__api, { document } = window, $ = (id) => document.getElementById(id), state = window.__startup;
  const tracks = state.tracks;
  if (!api) { state.cleanup(); return; }
  try {
    if (name === 'empty') {
      await wait(600);
      check(api.editorNow().id === 'phone' && api.actualNow() === null, 'no saved selection retains the empty phone view without inventing a current track');
      check(state.writes.every((name) => name === 'remoteMusic'), 'empty initialization does not write a default playlist');
    } else if (name.startsWith('delayed')) {
      check(api.editorNow().id === 'phone' && !!state.release, 'delayed storage has not prematurely published a saved editor');
      const get = api.storeNow.get; let held = false, release;
      try {
        api.storeNow.get = async function (id) {
          const value = await get.call(this, id);
          if (id === 'startup-list') {
            held = true; await new Promise((resolve) => { release = resolve; });
            if (name === 'delayed-failure') throw new Error('retired saved read failed');
          }
          return value;
        };
        state.release();
        check(await until(() => held), 'the saved playlist read captures old data before the newer choice');
        await api.choosePlaylist('newer-list');
        release(); await wait(100);
        check(api.editorNow().id === 'newer-list', 'a newer playlist choice wins over the delayed startup restore');
        if (name === 'delayed-failure') check(!$('status').textContent.includes('retired saved read failed'), 'a retired startup read failure cannot replace the newer view feedback');
      } finally { release?.(); api.storeNow.get = get; }
    } else if (name === 'fragment') {
      check(await until(() => api.actualNow()?.url === 'https://example.test/explicit-link.mod'), 'explicit full-list fragment wins over the saved playlist');
      check(api.editorNow().id === 'phone', 'the explicit handoff owns the editor after initialization');
    } else {
      check(await until(() => api.editorNow().id === 'startup-list'), 'fresh startup restores the saved playlist');
      check(api.actualNow()?.url === tracks[1].url && api.indexNow() === 1, 'fresh startup selects the stored second track');
      await wait(600);
      check(state.writes.every((name) => name === 'remoteMusic'), 'restoring a saved playlist performs no initialization database writes');
    }
    const order = api.planNow().context.order, { choice } = state;
    const selectedMix = choice.stereo ?? 'original';
    check(document.querySelector(`input[name="stereomix"][value="${selectedMix}"]`)?.checked,
      `${name}: startup restores the native global stereo choice`);
    check(api.folderAheadNow().mode === (choice.folderMode ?? 'wifi')
      && document.querySelector(`input[name="cacheahead"][value="${choice.folderMode ?? 'wifi'}"]`).checked,
      `${name}: startup restores the folder policy without changing playback or writing defaults`);
    check(order.shuffle === choice.shuffle && order.repeat === choice.repeat, `${name}: restored transport modes match native defaults and saved values`);
    check(api.editorNow().order.shuffle === choice.shuffle && api.editorNow().order.repeat === choice.repeat, `${name}: visible editor and initial context receive the same restored modes`);
    check($('shuffle').getAttribute('aria-pressed') === String(choice.shuffle), `${name}: restored shuffle presentation is explicit`);
    check($('repeat').title === `Repeat ${choice.repeat}` && $('repeat').classList.contains('on') === (choice.repeat !== 'off'), `${name}: restored repeat mode has its accessible name and active state`);
    if (choice.repeat === 'one') check($('repeat').querySelector('path').getAttribute('d') !== state.initialRepeatGlyph, 'restored Repeat One uses its own glyph before any click');
    check($('settings-allsubsongs').checked === choice.all && $('allsubsongs').getAttribute('aria-checked') === String(choice.all), `${name}: both subsong controls reflect the one restored preference`);
    check(api.contextNow() === null && window.__toWorklet.length === 0 && $('playpause').title === 'Play', `${name}: startup never constructs audio or opens a decoder`);
    check(state.trackRequests === 0, `${name}: restoring a playlist or folder policy never fetches speculative music`);
    check(!api.dirtyNow(), `${name}: restored modes are not playlist content edits`);
    check(state.preferenceWrites.length === 0, `${name}: initialization does not write defaults over saved preferences`);
    if (name.startsWith('stereo-')) {
      if (name !== 'stereo-denied') check(window.localStorage.getItem('protracktor.stereoMix') === (choice.raw.stereoMix ?? null),
        'startup preserves the exact retained stereo preference without rewriting a fallback');
      if (name === 'stereo-denied') check(state.notices.some((text) => text.includes('stereo mix preference read refused')),
        'a refused stereo read explains the Original fallback without starting audio');
      $('playpause').click();
      if (name === 'stereo-loading') {
        check(await until(() => !!state.releaseEngine), 'the actual engine load can be delayed while Settings stays usable');
        api.showPanel('settings'); document.querySelector('input[name="stereomix"][value="mono"]').click();
        state.releaseEngine();
      }
      check(await until(() => !!window.__workletOptions), 'an explicit first Play constructs the actual worklet');
      const width = name === 'stereo-loading' ? 0 : selectedMix === 'narrow' ? 50 : selectedMix === 'mono' ? 0 : 100;
      check(window.__workletOptions?.processorOptions.stereoWidth === width,
        'the latest selected stereo width reaches the first worklet construction after asynchronous loading');
    }
    if (name === 'cache' || name === 'asma') {
      $('playpause').click();
      check(await until(() => window.__toWorklet.some((message) => message.type === 'open')),
        'a cold page can explicitly start its previously retained music');
      const opened = window.__toWorklet.find((message) => message.type === 'open');
      check(state.trackRequests === 0 && opened?.bytes.byteLength === 4 && new Uint8Array(opened.bytes)[0] === 21,
        'fresh production module state reads the retained original bytes without a network fetch');
      if (opened) api.onWorklet({ type: 'opened', requestId: opened.requestId, describe: '', duration: 90, rate: 44100, subsongs: 1, current: 0, canSeek: true });
    }
    if (name === 'denied') check(state.notices.some((text) => text.includes('playback preference read refused')), 'a refused playback read is visibly explained while the saved playlist still restores paused');
    if (name === 'prefetch-denied') check(state.notices.some((text) => text.includes('folder download preference read refused')),
      'a refused folder preference read explains its safe default without starting downloads');
  } finally { state.cleanup(); }
}
