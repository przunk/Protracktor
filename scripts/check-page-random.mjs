// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// RandomIsNotSearch, RandomScreen and actual PlaybackController navigation/advance paths.
import { catalogue, played } from '../web/src/store.js';
import * as archive from '../web/src/catalogue.js';

export default async function checkRememberedRandom({ window, check }) {
  console.log('\nremembered Random through actual page actions:');
  const api = window.__api, $ = (id) => window.document.getElementById(id);
  const settle = (ms = 90) => new Promise((resolve) => setTimeout(resolve, ms));
  const bounded = (promise) => Promise.race([promise, new Promise((_, reject) => window.setTimeout(() => reject(new Error('Random page boundary was not reached')), 10000))]);
  const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; };
  const lastOpen = () => window.__toWorklet.filter((message) => message.type === 'open').at(-1);
  const opened = () => api.onWorklet({ type: 'opened', requestId: lastOpen()?.requestId,
    describe: 'title\tRemembered Random', subsongs: 1, current: 0, duration: 90, rate: 44100, canSeek: true });
  const tap = async (id) => {
    $(id).dispatchEvent(new window.Event('pointerdown')); $(id).dispatchEvent(new window.Event('pointerup'));
    $(id).click(); await settle();
  };
  const enter = async () => {
    api.showPanel('browse'); await api.browseTo([]);
    [...$('browselist').children].find((row) => row.querySelector('.bname')?.textContent === 'Random').click();
    await settle();
  };
  const rows = () => [...$('queue').querySelectorAll('li.track')];
  const reroll = async () => { $('random-dice').dispatchEvent(new window.MouseEvent('dblclick', { bubbles: true, detail: 2 })); await settle(); };
  api.showPanel(null); api.clearPlaybackQueue();
  if (api.dirtyNow()) await api.discardEdits();
  await catalogue.clear('modland:'); await catalogue.clear('asma:');
  const built = archive.toRecords(['a', 'b', 'c', 'd'].map((name) => `1\tProtracker/Remembered/${name}.mod`).join('\n'));
  await catalogue.putAll(built.records);
  await catalogue.putAll([{ key: 'modland:meta', tracks: built.tracks, total: built.tracks,
    buckets: built.buckets, formats: built.formats, fingerprint: 'remembered-random' }]);
  api.useRandomSource(() => 0);
  await api.rerollRandom(); await settle(); opened();
  const initial = api.randomState();
  check(initial.history.playedPicks.length === 1 && initial.history.upcoming(3).length === 3
    && new Set(initial.history.upcoming(3).map((track) => track.url)).size === 3 && rows().length === 1,
    'read-ahead holds three invisible picks while the actual Random list contains only one heard row');
  await tap('next'); opened(); await tap('next'); opened();
  const record = initial.history.playedPicks;
  check(record.length === 3 && new Set(record.map((track) => track.url)).size === 3,
    'successive foreground advances publish exactly one unique heard occurrence at a time');
  api.endSession();
  const plan = api.planNow(), before = window.__toWorklet.length;
  await enter();
  check(api.randomState() === initial && api.planNow().context === plan.context && api.scheduleNow() === plan.schedule
    && initial.history.playedPicks === record && window.__toWorklet.length === before,
    'Browse → remembered Random reveals the same session without drawing, selecting or sending audio commands');
  check(!$('randomhint').hidden && $('randomhint').textContent === 'Double-tap the dice to reroll the list'
    && !$('random-dice').hidden && $('sessionicon').hidden, 'the Random view exposes one labelled dice action and the agreed double-tap hint');
  $('random-leave').click(); await settle();
  check(!$('browse').hidden && $('browsetitle').textContent === 'Browse' && window.__toWorklet.length === before,
    'Back from ordinary Random returns one level to Browse without ending the active context');
  api.showPanel(null);
  const authors = ['author0', 'author1', 'author2'].map((name) => ({ url: `https://example.test/remembered/${name}.mod`, name: `${name}.mod` }));
  api.openAway(authors, 0, 'browse'); await settle(); opened(); api.endSession();
  const authorPlan = api.planNow(), authorCommands = window.__toWorklet.length;
  await enter();
  check(api.randomState() == null && api.rememberedRandomNow() === initial && api.visibleTracksNow() === record
    && api.planNow().context === authorPlan.context && api.scheduleNow() === authorPlan.schedule
    && window.__toWorklet.length === authorCommands,
    'Random → author playback → playlist → Random leaves the author as the sole playback owner');
  check(api.visibleIndexNow() === -1 && rows().every((row) => !row.matches('.playing,.selected,.loading'))
    && $('sessiontitle').textContent === 'Random', 'an inactive Random record borrows no author highlight or Playing heading');
  $('random-leave').click(); await settle();
  check(!$('browse').hidden && $('browsetitle').textContent === 'Browse' && !$('browseclose').hidden
    && $('browsedigression').hidden && window.__toWorklet.length === authorCommands,
    'Back from inactive Random reveals a normal Browse root without an active author detour leaking into navigation');
  await enter();
  await tap('next'); opened();
  check(api.actualNow().url === authors[1].url && api.sourceNow().origin === 'browse' && initial.history.playedPicks === record,
    'Next while viewing inactive Random advances the actual author context and does not draw a pick');
  rows()[0].querySelector('.rowmenu').click();
  [...$('menu').children].find((button) => button.textContent === 'Remove from this list').click();
  check(initial.history.playedPicks.length === 2 && api.actualNow().url === authors[1].url && api.queueNow().length === 3,
    'a Random menu removes the visible heard position without deleting or replacing an active author row');
  rows()[0].click(); await settle(); opened();
  check(api.randomState() === initial && api.actualNow().url === initial.history.playedPicks[0].url
    && api.sourceNow().kind === 'random' && api.visibleIndexNow() === 0,
    'only an explicit remembered-row tap activates that Random session at its chosen occurrence');
  await tap('next'); opened();
  check(initial.history.cursor === 1 && initial.history.playedPicks.length === 2,
    'Next first traverses the remaining heard record rather than replacing it with read-ahead');
  const anchor = api.actualNow();
  api.enqueueTrack({ url: 'https://example.test/remembered/manual.mod', name: 'Manual' });
  await tap('next'); opened();
  check(api.scheduleNow().current.manual && api.visibleIndexNow() === -1 && $('sessiontitle').textContent === 'Random',
    'a manual occurrence parks Random without highlighting a heard row or claiming Random is playing');
  await tap('prev'); opened();
  check(api.actualNow().url === anchor.url && !api.scheduleNow().current.manual && initial.history.cursor === 1,
    'Previous from manual playback restores the parked Random occurrence and synchronizes its heard cursor');

  // Clear can run in the first microtask gap after a prepared heard-row selection.
  const selectedPick = initial.history.playedPicks[0], ahead = api.aheadNow(), read = ahead.read;
  ahead.read = async () => new ArrayBuffer(24);
  try { await ahead.prepare([selectedPick]); } finally { ahead.read = read; }
  rows()[0].click(); api.clearPlaybackQueue();
  const clearLoad = api.actualNow();
  await settle();
  check(api.sourceNow().kind === 'none' && api.actualNow() === clearLoad && lastOpen().bytes.byteLength === 24,
    'Clear in the AudioContext start gap preserves the already adopted Random buffer');
  opened(); $('snackundo').click(); await settle();

  // Active History must not lend replay-without-recording semantics to a visible Random row.
  api.openAway(authors, 0, 'history'); await settle(); opened();
  await enter();
  rows()[0].querySelector('.rowmenu').click();
  [...$('menu').children].find((button) => button.textContent === 'Add to queue').click();
  check(api.sourceNow().origin === 'history' && api.scheduleNow().pending.at(-1).fromHistory === false,
    'a request from visible Random has ordinary origin even while History owns actual playback');
  api.clearPlaybackQueue();
  const retained = initial.history.playedPicks, historyBefore = await played.count();
  await enter(); const clearedCommands = window.__toWorklet.length;
  check(api.visibleTracksNow() === retained && api.sourceNow().kind === 'none',
    'Clear leaves the heard Random record available as navigation without restoring its cleared source');
  $('random-dice').dispatchEvent(new window.MouseEvent('click', { bubbles: true, detail: 1 })); await settle();
  check(window.__toWorklet.length === clearedCommands && api.rememberedRandomNow() === initial,
    'a single real pointer click on the dice does not reroll or start playback');
  await reroll(); opened();
  const rerolled = api.randomState();
  check(rerolled !== initial && rerolled?.sessionId === initial.sessionId + 1 && rerolled.history.playedPicks.length === 1,
    'the actual dice double-click creates a fresh heard record and session');
  check(await played.count() >= historyBefore, 'resetting a Random list does not clear the independent global History');
  api.enqueueTrack({ url: 'https://example.test/remembered/pending.mod', name: 'Pending across reroll' });
  const pending = api.scheduleNow().pending[0].id;
  await reroll(); opened();
  check(api.scheduleNow().pending[0]?.id === pending && api.randomState().history.playedPicks.length === 1,
    'deliberate reroll replaces Random while retaining already waiting manual request identities');
  api.clearPlaybackQueue();
  const clearPlan = api.planNow(), clearedRecord = api.visibleTracksNow(), clearCommands = window.__toWorklet.length;
  $('snackundo').click(); await settle();
  check(api.sourceNow().kind === 'random' && api.visibleTracksNow() === clearedRecord && api.actualNow().url === clearPlan.schedule.current.track.url && window.__toWorklet.length === clearCommands,
    'Clear/Undo restores the plan around the same heard record without reopening the actual occurrence');

  // Both draws are held at the production catalogue boundary. The old finalizer must not retire the new one.
  api.clearPlaybackQueue(); await reroll(); opened();
  const old = api.randomState(); old.history.changeScope(); old.table = null;
  const originalGet = api.catalogueStore.get, oldEntered = deferred(), newEntered = deferred(), oldRead = deferred(), newRead = deferred();
  let reads = 0;
  api.catalogueStore.get = async (key) => {
    const result = await originalGet(key);
    if (key === 'modland:formats') {
      if (reads++ === 0) { oldEntered.resolve(); await oldRead.promise; }
      else if (reads === 2) { newEntered.resolve(); await newRead.promise; }
    }
    return result;
  };
  try {
    const advancing = tap('next'); await bounded(oldEntered.promise);
    $('random-dice').dispatchEvent(new window.MouseEvent('dblclick', { bubbles: true, detail: 2 }));
    await bounded(newEntered.promise);
    oldRead.resolve(); await advancing; await settle();
    check(api.randomState() !== old && api.randomState().history.playedPicks.length === 0 && $('next').disabled,
      'a superseded draw cannot append old rows or clear the newer session draw busy state');
    newRead.resolve(); await settle(); opened();
    check(api.randomState().history.playedPicks.length === 1 && !$('next').disabled,
      'only the replacement session publishes its first heard row when its own catalogue read completes');
  } finally { oldRead.resolve(); newRead.resolve(); api.catalogueStore.get = originalGet; }

  await catalogue.clear('modland:');
  const single = archive.toRecords('1\tProtracker/Single/only.mod');
  await catalogue.putAll(single.records);
  await reroll(); opened(); await tap('next'); opened(); await tap('next'); opened();
  const duplicateIds = api.planNow().context.entryIds;
  check(api.randomState().history.playedPicks.length === 3 && new Set(api.queueNow()).size === 1
    && new Set(duplicateIds).size === 3, 'a small exhausted pool permits repeated files with distinct source occurrence identities');
  const audible = api.actualNow(), commandsBeforePrune = window.__toWorklet.length;
  rows()[1].querySelector('.rowmenu').click();
  [...$('menu').children].find((button) => button.textContent === 'Remove from this list').click();
  check(api.planNow().context.entryIds.join() === [duplicateIds[0], duplicateIds[2]].join()
    && api.indexNow() === 1 && api.actualNow() === audible && window.__toWorklet.length === commandsBeforePrune,
    'pruning the middle duplicate preserves the final audible occurrence ID and never restarts audio');

  // An exhausted remembered session is still a real session, not a request to try playing again.
  await catalogue.clear('modland:'); await catalogue.clear('asma:');
  await reroll(); const empty = api.rememberedRandomNow();
  check(empty.history.playedPicks.length === 0 && !$('randomnote').hidden, 'an empty first draw explains the unavailable Random source');
  api.endSession(); const emptyCommands = window.__toWorklet.length;
  await enter();
  check(api.rememberedRandomNow() === empty && rows().length === 0 && window.__toWorklet.length === emptyCommands,
    'reentering even an exhausted empty Random session is navigation without a hidden retry');
  api.endSession(); api.useRandomSource(Math.random);
}
