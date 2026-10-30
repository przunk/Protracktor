// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import * as archive from '../web/src/catalogue.js';

export default async function checkLearnedTitles({ window, check }) {
  console.log('\nLearned titles through History and actual page actions:');
  const api = window.__api, played = window.__played, $ = (id) => window.document.getElementById(id);
  const settle = (ms = 90) => new Promise((resolve) => setTimeout(resolve, ms));
  const latest = (type) => window.__toWorklet.filter((message) => message.type === type).at(-1);
  const send = (message) => window.__workletPort.onmessage({ data: { ...message, requestId: latest('open')?.requestId } });
  const menu = async (row, name) => { row.querySelector('.bmore, .rowmenu').click(); [...$('menu').querySelectorAll('button')].find((button) => button.textContent === name).click(); await settle(); };
  const a = { url: archive.urlFor('Protracker', 'Titles', 'a.mod'), name: 'a.mod', file: 'a.mod', meta: 'Modland/Protracker/Titles' };
  const z = { url: archive.urlFor('Protracker', 'Titles', 'z.mod'), name: 'z.mod', file: 'z.mod', meta: 'Modland/Protracker/Titles' };
  const rows = () => [...$('browselist').querySelectorAll('.btrack')];
  api.showPanel(null); api.clearPlaybackQueue(); await played.clear();
  const built = archive.toRecords('1\tProtracker/Titles/a.mod\n1\tProtracker/Titles/z.mod', 'modland', () => true);
  await api.catalogueStore.replacePrefix('modland:', [...built.records, { key: 'modland:meta', tracks: 2, total: 2, complete: true }]);
  await api.storeNow.save({ id: 'title-document', name: 'Title document', tracks: [] }); await api.switchTo('title-document');
  api.showPanel('browse'); await api.browseTo(['modland', 'Protracker', 'Titles']); await settle();
  const originalRows = rows(); $('browselist').scrollTop = 80;
  check(originalRows.map((row) => row.querySelector('.bname').textContent).join('|') === 'a.mod|z.mod',
    'unknown Browse tracks start with catalogue names in catalogue order');
  await played.record({ ...a, name: 'Zulu', replayable: true }); await played.record({ ...z, name: 'Alpha', replayable: true }); await settle();
  check(rows()[0] === originalRows[0] && rows()[1] === originalRows[1] && $('browselist').scrollTop === 80
    && rows().map((row) => row.querySelector('.bname').textContent).join('|') === 'Zulu|Alpha',
    'History refresh improves existing row titles without sorting rebuilding or scrolling their list');
  check((await played.recent()).every((row) => !row.listened) && rows()[0].querySelector('.bmeta').textContent.endsWith('/a.mod'),
    'a decoded title appears before qualification and leaves the original filename in its source line');
  check((await archive.tracksIn('Protracker', 'Titles')).map((track) => track.name).join('|') === 'a.mod|z.mod',
    'the title overlay never writes back into the catalogue sort fields');

  rows()[0].querySelector('.badd').click(); await settle();
  check(api.editorNow().order.tracks[0].name === 'Zulu' && api.editorNow().order.tracks[0].file === 'a.mod',
    'the actual Browse Add keeps the displayed title and original decoder filename');
  await api.saveEdits();
  await menu(rows()[0], 'Add to queue');
  check(api.scheduleNow().pending.at(-1).track.name === 'Zulu' && api.scheduleNow().pending.at(-1).track.file === 'a.mod',
    'the actual Browse enqueue action also uses a display copy with its filename intact');
  api.clearPlaybackQueue();
  rows()[0].click(); await settle();
  check(latest('open').name === 'a.mod' && api.queueNow().join('|') === [a.url, z.url].join('|') && api.indexNow() === 0,
    'tapping the improved title still selects the original collection index and opens a.mod');
  window.document.body.dispatchEvent(new window.Event('pointerdown', { bubbles: true })); await settle(0);
  send({ type: 'opened', describe: 'title\tZulu', duration: 90, subsongs: 2, current: 0, rate: 44100 }); await settle();
  const information = menu(rows()[0], 'Information'); await information;
  check(latest('describe').name === 'a.mod', 'Information reads the original filename after the row title has improved');
  send({ type: 'described', id: latest('describe').id, ok: true, describe: 'title\tZulu', subsongs: 2 }); await settle();

  // A display copy must still export the currently selected subsong of the same file.
  send({ type: 'subsong', index: 1, duration: 90, describe: 'title\tSubsong two' });
  const Worker = window.Worker, canShare = window.navigator.canShare;
  window.navigator.canShare = () => true;
  const priorWorker = window.__exportWorker(); let exported;
  const priorPost = priorWorker?.postMessage;
  if (priorWorker) priorWorker.postMessage = function (message, ...rest) { exported = message; return priorPost.call(this, message, ...rest); };
  else window.Worker = class { postMessage(message) { exported = message; queueMicrotask(() => this.onmessage?.({ data: { ok: true, bytes: new ArrayBuffer(12) } })); } };
  try {
    await api.shareAsAudio({ ...a, name: 'Zulu' });
    check(exported?.name === 'a.mod' && exported?.subsong === 1,
      'an audio export of the displayed copy keeps both its original filename and actual current subsong');
  } finally { if (priorWorker) priorWorker.postMessage = priorPost; window.Worker = Worker; window.navigator.canShare = canShare; }

  api.showPanel('browse'); await api.browseTo(['modland', 'Protracker', 'Titles']); await settle();
  await menu(rows()[0], 'Add to queue');
  const pending = api.scheduleNow().pending, context = api.planNow().context;
  api.showPanel(null); $('showqueue').click(); await settle();
  const beforeNodes = [...$('playback-list').children]; $('playback-list').scrollTop = 60;
  await played.record({ ...a, name: 'Updated title', replayable: true }); await settle();
  const afterNodes = [...$('playback-list').children];
  check(afterNodes.length === beforeNodes.length && afterNodes.every((node, i) => node === beforeNodes[i]) && $('playback-list').scrollTop === 60,
    'Queue title refresh retains keyed nodes and its current scroll position');
  check(afterNodes.filter((node) => node.dataset.url === a.url).every((node) => node.querySelector('.title').textContent === 'Updated title')
    && api.planNow().context === context && api.scheduleNow().pending === pending && pending.at(-1).track.name === 'Zulu',
    'Queue improves all equal-URL occurrences without mutating the source or a manually added snapshot');
  const clear = played.clear();
  check($('playback-list').querySelector(`[data-kind="source"][data-url="${a.url}"] .title`).textContent === 'a.mod',
    'ordered History Clear removes a visible Queue source overlay synchronously');
  await clear; await settle();
  check(api.planNow().context === context && api.scheduleNow().pending === pending,
    'clearing learned presentation never changes the playing context or manual FIFO');

  api.showPanel('browse'); await api.browseTo(['modland', 'Protracker', 'Titles']); await settle();
  check(rows().map((row) => row.querySelector('.bname').textContent).join('|') === 'a.mod|z.mod',
    'Browse falls back to catalogue names after History Clear');

  // A retained Random record keeps duplicate occurrence positions even when both labels improve.
  api.clearPlaybackQueue();
  send({ type: 'ready', backends: 'openmpt:check;uade:none' });
  await api.catalogueStore.clear('asma:');
  await api.catalogueStore.replacePrefix('modland:', [...archive.toRecords('1\tProtracker/Titles/a.mod', 'modland', () => true).records,
    { key: 'modland:meta', tracks: 1, total: 1, complete: true }]);
  api.setRandomScope('everything'); await api.rerollRandom(); api.showPanel(null); await settle();
  send({ type: 'opened', describe: 'title\tRandom title', duration: 90, subsongs: 1, current: 0, rate: 44100 });
  $('next').dispatchEvent(new window.Event('pointerdown')); $('next').dispatchEvent(new window.Event('pointerup')); $('next').click(); await settle();
  send({ type: 'opened', describe: 'title\tRandom title', duration: 90, subsongs: 1, current: 0, rate: 44100 }); await settle();
  const picks = api.rememberedRandomNow().history.playedPicks;
  check(picks.length === 2 && picks[0].name === 'a.mod' && picks[1].name === 'a.mod'
    && [...$('queue').querySelectorAll('.title')].map((node) => node.textContent).join('|') === 'Random title|Random title',
    'Random shows the learned title on duplicate occurrences without modifying its raw heard record');
  check($('browse').hidden && !window.document.querySelector('main').hidden, 'Random is visible before its Select event is driven');
  $('queue').querySelector('.rowmenu').click(); [...$('menu').querySelectorAll('button')].find((button) => button.textContent === 'Select').click(); await settle();
  check(!$('selectbar').hidden && $('queue').querySelector('.tick')?.checked,
    'the actual Random Select action retains raw occurrence references while displaying learned titles');
  window.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape' })); await settle();
  await api.switchTo('title-document'); api.showPanel(null); await settle();
  check($('queue').querySelector('.title').textContent === 'Zulu', 'the selected playlist keeps its saved title independently of the live History overlay');
}
