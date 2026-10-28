// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { deflateSync } from 'node:zlib';

const tracks = ['a', 'b'].map((name) => ({ url: `https://example.test/playback-startup/${name}.mod`, name }));
const cases = {
  defaults: { raw: {}, shuffle: false, repeat: 'off', all: false },
  empty: { raw: { shuffle: '1', repeat: 'one', allsubsongs: '1' }, shuffle: true, repeat: 'one', all: true },
  malformed: { raw: { shuffle: 'true', repeat: 'future-mode', allsubsongs: 'yes' }, shuffle: false, repeat: 'off', all: false },
  one: { raw: { shuffle: '1', repeat: 'one', allsubsongs: '1' }, shuffle: true, repeat: 'one', all: true },
  all: { raw: { shuffle: '0', repeat: 'all', allsubsongs: '1' }, shuffle: false, repeat: 'all', all: true },
  legacy: { raw: { allsubsongs: '1' }, shuffle: false, repeat: 'off', all: true },
  denied: { raw: {}, shuffle: false, repeat: 'off', all: false },
  delayed: { raw: { shuffle: '1', repeat: 'one' }, shuffle: true, repeat: 'one', all: false },
  'delayed-failure': { raw: { shuffle: '1', repeat: 'one' }, shuffle: true, repeat: 'one', all: false },
  fragment: { raw: { shuffle: '1', repeat: 'all' }, shuffle: true, repeat: 'all', all: false },
};
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const until = async (fn) => { for (let n = 0; n < 200; n++) { if (fn()) return true; await wait(10); } return false; };

/** Seed through the production store before the common page entry; no alternate app bootstrap. */
export async function prepare({ window, rulesSource, storeSource, name }) {
  const choice = cases[name]; assert(choice, `Unknown startup case: ${name}`);
  for (const [key, value] of Object.entries(choice.raw)) window.localStorage.setItem(`protracktor.${key}`, value);
  const store = window.eval(`(() => { ${rulesSource} ${storeSource}; return { playlists, settings }; })()`);
  await store.playlists.save({ id: 'startup-list', name: 'Stored playlist', tracks, index: 1 });
  await store.playlists.save({ id: 'newer-list', name: 'Newer choice', tracks: [tracks[0]], index: 0 });
  if (name !== 'empty') await store.settings.set('active', 'startup-list');
  const state = window.__startup = { writes: [], preferenceWrites: [], choice, tracks,
    initialRepeatGlyph: window.document.querySelector('#repeat path').getAttribute('d') };
  const transaction = IDBDatabase.prototype.transaction;
  IDBDatabase.prototype.transaction = function (stores, mode, ...rest) {
    if (mode === 'readwrite') state.writes.push(...(typeof stores === 'string' ? [stores] : stores));
    return transaction.call(this, stores, mode, ...rest);
  };
  const set = window.Storage.prototype.setItem, get = window.Storage.prototype.getItem;
  window.Storage.prototype.setItem = function (key, value) {
    if (/^protracktor\.(shuffle|repeat|allsubsongs)$/.test(key)) state.preferenceWrites.push([key, value]);
    return set.call(this, key, value);
  };
  if (name === 'denied') window.Storage.prototype.getItem = function (key) {
    if (/^protracktor\.(shuffle|repeat|allsubsongs)$/.test(key)) throw new Error('playback preference read refused');
    return get.call(this, key);
  };
  if (name.startsWith('delayed')) window.navigator.storage.persisted = () => new Promise((resolve) => { state.release = () => resolve(true); });
  if (name === 'fragment') window.location.hash = deflateSync('https://example.test/explicit-link.mod').toString('base64url');
  state.cleanup = () => { IDBDatabase.prototype.transaction = transaction; window.Storage.prototype.setItem = set; window.Storage.prototype.getItem = get; };
}

export async function verify({ window, check, name }) {
  const api = window.__api, { document } = window, $ = (id) => document.getElementById(id), state = window.__startup;
  if (!api) { state.cleanup(); return; }
  try {
    if (name === 'empty') {
      await wait(600);
      check(api.editorNow().id === 'phone' && api.actualNow() === null, 'no saved selection retains the empty phone view without inventing a current track');
      check(state.writes.length === 0, 'empty initialization does not write a default playlist');
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
      check(state.writes.length === 0, 'restoring a saved playlist performs no initialization database writes');
    }
    const order = api.planNow().context.order, { choice } = state;
    check(order.shuffle === choice.shuffle && order.repeat === choice.repeat, `${name}: restored transport modes match native defaults and saved values`);
    check(api.editorNow().order.shuffle === choice.shuffle && api.editorNow().order.repeat === choice.repeat, `${name}: visible editor and initial context receive the same restored modes`);
    check($('shuffle').getAttribute('aria-pressed') === String(choice.shuffle), `${name}: restored shuffle presentation is explicit`);
    check($('repeat').title === `Repeat ${choice.repeat}` && $('repeat').classList.contains('on') === (choice.repeat !== 'off'), `${name}: restored repeat mode has its accessible name and active state`);
    if (choice.repeat === 'one') check($('repeat').querySelector('path').getAttribute('d') !== state.initialRepeatGlyph, 'restored Repeat One uses its own glyph before any click');
    check($('settings-allsubsongs').checked === choice.all && $('allsubsongs').getAttribute('aria-checked') === String(choice.all), `${name}: both subsong controls reflect the one restored preference`);
    check(api.contextNow() === null && window.__toWorklet.length === 0 && $('playpause').title === 'Play', `${name}: startup never constructs audio or opens a decoder`);
    check(!api.dirtyNow(), `${name}: restored modes are not playlist content edits`);
    check(state.preferenceWrites.length === 0, `${name}: initialization does not write defaults over saved preferences`);
    if (name === 'denied') check($('snacktext').textContent.includes('playback preference read refused'), 'a refused playback read is explained while the saved playlist still restores paused');
  } finally { state.cleanup(); }
}
