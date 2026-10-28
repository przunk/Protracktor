// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { catalogue } from '../web/src/store.js';
import * as archive from '../web/src/catalogue.js';

export default async function checkSourceRoutes({ window, check }) {
  const api = window.__api;
  const $ = (id) => window.document.getElementById(id);
  const settle = (ms = 90) => new Promise((resolve) => setTimeout(resolve, ms));
  const deferred = () => {
    let resolve;
    const promise = new Promise((done) => { resolve = done; });
    return { promise, resolve };
  };
  const seed = async () => {
    api.showPanel(null);
    api.clearPlaybackQueue();
    if (api.dirtyNow()) await api.discardEdits();
    await catalogue.clear('modland:'); await catalogue.clear('asma:');
    const parsed = archive.toRecords('1\tProtracker/Navigation/alpha.mod\n1\tProtracker/Navigation/beta.mod');
    await catalogue.putAll(parsed.records);
    await catalogue.putAll([{ key: 'modland:meta', tracks: parsed.tracks, total: parsed.tracks,
      complete: true, buckets: parsed.buckets, formats: parsed.formats, fingerprint: 'navigation-routes' }]);
  };
  await seed();
  api.showPanel('browse'); await api.browseTo(['search']);
  $('browsesearch').value = 'Navigation'; $('browsesearch').dispatchEvent(new window.Event('input', { bubbles: true }));
  $('browsesubmit').click();
  for (let tries = 0; tries < 200 && $('browselist').querySelectorAll('.btrack').length !== 2; tries++) await settle(10);
  const originalResults = [...$('browselist').querySelectorAll('.btrack')].map((row) => row.dataset.url);
  check(originalResults.length === 2, 'the source-route fixture uses actual indexed search results');
  const row = $('browselist').querySelector('.btrack');
  $('browsesearch').value = 'Waiting query';
  $('browsesearch').dispatchEvent(new window.Event('input'));
  row.click(); await settle();
  check(api.sourceNow().query === 'Navigation' && api.planNow().context.route.query === 'Navigation',
    'a source captures the query which produced its rows, rather than newly typed pending text');
  api.showPanel(null); api.endSession();
  const searchPlan = api.planNow(), searchCommands = window.__toWorklet.length;
  $('showqueue').click(); $('queue-source').click(); await settle(300);
  check(!$('browse').hidden && $('browsesearch').value === 'Navigation'
    && [...$('browselist').querySelectorAll('.btrack')].map((entry) => entry.dataset.url).join() === originalResults.join()
    && api.planNow().context === searchPlan.context && api.scheduleNow() === searchPlan.schedule
    && window.__toWorklet.length === searchCommands,
    'Search source return restores its submitted query/results and retires newly typed text without playback');

  const slowPath = ['modland', 'Protracker', 'Slow source'];
  const slowKey = 'modland:tracks:Protracker/Slow source';
  await catalogue.putAll([{ key: slowKey, tracks: [{ t: 'unrelated.mod', s: 64 }] }]);
  const entered = deferred(), release = deferred();
  const originalGet = api.catalogueStore.get;
  let held = false;
  api.catalogueStore.get = async (key) => {
    const result = await originalGet(key);
    if (key === slowKey && !held) { held = true; entered.resolve(); await release.promise; }
    return result;
  };
  try {
    const pending = api.browseTo(slowPath);
    await entered.promise;
    await catalogue.putAll([{ key: 'modland:meta', tracks: 999, total: 999, complete: true,
      buckets: {}, formats: 1, fingerprint: 'new-live-resource' }]);
    const before = window.__toWorklet.length;
    $('showqueue').click(); $('queue-source').click(); await settle();
    release.resolve(); await pending; await settle();
    check($('browsetitle').textContent === 'Search' && $('browsesearch').value === 'Navigation'
      && [...$('browselist').querySelectorAll('.btrack')].map((entry) => entry.dataset.url).join() === originalResults.join()
      && (await api.archiveMeta('modland')).tracks === 999 && window.__toWorklet.length === before,
      'a restored source retires a late folder read and preserves newer index/storage knowledge');
  } finally { release.resolve(); api.catalogueStore.get = originalGet; }
  $('browseback').click(); await settle();
  check($('browsetitle').textContent === 'Browse' && $('browsesearch').value === 'Navigation'
    && window.__toWorklet.length === searchCommands,
    'Back from a restored Everywhere search returns to Browse root in one action');

  await seed();
  await api.rerollRandom(); await settle();
  const opened = window.__toWorklet.filter((message) => message.type === 'open').at(-1);
  api.onWorklet({ type: 'opened', requestId: opened.requestId, describe: 'title\tRemembered source', duration: 90,
    rate: 44100, subsongs: 1, current: 0, canSeek: true });
  api.enqueueTrack({ name: 'Manual over Random', url: 'https://example.test/source-random/manual.mod' });
  $('next').dispatchEvent(new window.Event('pointerdown'));
  $('next').dispatchEvent(new window.Event('pointerup'));
  $('next').click(); await settle();
  api.endSession();
  api.showPanel('browse'); await api.browseTo(['history']); api.showPanel(null);
  const heard = api.queueNow().join(), beforeRandom = api.planNow(), beforeRandomCommands = window.__toWorklet.length;
  const session = api.randomState().sessionId;
  $('showqueue').click(); $('queue-source').click(); await settle();
  check($('playback-panel').hidden && !api.editorShown() && api.sourceNow().kind === 'random'
    && api.randomState().sessionId === session && api.queueNow().join() === heard
    && api.planNow().context === beforeRandom.context && api.scheduleNow() === beforeRandom.schedule
    && window.__toWorklet.length === beforeRandomCommands && api.scheduleNow().current.manual,
    'Random source return reveals its existing list while retaining a manual occurrence and the session');
  check(window.document.activeElement === $('sessiontitle'), 'Random source navigation leaves keyboard focus on the visible session');
  $('random-leave').click(); await settle();
  check(!$('browse').hidden && $('browsetitle').textContent === 'Browse'
    && window.__toWorklet.length === beforeRandomCommands,
    'Back from a returned Random source goes to Browse root without stopping playback');
  $('browseclose').click(); $('showqueue').click();
  api.randomState().sessionId = session + 1;
  $('queue-source').click(); await settle();
  check(!$('playback-panel').hidden && $('snacktext').textContent === 'The playback source is no longer available.'
    && api.queueNow().join() === heard && window.__toWorklet.length === beforeRandomCommands,
    'a missing remembered Random session is explained without rerolling or fabricating a source');
  api.randomState().sessionId = session;
  api.showPanel(null);
}
