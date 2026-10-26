// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Actual page/MessagePort counterparts of Android ManualQueueControllerTest.
import { played, catalogue } from '../web/src/store.js';
import * as archive from '../web/src/catalogue.js';

export default async function checkManualQueue({ window, check }) {
  if (!window.__api?.scheduleNow) throw new Error('Manual-queue page API is missing');
  console.log('\nmanual queue through page actions:');
  const api = window.__api;
  const $ = (id) => window.document.getElementById(id);
  const settle = (ms = 60) => new Promise((resolve) => setTimeout(resolve, ms));
  const command = () => window.__toWorklet.filter((message) => message.type === 'open').at(-1);
  const opens = () => window.__toWorklet.filter((message) => message.type === 'open').length;
  const tap = async (id) => {
    $(id).dispatchEvent(new window.Event('pointerdown'));
    $(id).dispatchEvent(new window.Event('pointerup'));
    $(id).click();
    await settle();
  };
  const opened = (title = 'Manual test') => api.onWorklet({ type: 'opened', requestId: command()?.requestId,
    describe: `title\t${title}`, duration: 90, rate: 44100, subsongs: 1, current: 0, canSeek: true });
  const add = (at) => {
    $('queue').querySelectorAll('li.track')[at].querySelector('.rowmenu').click();
    [...$('menu').children].find((button) => button.textContent === 'Add to queue').click();
  };
  const url = (name) => `https://example.test/manual/${name}.mod`;
  const a = ['A0', 'A1', 'A2'].map((name) => ({ url: url(name), name: `${name}.mod` }));
  const b = ['B0', 'B1'].map((name) => ({ url: url(name), name: `${name}.mod` }));
  if (api.dirtyNow()) await api.saveEdits();
  api.clearPlaybackQueue();
  await api.storeNow.save({ id: 'manual-A', name: 'Manual A', tracks: a, index: 0 });
  await api.storeNow.save({ id: 'manual-B', name: 'Manual B', tracks: b, index: 0 });
  await api.choosePlaylist('manual-A');
  $('queue').querySelector('li.track').click();
  await settle();
  opened('A is playing');
  if ($('shuffle').classList.contains('on')) $('shuffle').click();
  for (let i = 0; i < 3 && $('repeat').title !== 'Repeat off'; i++) $('repeat').click();
  await api.choosePlaylist('manual-B');
  check(!$('nowkeep').hidden, 'the dock offers Add while A plays and B is the selected editor');
  $('nowkeep').click();
  check(api.editorNow().order.tracks.at(-1).url === a[0].url && api.actualNow().url === a[0].url
    && api.sourceNow().id === 'manual-A', 'dock Add edits B and preserves actual A playback');
  await api.discardEdits();
  const commands = window.__toWorklet.length;
  add(0); add(0); add(1);
  check(api.scheduleNow().pending.length === 3 && new Set(api.scheduleNow().pending.map((entry) => entry.id)).size === 3,
    'the actual row menu queues duplicate files as independent FIFO requests');
  check(window.__toWorklet.length === commands && api.actualNow().url === a[0].url && !api.dirtyNow(),
    'enqueue neither changes playback nor edits the selected playlist');
  await tap('next');
  const first = api.scheduleNow().current.id;
  check(command().name === 'B0.mod' && api.actualNow().url === b[0].url && api.indexNow() === 0,
    'Next opens the first manual file while the source remains parked on A0');
  opened('B0 manual');
  check([...$('queue').querySelectorAll('li.track')].every((row) => !row.classList.contains('playing')),
    'a manual occurrence does not lend its highlight to a source/editor row');
  await tap('next');
  check(command().name === 'B0.mod' && api.scheduleNow().current.id !== first,
    'Next opens the second equal file with a different occurrence identity');
  opened();
  await tap('prev');
  check(command().name === 'A0.mod' && api.scheduleNow().pending.length === 1 && !api.scheduleNow().current.manual,
    'Previous returns to the parked context and retains other waiting requests');
  opened();
  await tap('next'); opened();
  check(command().name === 'B1.mod', 'Next drains the remaining manual request before the source');
  await tap('next'); opened();
  check(command().name === 'A1.mod' && api.scheduleNow().pending.length === 0,
    'after the manual FIFO the source continues at its next file');
  await tap('prev'); opened();
  check(command().name === 'A0.mod', 'source Previous never resurrects a consumed manual request');
  add(0);
  for (let i = 0; i < 3 && $('repeat').title !== 'Repeat one'; i++) $('repeat').click();
  await tap('next'); opened();
  add(1);
  const manualId = api.scheduleNow().current.id;
  const beforeReplay = opens();
  api.onWorklet({ type: 'ended', requestId: command().requestId });
  await settle();
  check(opens() === beforeReplay && window.__toWorklet.at(-1).type === 'rewind'
    && api.scheduleNow().current.id === manualId && api.scheduleNow().pending.length === 1,
    'Repeat One natural completion rewinds the actual manual file without draining FIFO');
  await tap('next'); opened();
  check(command().name === 'B1.mod' && api.scheduleNow().pending.length === 0,
    'explicit Next bypasses Repeat One and consumes the waiting manual request');
  for (let i = 0; i < 3 && $('repeat').title !== 'Repeat off'; i++) $('repeat').click();
  const held = api.actualNow();
  const beforeClearPlay = opens();
  api.clearPlaybackQueue();
  check(api.planNow().context.source.kind === 'none' && api.actualNow() === held && opens() === beforeClearPlay,
    'Clear retains the actual occurrence without opening a replacement decoder');
  $('snackundo').click();
  check(api.sourceNow().id === 'manual-A' && api.actualNow() === held && opens() === beforeClearPlay,
    'the actual snackbar Undo restores only the playback plan');
  api.clearPlaybackQueue();
  add(0); add(0);
  await tap('next'); opened();
  await tap('next'); opened();
  api.onWorklet({ type: 'ended', requestId: command().requestId });
  await settle();
  check(api.scheduleNow().consumed && api.planNow().schedule.rows(api.planNow().context).length === 0
    && $('prev').disabled && $('next').disabled, 'manual-only duplicates drain once and stop without restoring cleared context');
  add(1);
  await tap('playpause');
  check(command().name === 'B1.mod' && !api.scheduleNow().consumed,
    'Play after manual-only exhaustion starts the newly queued file');
  opened();
  const beforeFailure = opens();
  api.onWorklet({ type: 'failed', requestId: command().requestId, reason: 'Controlled manual refusal' });
  await tap('playpause');
  check(opens() === beforeFailure + 1 && command().name === 'B1.mod',
    'a failed actual manual occurrence can be retried through Play');

  // Manual History origin follows the requested row, not the suspended source context.
  api.playFromBrowse(a, 0, 'history');
  await settle(); opened();
  api.endSession();
  const beforeCount = (await played.recent()).find((entry) => entry.url === b[0].url)?.playCount ?? 0;
  add(0);
  await tap('next'); opened('Manual outside History'); await settle();
  check((await played.recent()).find((entry) => entry.url === b[0].url)?.playCount === beforeCount + 1,
    'manual playback is recorded even while the parked source is History');
  const h = { url: url('HistoryOnly'), name: 'HistoryOnly.mod', file: 'HistoryOnly.mod', replayable: true };
  await played.record(h);
  const historyBefore = (await played.recent()).find((entry) => entry.url === h.url);
  await api.choosePlaylist('manual-A');
  $('queue').querySelector('li.track').click(); await settle(); opened();
  api.showPanel('browse'); await api.browseTo(['history']);
  const historyRow = [...$('browselist').querySelectorAll('li.btrack')].find((row) => row.dataset.url === h.url);
  check(!!historyRow, 'the History-origin test uses an actual retained History row');
  historyRow.querySelector('.bmore').click();
  [...$('menu').children].find((button) => button.textContent === 'Add to queue').click();
  api.showPanel(null);
  await tap('next'); opened('History manual'); await settle();
  const historyAfter = (await played.recent()).find((entry) => entry.url === h.url);
  check(api.scheduleNow().current.fromHistory && historyAfter.playedAt === historyBefore.playedAt
    && historyAfter.playCount === historyBefore.playCount,
    'a request queued from History replays without moving its stored History occurrence');

  // A failed manually queued file over Random must be retained rather than skipped as a draw.
  api.clearPlaybackQueue();
  const records = archive.toRecords('1\tProtracker/ManualTest/one.mod\n1\tProtracker/ManualTest/two.mod');
  await catalogue.clear('modland:');
  await catalogue.putAll(records.records);
  await catalogue.putAll([{ key: 'modland:meta', tracks: records.tracks, total: records.tracks,
    phoneOnly: 0, buckets: records.buckets, formats: records.formats, fingerprint: 'manual-test' }]);
  await api.openRandom(); await settle(); opened('Random source');
  const heard = api.queueNow().join();
  api.endSession(); add(1); await tap('next');
  const failedOpen = command();
  api.onWorklet({ type: 'failed', requestId: failedOpen.requestId, reason: 'Controlled manual over Random' });
  await settle();
  check(api.actualNow().url === a[1].url && api.scheduleNow().current.manual
    && api.queueNow().join() === heard && $('error').textContent.includes('Controlled manual over Random'),
    'a manual failure over Random keeps its actual occurrence and the heard record');
  const retryCount = opens();
  await tap('playpause');
  check(opens() === retryCount + 1 && command().name === 'A1.mod', 'Play retries that same manual occurrence instead of drawing Random');
  api.clearPlaybackQueue();
  await catalogue.clear('modland:');
  api.showPanel(null);
}
